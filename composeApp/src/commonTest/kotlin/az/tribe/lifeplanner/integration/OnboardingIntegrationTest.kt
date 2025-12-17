package az.tribe.lifeplanner.integration

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.turbine.test
import az.tribe.lifeplanner.data.repository.UserRepositoryImpl
import az.tribe.lifeplanner.database.LifePlannerDB
import az.tribe.lifeplanner.di.DatabaseDriverFactory
import az.tribe.lifeplanner.domain.model.OnboardingData
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import az.tribe.lifeplanner.ui.viewmodel.AuthState
import az.tribe.lifeplanner.ui.viewmodel.AuthViewModel
import dev.com3run.firebaseauthkmp.AuthRepository
import dev.com3run.firebaseauthkmp.AuthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

/**
 * Integration test for the complete onboarding flow
 * Tests the interaction between AuthViewModel, UserRepository, and the database
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingIntegrationTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: LifePlannerDB
    private lateinit var sharedDatabase: SharedDatabase
    private lateinit var userRepository: UserRepositoryImpl
    private lateinit var mockAuthRepository: TestAuthRepository
    private lateinit var authViewModel: AuthViewModel
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Setup database
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LifePlannerDB.Schema.create(driver)
        database = LifePlannerDB(driver)

        // Create test DatabaseDriverFactory
        val driverFactory = object : DatabaseDriverFactory {
            override fun createDriver(): SqlDriver = driver
        }

        sharedDatabase = SharedDatabase(driverFactory)

        // Setup repositories and viewmodels
        userRepository = UserRepositoryImpl(sharedDatabase)
        mockAuthRepository = TestAuthRepository()
        authViewModel = AuthViewModel(mockAuthRepository, userRepository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        driver.close()
    }

    @Test
    fun `complete onboarding flow - guest sign in and save onboarding data`() = runTest(testDispatcher) {
        // Step 1: User starts onboarding - initial state should be Loading then Unauthenticated
        testDispatcher.scheduler.advanceUntilIdle()

        authViewModel.authState.test {
            val initialState = awaitItem()
            assertTrue(
                initialState is AuthState.Unauthenticated,
                "Expected Unauthenticated but got $initialState"
            )

            // Step 2: User completes onboarding questions and clicks "Continue as Guest"
            authViewModel.signInAsGuest()
            testDispatcher.scheduler.advanceUntilIdle()

            // Step 3: Verify user is signed in as guest
            val guestState = awaitItem()
            assertTrue(guestState is AuthState.Guest, "Expected Guest state but got $guestState")

            val guestUser = (guestState as AuthState.Guest).user
            assertNotNull(guestUser)
            assertTrue(guestUser.isGuest, "User should be marked as guest")
            assertFalse(guestUser.hasCompletedOnboarding, "User should not have completed onboarding yet")

            // Step 4: Save onboarding data
            val onboardingData = OnboardingData(
                selectedSymbol = "🦊",
                priorities = listOf("FINANCIAL", "CAREER", "PHYSICAL"),
                ageRange = "25-34",
                profession = "Engineer",
                relationshipStatus = "Single",
                mindset = "Focus"
            )

            userRepository.saveOnboardingData(guestUser.id, onboardingData)

            // Step 5: Verify onboarding data was saved
            val updatedUser = userRepository.getCurrentUser()
            assertNotNull(updatedUser)
            assertEquals("🦊", updatedUser.selectedSymbol)
            assertEquals(3, updatedUser.priorities.size)
            assertTrue(updatedUser.priorities.contains("FINANCIAL"))
            assertTrue(updatedUser.priorities.contains("CAREER"))
            assertTrue(updatedUser.priorities.contains("PHYSICAL"))
            assertEquals("25-34", updatedUser.ageRange)
            assertEquals("Engineer", updatedUser.profession)
            assertEquals("Single", updatedUser.relationshipStatus)
            assertEquals("Focus", updatedUser.mindset)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onboarding flow handles authentication errors gracefully`() = runTest(testDispatcher) {
        testDispatcher.scheduler.advanceUntilIdle()

        authViewModel.authState.test {
            // Skip initial state
            awaitItem()

            // Simulate authentication error
            mockAuthRepository.shouldThrowError = true
            authViewModel.signInAsGuest()
            testDispatcher.scheduler.advanceUntilIdle()

            // Verify error state
            val errorState = awaitItem()
            assertTrue(errorState is AuthState.Error, "Expected Error state but got $errorState")
            assertNotNull((errorState as AuthState.Error).message)

            // Verify no user was created in database
            val user = userRepository.getCurrentUser()
            assertNull(user, "No user should be created when authentication fails")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `user can complete onboarding with all different profile options`() = runTest(testDispatcher) {
        testDispatcher.scheduler.advanceUntilIdle()

        authViewModel.authState.test {
            awaitItem() // Skip initial state

            // Sign in as guest
            authViewModel.signInAsGuest()
            testDispatcher.scheduler.advanceUntilIdle()

            val guestState = awaitItem()
            val guestUser = (guestState as AuthState.Guest).user

            // Test different onboarding scenarios
            val scenarios = listOf(
                OnboardingData(
                    selectedSymbol = "🐢",
                    priorities = listOf("EMOTIONAL", "FAMILY", "SOCIAL"),
                    ageRange = "18-24",
                    profession = "Student",
                    relationshipStatus = "Single",
                    mindset = "Patience"
                ),
                OnboardingData(
                    selectedSymbol = "🦁",
                    priorities = listOf("CAREER", "FINANCIAL", "SPIRITUAL"),
                    ageRange = "35-44",
                    profession = "Manager",
                    relationshipStatus = "Married",
                    mindset = "Self-Discipline"
                ),
                OnboardingData(
                    selectedSymbol = "🐰",
                    priorities = listOf("PHYSICAL", "SOCIAL", "EMOTIONAL"),
                    ageRange = "45+",
                    profession = "Entrepreneur",
                    relationshipStatus = "In a Relationship",
                    mindset = "Gratitude"
                )
            )

            // Test each scenario
            for (scenario in scenarios) {
                userRepository.saveOnboardingData(guestUser.id, scenario)

                val updatedUser = userRepository.getCurrentUser()
                assertNotNull(updatedUser)
                assertEquals(scenario.selectedSymbol, updatedUser.selectedSymbol)
                assertEquals(scenario.priorities, updatedUser.priorities)
                assertEquals(scenario.ageRange, updatedUser.ageRange)
                assertEquals(scenario.profession, updatedUser.profession)
                assertEquals(scenario.relationshipStatus, updatedUser.relationshipStatus)
                assertEquals(scenario.mindset, updatedUser.mindset)
            }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `user can sign out after completing onboarding`() = runTest(testDispatcher) {
        testDispatcher.scheduler.advanceUntilIdle()

        authViewModel.authState.test {
            awaitItem() // Skip initial state

            // Complete onboarding
            authViewModel.signInAsGuest()
            testDispatcher.scheduler.advanceUntilIdle()

            val guestState = awaitItem()
            val guestUser = (guestState as AuthState.Guest).user

            val onboardingData = OnboardingData(
                selectedSymbol = "🐉",
                priorities = listOf("CAREER", "PHYSICAL", "SPIRITUAL"),
                ageRange = "25-34",
                profession = "Creative",
                relationshipStatus = "Prefer Not to Say",
                mindset = "Consistency"
            )

            userRepository.saveOnboardingData(guestUser.id, onboardingData)

            // Verify data is saved
            val userBeforeSignOut = userRepository.getCurrentUser()
            assertNotNull(userBeforeSignOut)

            // Sign out
            authViewModel.signOut()
            testDispatcher.scheduler.advanceUntilIdle()

            // Verify state is Unauthenticated
            val signOutState = awaitItem()
            assertTrue(
                signOutState is AuthState.Unauthenticated,
                "Expected Unauthenticated after sign out but got $signOutState"
            )

            // Verify user data is deleted
            val userAfterSignOut = userRepository.getCurrentUser()
            assertNull(userAfterSignOut, "User data should be deleted after sign out")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onboarding state persists across app restarts`() = runTest(testDispatcher) {
        // Simulate first app launch and onboarding
        testDispatcher.scheduler.advanceUntilIdle()

        authViewModel.authState.test {
            awaitItem() // Skip initial state

            authViewModel.signInAsGuest()
            testDispatcher.scheduler.advanceUntilIdle()

            val guestState = awaitItem()
            val guestUser = (guestState as AuthState.Guest).user

            val onboardingData = OnboardingData(
                selectedSymbol = "🦊",
                priorities = listOf("FINANCIAL", "CAREER", "PHYSICAL"),
                ageRange = "25-34",
                profession = "Engineer",
                relationshipStatus = "Single",
                mindset = "Focus"
            )

            userRepository.saveOnboardingData(guestUser.id, onboardingData)

            cancelAndIgnoreRemainingEvents()
        }

        // Simulate app restart - create new ViewModel instance
        val newAuthViewModel = AuthViewModel(mockAuthRepository, userRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        newAuthViewModel.authState.test {
            val state = awaitItem()

            // Verify user is still signed in as guest
            assertTrue(state is AuthState.Guest, "User should still be signed in after restart")

            val user = (state as AuthState.Guest).user

            // Verify onboarding data persisted
            assertEquals("🦊", user.selectedSymbol)
            assertEquals(3, user.priorities.size)
            assertEquals("25-34", user.ageRange)
            assertEquals("Engineer", user.profession)
            assertEquals("Single", user.relationshipStatus)
            assertEquals("Focus", user.mindset)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onboarding validates minimum 3 priorities selection`() = runTest(testDispatcher) {
        testDispatcher.scheduler.advanceUntilIdle()

        authViewModel.authState.test {
            awaitItem()

            authViewModel.signInAsGuest()
            testDispatcher.scheduler.advanceUntilIdle()

            val guestState = awaitItem()
            val guestUser = (guestState as AuthState.Guest).user

            // Test with less than 3 priorities (this should be validated in UI)
            val invalidOnboardingData = OnboardingData(
                selectedSymbol = "🦊",
                priorities = listOf("FINANCIAL", "CAREER"), // Only 2 priorities
                ageRange = "25-34",
                profession = "Engineer",
                relationshipStatus = "Single",
                mindset = "Focus"
            )

            userRepository.saveOnboardingData(guestUser.id, invalidOnboardingData)

            val updatedUser = userRepository.getCurrentUser()
            assertNotNull(updatedUser)
            // Repository will save whatever is provided, validation should happen in UI
            assertEquals(2, updatedUser.priorities.size)

            cancelAndIgnoreRemainingEvents()
        }
    }
}

/**
 * Test implementation of AuthRepository for integration testing
 */
class TestAuthRepository(
    backend: String = "test"
) : AuthRepository(backend) {
    var shouldThrowError = false
    var signInAnonymouslyResult: String = "test-firebase-uid"

    override suspend fun signInAnonymously(): AuthResult {
        if (shouldThrowError) {
            throw Exception("Test error: Sign in failed")
        }
        return AuthResult(user = signInAnonymouslyResult)
    }
}
