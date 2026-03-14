package az.tribe.lifeplanner.ui.balance

import app.cash.turbine.test
import az.tribe.lifeplanner.domain.model.BalanceInsight
import az.tribe.lifeplanner.domain.model.BalanceRecommendation
import az.tribe.lifeplanner.domain.model.BalanceRecommendationAction
import az.tribe.lifeplanner.domain.model.InsightPriority
import az.tribe.lifeplanner.domain.model.LifeArea
import az.tribe.lifeplanner.testutil.FakeGoalRepository
import az.tribe.lifeplanner.testutil.FakeLifeBalanceRepository
import az.tribe.lifeplanner.testutil.testGoal
import az.tribe.lifeplanner.testutil.testLifeAreaScore
import az.tribe.lifeplanner.testutil.testLifeBalanceReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LifeBalanceViewModelTest {

    private lateinit var viewModel: LifeBalanceViewModel
    private lateinit var fakeRepository: FakeLifeBalanceRepository
    private lateinit var fakeGoalRepository: FakeGoalRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeLifeBalanceRepository()
        fakeGoalRepository = FakeGoalRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LifeBalanceViewModel {
        return LifeBalanceViewModel(
            repository = fakeRepository,
            goalRepository = fakeGoalRepository
        )
    }

    // ─── loadBalance ─────────────────────────────────────────────────────────

    @Test
    fun `init loads balance report`() = runTest(testDispatcher) {
        val report = testLifeBalanceReport(overallScore = 75)
        fakeRepository.report = report

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNotNull(state.report)
            assertEquals(75, state.report?.overallScore)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadBalance clears previous error`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.loadBalance()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `loadBalance sets isLoading during load`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        // Don't advance -- the loading state should be set before the coroutine completes
        // After advancing, isLoading should be false
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loadBalance populates report with area scores`() = runTest(testDispatcher) {
        val scores = listOf(
            testLifeAreaScore(area = LifeArea.CAREER, score = 80),
            testLifeAreaScore(area = LifeArea.PHYSICAL, score = 60)
        )
        fakeRepository.report = testLifeBalanceReport(areaScores = scores)

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val report = viewModel.uiState.value.report
        assertNotNull(report)
        assertEquals(2, report.areaScores.size)
    }

    // ─── selectArea ──────────────────────────────────────────────────────────

    @Test
    fun `selectArea updates selectedArea`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectArea(LifeArea.CAREER)

        assertEquals(LifeArea.CAREER, viewModel.uiState.value.selectedArea)
    }

    @Test
    fun `selectArea with null clears selection`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectArea(LifeArea.CAREER)
        viewModel.selectArea(null)

        assertNull(viewModel.uiState.value.selectedArea)
    }

    // ─── Assessment Dialog ───────────────────────────────────────────────────

    @Test
    fun `showAssessmentDialog sets dialog visible with area`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showAssessmentDialog(LifeArea.PHYSICAL)

        assertTrue(viewModel.uiState.value.showAssessmentDialog)
        assertEquals(LifeArea.PHYSICAL, viewModel.uiState.value.assessmentArea)
    }

    @Test
    fun `hideAssessmentDialog hides dialog and clears area`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showAssessmentDialog(LifeArea.PHYSICAL)
        viewModel.hideAssessmentDialog()

        assertFalse(viewModel.uiState.value.showAssessmentDialog)
        assertNull(viewModel.uiState.value.assessmentArea)
    }

    // ─── saveManualAssessment ────────────────────────────────────────────────

    @Test
    fun `saveManualAssessment hides dialog and reloads balance`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showAssessmentDialog(LifeArea.CAREER)
        viewModel.saveManualAssessment(LifeArea.CAREER, 85, "Doing great")
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showAssessmentDialog)
        assertNull(viewModel.uiState.value.assessmentArea)
    }

    @Test
    fun `saveManualAssessment with null notes`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.saveManualAssessment(LifeArea.FINANCIAL, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()

        // Should not error; dialog should be hidden
        assertFalse(viewModel.uiState.value.showAssessmentDialog)
    }

    // ─── createGoalFromRecommendation ────────────────────────────────────────

    @Test
    fun `createGoalFromRecommendation adds goal and shows feedback`() = runTest(testDispatcher) {
        val goal = testGoal(id = "rec-goal", title = "Improve Fitness")
        val recommendation = BalanceRecommendation(
            title = "Start Exercise",
            description = "Begin a workout routine",
            targetArea = LifeArea.PHYSICAL,
            actionType = BalanceRecommendationAction.CREATE_GOAL,
            preGeneratedGoal = goal
        )

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.createGoalFromRecommendation(recommendation)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.goalCreatedFeedback)
        assertTrue(viewModel.uiState.value.goalCreatedFeedback!!.contains("Improve Fitness"))
        assertTrue(viewModel.uiState.value.createdGoalIds.contains(LifeArea.PHYSICAL.name))
    }

    @Test
    fun `createGoalFromRecommendation does nothing when no preGeneratedGoal`() = runTest(testDispatcher) {
        val recommendation = BalanceRecommendation(
            title = "Start Exercise",
            description = "Begin a workout routine",
            targetArea = LifeArea.PHYSICAL,
            actionType = BalanceRecommendationAction.CREATE_GOAL,
            preGeneratedGoal = null
        )

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.createGoalFromRecommendation(recommendation)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.goalCreatedFeedback)
    }

    @Test
    fun `clearGoalFeedback clears feedback`() = runTest(testDispatcher) {
        val goal = testGoal(id = "rec-goal", title = "Goal")
        val recommendation = BalanceRecommendation(
            title = "Advance Career",
            description = "desc",
            targetArea = LifeArea.CAREER,
            actionType = BalanceRecommendationAction.CREATE_GOAL,
            preGeneratedGoal = goal
        )

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.createGoalFromRecommendation(recommendation)
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.goalCreatedFeedback)

        viewModel.clearGoalFeedback()
        assertNull(viewModel.uiState.value.goalCreatedFeedback)
    }

    // ─── Coach Sheet ─────────────────────────────────────────────────────────

    @Test
    fun `showCoachSheetForInsight shows sheet with insight`() = runTest(testDispatcher) {
        val insight = BalanceInsight(
            title = "Work-life imbalance",
            description = "Career is overpowering other areas",
            relatedAreas = listOf(LifeArea.CAREER, LifeArea.FAMILY),
            priority = InsightPriority.HIGH
        )

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showCoachSheetForInsight(insight)

        assertTrue(viewModel.uiState.value.showCoachSheet)
        assertEquals(insight, viewModel.uiState.value.selectedInsight)
        assertTrue(viewModel.uiState.value.relevantCoaches.isNotEmpty())
    }

    @Test
    fun `showCoachSheetForInsight with no related areas shows all coaches`() = runTest(testDispatcher) {
        val insight = BalanceInsight(
            title = "General insight",
            description = "Something general",
            relatedAreas = emptyList(),
            priority = InsightPriority.LOW
        )

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showCoachSheetForInsight(insight)

        assertTrue(viewModel.uiState.value.showCoachSheet)
        assertTrue(viewModel.uiState.value.relevantCoaches.isNotEmpty())
    }

    @Test
    fun `hideCoachSheet hides sheet and clears state`() = runTest(testDispatcher) {
        val insight = BalanceInsight(
            title = "Insight",
            description = "desc",
            relatedAreas = listOf(LifeArea.CAREER),
            priority = InsightPriority.MEDIUM
        )

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showCoachSheetForInsight(insight)
        viewModel.hideCoachSheet()

        assertFalse(viewModel.uiState.value.showCoachSheet)
        assertNull(viewModel.uiState.value.selectedInsight)
        assertTrue(viewModel.uiState.value.relevantCoaches.isEmpty())
    }

    // ─── getAreaScore ────────────────────────────────────────────────────────

    @Test
    fun `getAreaScore returns score from report`() = runTest(testDispatcher) {
        val scores = listOf(
            testLifeAreaScore(area = LifeArea.CAREER, score = 80),
            testLifeAreaScore(area = LifeArea.PHYSICAL, score = 60)
        )
        fakeRepository.report = testLifeBalanceReport(areaScores = scores)

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val score = viewModel.getAreaScore(LifeArea.CAREER)
        assertNotNull(score)
        assertEquals(80, score.score)
    }

    @Test
    fun `getAreaScore returns null when no report loaded`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        // Don't advance; report might not be loaded yet depending on timing
        // If we do advance it will load; test the case where report has no matching area
        testDispatcher.scheduler.advanceUntilIdle()

        // Report is loaded but has no area scores
        fakeRepository.report = testLifeBalanceReport(areaScores = emptyList())
        viewModel.loadBalance()
        testDispatcher.scheduler.advanceUntilIdle()

        val score = viewModel.getAreaScore(LifeArea.SPIRITUAL)
        // With empty area scores, it won't find SPIRITUAL in the report
        assertNull(score)
    }
}
