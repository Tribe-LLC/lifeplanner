package az.tribe.lifeplanner.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.analytics.Analytics
import az.tribe.lifeplanner.data.analytics.FacebookAnalytics
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.di.getPlatform
import az.tribe.lifeplanner.data.auth.AuthResult
import az.tribe.lifeplanner.data.auth.AuthService
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.domain.model.User
import az.tribe.lifeplanner.domain.repository.UserRepository
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * Authentication state
 */
sealed class AuthState {
    data object Loading : AuthState()
    data object Unauthenticated : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data class Guest(val user: User) : AuthState()
    /** Email signup succeeded but the user must verify their email before signing in. */
    data class EmailVerificationPending(val email: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * ViewModel for authentication (Supabase-based)
 */
class AuthViewModel(
    private val authService: AuthService,
    private val userRepository: UserRepository,
    private val syncManager: SyncManager,
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    companion object {
        /** Global channel for deep link auth errors (e.g. expired magic link). */
        private val _deepLinkError = MutableSharedFlow<String>(extraBufferCapacity = 1)

        /** Called from platform DeepLinkHandler when the callback URL contains an error. */
        fun reportDeepLinkError(message: String) {
            _deepLinkError.tryEmit(message)
        }
    }

    private val settings = Settings()
    private val PENDING_VERIFY_EMAIL_KEY = "pending_verification_email"

    /** Serializes all auth state mutations to prevent race conditions between
     *  checkAuthStatus, observeSessionChanges, and observeDeepLinkErrors. */
    private val authMutex = Mutex()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow<Boolean?>(null)
    val hasCompletedOnboarding: StateFlow<Boolean?> = _hasCompletedOnboarding.asStateFlow()

    /** True when anonymous auth failed and user is in local-only mode (no sync). */
    private val _isLocalOnlyGuest = MutableStateFlow(false)
    val isLocalOnlyGuest: StateFlow<Boolean> = _isLocalOnlyGuest.asStateFlow()

    /** True when a magic link has been sent and user can enter OTP. */
    private val _magicLinkSent = MutableStateFlow(false)
    val magicLinkSent: StateFlow<Boolean> = _magicLinkSent.asStateFlow()

    /** Email that has been linked but not yet verified (for banner status). */
    private val _pendingVerificationEmail = MutableStateFlow<String?>(null)
    val pendingVerificationEmail: StateFlow<String?> = _pendingVerificationEmail.asStateFlow()

    /** Transient success message shown briefly after operations like account linking. */
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    init {
        checkAuthStatus()
        observeSessionChanges()
        observeDeepLinkErrors()
    }

    /**
     * Listen for Supabase session changes (e.g. deep link auth callback).
     * When a new session is established externally (handleDeeplinks), refresh the auth state.
     */
    private fun observeSessionChanges() {
        viewModelScope.launch {
            supabaseClient.auth.sessionStatus.collect { status ->
                Logger.d("AuthViewModel") { "Session status changed: $status" }
                when (status) {
                    is SessionStatus.Authenticated -> authMutex.withLock {
                        // React to session changes unless already authenticated with matching user
                        val current = _authState.value
                        // Skip if a login/linking operation is in progress — it will set
                        // the auth state itself once it finishes with the correct data.
                        if (current is AuthState.Loading) {
                            Logger.d("AuthDebug") { "observeSession: Authenticated event while Loading — skipping (login in progress)" }
                            return@withLock
                        }
                        val supabaseUid = supabaseClient.auth.currentUserOrNull()?.id
                        val rawEmail = supabaseClient.auth.currentUserOrNull()?.email
                        Logger.d("AuthDebug") { "observeSession: Authenticated event, uid=$supabaseUid, rawEmail='$rawEmail', currentState=${current::class.simpleName}" }
                        val currentUid = when (current) {
                            is AuthState.Authenticated -> current.user.firebaseUid
                            is AuthState.Guest -> current.user.firebaseUid
                            else -> null
                        }
                        val alreadyMatchingAuth = currentUid != null && currentUid == supabaseUid
                        Logger.d("AuthDebug") { "observeSession: currentUid=$currentUid, alreadyMatching=$alreadyMatchingAuth" }
                        if (!alreadyMatchingAuth) {
                            val user = supabaseClient.auth.currentUserOrNull()
                            if (user != null) {
                                val email = user.email?.takeIf { it.isNotBlank() }
                                Logger.d("AuthDebug") { "observeSession: resolvedEmail=${email}, isGuest=${email == null}" }
                                val localUser = findOrCreateLocalUser(
                                    uid = user.id,
                                    email = email,
                                    displayName = user.userMetadata?.get("display_name")
                                        ?.toString()?.removeSurrounding("\"") ?: "User",
                                    isGuest = email == null
                                )
                                _isLocalOnlyGuest.value = false
                                if (email == null) {
                                    identifyInPostHog(localUser)
                                    _authState.value = AuthState.Guest(localUser)
                                } else {
                                    setAuthenticatedAndSync(localUser)
                                }
                                Logger.d("AuthViewModel") { "Deep link auth completed for ${user.id}" }
                            }
                        }
                    }
                    is SessionStatus.NotAuthenticated -> {
                        Logger.d("AuthDebug") { "observeSession: NotAuthenticated event, currentState=${_authState.value::class.simpleName}" }
                        // Don't overwrite loading or other transient states
                    }
                    else -> {}
                }
            }
        }
    }

    /**
     * Listen for deep link auth errors (e.g. expired magic link) and surface them.
     */
    private fun observeDeepLinkErrors() {
        viewModelScope.launch {
            _deepLinkError.collect { errorMessage ->
                authMutex.withLock {
                    Logger.w("AuthViewModel") { "Deep link auth error: $errorMessage" }
                    _magicLinkSent.value = false
                    _authState.value = AuthState.Error(errorMessage)
                }
            }
        }
    }

    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    /**
     * Check if user is already authenticated.
     * Tries to restore the Supabase session first, then reconciles with local DB.
     */
    private fun checkAuthStatus() {
        viewModelScope.launch {
            try {
                val supabaseUser = try {
                    authService.restoreSession()
                } catch (e: Exception) {
                    Logger.w("AuthViewModel", e) { "Session restore failed - ${e.message}" }
                    null
                }

                val localUser = userRepository.getCurrentUser()

                Logger.d("AuthDebug") { "checkAuthStatus: supabaseUser=${supabaseUser != null}, email='${supabaseUser?.email}', isAnonymous=${supabaseUser?.isAnonymous}" }
                Logger.d("AuthDebug") { "checkAuthStatus: localUser=${localUser != null}, localEmail='${localUser?.email}', isGuest=${localUser?.isGuest}, uid=${localUser?.firebaseUid}" }

                authMutex.withLock {
                    when {
                        // Both exist and match — use local user (preserves onboarding data)
                        supabaseUser != null && localUser != null && localUser.firebaseUid == supabaseUser.uid -> {
                            val reconciledEmail = supabaseUser.email
                            val reconciledGuest = supabaseUser.isAnonymous
                            val reconciledUser = if (localUser.email != reconciledEmail || localUser.isGuest != reconciledGuest) {
                                val updated = localUser.copy(email = reconciledEmail, isGuest = reconciledGuest)
                                userRepository.updateUser(updated)
                                Logger.d("AuthDebug") { "checkAuthStatus: reconciled local user email='${reconciledEmail}', isGuest=$reconciledGuest" }
                                updated
                            } else {
                                localUser
                            }
                            _hasCompletedOnboarding.value = reconciledUser.hasCompletedOnboarding
                            _isLocalOnlyGuest.value = false
                            val state = if (reconciledGuest) {
                                AuthState.Guest(reconciledUser)
                            } else {
                                AuthState.Authenticated(reconciledUser)
                            }
                            Logger.d("AuthDebug") { "checkAuthStatus: matched → ${state::class.simpleName}, email='${reconciledUser.email}'" }
                            identifyInPostHog(reconciledUser)
                            _authState.value = state
                        }
                        // Supabase session exists but no matching local user — recreate locally
                        supabaseUser != null -> {
                            val recreated = User(
                                id = supabaseUser.uid,
                                firebaseUid = supabaseUser.uid,
                                email = supabaseUser.email,
                                displayName = supabaseUser.displayName ?: if (supabaseUser.isAnonymous) "Guest User" else "User",
                                isGuest = supabaseUser.isAnonymous,
                                hasCompletedOnboarding = false,
                                createdAt = Clock.System.now()
                            )
                            // Atomic: clear + create in sequence under the lock
                            userRepository.deleteAllUsers()
                            userRepository.createUser(recreated)
                            _hasCompletedOnboarding.value = false
                            _isLocalOnlyGuest.value = false
                            val state = if (supabaseUser.isAnonymous) {
                                AuthState.Guest(recreated)
                            } else {
                                AuthState.Authenticated(recreated)
                            }
                            Logger.d("AuthDebug") { "checkAuthStatus: recreated → ${state::class.simpleName}, email='${recreated.email}'" }
                            identifyInPostHog(recreated)
                            _authState.value = state
                        }
                        // No Supabase session but local user exists — degraded (local-only) mode
                        localUser != null -> {
                            _hasCompletedOnboarding.value = localUser.hasCompletedOnboarding
                            _isLocalOnlyGuest.value = true
                            identifyInPostHog(localUser)
                            _authState.value = if (localUser.isGuest) {
                                AuthState.Guest(localUser)
                            } else {
                                AuthState.Authenticated(localUser)
                            }
                        }
                        // Neither — check for pending verification before showing Unauthenticated
                        else -> {
                            _hasCompletedOnboarding.value = false
                            val pendingEmail = settings.getStringOrNull(PENDING_VERIFY_EMAIL_KEY)
                            if (pendingEmail != null) {
                                Logger.d("AuthDebug") { "Recovered pending verification for $pendingEmail" }
                                _authState.value = AuthState.EmailVerificationPending(pendingEmail)
                            } else {
                                _authState.value = AuthState.Unauthenticated
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                authMutex.withLock {
                    _authState.value = AuthState.Error(e.message ?: "Unknown error")
                }
            }
        }
    }

    /**
     * Set authenticated state and immediately trigger sync.
     * This ensures sync runs AFTER findOrCreateLocalUser() completes (no race condition).
     */
    private fun setAuthenticatedAndSync(user: User) {
        settings.remove(PENDING_VERIFY_EMAIL_KEY) // Clear pending verification
        _authState.value = AuthState.Authenticated(user)
        identifyInPostHog(user)
        // Launch on SyncManager's own scope so it survives ViewModel cancellation
        syncManager.launchFullSync(resetRetry = true)
    }

    /**
     * Identify a user (guest or authenticated) in PostHog.
     * Call this whenever the user identity is established or changes.
     */
    private fun identifyInPostHog(user: User) {
        PostHogAnalytics.identify(user.id, buildMap {
            put("email", user.email ?: "")
            put("display_name", user.displayName ?: "")
            put("is_guest", user.isGuest)
        })
        PostHogAnalytics.setUserProperties(buildMap {
            put("platform", getPlatform().name)
            put("is_guest", user.isGuest)
            put("has_completed_onboarding", user.hasCompletedOnboarding)
        })
    }

    /**
     * Find or create a local user matching the Supabase auth UID.
     * Ensures single-user invariant: clears other rows before inserting.
     */
    private suspend fun findOrCreateLocalUser(
        uid: String,
        email: String?,
        displayName: String,
        isGuest: Boolean
    ): User {
        val existing = userRepository.getUserByFirebaseUid(uid)
        if (existing != null) {
            // Update mutable fields if needed
            val updated = existing.copy(
                email = email ?: existing.email,
                displayName = displayName,
                isGuest = isGuest
            )
            if (updated != existing) {
                userRepository.updateUser(updated)
            }
            _hasCompletedOnboarding.value = updated.hasCompletedOnboarding
            return updated
        }

        // New user — cancel syncs and clear all old data first
        syncManager.onLogout()
        userRepository.clearAllLocalData()
        val user = User(
            id = uid,
            firebaseUid = uid,
            email = email,
            displayName = displayName,
            isGuest = isGuest,
            hasCompletedOnboarding = false,
            createdAt = Clock.System.now()
        )
        userRepository.createUser(user)
        _hasCompletedOnboarding.value = false
        return user
    }

    /**
     * Sign in with email and password
     */
    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                Logger.d("AuthViewModel") { "Starting email sign-in for $email" }

                when (val result = authService.signInWithEmail(email, password)) {
                    is AuthResult.Success -> {
                        Logger.d("AuthViewModel") { "Email sign-in successful, UID: ${result.user.uid}" }
                        Analytics.signInCompleted("email")
                        val user = findOrCreateLocalUser(
                            uid = result.user.uid,
                            email = result.user.email,
                            displayName = result.user.displayName ?: "User",
                            isGuest = false
                        )
                        _isLocalOnlyGuest.value = false
                        setAuthenticatedAndSync(user)
                    }
                    is AuthResult.EmailVerificationPending -> {
                        _authState.value = AuthState.EmailVerificationPending(result.email)
                    }
                    is AuthResult.Error -> {
                        _authState.value = AuthState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Sign up with email and password.
     * If email verification is required, transitions to EmailVerificationPending state.
     */
    fun signUpWithEmail(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                Logger.d("AuthViewModel") { "Starting email sign-up for $email" }
                Analytics.signUpStarted("email")

                when (val result = authService.signUpWithEmail(email, password, displayName)) {
                    is AuthResult.Success -> {
                        Logger.d("AuthViewModel") { "Email sign-up successful, UID: ${result.user.uid}" }
                        FacebookAnalytics.logCompleteRegistration("email")
                        Analytics.signUpCompleted("email")
                        val user = findOrCreateLocalUser(
                            uid = result.user.uid,
                            email = result.user.email,
                            displayName = displayName,
                            isGuest = false
                        )
                        _isLocalOnlyGuest.value = false
                        setAuthenticatedAndSync(user)
                    }
                    is AuthResult.EmailVerificationPending -> {
                        Logger.d("AuthViewModel") { "Email verification pending for ${result.email}" }
                        settings.putString(PENDING_VERIFY_EMAIL_KEY, result.email)
                        _authState.value = AuthState.EmailVerificationPending(result.email)
                    }
                    is AuthResult.Error -> {
                        _authState.value = AuthState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Sign in with Google
     */
    fun signInWithGoogle() {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                Logger.d("AuthViewModel") { "Starting Google sign-in" }

                when (val result = authService.signInWithGoogle()) {
                    is AuthResult.Success -> {
                        Logger.d("AuthViewModel") { "Google sign-in successful, UID: ${result.user.uid}" }
                        Analytics.signInCompleted("google")
                        val user = findOrCreateLocalUser(
                            uid = result.user.uid,
                            email = result.user.email,
                            displayName = result.user.displayName ?: "User",
                            isGuest = false
                        )
                        _isLocalOnlyGuest.value = false
                        setAuthenticatedAndSync(user)
                    }
                    is AuthResult.EmailVerificationPending -> {
                        _authState.value = AuthState.EmailVerificationPending(result.email)
                    }
                    is AuthResult.Error -> {
                        _authState.value = AuthState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Sign in as guest using Supabase anonymous auth.
     * Falls back to local-only guest if Supabase fails.
     */
    fun signInAsGuest() {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                Logger.d("AuthViewModel") { "Starting anonymous sign-in" }

                when (val result = authService.signInAnonymously()) {
                    is AuthResult.Success -> {
                        Logger.d("AuthViewModel") { "Anonymous sign-in successful, UID: ${result.user.uid}" }
                        val user = findOrCreateLocalUser(
                            uid = result.user.uid,
                            email = null,
                            displayName = "Guest User",
                            isGuest = true
                        )
                        _isLocalOnlyGuest.value = false
                        identifyInPostHog(user)
                        _authState.value = AuthState.Guest(user)
                    }
                    is AuthResult.EmailVerificationPending -> {
                        // Should never happen for anonymous, but handle gracefully
                        _authState.value = AuthState.Error("Unexpected state")
                    }
                    is AuthResult.Error -> {
                        Logger.w("AuthViewModel") { "Anonymous sign-in failed, using local guest - ${result.message}" }
                        createLocalGuestUser()
                    }
                }
            } catch (e: Exception) {
                Logger.w("AuthViewModel", e) { "Exception during anonymous sign-in, using local guest - ${e.message}" }
                createLocalGuestUser()
            }
        }
    }

    /**
     * Create a local-only guest user (fallback when Supabase is unavailable)
     */
    private suspend fun createLocalGuestUser() {
        try {
            val localId = "local_guest"
            userRepository.deleteAllUsers()
            val guestUser = User(
                id = localId,
                firebaseUid = localId,
                email = null,
                displayName = "Guest User",
                isGuest = true,
                hasCompletedOnboarding = false,
                createdAt = Clock.System.now()
            )
            userRepository.createUser(guestUser)
            _isLocalOnlyGuest.value = true
            _hasCompletedOnboarding.value = false
            _authState.value = AuthState.Guest(guestUser)
            Logger.d("AuthViewModel") { "Local guest user created (offline mode)" }
        } catch (e: Exception) {
            _authState.value = AuthState.Error("Failed to create guest user: ${e.message}")
        }
    }

    /**
     * Link a guest account to an email/password identity.
     * Preserves the same Supabase auth UID so all synced data stays linked.
     * A verification email will be sent to the new address.
     */
    fun linkGuestAccount(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                Logger.d("AuthViewModel") { "Linking guest account to $email" }

                when (val result = authService.linkEmailToAnonymousAccount(email, password, displayName)) {
                    is AuthResult.Success -> {
                        Logger.d("AuthViewModel") { "Account linked successfully, UID: ${result.user.uid}" }
                        // Reset PostHog to cleanly separate guest session from authenticated session
                        PostHogAnalytics.reset()
                        _pendingVerificationEmail.value = email
                        // Update EXISTING local user — same ID preserved
                        val currentUser = userRepository.getCurrentUser()
                        if (currentUser != null) {
                            val updatedUser = currentUser.copy(
                                isGuest = false,
                                email = email,
                                displayName = displayName
                            )
                            userRepository.updateUser(updatedUser)
                            _isLocalOnlyGuest.value = false
                            _successMessage.value = "Check your email to verify $email"
                            setAuthenticatedAndSync(updatedUser)
                        } else {
                            val user = findOrCreateLocalUser(
                                uid = result.user.uid,
                                email = email,
                                displayName = displayName,
                                isGuest = false
                            )
                            _isLocalOnlyGuest.value = false
                            _successMessage.value = "Check your email to verify $email"
                            setAuthenticatedAndSync(user)
                        }
                        // Start polling to auto-clear banner when verified
                        startLinkVerificationPolling(email)
                    }
                    is AuthResult.EmailVerificationPending -> {
                        _pendingVerificationEmail.value = email
                        val currentUser = userRepository.getCurrentUser()
                        if (currentUser != null) {
                            val updatedUser = currentUser.copy(
                                isGuest = false,
                                email = email,
                                displayName = displayName
                            )
                            userRepository.updateUser(updatedUser)
                            _successMessage.value = "Check your email to verify $email"
                            setAuthenticatedAndSync(updatedUser)
                        }
                        startLinkVerificationPolling(email)
                    }
                    is AuthResult.Error -> {
                        _authState.value = AuthState.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Failed to link account")
            }
        }
    }

    /**
     * Resend verification email for a pending signup.
     */
    fun resendVerificationEmail(email: String) {
        viewModelScope.launch {
            try {
                authService.resendVerificationEmail(email)
                _successMessage.value = "Verification email resent to $email"
                Logger.d("AuthViewModel") { "Verification email resent to $email" }
            } catch (e: Exception) {
                _successMessage.value = "Could not resend email. Please try again."
                Logger.e("AuthViewModel", e) { "Failed to resend verification - ${e.message}" }
            }
        }
    }

    /**
     * Poll Supabase session to detect email verification done on another device.
     * Checks every 3 seconds while in EmailVerificationPending state.
     * Auto-signs the user in as soon as verification is detected.
     */
    fun startVerificationPolling() {
        viewModelScope.launch {
            while (_authState.value is AuthState.EmailVerificationPending) {
                try {
                    kotlinx.coroutines.delay(3000)
                    if (_authState.value !is AuthState.EmailVerificationPending) break

                    supabaseClient.auth.refreshCurrentSession()
                    val user = supabaseClient.auth.currentUserOrNull()
                    if (user != null && user.email != null && user.emailConfirmedAt != null) {
                        Logger.d("AuthViewModel") { "Email verified on another device! Auto-signing in." }
                        val localUser = findOrCreateLocalUser(
                            uid = user.id,
                            email = user.email,
                            displayName = user.userMetadata?.get("display_name")
                                ?.toString()?.removeSurrounding("\"") ?: "User",
                            isGuest = false
                        )
                        _isLocalOnlyGuest.value = false
                        setAuthenticatedAndSync(localUser)
                        break
                    }
                } catch (e: Exception) {
                    Logger.d("AuthViewModel") { "Verification poll: ${e.message}" }
                }
            }
        }
    }

    /**
     * Poll to detect when a linked account's email is verified.
     * Clears the pendingVerificationEmail banner and marks SECURE_ACCOUNT objective complete.
     */
    private fun startLinkVerificationPolling(email: String) {
        viewModelScope.launch {
            while (_pendingVerificationEmail.value == email) {
                try {
                    kotlinx.coroutines.delay(5000)
                    if (_pendingVerificationEmail.value != email) break
                    supabaseClient.auth.refreshCurrentSession()
                    val user = supabaseClient.auth.currentUserOrNull()
                    if (user?.emailConfirmedAt != null) {
                        Logger.d("AuthViewModel") { "Email $email verified! Clearing banner." }
                        _pendingVerificationEmail.value = null
                        Analytics.accountSecured()
                        break
                    }
                } catch (e: Exception) {
                    Logger.d("AuthViewModel") { "Link verification poll: ${e.message}" }
                }
            }
        }
    }

    /**
     * Verify a 6-digit OTP code for signup email confirmation.
     */
    fun verifySignupOtp(email: String, token: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                when (val result = authService.verifySignupOtp(email, token)) {
                    is AuthResult.Success -> {
                        val user = findOrCreateLocalUser(
                            uid = result.user.uid,
                            email = result.user.email,
                            displayName = result.user.displayName ?: "User",
                            isGuest = false
                        )
                        _isLocalOnlyGuest.value = false
                        setAuthenticatedAndSync(user)
                    }
                    is AuthResult.Error -> _authState.value = AuthState.Error(result.message)
                    is AuthResult.EmailVerificationPending -> {}
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Verification failed")
            }
        }
    }

    /**
     * Send password reset email
     */
    fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            try {
                authService.sendPasswordResetEmail(email)
                Logger.d("AuthViewModel") { "Password reset email sent to $email" }
            } catch (e: Exception) {
                Logger.e("AuthViewModel", e) { "Failed to send password reset email - ${e.message}" }
            }
        }
    }

    /**
     * Send a magic link (passwordless) to the given email.
     */
    fun sendMagicLink(email: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                authService.signInWithMagicLink(email)
                _magicLinkSent.value = true
                _authState.value = AuthState.Unauthenticated
                _successMessage.value = "Magic link sent! Check your email."
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Failed to send magic link")
            }
        }
    }

    /**
     * Verify a 6-digit OTP code from a magic link email.
     */
    fun verifyOtp(email: String, token: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                when (val result = authService.verifyOtp(email, token)) {
                    is AuthResult.Success -> {
                        Analytics.signInCompleted("magic_link")
                        val user = findOrCreateLocalUser(
                            uid = result.user.uid,
                            email = result.user.email,
                            displayName = result.user.displayName ?: "User",
                            isGuest = false
                        )
                        _magicLinkSent.value = false
                        _isLocalOnlyGuest.value = false
                        setAuthenticatedAndSync(user)
                    }
                    is AuthResult.Error -> _authState.value = AuthState.Error(result.message)
                    is AuthResult.EmailVerificationPending -> {}
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Verification failed")
            }
        }
    }

    fun clearMagicLinkState() {
        _magicLinkSent.value = false
    }

    /**
     * Update the display name for the current user (locally and on Supabase).
     */
    fun updateDisplayName(newName: String) {
        viewModelScope.launch {
            try {
                val trimmed = newName.trim()
                if (trimmed.isBlank() || trimmed.length < 2) return@launch

                // Update on Supabase
                supabaseClient.auth.updateUser {
                    data = kotlinx.serialization.json.buildJsonObject {
                        put("display_name", kotlinx.serialization.json.JsonPrimitive(trimmed))
                    }
                }

                // Update locally
                val currentUser = userRepository.getCurrentUser() ?: return@launch
                val updated = currentUser.copy(displayName = trimmed)
                userRepository.updateUser(updated)

                // Refresh auth state
                val currentState = _authState.value
                _authState.value = when (currentState) {
                    is AuthState.Authenticated -> AuthState.Authenticated(updated)
                    is AuthState.Guest -> AuthState.Guest(updated)
                    else -> currentState
                }

                _successMessage.value = "Display name updated"
                Logger.d("AuthViewModel") { "Display name updated to: $trimmed" }
            } catch (e: Exception) {
                Logger.e("AuthViewModel", e) { "Failed to update display name: ${e.message}" }
                _successMessage.value = "Failed to update name: ${e.message}"
            }
        }
    }

    /**
     * Sign out — clears all local user records and Supabase session
     */
    fun signOut() {
        viewModelScope.launch {
            try {
                syncManager.onLogout()
                userRepository.clearAllLocalData()
                authService.signOut()
                Analytics.signOutCompleted()
                PostHogAnalytics.reset()
                settings.remove(PENDING_VERIFY_EMAIL_KEY)
                _isLocalOnlyGuest.value = false
                _hasCompletedOnboarding.value = false
                _authState.value = AuthState.Unauthenticated
                Logger.d("AuthViewModel") { "Sign out successful" }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Failed to sign out")
            }
        }
    }

    /**
     * Mark onboarding complete for the current user (no personalization data).
     */
    fun completeOnboarding() {
        // Idempotency guard: prevent double-firing if called from multiple call sites
        // (e.g. handleFinish() + LaunchedEffect(authState) in OnboardingScreen)
        _hasCompletedOnboarding.value = true
        viewModelScope.launch {
            try {
                val user = userRepository.getCurrentUser()
                if (user != null) {
                    userRepository.markOnboardingComplete(user.id)
                    Analytics.onboardingCompleted()
                    PostHogAnalytics.setUserProperties(mapOf("has_completed_onboarding" to true))
                    Logger.d("AuthViewModel") { "Onboarding marked complete for ${user.id}" }
                }
            } catch (e: Exception) {
                Logger.e("AuthViewModel", e) { "Failed to mark onboarding complete: ${e.message}" }
            }
        }
    }

    /**
     * Refresh auth state after changes
     */
    fun refreshAuthState() {
        checkAuthStatus()
    }
}
