package az.tribe.lifeplanner.ui.viewmodel

import az.tribe.lifeplanner.data.analytics.Analytics
import az.tribe.lifeplanner.data.analytics.FacebookAnalytics
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.auth.AuthResult
import co.touchlab.kermit.Logger
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock
import az.tribe.lifeplanner.domain.model.User

/**
 * Sign in with email and password
 */
fun AuthViewModel.signInWithEmail(email: String, password: String) {
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
fun AuthViewModel.signUpWithEmail(email: String, password: String, displayName: String) {
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
                    // Most email sign-ups land here rather than on Success, and this branch used to
                    // report nothing at all, which is why signup_started ran ~60x signup_completed.
                    // The pair of events makes the gap readable: how many reach the inbox step, and
                    // how many come back from it.
                    Analytics.signUpVerificationPending("email")
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
fun AuthViewModel.signInWithGoogle() {
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
fun AuthViewModel.signInAsGuest() {
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
internal suspend fun AuthViewModel.createLocalGuestUser() {
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
fun AuthViewModel.linkGuestAccount(email: String, password: String, displayName: String) {
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
                    // Update EXISTING local user, same ID preserved
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
fun AuthViewModel.resendVerificationEmail(email: String) {
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
fun AuthViewModel.startVerificationPolling() {
    viewModelScope.launch {
        while (_authState.value is AuthState.EmailVerificationPending) {
            try {
                kotlinx.coroutines.delay(3000)
                if (_authState.value !is AuthState.EmailVerificationPending) break

                supabaseClient?.auth?.refreshCurrentSession()
                val user = supabaseClient?.auth?.currentUserOrNull()
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
internal fun AuthViewModel.startLinkVerificationPolling(email: String) {
    viewModelScope.launch {
        while (_pendingVerificationEmail.value == email) {
            try {
                kotlinx.coroutines.delay(5000)
                if (_pendingVerificationEmail.value != email) break
                supabaseClient?.auth?.refreshCurrentSession()
                val user = supabaseClient?.auth?.currentUserOrNull()
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
fun AuthViewModel.verifySignupOtp(email: String, token: String) {
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
fun AuthViewModel.sendPasswordResetEmail(email: String) {
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
fun AuthViewModel.sendMagicLink(email: String) {
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
fun AuthViewModel.verifyOtp(email: String, token: String) {
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

fun AuthViewModel.clearMagicLinkState() {
    _magicLinkSent.value = false
}

/**
 * Update the display name for the current user (locally and on Supabase).
 */
fun AuthViewModel.updateDisplayName(newName: String) {
    viewModelScope.launch {
        try {
            val trimmed = newName.trim()
            if (trimmed.isBlank() || trimmed.length < 2) return@launch

            // Update on Supabase
            supabaseClient?.auth?.updateUser {
                data = buildJsonObject {
                    put("display_name", JsonPrimitive(trimmed))
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
 * Sign out, clears all local user records and Supabase session.
 * Always transitions to Unauthenticated so navigation to Welcome fires even on errors.
 */
fun AuthViewModel.signOut() {
    viewModelScope.launch {
        try {
            syncManager.onLogout()
            userRepository.clearAllLocalData()
            try {
                authService.signOut()
            } catch (e: Exception) {
                Logger.w("AuthViewModel", e) { "Supabase sign-out failed (continuing): ${e.message}" }
            }
            Analytics.signOutCompleted()
            PostHogAnalytics.reset()
        } catch (e: Exception) {
            Logger.e("AuthViewModel", e) { "Sign-out error: ${e.message}" }
        } finally {
            settings.remove(PENDING_VERIFY_EMAIL_KEY)
            // Coach onboarding is per-account, not per-device, clear so next login always re-onboards
            settings.remove("coach_onboarding_complete")
            _isLocalOnlyGuest.value = false
            _hasCompletedOnboarding.value = false
            _authState.value = AuthState.Unauthenticated
            Logger.d("AuthViewModel") { "Sign out completed" }
        }
    }
}

/**
 * Mark onboarding complete for the current user (no personalization data).
 */
fun AuthViewModel.completeOnboarding() {
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
