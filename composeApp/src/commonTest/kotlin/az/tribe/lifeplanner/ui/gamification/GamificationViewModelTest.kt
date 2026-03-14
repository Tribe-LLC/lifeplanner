package az.tribe.lifeplanner.ui.gamification

import app.cash.turbine.test
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.domain.enum.BadgeType
import az.tribe.lifeplanner.domain.enum.ChallengeType
import az.tribe.lifeplanner.domain.model.ChallengeTargets
import az.tribe.lifeplanner.testutil.FakeGamificationRepository
import az.tribe.lifeplanner.testutil.testBadge
import az.tribe.lifeplanner.testutil.testChallenge
import az.tribe.lifeplanner.testutil.testUserProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GamificationViewModelTest {

    private lateinit var viewModel: GamificationViewModel
    private lateinit var fakeRepository: FakeGamificationRepository
    private lateinit var syncManager: SyncManager
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeGamificationRepository()
        syncManager = SyncManager(
            userIdProvider = { null },
            isConnectedProvider = { false },
            connectivityFlow = null,
            syncersProvider = { emptyList() }
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): GamificationViewModel {
        return GamificationViewModel(
            gamificationRepository = fakeRepository,
            syncManager = syncManager
        )
    }

    @Test
    fun `init loads user progress`() = runTest(testDispatcher) {
        fakeRepository.userProgress = testUserProgress(totalXp = 999, currentLevel = 7)
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val progress = viewModel.userProgress.value
        assertNotNull(progress)
        assertEquals(999, progress.totalXp)
        assertEquals(7, progress.currentLevel)
    }

    @Test
    fun `init loads badges`() = runTest(testDispatcher) {
        fakeRepository.setBadges(listOf(
            testBadge(id = "b1", type = BadgeType.FIRST_STEP),
            testBadge(id = "b2", type = BadgeType.GOAL_1)
        ))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.badges.value.size)
    }

    @Test
    fun `init loads new badges`() = runTest(testDispatcher) {
        fakeRepository.setBadges(listOf(
            testBadge(id = "b1", type = BadgeType.FIRST_STEP, isNew = true),
            testBadge(id = "b2", type = BadgeType.GOAL_1, isNew = false)
        ))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.newBadges.value.size)
        assertEquals(BadgeType.FIRST_STEP, viewModel.newBadges.value.first().type)
    }

    @Test
    fun `init loads active challenges`() = runTest(testDispatcher) {
        fakeRepository.setChallenges(listOf(
            testChallenge(id = "c1", isCompleted = false),
            testChallenge(id = "c2", isCompleted = true)
        ))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.activeChallenges.value.size)
        assertEquals("c1", viewModel.activeChallenges.value.first().id)
    }

    @Test
    fun `init loads completed challenges`() = runTest(testDispatcher) {
        fakeRepository.setChallenges(listOf(
            testChallenge(id = "c1", isCompleted = false),
            testChallenge(id = "c2", isCompleted = true)
        ))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.completedChallenges.value.size)
        assertEquals("c2", viewModel.completedChallenges.value.first().id)
    }

    @Test
    fun `init loads available challenges`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // FakeGamificationRepository returns all ChallengeType entries
        assertEquals(ChallengeType.entries.size, viewModel.availableChallenges.value.size)
    }

    @Test
    fun `isLoading is false after init completes`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.isLoading.value)
    }

    @Test
    fun `markBadgeAsSeen marks badge and reloads`() = runTest(testDispatcher) {
        fakeRepository.setBadges(listOf(
            testBadge(id = "b1", type = BadgeType.FIRST_STEP, isNew = true)
        ))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.newBadges.value.size)

        viewModel.markBadgeAsSeen("b1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.newBadges.value.isEmpty())
    }

    @Test
    fun `markAllBadgesAsSeen marks all badges and reloads`() = runTest(testDispatcher) {
        fakeRepository.setBadges(listOf(
            testBadge(id = "b1", type = BadgeType.FIRST_STEP, isNew = true),
            testBadge(id = "b2", type = BadgeType.GOAL_1, isNew = true)
        ))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.newBadges.value.size)

        viewModel.markAllBadgesAsSeen()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.newBadges.value.isEmpty())
    }

    @Test
    fun `startChallenge adds challenge and reloads`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.activeChallenges.value.isEmpty())

        viewModel.startChallenge(ChallengeType.DAILY_CHECK_IN)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.activeChallenges.value.size)
        assertEquals(ChallengeType.DAILY_CHECK_IN, viewModel.activeChallenges.value.first().type)
    }

    @Test
    fun `updateChallengeProgress updates progress`() = runTest(testDispatcher) {
        val target = ChallengeTargets.getTargetForType(ChallengeType.WEEKLY_GOALS)
        fakeRepository.setChallenges(listOf(
            testChallenge(id = "c1", type = ChallengeType.WEEKLY_GOALS, currentProgress = 0, targetProgress = target, isCompleted = false)
        ))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateChallengeProgress("c1", 1)
        testDispatcher.scheduler.advanceUntilIdle()

        val active = viewModel.activeChallenges.value
        assertEquals(1, active.size)
        assertEquals(1, active.first().currentProgress)
    }

    @Test
    fun `updateChallengeProgress completes challenge when target reached`() = runTest(testDispatcher) {
        val target = ChallengeTargets.getTargetForType(ChallengeType.DAILY_CHECK_IN)
        fakeRepository.setChallenges(listOf(
            testChallenge(id = "c1", type = ChallengeType.DAILY_CHECK_IN, currentProgress = 0, targetProgress = target, isCompleted = false)
        ))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateChallengeProgress("c1", target)
        testDispatcher.scheduler.advanceUntilIdle()

        // Challenge should now be completed
        assertEquals(1, viewModel.completedChallenges.value.size)
        assertTrue(viewModel.activeChallenges.value.isEmpty())
    }

    @Test
    fun `checkDailyStreak updates streak and reloads progress`() = runTest(testDispatcher) {
        fakeRepository.streakResult = Pair(5, 10)
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.checkDailyStreak()
        testDispatcher.scheduler.advanceUntilIdle()

        // Progress should be reloaded
        assertNotNull(viewModel.userProgress.value)
    }

    @Test
    fun `getAllBadgeTypesWithStatus returns all badge types`() = runTest(testDispatcher) {
        fakeRepository.setBadges(listOf(
            testBadge(id = "b1", type = BadgeType.FIRST_STEP)
        ))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val allTypes = viewModel.getAllBadgeTypesWithStatus()
        assertEquals(BadgeType.entries.size, allTypes.size)

        // FIRST_STEP should have a badge
        val firstStep = allTypes.find { it.first == BadgeType.FIRST_STEP }
        assertNotNull(firstStep)
        assertNotNull(firstStep.second)

        // Other types should have null
        val goal5 = allTypes.find { it.first == BadgeType.GOAL_5 }
        assertNotNull(goal5)
        assertNull(goal5.second)
    }

    @Test
    fun `refresh reloads all data`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        fakeRepository.userProgress = testUserProgress(totalXp = 1234)
        viewModel.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1234, viewModel.userProgress.value?.totalXp)
    }

    @Test
    fun `gamificationEvents emits ChallengeStarted on startChallenge`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.gamificationEvents.test {
            viewModel.startChallenge(ChallengeType.DAILY_JOURNAL)
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is GamificationEvent.ChallengeStarted)
            assertEquals(ChallengeType.DAILY_JOURNAL, (event as GamificationEvent.ChallengeStarted).challenge.type)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `gamificationEvents emits StreakUpdated when xp awarded`() = runTest(testDispatcher) {
        fakeRepository.streakResult = Pair(7, 15)
        viewModel = createViewModel()

        viewModel.gamificationEvents.test {
            // init calls checkDailyStreak which should emit StreakUpdated if xp > 0
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is GamificationEvent.StreakUpdated)
            assertEquals(7, (event as GamificationEvent.StreakUpdated).newStreak)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `gamificationEvents emits ChallengeCompleted when challenge finishes`() = runTest(testDispatcher) {
        val target = ChallengeTargets.getTargetForType(ChallengeType.DAILY_CHECK_IN)
        fakeRepository.setChallenges(listOf(
            testChallenge(id = "c1", type = ChallengeType.DAILY_CHECK_IN, currentProgress = 0, targetProgress = target, isCompleted = false)
        ))
        fakeRepository.streakResult = Pair(1, 0) // no xp to avoid StreakUpdated event
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.gamificationEvents.test {
            viewModel.updateChallengeProgress("c1", target)
            testDispatcher.scheduler.advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is GamificationEvent.ChallengeCompleted)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
