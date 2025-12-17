package az.tribe.lifeplanner.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.auth.AuthResult
import az.tribe.lifeplanner.data.auth.AuthService
import az.tribe.lifeplanner.data.network.dto.AuthTokenRequest
import az.tribe.lifeplanner.data.network.LifePlannerApiService
import az.tribe.lifeplanner.domain.model.User
import az.tribe.lifeplanner.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Authentication state
 */
sealed class AuthState {
    data object Loading : AuthState()
    data object Unauthenticated : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data class Guest(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * ViewModel for authentication
 */
class AuthViewModel(
    private val authService: AuthService,
    private val userRepository: UserRepository,
    private val apiService: LifePlannerApiService
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow<Boolean?>(null)
    val hasCompletedOnboarding: StateFlow<Boolean?> = _hasCompletedOnboarding.asStateFlow()

    init {
        checkAuthStatus()
    }

    /**
     * Check if user is already authenticated and onboarding status
     */
    private fun checkAuthStatus() {
        viewModelScope.launch {
            try {
                // Check onboarding status
                _hasCompletedOnboarding.value = userRepository.hasCompletedOnboarding()

                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null) {
                    _authState.value = if (currentUser.isGuest) {
                        AuthState.Guest(currentUser)
                    } else {
                        AuthState.Authenticated(currentUser)
                    }
                } else {
                    _authState.value = AuthState.Unauthenticated
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Sign in with email and password
     */
    @OptIn(ExperimentalUuidApi::class)
    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                println("AuthViewModel: Starting email sign-in for $email")

                when (val result = authService.signInWithEmail(email, password)) {
                    is AuthResult.Success -> {
                        println("AuthViewModel: Email sign-in successful, UID: ${result.user.uid}")

                        // Get Firebase ID token
                        val firebaseToken = authService.getIdToken(forceRefresh = true)

                        if (firebaseToken != null) {
                            try {
                                // Send token to backend
                                val response = apiService.authenticateWithToken(
                                    AuthTokenRequest(
                                        firebaseToken = firebaseToken,
                                        deviceInfo = "mobile"
                                    )
                                )
                                println("Backend authentication successful: ${response.message}")
                            } catch (e: Exception) {
                                println("Backend authentication failed: ${e.message}")
                            }
                        }

                        // Create or update user in local database
                        val user = User(
                            id = Uuid.random().toString(),
                            firebaseUid = result.user.uid,
                            email = result.user.email,
                            displayName = result.user.displayName ?: "User",
                            isGuest = false,
                            hasCompletedOnboarding = false,
                            createdAt = Clock.System.now()
                        )

                        userRepository.createUser(user)
                        _authState.value = AuthState.Authenticated(user)
                        println("AuthViewModel: User created successfully")
                    }
                    is AuthResult.Error -> {
                        _authState.value = AuthState.Error(result.message)
                        println("AuthViewModel: Email sign-in failed - ${result.message}")
                    }
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error")
                println("AuthViewModel: Exception during email sign-in - ${e.message}")
            }
        }
    }

    /**
     * Sign up with email and password
     */
    @OptIn(ExperimentalUuidApi::class)
    fun signUpWithEmail(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                println("AuthViewModel: Starting email sign-up for $email")

                when (val result = authService.signUpWithEmail(email, password, displayName)) {
                    is AuthResult.Success -> {
                        println("AuthViewModel: Email sign-up successful, UID: ${result.user.uid}")

                        // Get Firebase ID token
                        val firebaseToken = authService.getIdToken(forceRefresh = true)

                        if (firebaseToken != null) {
                            try {
                                // Send token to backend
                                val response = apiService.authenticateWithToken(
                                    AuthTokenRequest(
                                        firebaseToken = firebaseToken,
                                        deviceInfo = "mobile"
                                    )
                                )
                                println("Backend authentication successful: ${response.message}")
                            } catch (e: Exception) {
                                println("Backend authentication failed: ${e.message}")
                            }
                        }

                        // Create user in local database
                        val user = User(
                            id = Uuid.random().toString(),
                            firebaseUid = result.user.uid,
                            email = result.user.email,
                            displayName = displayName,
                            isGuest = false,
                            hasCompletedOnboarding = false,
                            createdAt = Clock.System.now()
                        )

                        userRepository.createUser(user)
                        _authState.value = AuthState.Authenticated(user)
                        println("AuthViewModel: User created successfully")
                    }
                    is AuthResult.Error -> {
                        _authState.value = AuthState.Error(result.message)
                        println("AuthViewModel: Email sign-up failed - ${result.message}")
                    }
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error")
                println("AuthViewModel: Exception during email sign-up - ${e.message}")
            }
        }
    }

    /**
     * Sign in with Google
     */
    @OptIn(ExperimentalUuidApi::class)
    fun signInWithGoogle() {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                println("AuthViewModel: Starting Google sign-in")

                when (val result = authService.signInWithGoogle()) {
                    is AuthResult.Success -> {
                        println("AuthViewModel: Google sign-in successful, UID: ${result.user.uid}")

                        // Get Firebase ID token
                        val firebaseToken = authService.getIdToken(forceRefresh = true)

                        if (firebaseToken != null) {
                            try {
                                // Send token to backend
                                val response = apiService.authenticateWithToken(
                                    AuthTokenRequest(
                                        firebaseToken = firebaseToken,
                                        deviceInfo = "mobile"
                                    )
                                )
                                println("Backend authentication successful: ${response.message}")
                            } catch (e: Exception) {
                                println("Backend authentication failed: ${e.message}")
                            }
                        }

                        // Create or update user in local database
                        val user = User(
                            id = Uuid.random().toString(),
                            firebaseUid = result.user.uid,
                            email = result.user.email,
                            displayName = result.user.displayName ?: "User",
                            isGuest = false,
                            hasCompletedOnboarding = false,
                            createdAt = Clock.System.now()
                        )

                        userRepository.createUser(user)
                        _authState.value = AuthState.Authenticated(user)
                        println("AuthViewModel: User created successfully")
                    }
                    is AuthResult.Error -> {
                        _authState.value = AuthState.Error(result.message)
                        println("AuthViewModel: Google sign-in failed - ${result.message}")
                    }
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error")
                println("AuthViewModel: Exception during Google sign-in - ${e.message}")
            }
        }
    }

    /**
     * Sign in as guest using Firebase Anonymous Authentication
     */
    @OptIn(ExperimentalUuidApi::class)
    fun signInAsGuest() {
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                println("AuthViewModel: Starting anonymous sign-in")

                when (val result = authService.signInAnonymously()) {
                    is AuthResult.Success -> {
                        println("AuthViewModel: Anonymous sign-in successful, UID: ${result.user.uid}")

                        // Get Firebase ID token
                        val firebaseToken = authService.getIdToken(forceRefresh = true)

                        if (firebaseToken != null) {
                            try {
                                // Send token to backend
                                val response = apiService.authenticateWithToken(
                                    AuthTokenRequest(
                                        firebaseToken = firebaseToken,
                                        deviceInfo = "mobile"
                                    )
                                )
                                println("Backend authentication successful: ${response.message}")
                            } catch (e: Exception) {
                                println("Backend authentication failed: ${e.message}")
                                // Continue with local flow even if backend fails
                            }
                        }

                        // Create guest user in local database
                        val guestUser = User(
                            id = Uuid.random().toString(),
                            firebaseUid = result.user.uid,
                            email = null,
                            displayName = "Guest User",
                            isGuest = true,
                            hasCompletedOnboarding = false,
                            createdAt = Clock.System.now()
                        )

                        userRepository.createUser(guestUser)
                        _authState.value = AuthState.Guest(guestUser)
                        println("AuthViewModel: Guest user created successfully")
                    }
                    is AuthResult.Error -> {
                        _authState.value = AuthState.Error(result.message)
                        println("AuthViewModel: Anonymous sign-in failed - ${result.message}")
                    }
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error")
                println("AuthViewModel: Exception during anonymous sign-in - ${e.message}")
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
                println("AuthViewModel: Password reset email sent to $email")
            } catch (e: Exception) {
                println("AuthViewModel: Failed to send password reset email - ${e.message}")
            }
        }
    }

    /**
     * Get Firebase ID token for backend API requests
     */
    suspend fun getIdToken(forceRefresh: Boolean = false): String? {
        return try {
            authService.getIdToken(forceRefresh)
        } catch (e: Exception) {
            println("AuthViewModel: Failed to get ID token - ${e.message}")
            null
        }
    }

    /**
     * Sign out
     */
    fun signOut() {
        viewModelScope.launch {
            try {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null) {
                    userRepository.deleteUser(currentUser.id)
                }
                authService.signOut()
                _authState.value = AuthState.Unauthenticated
                println("AuthViewModel: Sign out successful")
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Failed to sign out")
                println("AuthViewModel: Sign out failed - ${e.message}")
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
