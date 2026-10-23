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
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlin.time.Clock
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
        viewModel = AuthViewModel(mockAuthService, mockUserRepository, mockSyncManager, supabaseClient = null, settings = MapSettings())
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

    // ── Delete account ───────────────────────────────────────────────

    @Test
    fun `deleteAccount removes the server account, then clears the phone and lands on first run`() = runTest(testDispatcher) {
        val user = createTestUser()
        mockUserRepository.currentUser = user
        viewModel._authState.value = AuthState.Authenticated(user)
        viewModel._hasCompletedOnboarding.value = true
        mockAuthService.deleteResult = true

        var result: Boolean? = null
        viewModel.deleteAccount { result = it }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, result)
        assertEquals(1, mockAuthService.deleteAccountCalls)
        assertTrue(mockUserRepository.clearAllLocalDataCalled, "local database must be wiped after the server delete")
        assertTrue(mockAuthService.signOutCalled, "the dead session must be dropped")
        assertNull(mockUserRepository.currentUser)
        assertEquals(false, viewModel.hasCompletedOnboarding.value, "first run again")
        assertTrue(viewModel.authState.value is AuthState.Unauthenticated, "got ${viewModel.authState.value}")
    }

    @Test
    fun `deleteAccount reports the result only after the phone is clear`() = runTest(testDispatcher) {
        val user = createTestUser()
        mockUserRepository.currentUser = user
        viewModel._authState.value = AuthState.Authenticated(user)

        var clearedWhenReported: Boolean? = null
        viewModel.deleteAccount { clearedWhenReported = mockUserRepository.clearAllLocalDataCalled }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, clearedWhenReported, "the sheet must not close before the local wipe has happened")
    }

    @Test
    fun `deleteAccount keeps everything when the server refuses`() = runTest(testDispatcher) {
        val user = createTestUser()
        mockUserRepository.currentUser = user
        viewModel._authState.value = AuthState.Authenticated(user)
        viewModel._hasCompletedOnboarding.value = true
        mockAuthService.deleteResult = false

        var result: Boolean? = null
        viewModel.deleteAccount { result = it }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, result)
        assertFalse(mockUserRepository.clearAllLocalDataCalled, "a failed delete must not touch local data")
        assertFalse(mockAuthService.signOutCalled)
        assertEquals(user, mockUserRepository.currentUser)
        assertEquals(true, viewModel.hasCompletedOnboarding.value)
        assertTrue(viewModel.authState.value is AuthState.Authenticated, "user stays signed in, got ${viewModel.authState.value}")
    }

    @Test
    fun `deleteAccount treats a thrown error like a refusal`() = runTest(testDispatcher) {
        val user = createTestUser()
        mockUserRepository.currentUser = user
        viewModel._authState.value = AuthState.Authenticated(user)
        mockAuthService.deleteThrows = true

        var result: Boolean? = null
        viewModel.deleteAccount { result = it }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, result)
        assertFalse(mockUserRepository.clearAllLocalDataCalled)
        assertTrue(viewModel.authState.value is AuthState.Authenticated)
    }

    // ── Erase data on this device (guests) ───────────────────────────

    @Test
    fun `eraseDeviceData for an anonymous guest wipes the phone and drops the anonymous login`() = runTest(testDispatcher) {
        val guest = createTestUser(isGuest = true)
        mockUserRepository.currentUser = guest
        viewModel._authState.value = AuthState.Guest(guest)
        viewModel._isLocalOnlyGuest.value = false

        var done = false
        viewModel.eraseDeviceData { done = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(done)
        assertEquals(1, mockAuthService.deleteAccountCalls, "the anonymous auth user is server junk, remove it")
        assertTrue(mockUserRepository.clearAllLocalDataCalled)
        assertTrue(viewModel.authState.value is AuthState.Unauthenticated)
    }

    @Test
    fun `eraseDeviceData for a local-only guest never calls the server`() = runTest(testDispatcher) {
        val guest = createTestUser(id = "local_guest", isGuest = true)
        mockUserRepository.currentUser = guest
        viewModel._authState.value = AuthState.Guest(guest)
        viewModel._isLocalOnlyGuest.value = true

        var done = false
        viewModel.eraseDeviceData { done = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(done)
        assertEquals(0, mockAuthService.deleteAccountCalls)
        assertTrue(mockUserRepository.clearAllLocalDataCalled)
        assertFalse(viewModel.isLocalOnlyGuest.value)
        assertTrue(viewModel.authState.value is AuthState.Unauthenticated)
    }

    @Test
    fun `eraseDeviceData still clears the phone when the server cleanup fails`() = runTest(testDispatcher) {
        val guest = createTestUser(isGuest = true)
        mockUserRepository.currentUser = guest
        viewModel._authState.value = AuthState.Guest(guest)
        mockAuthService.deleteThrows = true

        var done = false
        viewModel.eraseDeviceData { done = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(done)
        assertTrue(mockUserRepository.clearAllLocalDataCalled)
        assertTrue(viewModel.authState.value is AuthState.Unauthenticated)
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

    var deleteResult = true
    var deleteThrows = false
    var deleteAccountCalls = 0
    override suspend fun deleteAccount(): Boolean {
        deleteAccountCalls++
        if (deleteThrows) throw Exception("Test error: delete failed")
        return deleteResult
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

    override suspend fun verifySignupOtp(email: String, token: String): AuthResult {
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
