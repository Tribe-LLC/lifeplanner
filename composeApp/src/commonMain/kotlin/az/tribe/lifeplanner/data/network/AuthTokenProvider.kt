package az.tribe.lifeplanner.data.network

/**
 * Provides an auth token for AI proxy requests.
 * Extracted for testability — tests can supply a fixed token.
 */
fun interface AuthTokenProvider {
    suspend fun getToken(): String
}
