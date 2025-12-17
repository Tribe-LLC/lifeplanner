package az.tribe.lifeplanner.data.network

/**
 * Platform-specific Firebase token provider
 * Provides access to Firebase ID tokens for authentication
 */
interface FirebaseTokenProvider {
    /**
     * Get the current Firebase ID token
     * @param forceRefresh if true, forces a token refresh
     * @return Firebase ID token or null if user is not authenticated
     */
    suspend fun getIdToken(forceRefresh: Boolean = false): String?
}
