package az.tribe.lifeplanner.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.auth.AuthResult
import az.tribe.lifeplanner.data.auth.AuthService
import az.tribe.lifeplanner.domain.model.User
import az.tribe.lifeplanner.domain.repository.UserRepository
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val userRepository: UserRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow<Boolean?>(null)
    val hasCompletedOnboarding: StateFlow<Boolean?> = _hasCompletedOnboarding.asStateFlow()

    /** True when anonymous auth failed and user is in local-only mode (no sync). */
    private val _isLocalOnlyGuest = MutableStateFlow(false)
    val isLocalOnlyGuest: StateFlow<Boolean> = _isLocalOnlyGuest.asStateFlow()

    /** Transient success message shown briefly after operations like account linking. */
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    init {
        checkAuthStatus()
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

                when {
                    // Both exist and match — use local user (preserves onboarding data)
                    supabaseUser != null && localUser != null && localUser.firebaseUid == supabaseUser.uid -> {
                        _hasCompletedOnboarding.value = localUser.hasCompletedOnboarding
                        _isLocalOnlyGuest.value = false
                        _authState.value = if (supabaseUser.isAnonymous) {
                            AuthState.Guest(localUser)
                        } else {
                            AuthState.Authenticated(localUser)
                        }
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
                        userRepository.deleteAllUsers()
                        userRepository.createUser(recreated)
                        _hasCompletedOnboarding.value = false
                        _isLocalOnlyGuest.value = false
                        _authState.value = if (supabaseUser.isAnonymous) {
                            AuthState.Guest(recreated)
                        } else {
                            AuthState.Authenticated(recreated)
                        }
                    }
                    // No Supabase session but local user exists — degraded (local-only) mode
                    localUser != null -> {
                        _hasCompletedOnboarding.value = localUser.hasCompletedOnboarding
                        _isLocalOnlyGuest.value = true
                        _authState.value = if (localUser.isGuest) {
                            AuthState.Guest(localUser)
                        } else {
                            AuthState.Authenticated(localUser)
                        }
                    }
                    // Neither — unauthenticated
                    else -> {
                        _hasCompletedOnboarding.value = false
                        _authState.value = AuthState.Unauthenticated
                    }
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error")
            }
        }
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
            return updated
        }

        // New user — clear all old data and sync timestamps first
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
                        val user = findOrCreateLocalUser(
                            uid = result.user.uid,
                            email = result.user.email,
                            displayName = result.user.displayName ?: "User",
                            isGuest = false
                        )
                        _isLocalOnlyGuest.value = false
                        _authState.value = AuthState.Authenticated(user)
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

                when (val result = authService.signUpWithEmail(email, password, displayName)) {
                    is AuthResult.Success -> {
                        Logger.d("AuthViewModel") { "Email sign-up successful, UID: ${result.user.uid}" }
                        val user = findOrCreateLocalUser(
                            uid = result.user.uid,
                            email = result.user.email,
                            displayName = displayName,
                            isGuest = false
                        )
                        _isLocalOnlyGuest.value = false
                        _authState.value = AuthState.Authenticated(user)
                    }
                    is AuthResult.EmailVerificationPending -> {
                        Logger.d("AuthViewModel") { "Email verification pending for ${result.email}" }
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
                        val user = findOrCreateLocalUser(
                            uid = result.user.uid,
                            email = result.user.email,
                            displayName = result.user.displayName ?: "User",
                            isGuest = false
                        )
                        _isLocalOnlyGuest.value = false
                        _authState.value = AuthState.Authenticated(user)
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
                            _successMessage.value = "Account linked! A verification email has been sent to $email."
                            _authState.value = AuthState.Authenticated(updatedUser)
                        } else {
                            val user = findOrCreateLocalUser(
                                uid = result.user.uid,
                                email = email,
                                displayName = displayName,
                                isGuest = false
                            )
                            _isLocalOnlyGuest.value = false
                            _successMessage.value = "Account linked! A verification email has been sent to $email."
                            _authState.value = AuthState.Authenticated(user)
                        }
                    }
                    is AuthResult.EmailVerificationPending -> {
                        // Account linking with email verification — session stays valid
                        val currentUser = userRepository.getCurrentUser()
                        if (currentUser != null) {
                            val updatedUser = currentUser.copy(
                                isGuest = false,
                                email = email,
                                displayName = displayName
                            )
                            userRepository.updateUser(updatedUser)
                            _successMessage.value = "Check your email! Verify $email to complete linking."
                            _authState.value = AuthState.Authenticated(updatedUser)
                        }
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
     * Sign out — clears all local user records and Supabase session
     */
    fun signOut() {
        viewModelScope.launch {
            try {
                userRepository.clearAllLocalData()
                authService.signOut()
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
     * Refresh auth state after changes
     */
    fun refreshAuthState() {
        checkAuthStatus()
    }
}
