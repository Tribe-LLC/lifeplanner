package az.tribe.lifeplanner.data.auth

/**
 * Authentication service interface for Firebase auth
 */
interface AuthService {
    /**
     * Sign in with email and password
     */
    suspend fun signInWithEmail(email: String, password: String): AuthResult

    /**
     * Sign up with email and password
     */
    suspend fun signUpWithEmail(email: String, password: String, displayName: String): AuthResult

    /**
     * Sign in with Google
     */
    suspend fun signInWithGoogle(): AuthResult

    /**
     * Sign in anonymously
     */
    suspend fun signInAnonymously(): AuthResult

    /**
     * Sign out
     */
    suspend fun signOut()

    /**
     * Get current user
     */
    suspend fun getCurrentUser(): FirebaseUser?

    /**
     * Get ID token
     */
    suspend fun getIdToken(forceRefresh: Boolean = false): String?

    /**
     * Send password reset email
     */
    suspend fun sendPasswordResetEmail(email: String)
}

/**
 * Authentication result
 */
sealed class AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * Firebase user data
 */
data class FirebaseUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val isAnonymous: Boolean
)
