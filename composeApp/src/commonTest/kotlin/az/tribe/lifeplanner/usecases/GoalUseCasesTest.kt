package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.data.model.DataError
import az.tribe.lifeplanner.data.model.GoalTypeQuestions
import az.tribe.lifeplanner.data.model.Question
import az.tribe.lifeplanner.data.model.QuestionAnswer
import az.tribe.lifeplanner.data.model.Result
import az.tribe.lifeplanner.data.model.UserQuestionnaireAnswers
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.GoalAnalytics
import az.tribe.lifeplanner.domain.repository.GeminiRepository
import az.tribe.lifeplanner.testutil.FakeGoalHistoryRepository
import az.tribe.lifeplanner.testutil.FakeGoalRepository
import az.tribe.lifeplanner.testutil.testGoal
import az.tribe.lifeplanner.testutil.testGoalAnalytics
import az.tribe.lifeplanner.testutil.testGoalChange
import az.tribe.lifeplanner.testutil.testMilestone
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GoalUseCasesTest {

    private lateinit var goalRepo: FakeGoalRepository
    private lateinit var historyRepo: FakeGoalHistoryRepository

    @BeforeTest
    fun setUp() {
        goalRepo = FakeGoalRepository()
        historyRepo = FakeGoalHistoryRepository()
    }

    // ── CreateGoalUseCase ────────────────────────────────────────────

    @Test
    fun `CreateGoal inserts a single goal`() = runTest {
        val useCase = CreateGoalUseCase(goalRepo)
        val goal = testGoal()

        useCase(goal)

        val all = goalRepo.getAllGoals()
        assertEquals(1, all.size)
        assertEquals(goal, all.first())
    }

    @Test
    fun `CreateGoal inserts a batch of goals`() = runTest {
        val useCase = CreateGoalUseCase(goalRepo)
        val goals = listOf(
            testGoal(id = "g1", title = "First"),
            testGoal(id = "g2", title = "Second"),
            testGoal(id = "g3", title = "Third")
        )

        useCase(goals)

        assertEquals(3, goalRepo.getAllGoals().size)
    }

    @Test
    fun `CreateGoal batch preserves all goal data`() = runTest {
        val useCase = CreateGoalUseCase(goalRepo)
        val goals = listOf(
            testGoal(id = "g1", category = GoalCategory.CAREER),
            testGoal(id = "g2", category = GoalCategory.PHYSICAL)
        )

        useCase(goals)

        val stored = goalRepo.getAllGoals()
        assertEquals(GoalCategory.CAREER, stored[0].category)
        assertEquals(GoalCategory.PHYSICAL, stored[1].category)
    }

    // ── UpdateGoalUseCase ────────────────────────────────────────────

    @Test
    fun `UpdateGoal updates an existing goal`() = runTest {
        val useCase = UpdateGoalUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal()))

        val updated = testGoal(title = "Updated Title")
        useCase(updated)

        assertEquals("Updated Title", goalRepo.getAllGoals().first().title)
    }

    @Test
    fun `UpdateGoal does nothing when goal not found`() = runTest {
        val useCase = UpdateGoalUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "existing")))

        useCase(testGoal(id = "nonexistent", title = "Nope"))

        assertEquals(1, goalRepo.getAllGoals().size)
        assertEquals("Test Goal", goalRepo.getAllGoals().first().title)
    }

    // ── DeleteGoalUseCase ────────────────────────────────────────────

    @Test
    fun `DeleteGoal removes goal by id`() = runTest {
        val useCase = DeleteGoalUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1"), testGoal(id = "g2")))

        useCase("g1")

        assertEquals(1, goalRepo.getAllGoals().size)
        assertEquals("g2", goalRepo.getAllGoals().first().id)
    }

    @Test
    fun `DeleteGoal removes all goals when called without id`() = runTest {
        val useCase = DeleteGoalUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1"), testGoal(id = "g2")))

        useCase()

        assertTrue(goalRepo.getAllGoals().isEmpty())
    }

    @Test
    fun `DeleteGoal by id does nothing for nonexistent id`() = runTest {
        val useCase = DeleteGoalUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1")))

        useCase("nonexistent")

        assertEquals(1, goalRepo.getAllGoals().size)
    }

    // ── GetAllGoalsUseCase ───────────────────────────────────────────

    @Test
    fun `GetAllGoals returns empty list when no goals exist`() = runTest {
        val useCase = GetAllGoalsUseCase(goalRepo)

        val result = useCase()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `GetAllGoals returns all stored goals`() = runTest {
        val useCase = GetAllGoalsUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1"), testGoal(id = "g2")))

        val result = useCase()

        assertEquals(2, result.size)
    }

    // ── GetGoalByIdUseCase ───────────────────────────────────────────

    @Test
    fun `GetGoalById returns goal when found`() = runTest {
        val useCase = GetGoalByIdUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1", title = "Target")))

        val result = useCase("g1")

        assertNotNull(result)
        assertEquals("Target", result.title)
    }

    @Test
    fun `GetGoalById returns null when not found`() = runTest {
        val useCase = GetGoalByIdUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1")))

        val result = useCase("nonexistent")

        assertNull(result)
    }

    @Test
    fun `GetGoalById returns null from empty repository`() = runTest {
        val useCase = GetGoalByIdUseCase(goalRepo)

        val result = useCase("any-id")

        assertNull(result)
    }

    // ── SearchGoalsUseCase ───────────────────────────────────────────

    @Test
    fun `SearchGoals returns matching goals`() = runTest {
        val useCase = SearchGoalsUseCase(goalRepo)
        goalRepo.setGoals(listOf(
            testGoal(id = "g1", title = "Learn Kotlin"),
            testGoal(id = "g2", title = "Learn Swift"),
            testGoal(id = "g3", title = "Run marathon")
        ))

        val result = useCase("Learn")

        assertEquals(2, result.size)
    }

    @Test
    fun `SearchGoals is case insensitive`() = runTest {
        val useCase = SearchGoalsUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1", title = "KOTLIN")))

        val result = useCase("kotlin")

        assertEquals(1, result.size)
    }

    @Test
    fun `SearchGoals returns empty for no match`() = runTest {
        val useCase = SearchGoalsUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1", title = "Learn Kotlin")))

        val result = useCase("Swift")

        assertTrue(result.isEmpty())
    }

    // ── FilterGoalsByStatusUseCase ───────────────────────────────────

    @Test
    fun `FilterGoalsByStatus returns only matching status`() = runTest {
        val useCase = FilterGoalsByStatusUseCase(goalRepo)
        goalRepo.setGoals(listOf(
            testGoal(id = "g1", status = GoalStatus.IN_PROGRESS),
            testGoal(id = "g2", status = GoalStatus.COMPLETED),
            testGoal(id = "g3", status = GoalStatus.IN_PROGRESS)
        ))

        val result = useCase(GoalStatus.IN_PROGRESS)

        assertEquals(2, result.size)
        assertTrue(result.all { it.status == GoalStatus.IN_PROGRESS })
    }

    @Test
    fun `FilterGoalsByStatus returns empty when no goals match`() = runTest {
        val useCase = FilterGoalsByStatusUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(status = GoalStatus.IN_PROGRESS)))

        val result = useCase(GoalStatus.COMPLETED)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `FilterGoalsByStatus returns empty for empty repository`() = runTest {
        val useCase = FilterGoalsByStatusUseCase(goalRepo)

        val result = useCase(GoalStatus.IN_PROGRESS)

        assertTrue(result.isEmpty())
    }

    // ── GetActiveGoalsUseCase ────────────────────────────────────────

    @Test
    fun `GetActiveGoals returns only in-progress goals`() = runTest {
        val useCase = GetActiveGoalsUseCase(goalRepo)
        goalRepo.setGoals(listOf(
            testGoal(id = "g1", status = GoalStatus.IN_PROGRESS),
            testGoal(id = "g2", status = GoalStatus.COMPLETED),
            testGoal(id = "g3", status = GoalStatus.NOT_STARTED)
        ))

        val result = useCase()

        assertEquals(1, result.size)
        assertEquals("g1", result.first().id)
    }

    @Test
    fun `GetActiveGoals returns empty when none active`() = runTest {
        val useCase = GetActiveGoalsUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(status = GoalStatus.COMPLETED)))

        val result = useCase()

        assertTrue(result.isEmpty())
    }

    // ── GetCompletedGoalsUseCase ─────────────────────────────────────

    @Test
    fun `GetCompletedGoals returns only completed goals`() = runTest {
        val useCase = GetCompletedGoalsUseCase(goalRepo)
        goalRepo.setGoals(listOf(
            testGoal(id = "g1", status = GoalStatus.COMPLETED),
            testGoal(id = "g2", status = GoalStatus.IN_PROGRESS),
            testGoal(id = "g3", status = GoalStatus.COMPLETED)
        ))

        val result = useCase()

        assertEquals(2, result.size)
        assertTrue(result.all { it.status == GoalStatus.COMPLETED })
    }

    @Test
    fun `GetCompletedGoals returns empty when none completed`() = runTest {
        val useCase = GetCompletedGoalsUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(status = GoalStatus.IN_PROGRESS)))

        val result = useCase()

        assertTrue(result.isEmpty())
    }

    // ── GetGoalsByCategoryUseCase ────────────────────────────────────

    @Test
    fun `GetGoalsByCategory filters by category`() = runTest {
        val useCase = GetGoalsByCategoryUseCase(goalRepo)
        goalRepo.setGoals(listOf(
            testGoal(id = "g1", category = GoalCategory.CAREER),
            testGoal(id = "g2", category = GoalCategory.PHYSICAL),
            testGoal(id = "g3", category = GoalCategory.CAREER)
        ))

        val result = useCase(GoalCategory.CAREER)

        assertEquals(2, result.size)
        assertTrue(result.all { it.category == GoalCategory.CAREER })
    }

    @Test
    fun `GetGoalsByCategory returns empty when no match`() = runTest {
        val useCase = GetGoalsByCategoryUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(category = GoalCategory.CAREER)))

        val result = useCase(GoalCategory.SPIRITUAL)

        assertTrue(result.isEmpty())
    }

    // ── GetGoalsByTimelineUseCase ────────────────────────────────────

    @Test
    fun `GetGoalsByTimeline delegates to repository`() = runTest {
        val useCase = GetGoalsByTimelineUseCase(goalRepo)
        goalRepo.setGoals(listOf(
            testGoal(id = "g1", timeline = GoalTimeline.SHORT_TERM),
            testGoal(id = "g2", timeline = GoalTimeline.LONG_TERM),
            testGoal(id = "g3", timeline = GoalTimeline.SHORT_TERM)
        ))

        val result = useCase(GoalTimeline.SHORT_TERM)

        assertEquals(2, result.size)
        assertTrue(result.all { it.timeline == GoalTimeline.SHORT_TERM })
    }

    @Test
    fun `GetGoalsByTimeline returns empty for no match`() = runTest {
        val useCase = GetGoalsByTimelineUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(timeline = GoalTimeline.SHORT_TERM)))

        val result = useCase(GoalTimeline.LONG_TERM)

        assertTrue(result.isEmpty())
    }

    // ── GetUpcomingDeadlinesUseCase ──────────────────────────────────

    @Test
    fun `GetUpcomingDeadlines uses default days parameter`() = runTest {
        val useCase = GetUpcomingDeadlinesUseCase(goalRepo)
        goalRepo.setGoals(listOf(
            testGoal(id = "g1"),
            testGoal(id = "g2"),
            testGoal(id = "g3")
        ))

        val result = useCase()

        // FakeGoalRepository.getUpcomingDeadlines takes first N goals
        assertEquals(3, result.size)
    }

    @Test
    fun `GetUpcomingDeadlines with custom days`() = runTest {
        val useCase = GetUpcomingDeadlinesUseCase(goalRepo)
        goalRepo.setGoals(listOf(
            testGoal(id = "g1"),
            testGoal(id = "g2"),
            testGoal(id = "g3")
        ))

        val result = useCase(days = 2)

        assertEquals(2, result.size)
    }

    @Test
    fun `GetUpcomingDeadlines returns empty from empty repository`() = runTest {
        val useCase = GetUpcomingDeadlinesUseCase(goalRepo)

        val result = useCase()

        assertTrue(result.isEmpty())
    }

    // ── GetGoalAnalyticsUseCase ──────────────────────────────────────

    @Test
    fun `GetGoalAnalytics delegates to repository`() = runTest {
        val useCase = GetGoalAnalyticsUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(), testGoal(id = "g2")))

        val analytics = useCase()

        assertEquals(2, analytics.totalGoals)
    }

    @Test
    fun `GetGoalAnalytics returns analytics for empty repository`() = runTest {
        val useCase = GetGoalAnalyticsUseCase(goalRepo)

        val analytics = useCase()

        assertEquals(0, analytics.totalGoals)
    }

    // ── GetGoalStatisticsUseCase ─────────────────────────────────────

    @Test
    fun `GetGoalStatistics calculates correct totals`() = runTest {
        val useCase = GetGoalStatisticsUseCase(goalRepo)
        goalRepo.setGoals(listOf(
            testGoal(id = "g1", status = GoalStatus.IN_PROGRESS),
            testGoal(id = "g2", status = GoalStatus.COMPLETED),
            testGoal(id = "g3", status = GoalStatus.IN_PROGRESS),
            testGoal(id = "g4", status = GoalStatus.COMPLETED)
        ))

        val stats = useCase()

        assertEquals(4, stats.totalGoals)
        assertEquals(2, stats.activeGoals)
        assertEquals(2, stats.completedGoals)
    }

    @Test
    fun `GetGoalStatistics calculates completion rate`() = runTest {
        val useCase = GetGoalStatisticsUseCase(goalRepo)
        goalRepo.setGoals(listOf(
            testGoal(id = "g1", status = GoalStatus.COMPLETED),
            testGoal(id = "g2", status = GoalStatus.IN_PROGRESS)
        ))

        val stats = useCase()

        assertEquals(0.5f, stats.completionRate, 0.001f)
    }

    @Test
    fun `GetGoalStatistics returns zero rate for empty goals`() = runTest {
        val useCase = GetGoalStatisticsUseCase(goalRepo)

        val stats = useCase()

        assertEquals(0, stats.totalGoals)
        assertEquals(0f, stats.completionRate)
    }

    @Test
    fun `GetGoalStatistics completion rate is 1 when all completed`() = runTest {
        val useCase = GetGoalStatisticsUseCase(goalRepo)
        goalRepo.setGoals(listOf(
            testGoal(id = "g1", status = GoalStatus.COMPLETED),
            testGoal(id = "g2", status = GoalStatus.COMPLETED)
        ))

        val stats = useCase()

        assertEquals(1.0f, stats.completionRate, 0.001f)
    }

    // ── GetGoalHistoryUseCase ────────────────────────────────────────

    @Test
    fun `GetGoalHistory returns changes for a goal`() = runTest {
        val useCase = GetGoalHistoryUseCase(historyRepo)
        historyRepo.setChanges(listOf(
            testGoalChange(id = "c1", goalId = "g1"),
            testGoalChange(id = "c2", goalId = "g1"),
            testGoalChange(id = "c3", goalId = "g2")
        ))

        val result = useCase("g1")

        assertEquals(2, result.size)
    }

    @Test
    fun `GetGoalHistory returns empty for goal with no history`() = runTest {
        val useCase = GetGoalHistoryUseCase(historyRepo)

        val result = useCase("nonexistent")

        assertTrue(result.isEmpty())
    }

    // ── LogGoalChangeUseCase ─────────────────────────────────────────

    @Test
    fun `LogGoalChange inserts a change record`() = runTest {
        val useCase = LogGoalChangeUseCase(historyRepo)

        useCase(goalId = "g1", field = "status", oldValue = "IN_PROGRESS", newValue = "COMPLETED")

        val history = historyRepo.getHistoryForGoal("g1")
        assertEquals(1, history.size)
        assertEquals("status", history.first().field)
        assertEquals("IN_PROGRESS", history.first().oldValue)
        assertEquals("COMPLETED", history.first().newValue)
    }

    @Test
    fun `LogGoalChange supports null old value`() = runTest {
        val useCase = LogGoalChangeUseCase(historyRepo)

        useCase(goalId = "g1", field = "notes", oldValue = null, newValue = "First note")

        val history = historyRepo.getHistoryForGoal("g1")
        assertNull(history.first().oldValue)
    }

    @Test
    fun `LogGoalChange generates unique ids`() = runTest {
        val useCase = LogGoalChangeUseCase(historyRepo)

        useCase(goalId = "g1", field = "title", oldValue = "Old", newValue = "New1")
        useCase(goalId = "g1", field = "title", oldValue = "New1", newValue = "New2")

        val history = historyRepo.getHistoryForGoal("g1")
        assertEquals(2, history.size)
        assertNotEquals(history[0].id, history[1].id)
    }

    // ── AddMilestoneUseCase ──────────────────────────────────────────

    @Test
    fun `AddMilestone adds milestone to goal`() = runTest {
        val useCase = AddMilestoneUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1")))
        val milestone = testMilestone(id = "m1", title = "Step 1")

        val result = useCase("g1", milestone)

        assertTrue(result.isSuccess)
        assertEquals(1, goalRepo.getAllGoals().first().milestones.size)
        assertEquals("Step 1", goalRepo.getAllGoals().first().milestones.first().title)
    }

    @Test
    fun `AddMilestone returns success result`() = runTest {
        val useCase = AddMilestoneUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1")))

        val result = useCase("g1", testMilestone())

        assertTrue(result.isSuccess)
    }

    @Test
    fun `AddMilestone appends to existing milestones`() = runTest {
        val useCase = AddMilestoneUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1", milestones = listOf(testMilestone(id = "m1")))))

        useCase("g1", testMilestone(id = "m2", title = "Second"))

        assertEquals(2, goalRepo.getAllGoals().first().milestones.size)
    }

    // ── UpdateMilestoneUseCase ───────────────────────────────────────

    @Test
    fun `UpdateMilestone updates the milestone`() = runTest {
        val useCase = UpdateMilestoneUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1", milestones = listOf(testMilestone(id = "m1", title = "Old")))))

        val result = useCase(testMilestone(id = "m1", title = "New"))

        assertTrue(result.isSuccess)
        assertEquals("New", goalRepo.getAllGoals().first().milestones.first().title)
    }

    @Test
    fun `UpdateMilestone returns success`() = runTest {
        val useCase = UpdateMilestoneUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(milestones = listOf(testMilestone()))))

        val result = useCase(testMilestone(title = "Updated"))

        assertTrue(result.isSuccess)
    }

    // ── DeleteMilestoneUseCase ───────────────────────────────────────

    @Test
    fun `DeleteMilestone removes milestone from goal`() = runTest {
        val useCase = DeleteMilestoneUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1", milestones = listOf(
            testMilestone(id = "m1"),
            testMilestone(id = "m2")
        ))))

        val result = useCase("m1")

        assertTrue(result.isSuccess)
        assertEquals(1, goalRepo.getAllGoals().first().milestones.size)
        assertEquals("m2", goalRepo.getAllGoals().first().milestones.first().id)
    }

    @Test
    fun `DeleteMilestone returns success`() = runTest {
        val useCase = DeleteMilestoneUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(milestones = listOf(testMilestone()))))

        val result = useCase("milestone-1")

        assertTrue(result.isSuccess)
    }

    // ── ToggleMilestoneCompletionUseCase ─────────────────────────────

    @Test
    fun `ToggleMilestoneCompletion marks milestone as completed`() = runTest {
        val useCase = ToggleMilestoneCompletionUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(milestones = listOf(testMilestone(id = "m1", isCompleted = false)))))

        val result = useCase("m1", true)

        assertTrue(result.isSuccess)
        assertTrue(goalRepo.getAllGoals().first().milestones.first().isCompleted)
    }

    @Test
    fun `ToggleMilestoneCompletion marks milestone as not completed`() = runTest {
        val useCase = ToggleMilestoneCompletionUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(milestones = listOf(testMilestone(id = "m1", isCompleted = true)))))

        val result = useCase("m1", false)

        assertTrue(result.isSuccess)
        assertFalse(goalRepo.getAllGoals().first().milestones.first().isCompleted)
    }

    @Test
    fun `ToggleMilestoneCompletion returns success`() = runTest {
        val useCase = ToggleMilestoneCompletionUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(milestones = listOf(testMilestone()))))

        val result = useCase("milestone-1", true)

        assertTrue(result.isSuccess)
    }

    // ── ArchiveGoalUseCase ───────────────────────────────────────────

    @Test
    fun `ArchiveGoal sets isArchived to true`() = runTest {
        val useCase = ArchiveGoalUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1", isArchived = false)))

        val result = useCase("g1")

        assertTrue(result.isSuccess)
        assertTrue(goalRepo.getAllGoals().first().isArchived)
    }

    @Test
    fun `ArchiveGoal returns success`() = runTest {
        val useCase = ArchiveGoalUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1")))

        val result = useCase("g1")

        assertTrue(result.isSuccess)
    }

    // ── UnarchiveGoalUseCase ─────────────────────────────────────────

    @Test
    fun `UnarchiveGoal sets isArchived to false`() = runTest {
        val useCase = UnarchiveGoalUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1", isArchived = true)))

        val result = useCase("g1")

        assertTrue(result.isSuccess)
        assertFalse(goalRepo.getAllGoals().first().isArchived)
    }

    @Test
    fun `UnarchiveGoal returns success`() = runTest {
        val useCase = UnarchiveGoalUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1", isArchived = true)))

        val result = useCase("g1")

        assertTrue(result.isSuccess)
    }

    // ── UpdateGoalProgressUseCase ────────────────────────────────────

    @Test
    fun `UpdateGoalProgress updates the progress value`() = runTest {
        val useCase = UpdateGoalProgressUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1", progress = 0)))

        useCase("g1", 75)

        assertEquals(75L, goalRepo.getAllGoals().first().progress)
    }

    @Test
    fun `UpdateGoalProgress sets to zero`() = runTest {
        val useCase = UpdateGoalProgressUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1", progress = 50)))

        useCase("g1", 0)

        assertEquals(0L, goalRepo.getAllGoals().first().progress)
    }

    @Test
    fun `UpdateGoalProgress sets to 100`() = runTest {
        val useCase = UpdateGoalProgressUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1", progress = 50)))

        useCase("g1", 100)

        assertEquals(100L, goalRepo.getAllGoals().first().progress)
    }

    // ── UpdateGoalStatusUseCase ──────────────────────────────────────

    @Test
    fun `UpdateGoalStatus updates status of existing goal`() = runTest {
        val useCase = UpdateGoalStatusUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1", status = GoalStatus.IN_PROGRESS)))

        val result = useCase("g1", GoalStatus.COMPLETED)

        assertTrue(result.isSuccess)
        assertEquals(GoalStatus.COMPLETED, goalRepo.getAllGoals().first().status)
    }

    @Test
    fun `UpdateGoalStatus returns failure when goal not found`() = runTest {
        val useCase = UpdateGoalStatusUseCase(goalRepo)

        val result = useCase("nonexistent", GoalStatus.COMPLETED)

        assertTrue(result.isFailure)
    }

    @Test
    fun `UpdateGoalStatus failure message is Goal not found`() = runTest {
        val useCase = UpdateGoalStatusUseCase(goalRepo)

        val result = useCase("nonexistent", GoalStatus.COMPLETED)

        assertEquals("Goal not found", result.exceptionOrNull()?.message)
    }

    // ── UpdateGoalNotesUseCase ───────────────────────────────────────

    @Test
    fun `UpdateGoalNotes updates the notes`() = runTest {
        val useCase = UpdateGoalNotesUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1", notes = "")))

        val result = useCase("g1", "New notes here")

        assertTrue(result.isSuccess)
        assertEquals("New notes here", goalRepo.getAllGoals().first().notes)
    }

    @Test
    fun `UpdateGoalNotes returns success`() = runTest {
        val useCase = UpdateGoalNotesUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1")))

        val result = useCase("g1", "notes")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `UpdateGoalNotes can set empty notes`() = runTest {
        val useCase = UpdateGoalNotesUseCase(goalRepo)
        goalRepo.setGoals(listOf(testGoal(id = "g1", notes = "existing")))

        useCase("g1", "")

        assertEquals("", goalRepo.getAllGoals().first().notes)
    }

    // ── CalculateGoalCompletionRateUseCase ───────────────────────────

    @Test
    fun `CalculateGoalCompletionRate returns 0 for empty milestones`() {
        val useCase = CalculateGoalCompletionRateUseCase()

        val rate = useCase(emptyList())

        assertEquals(0f, rate)
    }

    @Test
    fun `CalculateGoalCompletionRate returns 100 for all completed`() {
        val useCase = CalculateGoalCompletionRateUseCase()
        val milestones = listOf(
            testMilestone(id = "m1", isCompleted = true),
            testMilestone(id = "m2", isCompleted = true),
            testMilestone(id = "m3", isCompleted = true)
        )

        val rate = useCase(milestones)

        assertEquals(100f, rate, 0.001f)
    }

    @Test
    fun `CalculateGoalCompletionRate returns 50 for half completed`() {
        val useCase = CalculateGoalCompletionRateUseCase()
        val milestones = listOf(
            testMilestone(id = "m1", isCompleted = true),
            testMilestone(id = "m2", isCompleted = false)
        )

        val rate = useCase(milestones)

        assertEquals(50f, rate, 0.001f)
    }

    @Test
    fun `CalculateGoalCompletionRate returns 0 for none completed`() {
        val useCase = CalculateGoalCompletionRateUseCase()
        val milestones = listOf(
            testMilestone(id = "m1", isCompleted = false),
            testMilestone(id = "m2", isCompleted = false)
        )

        val rate = useCase(milestones)

        assertEquals(0f, rate, 0.001f)
    }

    @Test
    fun `CalculateGoalCompletionRate returns correct rate for 1 of 3`() {
        val useCase = CalculateGoalCompletionRateUseCase()
        val milestones = listOf(
            testMilestone(id = "m1", isCompleted = true),
            testMilestone(id = "m2", isCompleted = false),
            testMilestone(id = "m3", isCompleted = false)
        )

        val rate = useCase(milestones)

        assertEquals(33.333f, rate, 0.1f)
    }

    @Test
    fun `CalculateGoalCompletionRate returns correct rate for 2 of 3`() {
        val useCase = CalculateGoalCompletionRateUseCase()
        val milestones = listOf(
            testMilestone(id = "m1", isCompleted = true),
            testMilestone(id = "m2", isCompleted = true),
            testMilestone(id = "m3", isCompleted = false)
        )

        val rate = useCase(milestones)

        assertEquals(66.666f, rate, 0.1f)
    }

    @Test
    fun `CalculateGoalCompletionRate handles single milestone completed`() {
        val useCase = CalculateGoalCompletionRateUseCase()

        val rate = useCase(listOf(testMilestone(isCompleted = true)))

        assertEquals(100f, rate, 0.001f)
    }

    @Test
    fun `CalculateGoalCompletionRate handles single milestone not completed`() {
        val useCase = CalculateGoalCompletionRateUseCase()

        val rate = useCase(listOf(testMilestone(isCompleted = false)))

        assertEquals(0f, rate, 0.001f)
    }

    // ── GenerateAiQuestionnaireUseCase ───────────────────────────────

    @Test
    fun `GenerateAiQuestionnaire delegates to GeminiRepository`() = runTest {
        val expectedQuestions = listOf(
            GoalTypeQuestions("Career", listOf(Question("What role?", listOf("Dev", "PM"))))
        )
        val fakeGemini = FakeGeminiRepository(
            questionnaireResult = Result.Success(expectedQuestions)
        )
        val useCase = GenerateAiQuestionnaireUseCase(fakeGemini)

        val result = useCase("I want to advance my career")

        assertTrue(result is Result.Success)
        assertEquals(expectedQuestions, (result as Result.Success).data)
    }

    @Test
    fun `GenerateAiQuestionnaire returns error on failure`() = runTest {
        val fakeGemini = FakeGeminiRepository(
            questionnaireResult = Result.Error(DataError.Remote.SERVER_ERROR)
        )
        val useCase = GenerateAiQuestionnaireUseCase(fakeGemini)

        val result = useCase("anything")

        assertTrue(result is Result.Error)
    }

    // ── GenerateAiGoalsUseCase ───────────────────────────────────────

    @Test
    fun `GenerateAiGoals delegates to GeminiRepository`() = runTest {
        val expectedGoals = listOf(testGoal(id = "ai-1", title = "AI Generated Goal"))
        val fakeGemini = FakeGeminiRepository(
            goalsResult = Result.Success(expectedGoals)
        )
        val useCase = GenerateAiGoalsUseCase(fakeGemini)
        val answers = UserQuestionnaireAnswers(listOf(QuestionAnswer("Q1", "A1")))

        val result = useCase("prompt", answers)

        assertTrue(result is Result.Success)
        assertEquals(expectedGoals, (result as Result.Success).data)
    }

    @Test
    fun `GenerateAiGoals returns error on failure`() = runTest {
        val fakeGemini = FakeGeminiRepository(
            goalsResult = Result.Error(DataError.Remote.NO_INTERNET)
        )
        val useCase = GenerateAiGoalsUseCase(fakeGemini)
        val answers = UserQuestionnaireAnswers(listOf(QuestionAnswer("Q1", "A1")))

        val result = useCase("prompt", answers)

        assertTrue(result is Result.Error)
    }

    @Test
    fun `GenerateAiGoals passes original prompt and answers through`() = runTest {
        val fakeGemini = FakeGeminiRepository()
        val useCase = GenerateAiGoalsUseCase(fakeGemini)
        val answers = UserQuestionnaireAnswers(listOf(
            QuestionAnswer("What role?", "Developer"),
            QuestionAnswer("Timeline?", "6 months")
        ))

        useCase("advance career", answers)

        assertEquals("advance career", fakeGemini.lastOriginalPrompt)
        assertEquals(answers, fakeGemini.lastUserAnswers)
    }
}

// ── Minimal FakeGeminiRepository for AI use case tests ───────────────────

private class FakeGeminiRepository(
    private val questionnaireResult: Result<List<GoalTypeQuestions>, DataError.Remote> =
        Result.Success(emptyList()),
    private val goalsResult: Result<List<Goal>, DataError.Remote> =
        Result.Success(emptyList()),
    private val directGoalsResult: Result<List<Goal>, DataError.Remote> =
        Result.Success(emptyList())
) : GeminiRepository {

    var lastOriginalPrompt: String? = null
    var lastUserAnswers: UserQuestionnaireAnswers? = null

    override suspend fun generateQuestionnaire(userPrompt: String): Result<List<GoalTypeQuestions>, DataError.Remote> {
        return questionnaireResult
    }

    override suspend fun generatePersonalizedGoals(
        originalPrompt: String,
        userAnswers: UserQuestionnaireAnswers
    ): Result<List<Goal>, DataError.Remote> {
        lastOriginalPrompt = originalPrompt
        lastUserAnswers = userAnswers
        return goalsResult
    }

    override suspend fun generateGoalsDirect(prompt: String): Result<List<Goal>, DataError.Remote> {
        return directGoalsResult
    }
}
