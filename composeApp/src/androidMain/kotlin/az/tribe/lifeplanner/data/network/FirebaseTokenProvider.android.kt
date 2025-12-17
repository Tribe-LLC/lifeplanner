package az.tribe.lifeplanner.data.network

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Android implementation of FirebaseTokenProvider
 * Uses the native Firebase SDK to get ID tokens
 */
class AndroidFirebaseTokenProvider : FirebaseTokenProvider {
    override suspend fun getIdToken(forceRefresh: Boolean): String? {
        return try {
            val user = FirebaseAuth.getInstance().currentUser
            user?.getIdToken(forceRefresh)?.await()?.token
        } catch (e: Exception) {
            println("AndroidFirebaseTokenProvider: Failed to get ID token - ${e.message}")
            null
        }
    }
}
