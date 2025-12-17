package az.tribe.lifeplanner.data.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

/**
 * Android implementation of AuthService using Firebase Auth
 */
class AuthServiceImpl : AuthService {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override suspend fun signInWithEmail(email: String, password: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user

            if (firebaseUser != null) {
                AuthResult.Success(
                    FirebaseUser(
                        uid = firebaseUser.uid,
                        email = firebaseUser.email,
                        displayName = firebaseUser.displayName,
                        photoUrl = firebaseUser.photoUrl?.toString(),
                        isAnonymous = firebaseUser.isAnonymous
                    )
                )
            } else {
                AuthResult.Error("Sign in failed")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): AuthResult {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = result.user

            if (firebaseUser != null) {
                // Update display name
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()
                firebaseUser.updateProfile(profileUpdates).await()

                AuthResult.Success(
                    FirebaseUser(
                        uid = firebaseUser.uid,
                        email = firebaseUser.email,
                        displayName = displayName,
                        photoUrl = firebaseUser.photoUrl?.toString(),
                        isAnonymous = firebaseUser.isAnonymous
                    )
                )
            } else {
                AuthResult.Error("Sign up failed")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun signInWithGoogle(): AuthResult {
        // Google Sign-In will be handled by the Activity/Fragment
        // This is a placeholder - actual implementation needs Google Sign-In client
        return AuthResult.Error("Google Sign-In must be initiated from Activity")
    }

    override suspend fun signInAnonymously(): AuthResult {
        return try {
            val result = auth.signInAnonymously().await()
            val firebaseUser = result.user

            if (firebaseUser != null) {
                AuthResult.Success(
                    FirebaseUser(
                        uid = firebaseUser.uid,
                        email = firebaseUser.email,
                        displayName = firebaseUser.displayName,
                        photoUrl = firebaseUser.photoUrl?.toString(),
                        isAnonymous = true
                    )
                )
            } else {
                AuthResult.Error("Anonymous sign in failed")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun getCurrentUser(): FirebaseUser? {
        val firebaseUser = auth.currentUser
        return firebaseUser?.let {
            FirebaseUser(
                uid = it.uid,
                email = it.email,
                displayName = it.displayName,
                photoUrl = it.photoUrl?.toString(),
                isAnonymous = it.isAnonymous
            )
        }
    }

    override suspend fun getIdToken(forceRefresh: Boolean): String? {
        return try {
            val user = auth.currentUser
            val result = user?.getIdToken(forceRefresh)?.await()
            result?.token
        } catch (e: Exception) {
            println("AuthService: Failed to get ID token - ${e.message}")
            null
        }
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    /**
     * Sign in with Google credential (called after Google Sign-In)
     */
    suspend fun signInWithGoogleCredential(idToken: String): AuthResult {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val firebaseUser = result.user

            if (firebaseUser != null) {
                AuthResult.Success(
                    FirebaseUser(
                        uid = firebaseUser.uid,
                        email = firebaseUser.email,
                        displayName = firebaseUser.displayName,
                        photoUrl = firebaseUser.photoUrl?.toString(),
                        isAnonymous = firebaseUser.isAnonymous
                    )
                )
            } else {
                AuthResult.Error("Google sign in failed")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Unknown error")
        }
    }
}
