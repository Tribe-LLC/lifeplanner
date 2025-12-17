package az.tribe.lifeplanner.data.network

import io.ktor.client.plugins.api.createClientPlugin

/**
 * Ktor plugin that adds Firebase authentication token to requests
 */
val AuthInterceptorPlugin = createClientPlugin("AuthInterceptor", ::AuthInterceptorConfig) {
    val tokenProvider = pluginConfig.tokenProvider

    onRequest { request, content ->
        try {
            // Get the current auth state
            val token = tokenProvider?.invoke()

            if (token != null) {
                request.headers.append("Authorization", "Bearer $token")
            }
        } catch (e: Exception) {
            println("AuthInterceptor: Failed to add token - ${e.message}")
        }
    }
}

/**
 * Configuration for AuthInterceptor plugin
 */
class AuthInterceptorConfig {
    /**
     * Custom token provider function that returns the Firebase ID token
     * This should be implemented platform-specifically to call the native Firebase SDK
     */
    var tokenProvider: (suspend () -> String?)? = null
}
