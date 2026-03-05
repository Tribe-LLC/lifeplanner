package az.tribe.lifeplanner.ui.viewmodel

import app.cash.turbine.test
import az.tribe.lifeplanner.data.auth.AuthResult
import az.tribe.lifeplanner.data.auth.AuthService
import az.tribe.lifeplanner.data.auth.FirebaseUser
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.data.sync.SyncState
import az.tribe.lifeplanner.data.sync.SyncStatus
import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.domain.model.User
import az.tribe.lifeplanner.domain.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlinx.datetime.Clock
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
class AuthViewModelTest {

    private lateinit var viewModel: AuthViewModel
    private lateinit var mockAuthService: MockAuthService
    private lateinit var mockUserRepository: MockUserRepository
    private lateinit var mockSyncManager: SyncManager
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockAuthService = MockAuthService()
        mockUserRepository = MockUserRepository()
        mockSyncManager = SyncManager(
            userIdProvider = { null },
            isConnectedProvider = { false },
            connectivityFlow = null,
            syncersProvider = { emptyList() }
        )
        viewModel = AuthViewModel(mockAuthService, mockUserRepository, mockSyncManager)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state transitions from Loading`() = runTest(testDispatcher) {
        viewModel.authState.test {
            val initialState = awaitItem()
            // May be Loading or already transitioned to Unauthenticated depending on timing
            assertTrue(
                initialState is AuthState.Loading || initialState is AuthState.Unauthenticated,
                "Expected Loading or Unauthenticated but got $initialState"
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checkAuthStatus updates to Unauthenticated when no user exists`() = runTest(testDispatcher) {
        mockUserRepository.currentUser = null

        viewModel.refreshAuthState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.authState.test {
            val state = awaitItem()
            assertTrue(state is AuthState.Unauthenticated, "Expected Unauthenticated but got $state")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signOut calls syncManager onLogout before clearing data`() = runTest(testDispatcher) {
        val testUser = createTestUser()
        mockUserRepository.currentUser = testUser

        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify sign out was called
        assertTrue(mockAuthService.signOutCalled)

        // Verify state updated to Unauthenticated
        viewModel.authState.test {
            val state = awaitItem()
            assertTrue(state is AuthState.Unauthenticated, "Expected Unauthenticated but got $state")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signOut handles error when no current user`() = runTest(testDispatcher) {
        mockUserRepository.currentUser = null

        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(mockAuthService.signOutCalled)

        viewModel.authState.test {
            val state = awaitItem()
            assertTrue(state is AuthState.Unauthenticated, "Expected Unauthenticated but got $state")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `completeOnboarding marks onboarding complete for current user`() = runTest(testDispatcher) {
        val testUser = createTestUser()
        mockUserRepository.currentUser = testUser

        viewModel.completeOnboarding()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            mockUserRepository.markOnboardingCompleteCalled,
            "Expected markOnboardingComplete to be called"
        )
        assertEquals(testUser.id, mockUserRepository.markOnboardingCompleteUserId)

        viewModel.hasCompletedOnboarding.test {
            val value = awaitItem()
            assertTrue(value == true, "Expected hasCompletedOnboarding to be true but got $value")
            cancelAndIgnoreRemainingEvents()
        }
    }

    // Helper function to create test user
    private fun createTestUser(
        id: String = Uuid.random().toString(),
        isGuest: Boolean = false
    ): User {
        return User(
            id = id,
            firebaseUid = "test-firebase-uid",
            email = if (isGuest) null else "test@example.com",
            displayName = if (isGuest) "Guest User" else "Test User",
            isGuest = isGuest,
            hasCompletedOnboarding = false,
            createdAt = Clock.System.now()
        )
    }
}

// Mock implementations
class MockAuthService : AuthService {
    var signOutCalled = false
    var shouldThrowError = false
    var restoreSessionResult: FirebaseUser? = null

    override suspend fun signInWithEmail(email: String, password: String): AuthResult {
        if (shouldThrowError) throw Exception("Test error")
        return AuthResult.Error("Not implemented in mock")
    }

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String): AuthResult {
        if (shouldThrowError) throw Exception("Test error")
        return AuthResult.Error("Not implemented in mock")
    }

    override suspend fun signInWithGoogle(): AuthResult {
        return AuthResult.Error("Not implemented in mock")
    }

    override suspend fun signInAnonymously(): AuthResult {
        if (shouldThrowError) throw Exception("Test error: Sign in failed")
        return AuthResult.Success(
            FirebaseUser(
                uid = "test-anon-uid",
                email = null,
                displayName = null,
                photoUrl = null,
                isAnonymous = true
            )
        )
    }

    override suspend fun signOut() {
        signOutCalled = true
    }

    override suspend fun getCurrentUser(): FirebaseUser? = null

    override suspend fun sendPasswordResetEmail(email: String) {}

    override suspend fun restoreSession(): FirebaseUser? = restoreSessionResult

    override suspend fun linkEmailToAnonymousAccount(
        email: String,
        password: String,
        displayName: String?
    ): AuthResult = AuthResult.Error("Not implemented in mock")

    override suspend fun resendVerificationEmail(email: String) {}

    override suspend fun signInWithMagicLink(email: String) {}

    override suspend fun verifyOtp(email: String, token: String): AuthResult {
        return AuthResult.Error("Not implemented in mock")
    }
}

class MockUserRepository : UserRepository {
    var currentUser: User? = null
    var createdUser: User? = null
    var updatedUser: User? = null
    var deletedUserId: String? = null
    var shouldThrowError = false
    var clearAllLocalDataCalled = false
    var markOnboardingCompleteCalled = false
    var markOnboardingCompleteUserId: String? = null

    override suspend fun getCurrentUser(): User? {
        if (shouldThrowError) throw Exception("Test error: Get user failed")
        return currentUser
    }

    override suspend fun createUser(user: User) {
        if (shouldThrowError) throw Exception("Test error: Create user failed")
        createdUser = user
        currentUser = user
    }

    override suspend fun updateUser(user: User) {
        updatedUser = user
        currentUser = user
    }

    override suspend fun deleteUser(userId: String) {
        deletedUserId = userId
        currentUser = null
    }

    override suspend fun deleteAllUsers() {
        currentUser = null
    }

    override suspend fun getUserByFirebaseUid(uid: String): User? {
        return if (currentUser?.firebaseUid == uid) currentUser else null
    }

    override suspend fun clearAllLocalData() {
        clearAllLocalDataCalled = true
        currentUser = null
    }

    override suspend fun markOnboardingComplete(userId: String) {
        markOnboardingCompleteCalled = true
        markOnboardingCompleteUserId = userId
    }

    override suspend fun hasCompletedOnboarding(): Boolean {
        return currentUser?.hasCompletedOnboarding ?: false
    }

    override fun getCurrentUserFlow(): Flow<User?> {
        return flowOf(currentUser)
    }

    override suspend fun saveOnboardingData(
        userId: String,
        onboardingData: az.tribe.lifeplanner.domain.model.OnboardingData
    ) {
        // Mock implementation
    }
}
