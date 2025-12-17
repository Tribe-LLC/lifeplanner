package az.tribe.lifeplanner.data.auth

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth

/**
 * iOS implementation of AuthService using GitLive Firebase Auth
 * Works with Swift Package Manager (SPM)
 */
class AuthServiceImpl : AuthService {

    private val auth = Firebase.auth

    override suspend fun signInWithEmail(email: String, password: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password)
            val user = result.user
            if (user != null) {
                AuthResult.Success(
                    FirebaseUser(
                        uid = user.uid,
                        email = user.email,
                        displayName = user.displayName,
                        photoUrl = user.photoURL?.toString(),
                        isAnonymous = user.isAnonymous
                    )
                )
            } else {
                AuthResult.Error("Sign in failed")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Sign in failed")
        }
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): AuthResult {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password)
            val user = result.user
            if (user != null) {
                // Update display name
                user.updateProfile(displayName = displayName)
                AuthResult.Success(
                    FirebaseUser(
                        uid = user.uid,
                        email = user.email,
                        displayName = displayName,
                        photoUrl = user.photoURL?.toString(),
                        isAnonymous = user.isAnonymous
                    )
                )
            } else {
                AuthResult.Error("Sign up failed")
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Sign up failed")
        }
    }

    override suspend fun signInWithGoogle(): AuthResult {
        return AuthResult.Error("iOS Google Sign-In not yet implemented")
    }

    override suspend fun signInAnonymously(): AuthResult {
        return try {
            val result = auth.signInAnonymously()
            val user = result.user
            if (user != null) {
                AuthResult.Success(
                    FirebaseUser(
                        uid = user.uid,
                        email = user.email,
                        displayName = user.displayName,
                        photoUrl = user.photoURL?.toString(),
                        isAnonymous = true
                    )
                )
            } else {
                AuthResult.Error("Anonymous sign in failed")
            }
        } catch (e: Exception) {
            println("AuthServiceImpl iOS: Anonymous sign in error - ${e.message}")
            AuthResult.Error(e.message ?: "Anonymous sign in failed")
        }
    }

    override suspend fun signOut() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            println("Sign out error: ${e.message}")
        }
    }

    override suspend fun getCurrentUser(): FirebaseUser? {
        val currentUser = auth.currentUser
        return currentUser?.let {
            FirebaseUser(
                uid = it.uid,
                email = it.email,
                displayName = it.displayName,
                photoUrl = it.photoURL?.toString(),
                isAnonymous = it.isAnonymous
            )
        }
    }

    override suspend fun getIdToken(forceRefresh: Boolean): String? {
        return try {
            auth.currentUser?.getIdToken(forceRefresh)
        } catch (e: Exception) {
            println("Failed to get ID token: ${e.message}")
            null
        }
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        try {
            auth.sendPasswordResetEmail(email)
        } catch (e: Exception) {
            println("Failed to send password reset: ${e.message}")
        }
    }
}
