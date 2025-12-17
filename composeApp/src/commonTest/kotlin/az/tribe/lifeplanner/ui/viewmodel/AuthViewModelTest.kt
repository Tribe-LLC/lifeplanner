package az.tribe.lifeplanner.ui.viewmodel

import app.cash.turbine.test
import az.tribe.lifeplanner.domain.model.User
import az.tribe.lifeplanner.domain.repository.UserRepository
import dev.com3run.firebaseauthkmp.AuthRepository
import dev.com3run.firebaseauthkmp.AuthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.datetime.Clock
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
class AuthViewModelTest {

    private lateinit var viewModel: AuthViewModel
    private lateinit var mockAuthRepository: MockAuthRepository
    private lateinit var mockUserRepository: MockUserRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockAuthRepository = MockAuthRepository()
        mockUserRepository = MockUserRepository()
        viewModel = AuthViewModel(mockAuthRepository, mockUserRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading`() = runTest(testDispatcher) {
        viewModel.authState.test {
            val initialState = awaitItem()
            assertTrue(initialState is AuthState.Loading)
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
    fun `checkAuthStatus updates to Guest when guest user exists`() = runTest(testDispatcher) {
        val guestUser = createTestUser(isGuest = true)
        mockUserRepository.currentUser = guestUser

        viewModel.refreshAuthState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.authState.test {
            val state = awaitItem()
            assertTrue(state is AuthState.Guest, "Expected Guest but got $state")
            assertEquals(guestUser, (state as AuthState.Guest).user)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checkAuthStatus updates to Authenticated when authenticated user exists`() = runTest(testDispatcher) {
        val authenticatedUser = createTestUser(isGuest = false)
        mockUserRepository.currentUser = authenticatedUser

        viewModel.refreshAuthState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.authState.test {
            val state = awaitItem()
            assertTrue(state is AuthState.Authenticated, "Expected Authenticated but got $state")
            assertEquals(authenticatedUser, (state as AuthState.Authenticated).user)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signInAsGuest creates guest user and updates state`() = runTest(testDispatcher) {
        mockAuthRepository.signInAnonymouslyResult = "test-firebase-uid"

        viewModel.signInAsGuest()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify user was created
        assertNotNull(mockUserRepository.createdUser)
        assertTrue(mockUserRepository.createdUser!!.isGuest)
        assertFalse(mockUserRepository.createdUser!!.hasCompletedOnboarding)

        // Verify state updated to Guest
        viewModel.authState.test {
            val state = awaitItem()
            assertTrue(state is AuthState.Guest, "Expected Guest but got $state")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signInAsGuest handles error correctly`() = runTest(testDispatcher) {
        mockAuthRepository.shouldThrowError = true

        viewModel.signInAsGuest()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.authState.test {
            val state = awaitItem()
            assertTrue(state is AuthState.Error, "Expected Error but got $state")
            assertNotNull((state as AuthState.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signOut deletes user and updates state to Unauthenticated`() = runTest(testDispatcher) {
        val testUser = createTestUser()
        mockUserRepository.currentUser = testUser

        viewModel.signOut()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify user was deleted
        assertEquals(testUser.id, mockUserRepository.deletedUserId)

        // Verify Firebase sign out was called
        assertTrue(mockAuthRepository.signOutCalled)

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

        // Should still call Firebase sign out
        assertTrue(mockAuthRepository.signOutCalled)

        // State should be Unauthenticated (not Error)
        viewModel.authState.test {
            val state = awaitItem()
            assertTrue(state is AuthState.Unauthenticated, "Expected Unauthenticated but got $state")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refreshAuthState rechecks authentication status`() = runTest(testDispatcher) {
        // Initially no user
        mockUserRepository.currentUser = null
        viewModel.refreshAuthState()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.authState.test {
            assertTrue(awaitItem() is AuthState.Unauthenticated)

            // Now add a user and refresh
            mockUserRepository.currentUser = createTestUser()
            viewModel.refreshAuthState()
            testDispatcher.scheduler.advanceUntilIdle()

            val newState = awaitItem()
            assertTrue(newState is AuthState.Authenticated, "Expected Authenticated after refresh but got $newState")
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
class MockAuthRepository(
    backend: String = "test"
) : AuthRepository(backend) {
    var signInAnonymouslyResult: String = "test-firebase-uid"
    var shouldThrowError = false
    var signOutCalled = false

    override suspend fun signInAnonymously(): AuthResult {
        if (shouldThrowError) {
            throw Exception("Test error: Sign in failed")
        }
        return AuthResult(user = signInAnonymouslyResult)
    }

    override suspend fun signOut() {
        signOutCalled = true
    }
}

class MockUserRepository : UserRepository {
    var currentUser: User? = null
    var createdUser: User? = null
    var updatedUser: User? = null
    var deletedUserId: String? = null
    var shouldThrowError = false

    override suspend fun getCurrentUser(): User? {
        if (shouldThrowError) {
            throw Exception("Test error: Get user failed")
        }
        return currentUser
    }

    override suspend fun createUser(user: User) {
        if (shouldThrowError) {
            throw Exception("Test error: Create user failed")
        }
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

    override suspend fun hasCompletedOnboarding(): Boolean {
        return currentUser?.hasCompletedOnboarding ?: false
    }

    override fun getCurrentUserFlow(): kotlinx.coroutines.flow.Flow<User?> {
        return kotlinx.coroutines.flow.flowOf(currentUser)
    }

    override suspend fun saveOnboardingData(
        userId: String,
        onboardingData: az.tribe.lifeplanner.domain.model.OnboardingData
    ) {
        // Mock implementation
    }
}
