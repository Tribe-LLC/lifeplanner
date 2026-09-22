package az.tribe.lifeplanner.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.analytics.Analytics
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
import kotlin.time.Clock

/**
 * ViewModel for authentication (Supabase-based)
 */
class AuthViewModel(
    internal val authService: AuthService,
    internal val userRepository: UserRepository,
    internal val syncManager: SyncManager,
    internal val supabaseClient: SupabaseClient?,
    internal val settings: Settings
) : ViewModel() {

    companion object {
        /** Global channel for deep link auth errors (e.g. expired magic link). */
        private val _deepLinkError = MutableSharedFlow<String>(extraBufferCapacity = 1)

        /** Called from platform DeepLinkHandler when the callback URL contains an error. */
        fun reportDeepLinkError(message: String) {
            _deepLinkError.tryEmit(message)
        }
    }

    internal val PENDING_VERIFY_EMAIL_KEY = "pending_verification_email"

    /** Serializes all auth state mutations to prevent race conditions between
     *  checkAuthStatus, observeSessionChanges, and observeDeepLinkErrors. */
    internal val authMutex = Mutex()

    internal val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val syncStatus = syncManager.syncStatus

    fun retrySync() {
        if (_authState.value !is AuthState.Authenticated) return
        viewModelScope.launch {
            syncManager.performFullSync(resetRetry = true)
        }
    }

    internal val _hasCompletedOnboarding = MutableStateFlow<Boolean?>(null)
    val hasCompletedOnboarding: StateFlow<Boolean?> = _hasCompletedOnboarding.asStateFlow()

    /** True when anonymous auth failed and user is in local-only mode (no sync). */
    internal val _isLocalOnlyGuest = MutableStateFlow(false)
    val isLocalOnlyGuest: StateFlow<Boolean> = _isLocalOnlyGuest.asStateFlow()

    /** True when a magic link has been sent and user can enter OTP. */
    internal val _magicLinkSent = MutableStateFlow(false)
    val magicLinkSent: StateFlow<Boolean> = _magicLinkSent.asStateFlow()

    /** Email that has been linked but not yet verified (for banner status). */
    internal val _pendingVerificationEmail = MutableStateFlow<String?>(null)
    val pendingVerificationEmail: StateFlow<String?> = _pendingVerificationEmail.asStateFlow()

    /** Transient success message shown briefly after operations like account linking. */
    internal val _successMessage = MutableStateFlow<String?>(null)
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
        val client = supabaseClient ?: return // no Supabase client (e.g. unit tests), nothing to observe
        viewModelScope.launch {
            client.auth.sessionStatus.collect { status ->
                Logger.d("AuthViewModel") { "Session status changed: $status" }
                when (status) {
                    is SessionStatus.Authenticated -> authMutex.withLock {
                        // React to session changes unless already authenticated with matching user
                        val current = _authState.value
                        // Skip if a login/linking operation is in progress, it will set
                        // the auth state itself once it finishes with the correct data.
                        if (current is AuthState.Loading) {
                            Logger.d("AuthDebug") { "observeSession: Authenticated event while Loading, skipping (login in progress)" }
                            return@withLock
                        }
                        val supabaseUid = client.auth.currentUserOrNull()?.id
                        val rawEmail = client.auth.currentUserOrNull()?.email
                        Logger.d("AuthDebug") { "observeSession: Authenticated event, uid=$supabaseUid, rawEmail='$rawEmail', currentState=${current::class.simpleName}" }
                        val currentUid = when (current) {
                            is AuthState.Authenticated -> current.user.firebaseUid
                            is AuthState.Guest -> current.user.firebaseUid
                            else -> null
                        }
                        val alreadyMatchingAuth = currentUid != null && currentUid == supabaseUid
                        Logger.d("AuthDebug") { "observeSession: currentUid=$currentUid, alreadyMatching=$alreadyMatchingAuth" }
                        if (!alreadyMatchingAuth) {
                            val user = client.auth.currentUserOrNull()
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
                        // Both exist and match, use local user (preserves onboarding data)
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
                        // Supabase session exists but no matching local user, recreate locally
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
                        // No Supabase session but local user exists, degraded (local-only) mode
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
                        // Neither, check for pending verification before showing Unauthenticated
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
    internal fun setAuthenticatedAndSync(user: User) {
        // Arriving here with a pending address that matches is the moment a verified sign-up
        // actually completes. It is reported here rather than at the sign-up call because the user
        // can come back through a deep link, a magic link or a cold start, and only this path is
        // common to all three. The address has to match, so signing in with Google while an
        // unrelated verification is outstanding is not counted as completing it.
        val pendingEmail = settings.getStringOrNull(PENDING_VERIFY_EMAIL_KEY)
        if (pendingEmail != null && pendingEmail.equals(user.email, ignoreCase = true)) {
            Analytics.signUpCompleted("email_verified")
        }
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
    internal fun identifyInPostHog(user: User) {
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
    internal suspend fun findOrCreateLocalUser(
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

        // New user, cancel syncs and clear all old data first
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
     * Refresh auth state after changes
     */
    fun refreshAuthState() {
        checkAuthStatus()
    }
}
