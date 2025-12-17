package az.tribe.lifeplanner.data.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import az.tribe.lifeplanner.database.LifePlannerDB
import az.tribe.lifeplanner.di.DatabaseDriverFactory
import az.tribe.lifeplanner.domain.model.OnboardingData
import az.tribe.lifeplanner.domain.model.User
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class UserRepositoryImplTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: LifePlannerDB
    private lateinit var sharedDatabase: SharedDatabase
    private lateinit var repository: UserRepositoryImpl

    @BeforeTest
    fun setup() {
        // Create in-memory database for testing
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LifePlannerDB.Schema.create(driver)
        database = LifePlannerDB(driver)

        // Create test DatabaseDriverFactory
        val driverFactory = object : DatabaseDriverFactory {
            override fun createDriver(): SqlDriver = driver
        }

        sharedDatabase = SharedDatabase(driverFactory)
        repository = UserRepositoryImpl(sharedDatabase)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `createUser inserts user into database`() = runTest {
        val testUser = createTestUser()

        repository.createUser(testUser)

        val retrievedUser = repository.getCurrentUser()
        assertNotNull(retrievedUser)
        assertEquals(testUser.id, retrievedUser.id)
        assertEquals(testUser.displayName, retrievedUser.displayName)
        assertEquals(testUser.email, retrievedUser.email)
        assertEquals(testUser.isGuest, retrievedUser.isGuest)
    }

    @Test
    fun `getCurrentUser returns null when no user exists`() = runTest {
        val user = repository.getCurrentUser()
        assertNull(user)
    }

    @Test
    fun `getCurrentUser returns most recently created user`() = runTest {
        val user1 = createTestUser(displayName = "User 1")
        val user2 = createTestUser(displayName = "User 2")

        repository.createUser(user1)
        repository.createUser(user2)

        val currentUser = repository.getCurrentUser()
        assertNotNull(currentUser)
        assertEquals(user2.displayName, currentUser.displayName)
    }

    @Test
    fun `updateUser modifies existing user data`() = runTest {
        val originalUser = createTestUser(displayName = "Original Name")
        repository.createUser(originalUser)

        val updatedUser = originalUser.copy(
            displayName = "Updated Name",
            email = "updated@example.com"
        )
        repository.updateUser(updatedUser)

        val retrievedUser = repository.getCurrentUser()
        assertNotNull(retrievedUser)
        assertEquals("Updated Name", retrievedUser.displayName)
        assertEquals("updated@example.com", retrievedUser.email)
    }

    @Test
    fun `saveOnboardingData updates user onboarding information`() = runTest {
        val testUser = createTestUser()
        repository.createUser(testUser)

        val onboardingData = OnboardingData(
            selectedSymbol = "🦊",
            priorities = listOf("FINANCIAL", "CAREER", "PHYSICAL"),
            ageRange = "25-34",
            profession = "Engineer",
            relationshipStatus = "Single",
            mindset = "Focus"
        )

        repository.saveOnboardingData(testUser.id, onboardingData)

        val retrievedUser = repository.getCurrentUser()
        assertNotNull(retrievedUser)
        assertEquals("🦊", retrievedUser.selectedSymbol)
        assertEquals(3, retrievedUser.priorities.size)
        assertTrue(retrievedUser.priorities.contains("FINANCIAL"))
        assertTrue(retrievedUser.priorities.contains("CAREER"))
        assertTrue(retrievedUser.priorities.contains("PHYSICAL"))
        assertEquals("25-34", retrievedUser.ageRange)
        assertEquals("Engineer", retrievedUser.profession)
        assertEquals("Single", retrievedUser.relationshipStatus)
        assertEquals("Focus", retrievedUser.mindset)
    }

    @Test
    fun `hasCompletedOnboarding returns false for new user`() = runTest {
        val testUser = createTestUser(hasCompletedOnboarding = false)
        repository.createUser(testUser)

        val hasCompleted = repository.hasCompletedOnboarding()
        assertFalse(hasCompleted)
    }

    @Test
    fun `hasCompletedOnboarding returns true when user completed onboarding`() = runTest {
        val testUser = createTestUser(hasCompletedOnboarding = true)
        repository.createUser(testUser)

        val hasCompleted = repository.hasCompletedOnboarding()
        assertTrue(hasCompleted)
    }

    @Test
    fun `hasCompletedOnboarding returns false when no user exists`() = runTest {
        val hasCompleted = repository.hasCompletedOnboarding()
        assertFalse(hasCompleted)
    }

    @Test
    fun `deleteUser removes user from database`() = runTest {
        val testUser = createTestUser()
        repository.createUser(testUser)

        val userBeforeDelete = repository.getCurrentUser()
        assertNotNull(userBeforeDelete)

        repository.deleteUser(testUser.id)

        val userAfterDelete = repository.getCurrentUser()
        assertNull(userAfterDelete)
    }

    @Test
    fun `getCurrentUserFlow emits current user`() = runTest {
        val testUser = createTestUser()
        repository.createUser(testUser)

        val userFlow = repository.getCurrentUserFlow()
        val emittedUser = userFlow.first()

        assertNotNull(emittedUser)
        assertEquals(testUser.id, emittedUser.id)
        assertEquals(testUser.displayName, emittedUser.displayName)
    }

    @Test
    fun `getCurrentUserFlow emits null when no user exists`() = runTest {
        val userFlow = repository.getCurrentUserFlow()
        val emittedUser = userFlow.first()

        assertNull(emittedUser)
    }

    @Test
    fun `createUser with guest user stores correct flags`() = runTest {
        val guestUser = createTestUser(
            isGuest = true,
            email = null,
            displayName = "Guest User"
        )

        repository.createUser(guestUser)

        val retrievedUser = repository.getCurrentUser()
        assertNotNull(retrievedUser)
        assertTrue(retrievedUser.isGuest)
        assertNull(retrievedUser.email)
        assertEquals("Guest User", retrievedUser.displayName)
    }

    @Test
    fun `priorities are correctly stored and retrieved as comma-separated list`() = runTest {
        val testUser = createTestUser()
        repository.createUser(testUser)

        val onboardingData = OnboardingData(
            selectedSymbol = "🦁",
            priorities = listOf("EMOTIONAL", "FAMILY", "SPIRITUAL"),
            ageRange = "35-44",
            profession = "Manager",
            relationshipStatus = "Married",
            mindset = "Gratitude"
        )

        repository.saveOnboardingData(testUser.id, onboardingData)

        val retrievedUser = repository.getCurrentUser()
        assertNotNull(retrievedUser)
        assertEquals(3, retrievedUser.priorities.size)
        assertEquals("EMOTIONAL", retrievedUser.priorities[0])
        assertEquals("FAMILY", retrievedUser.priorities[1])
        assertEquals("SPIRITUAL", retrievedUser.priorities[2])
    }

    @Test
    fun `empty priorities list is handled correctly`() = runTest {
        val testUser = createTestUser()
        repository.createUser(testUser)

        val onboardingData = OnboardingData(
            selectedSymbol = "🐢",
            priorities = emptyList(),
            ageRange = "18-24",
            profession = "Student",
            relationshipStatus = "Single",
            mindset = "Patience"
        )

        repository.saveOnboardingData(testUser.id, onboardingData)

        val retrievedUser = repository.getCurrentUser()
        assertNotNull(retrievedUser)
        assertTrue(retrievedUser.priorities.isEmpty())
    }

    @Test
    fun `updateUser updates lastSyncedAt timestamp`() = runTest {
        val originalUser = createTestUser()
        repository.createUser(originalUser)

        // Wait a bit to ensure timestamp difference
        kotlinx.coroutines.delay(10)

        val updatedUser = originalUser.copy(displayName = "Updated")
        repository.updateUser(updatedUser)

        val retrievedUser = repository.getCurrentUser()
        assertNotNull(retrievedUser)
        assertNotNull(retrievedUser.lastSyncedAt)
    }

    // Helper function to create test user
    private fun createTestUser(
        id: String = Uuid.random().toString(),
        firebaseUid: String = "test-firebase-uid-${Uuid.random()}",
        email: String? = "test@example.com",
        displayName: String = "Test User",
        isGuest: Boolean = false,
        hasCompletedOnboarding: Boolean = false,
        selectedSymbol: String? = null,
        priorities: List<String> = emptyList(),
        ageRange: String? = null,
        profession: String? = null,
        relationshipStatus: String? = null,
        mindset: String? = null
    ): User {
        return User(
            id = id,
            firebaseUid = firebaseUid,
            email = email,
            displayName = displayName,
            isGuest = isGuest,
            hasCompletedOnboarding = hasCompletedOnboarding,
            selectedSymbol = selectedSymbol,
            priorities = priorities,
            ageRange = ageRange,
            profession = profession,
            relationshipStatus = relationshipStatus,
            mindset = mindset,
            createdAt = Clock.System.now(),
            lastSyncedAt = null
        )
    }
}
