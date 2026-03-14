package az.tribe.lifeplanner.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import co.touchlab.kermit.Logger

/**
 * Authentication service interface
 */
interface AuthService {
    suspend fun signInWithEmail(email: String, password: String): AuthResult
    suspend fun signUpWithEmail(email: String, password: String, displayName: String): AuthResult
    suspend fun signInWithGoogle(): AuthResult
    suspend fun signInAnonymously(): AuthResult
    suspend fun signOut()
    suspend fun getCurrentUser(): FirebaseUser?
    suspend fun sendPasswordResetEmail(email: String)
    /** Restore the persisted Supabase session (auto-loads from storage in supabase-kt 3.x). */
    suspend fun restoreSession(): FirebaseUser?
    /** Link an email/password identity to the current anonymous account. */
    suspend fun linkEmailToAnonymousAccount(email: String, password: String, displayName: String? = null): AuthResult
    /** Resend the signup confirmation email. */
    suspend fun resendVerificationEmail(email: String)
    /** Send a magic link (passwordless OTP) to the given email. */
    suspend fun signInWithMagicLink(email: String)
    /** Verify a 6-digit OTP code sent via magic link. */
    suspend fun verifyOtp(email: String, token: String): AuthResult
    /** Verify a 6-digit OTP code sent for email signup confirmation. */
    suspend fun verifySignupOtp(email: String, token: String): AuthResult
}

/**
 * Common Supabase Auth implementation (multiplatform).
 * Google OAuth is a stub here — platform-specific handling can be added later.
 */
class SupabaseAuthService(
    private val supabase: SupabaseClient
) : AuthService {

    companion object {
        private const val REDIRECT_URL = "https://tribe.az/lifeplanner/auth/callback"
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthResult {
        return try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val user = supabase.auth.currentUserOrNull()
            if (user != null) {
                AuthResult.Success(
                    FirebaseUser(
                        uid = user.id,
                        email = user.email,
                        displayName = user.userMetadata?.get("display_name")?.toString()?.removeSurrounding("\""),
                        photoUrl = null,
                        isAnonymous = false
                    )
                )
            } else {
                AuthResult.Error("Sign in failed")
            }
        } catch (e: Exception) {
            Logger.e("SupabaseAuth") { "Sign in failed: ${e.message}" }
            // Provide user-friendly error messages for common cases
            val message = when {
                e.message?.contains("Invalid login credentials", ignoreCase = true) == true ->
                    "Incorrect email or password. Please try again."
                e.message?.contains("Email not confirmed", ignoreCase = true) == true ->
                    "Please verify your email before signing in. Check your inbox for a verification link."
                e.message?.contains("rate limit", ignoreCase = true) == true ->
                    "Too many attempts. Please wait a moment and try again."
                else -> e.message ?: "Sign in failed"
            }
            AuthResult.Error(message)
        }
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): AuthResult {
        return try {
            val signUpResult = supabase.auth.signUpWith(Email, redirectUrl = REDIRECT_URL) {
                this.email = email
                this.password = password
                this.data = kotlinx.serialization.json.buildJsonObject {
                    put("display_name", kotlinx.serialization.json.JsonPrimitive(displayName))
                }
            }

            // Check if a session was created (means email verification is NOT required)
            val currentUser = supabase.auth.currentUserOrNull()
            when {
                currentUser != null -> {
                    // Session established — no email verification needed
                    AuthResult.Success(
                        FirebaseUser(
                            uid = currentUser.id,
                            email = currentUser.email,
                            displayName = displayName,
                            photoUrl = null,
                            isAnonymous = false
                        )
                    )
                }
                signUpResult != null -> {
                    // User was created but no session — email verification is pending
                    AuthResult.EmailVerificationPending(email)
                }
                else -> {
                    AuthResult.Error("Sign up failed — please try again.")
                }
            }
        } catch (e: Exception) {
            Logger.e("SupabaseAuth") { "Sign up failed: ${e.message}" }
            val message = when {
                e.message?.contains("already registered", ignoreCase = true) == true ->
                    "An account with this email already exists. Try signing in instead."
                e.message?.contains("password", ignoreCase = true) == true &&
                        e.message?.contains("least", ignoreCase = true) == true ->
                    "Password is too short. Please use at least 6 characters."
                e.message?.contains("valid email", ignoreCase = true) == true ->
                    "Please enter a valid email address."
                e.message?.contains("rate limit", ignoreCase = true) == true ->
                    "Too many attempts. Please wait a moment and try again."
                else -> e.message ?: "Sign up failed"
            }
            AuthResult.Error(message)
        }
    }

    override suspend fun signInWithGoogle(): AuthResult {
        // Google OAuth requires platform-specific handling
        // For now, return error — will be implemented per-platform if needed
        return AuthResult.Error("Google Sign-In not yet configured for Supabase")
    }

    override suspend fun signInAnonymously(): AuthResult {
        return try {
            supabase.auth.signInAnonymously()
            val user = supabase.auth.currentUserOrNull()
            if (user != null) {
                AuthResult.Success(
                    FirebaseUser(
                        uid = user.id,
                        email = null,
                        displayName = null,
                        photoUrl = null,
                        isAnonymous = true
                    )
                )
            } else {
                AuthResult.Error("Anonymous sign in failed")
            }
        } catch (e: Exception) {
            Logger.e("SupabaseAuth") { "Anonymous sign in failed: ${e.message}" }
            AuthResult.Error(e.message ?: "Anonymous sign in failed")
        }
    }

    override suspend fun signOut() {
        try {
            supabase.auth.signOut()
        } catch (e: Exception) {
            Logger.e("SupabaseAuth") { "Sign out failed: ${e.message}" }
        }
    }

    override suspend fun getCurrentUser(): FirebaseUser? {
        val user = supabase.auth.currentUserOrNull() ?: return null
        val email = user.email?.takeIf { it.isNotBlank() }
        return FirebaseUser(
            uid = user.id,
            email = email,
            displayName = user.userMetadata?.get("display_name")?.toString()?.removeSurrounding("\""),
            photoUrl = null,
            isAnonymous = email == null
        )
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        try {
            supabase.auth.resetPasswordForEmail(email, redirectUrl = REDIRECT_URL)
        } catch (e: Exception) {
            Logger.e("SupabaseAuth") { "Password reset failed: ${e.message}" }
        }
    }

    override suspend fun restoreSession(): FirebaseUser? {
        return try {
            // supabase-kt 3.x auto-loads the session from storage when you access currentUserOrNull()
            val user = supabase.auth.currentUserOrNull()
            if (user != null) {
                val email = user.email?.takeIf { it.isNotBlank() }
                FirebaseUser(
                    uid = user.id,
                    email = email,
                    displayName = user.userMetadata?.get("display_name")?.toString()?.removeSurrounding("\""),
                    photoUrl = null,
                    isAnonymous = email == null
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e("SupabaseAuth") { "Session restore failed: ${e.message}" }
            null
        }
    }

    override suspend fun linkEmailToAnonymousAccount(
        email: String,
        password: String,
        displayName: String?
    ): AuthResult {
        return try {
            supabase.auth.updateUser(redirectUrl = REDIRECT_URL) {
                this.email = email
                this.password = password
                if (displayName != null) {
                    this.data = kotlinx.serialization.json.buildJsonObject {
                        put("display_name", kotlinx.serialization.json.JsonPrimitive(displayName))
                    }
                }
            }
            val user = supabase.auth.currentUserOrNull()
            if (user != null) {
                AuthResult.Success(
                    FirebaseUser(
                        uid = user.id,
                        email = user.email ?: email,
                        displayName = displayName
                            ?: user.userMetadata?.get("display_name")?.toString()?.removeSurrounding("\""),
                        photoUrl = null,
                        isAnonymous = false
                    )
                )
            } else {
                AuthResult.Error("Account linking failed")
            }
        } catch (e: Exception) {
            Logger.e("SupabaseAuth") { "Account linking failed: ${e.message}" }
            val message = when {
                e.message?.contains("already registered", ignoreCase = true) == true ->
                    "This email is already associated with another account. Try signing in instead."
                e.message?.contains("valid email", ignoreCase = true) == true ->
                    "Please enter a valid email address."
                else -> e.message ?: "Account linking failed"
            }
            AuthResult.Error(message)
        }
    }

    override suspend fun resendVerificationEmail(email: String) {
        try {
            supabase.auth.resendEmail(OtpType.Email.SIGNUP, email)
        } catch (e: Exception) {
            Logger.e("SupabaseAuth") { "Resend verification email failed: ${e.message}" }
            throw e
        }
    }

    override suspend fun signInWithMagicLink(email: String) {
        try {
            supabase.auth.signInWith(OTP, redirectUrl = REDIRECT_URL) {
                this.email = email
                this.createUser = false
            }
        } catch (e: Exception) {
            // Timeout errors usually mean the email was sent but the response was slow.
            // Treat timeouts as success since the OTP is fire-and-forget.
            val isTimeout = e.message?.contains("timeout", ignoreCase = true) == true ||
                    e.message?.contains("timed out", ignoreCase = true) == true
            if (isTimeout) {
                Logger.w("SupabaseAuth") { "Magic link request timed out (email likely sent): ${e.message}" }
                return // treat as success
            }
            Logger.e("SupabaseAuth") { "Magic link send failed: ${e.message}" }
            throw e
        }
    }

    override suspend fun verifyOtp(email: String, token: String): AuthResult {
        return try {
            supabase.auth.verifyEmailOtp(OtpType.Email.MAGIC_LINK, email, token)
            val user = supabase.auth.currentUserOrNull()
            if (user != null) {
                AuthResult.Success(
                    FirebaseUser(
                        uid = user.id,
                        email = user.email,
                        displayName = user.userMetadata?.get("display_name")?.toString()?.removeSurrounding("\""),
                        photoUrl = null,
                        isAnonymous = false
                    )
                )
            } else {
                AuthResult.Error("Verification failed")
            }
        } catch (e: Exception) {
            Logger.e("SupabaseAuth") { "OTP verification failed: ${e.message}" }
            val message = when {
                e.message?.contains("expired", ignoreCase = true) == true ->
                    "Code expired. Please request a new magic link."
                e.message?.contains("invalid", ignoreCase = true) == true ->
                    "Invalid code. Please check and try again."
                else -> e.message ?: "OTP verification failed"
            }
            AuthResult.Error(message)
        }
    }

    override suspend fun verifySignupOtp(email: String, token: String): AuthResult {
        return try {
            supabase.auth.verifyEmailOtp(OtpType.Email.SIGNUP, email, token)
            val user = supabase.auth.currentUserOrNull()
            if (user != null) {
                AuthResult.Success(
                    FirebaseUser(
                        uid = user.id,
                        email = user.email,
                        displayName = user.userMetadata?.get("display_name")?.toString()?.removeSurrounding("\""),
                        photoUrl = null,
                        isAnonymous = false
                    )
                )
            } else {
                AuthResult.Error("Verification failed")
            }
        } catch (e: Exception) {
            Logger.e("SupabaseAuth") { "Signup OTP verification failed: ${e.message}" }
            val message = when {
                e.message?.contains("expired", ignoreCase = true) == true ->
                    "Code expired. Please request a new verification email."
                e.message?.contains("invalid", ignoreCase = true) == true ->
                    "Invalid code. Please check and try again."
                else -> e.message ?: "Verification failed"
            }
            AuthResult.Error(message)
        }
    }
}

/**
 * Authentication result
 */
sealed class AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
    /** User was created but email verification is required before they can sign in. */
    data class EmailVerificationPending(val email: String) : AuthResult()
}

/**
 * Auth user data (kept as FirebaseUser name for backwards compatibility with existing code)
 */
data class FirebaseUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val isAnonymous: Boolean
)
