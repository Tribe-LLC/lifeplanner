package az.tribe.lifeplanner.ui

import app.cash.turbine.test
import az.tribe.lifeplanner.data.model.DataError
import az.tribe.lifeplanner.data.model.GoalTypeQuestions
import az.tribe.lifeplanner.data.model.Question
import az.tribe.lifeplanner.data.model.Result
import az.tribe.lifeplanner.data.model.UserQuestionnaireAnswers
import az.tribe.lifeplanner.domain.enum.GoalFilter
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.repository.GeminiRepository
import az.tribe.lifeplanner.testutil.FakeGoalHistoryRepository
import az.tribe.lifeplanner.testutil.FakeGoalRepository
import az.tribe.lifeplanner.testutil.testGoal
import az.tribe.lifeplanner.testutil.testGoalAnalytics
import az.tribe.lifeplanner.testutil.testGoalChange
import az.tribe.lifeplanner.testutil.testMilestone
import az.tribe.lifeplanner.usecases.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GoalViewModelTest {

    private lateinit var viewModel: GoalViewModel
    private lateinit var fakeGoalRepository: FakeGoalRepository
    private lateinit var fakeGoalHistoryRepository: FakeGoalHistoryRepository
    private lateinit var fakeGeminiRepository: FakeGeminiRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeGoalRepository = FakeGoalRepository()
        fakeGoalHistoryRepository = FakeGoalHistoryRepository()
        fakeGeminiRepository = FakeGeminiRepository()

        viewModel = createViewModel()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): GoalViewModel {
        return GoalViewModel(
            goalRepository = fakeGoalRepository,
            getAllGoalsUseCase = GetAllGoalsUseCase(fakeGoalRepository),
            createGoalUseCase = CreateGoalUseCase(fakeGoalRepository),
            updateGoalUseCase = UpdateGoalUseCase(fakeGoalRepository),
            deleteGoalUseCase = DeleteGoalUseCase(fakeGoalRepository),
            getGoalByIdUseCase = GetGoalByIdUseCase(fakeGoalRepository),
            updateGoalProgressUseCase = UpdateGoalProgressUseCase(fakeGoalRepository),
            updateGoalStatusUseCase = UpdateGoalStatusUseCase(fakeGoalRepository),
            updateGoalNotesUseCase = UpdateGoalNotesUseCase(fakeGoalRepository),
            searchGoalsUseCase = SearchGoalsUseCase(fakeGoalRepository),
            getActiveGoalsUseCase = GetActiveGoalsUseCase(fakeGoalRepository),
            getCompletedGoalsUseCase = GetCompletedGoalsUseCase(fakeGoalRepository),
            addMilestoneUseCase = AddMilestoneUseCase(fakeGoalRepository),
            toggleMilestoneCompletionUseCase = ToggleMilestoneCompletionUseCase(fakeGoalRepository),
            getGoalAnalyticsUseCase = GetGoalAnalyticsUseCase(fakeGoalRepository),
            getGoalHistoryUseCase = GetGoalHistoryUseCase(fakeGoalHistoryRepository),
            logGoalChangeUseCase = LogGoalChangeUseCase(fakeGoalHistoryRepository),
            generateAiQuestionnaireUseCase = GenerateAiQuestionnaireUseCase(fakeGeminiRepository),
            generateAiGoalsUseCase = GenerateAiGoalsUseCase(fakeGeminiRepository),
            geminiRepository = fakeGeminiRepository
        )
    }

    // Note: goals StateFlow uses SharingStarted.WhileSubscribed(5000), so the
    // upstream combine flow only runs while someone is subscribed. All tests that
    // read from viewModel.goals must subscribe first (via .test{}) and advance the
    // dispatcher inside that block.

    // ─── Reactive Goals Flow ─────────────────────────────────────────────────

    @Test
    fun `initial goals state is empty list`() = runTest(testDispatcher) {
        viewModel.goals.test {
            val initial = awaitItem()
            assertEquals(emptyList(), initial)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `goals flow emits when repository has goals`() = runTest(testDispatcher) {
        val goal1 = testGoal(id = "g1", title = "Goal 1")
        val goal2 = testGoal(id = "g2", title = "Goal 2")
        fakeGoalRepository.setGoals(listOf(goal1, goal2))

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            val goals = awaitItem()
            assertEquals(2, goals.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `goals flow updates reactively when goal is added`() = runTest(testDispatcher) {
        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()

            fakeGoalRepository.setGoals(listOf(testGoal(id = "g1")))
            val updated = awaitItem()
            assertEquals(1, updated.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isLoading becomes false after goals are loaded`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(listOf(testGoal()))

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // goals with data
            cancelAndIgnoreRemainingEvents()
        }

        assertFalse(viewModel.isLoading.value)
    }

    // ─── Search Query ────────────────────────────────────────────────────────

    @Test
    fun `updateSearchQuery updates searchQuery state`() = runTest(testDispatcher) {
        viewModel.updateSearchQuery("fitness")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("fitness", viewModel.searchQuery.value)
    }

    @Test
    fun `updateSearchQuery filters goals by title`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(
            listOf(
                testGoal(id = "g1", title = "Learn Kotlin"),
                testGoal(id = "g2", title = "Run a marathon"),
                testGoal(id = "g3", title = "Learn Python")
            )
        )

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // all 3 goals

            viewModel.updateSearchQuery("Learn")
            val filtered = awaitItem()
            assertEquals(2, filtered.size)
            assertTrue(filtered.all { it.title.contains("Learn") })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateSearchQuery filters goals by description`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(
            listOf(
                testGoal(id = "g1", title = "Goal A", description = "about fitness"),
                testGoal(id = "g2", title = "Goal B", description = "about reading")
            )
        )

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // all 2 goals

            viewModel.updateSearchQuery("fitness")
            val filtered = awaitItem()
            assertEquals(1, filtered.size)
            assertEquals("g1", filtered.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateSearchQuery is case insensitive`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(
            listOf(
                testGoal(id = "g1", title = "Learn KOTLIN"),
                testGoal(id = "g2", title = "Run marathon")
            )
        )

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // 2 goals

            viewModel.updateSearchQuery("kotlin")
            val filtered = awaitItem()
            assertEquals(1, filtered.size)
            assertEquals("g1", filtered.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty search query returns all goals`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(
            listOf(
                testGoal(id = "g1", title = "Goal 1"),
                testGoal(id = "g2", title = "Goal 2")
            )
        )

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // all 2 goals

            viewModel.updateSearchQuery("nothing")
            awaitItem() // 0 goals (filtered out)

            viewModel.updateSearchQuery("")
            val result = awaitItem()
            assertEquals(2, result.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── Filter ──────────────────────────────────────────────────────────────

    @Test
    fun `updateFilter updates selectedFilter state`() = runTest(testDispatcher) {
        viewModel.updateFilter(GoalFilter.ACTIVE)
        assertEquals(GoalFilter.ACTIVE, viewModel.selectedFilter.value)
    }

    @Test
    fun `filter ALL shows all goals`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(
            listOf(
                testGoal(id = "g1", status = GoalStatus.IN_PROGRESS),
                testGoal(id = "g2", status = GoalStatus.COMPLETED),
                testGoal(id = "g3", status = GoalStatus.NOT_STARTED)
            )
        )

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            val goals = awaitItem()
            assertEquals(3, goals.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filter ACTIVE excludes completed goals`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(
            listOf(
                testGoal(id = "g1", status = GoalStatus.IN_PROGRESS),
                testGoal(id = "g2", status = GoalStatus.COMPLETED),
                testGoal(id = "g3", status = GoalStatus.NOT_STARTED)
            )
        )

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // all 3 goals

            viewModel.updateFilter(GoalFilter.ACTIVE)
            val goals = awaitItem()
            assertEquals(2, goals.size)
            assertTrue(goals.none { it.status == GoalStatus.COMPLETED })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `filter COMPLETED shows only completed goals`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(
            listOf(
                testGoal(id = "g1", status = GoalStatus.IN_PROGRESS),
                testGoal(id = "g2", status = GoalStatus.COMPLETED),
                testGoal(id = "g3", status = GoalStatus.NOT_STARTED)
            )
        )

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // all 3 goals

            viewModel.updateFilter(GoalFilter.COMPLETED)
            val goals = awaitItem()
            assertEquals(1, goals.size)
            assertEquals(GoalStatus.COMPLETED, goals.first().status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search and filter combine correctly`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(
            listOf(
                testGoal(id = "g1", title = "Learn Kotlin", status = GoalStatus.IN_PROGRESS),
                testGoal(id = "g2", title = "Learn Python", status = GoalStatus.COMPLETED),
                testGoal(id = "g3", title = "Run marathon", status = GoalStatus.IN_PROGRESS)
            )
        )

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // all 3 goals

            viewModel.updateSearchQuery("Learn")
            viewModel.updateFilter(GoalFilter.ACTIVE)
            testDispatcher.scheduler.advanceUntilIdle()

            val goals = awaitItem()
            assertEquals(1, goals.size)
            assertEquals("g1", goals.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── CRUD Operations ─────────────────────────────────────────────────────

    @Test
    fun `createGoal adds goal to repository`() = runTest(testDispatcher) {
        val goal = testGoal(id = "new-goal", title = "New Goal")

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.createGoal(goal)
            testDispatcher.scheduler.advanceUntilIdle()

            val goals = awaitItem()
            assertEquals(1, goals.size)
            assertEquals("new-goal", goals.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `createGoal clears error on success`() = runTest(testDispatcher) {
        viewModel.createGoal(testGoal())
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.error.value)
    }

    @Test
    fun `updateGoal updates goal in repository`() = runTest(testDispatcher) {
        val goal = testGoal(id = "g1", title = "Original")
        fakeGoalRepository.setGoals(listOf(goal))

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // [goal]

            viewModel.updateGoal(goal.copy(title = "Updated"))
            testDispatcher.scheduler.advanceUntilIdle()

            val goals = awaitItem()
            assertEquals("Updated", goals.first().title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteGoal removes goal from repository`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(
            listOf(
                testGoal(id = "g1"),
                testGoal(id = "g2")
            )
        )

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // [g1, g2]

            viewModel.deleteGoal("g1")
            testDispatcher.scheduler.advanceUntilIdle()

            val goals = awaitItem()
            assertEquals(1, goals.size)
            assertEquals("g2", goals.first().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── Progress, Status, Notes ─────────────────────────────────────────────

    @Test
    fun `updateGoalProgress updates progress in repository`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(listOf(testGoal(id = "g1", progress = 0)))

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // [g1]

            viewModel.updateGoalProgress("g1", 50)
            testDispatcher.scheduler.advanceUntilIdle()

            val goals = awaitItem()
            assertEquals(50L, goals.first().progress)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateGoalStatus updates status in repository`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(listOf(testGoal(id = "g1", status = GoalStatus.IN_PROGRESS)))

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // [g1]

            viewModel.updateGoalStatus("g1", GoalStatus.COMPLETED)
            testDispatcher.scheduler.advanceUntilIdle()

            val goals = awaitItem()
            assertEquals(GoalStatus.COMPLETED, goals.first().status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateGoalStatus clears error on success`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(listOf(testGoal(id = "g1")))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateGoalStatus("g1", GoalStatus.COMPLETED)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.error.value)
    }

    @Test
    fun `updateGoalNotes updates notes in repository`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(listOf(testGoal(id = "g1", notes = "")))

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // [g1]

            viewModel.updateGoalNotes("g1", "Important notes")
            testDispatcher.scheduler.advanceUntilIdle()

            val goals = awaitItem()
            assertEquals("Important notes", goals.first().notes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── Milestones ──────────────────────────────────────────────────────────

    @Test
    fun `addMilestone adds milestone to goal`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(listOf(testGoal(id = "g1", milestones = emptyList())))

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // [g1]

            viewModel.addMilestone("g1", "New milestone")
            testDispatcher.scheduler.advanceUntilIdle()

            val goals = awaitItem()
            val milestones = goals.first().milestones
            assertEquals(1, milestones.size)
            assertEquals("New milestone", milestones.first().title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addMilestone clears error on success`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(listOf(testGoal(id = "g1")))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addMilestone("g1", "Milestone")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.error.value)
    }

    @Test
    fun `toggleMilestoneCompletion toggles milestone`() = runTest(testDispatcher) {
        val milestone = testMilestone(id = "m1", isCompleted = false)
        fakeGoalRepository.setGoals(listOf(testGoal(id = "g1", milestones = listOf(milestone))))

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // [g1 with m1]

            viewModel.toggleMilestoneCompletion("g1", "m1")
            testDispatcher.scheduler.advanceUntilIdle()

            val goals = awaitItem()
            assertTrue(goals.first().milestones.first().isCompleted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleMilestoneCompletion auto-transitions NOT_STARTED to IN_PROGRESS`() = runTest(testDispatcher) {
        val milestone = testMilestone(id = "m1", isCompleted = false)
        fakeGoalRepository.setGoals(
            listOf(testGoal(id = "g1", status = GoalStatus.NOT_STARTED, milestones = listOf(milestone)))
        )

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // [g1]

            viewModel.toggleMilestoneCompletion("g1", "m1")
            testDispatcher.scheduler.advanceUntilIdle()

            // Might get intermediate emissions, collect the latest
            val goals = expectMostRecentItem()
            assertEquals(GoalStatus.IN_PROGRESS, goals.first().status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleMilestoneCompletion prompts to complete goal when all milestones done`() = runTest(testDispatcher) {
        val m1 = testMilestone(id = "m1", isCompleted = true)
        val m2 = testMilestone(id = "m2", isCompleted = false)
        fakeGoalRepository.setGoals(
            listOf(testGoal(id = "g1", status = GoalStatus.IN_PROGRESS, milestones = listOf(m1, m2)))
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleMilestoneCompletion("g1", "m2")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("g1", viewModel.promptCompleteGoal.value)
    }

    @Test
    fun `clearCompleteGoalPrompt clears the prompt`() = runTest(testDispatcher) {
        val m1 = testMilestone(id = "m1", isCompleted = false)
        fakeGoalRepository.setGoals(
            listOf(testGoal(id = "g1", status = GoalStatus.IN_PROGRESS, milestones = listOf(m1)))
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleMilestoneCompletion("g1", "m1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.clearCompleteGoalPrompt()
        assertNull(viewModel.promptCompleteGoal.value)
    }

    // ─── Analytics and History ────────────────────────────────────────────────

    @Test
    fun `loadAnalytics populates analytics state`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(
            listOf(testGoal(id = "g1"), testGoal(id = "g2"))
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.loadAnalytics()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.analytics.value)
        assertEquals(2, viewModel.analytics.value?.totalGoals)
    }

    @Test
    fun `loadAnalytics clears error on success`() = runTest(testDispatcher) {
        viewModel.loadAnalytics()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.error.value)
    }

    @Test
    fun `loadGoalHistory populates goalHistory state`() = runTest(testDispatcher) {
        fakeGoalHistoryRepository.setChanges(
            listOf(
                testGoalChange(id = "c1", goalId = "g1"),
                testGoalChange(id = "c2", goalId = "g1"),
                testGoalChange(id = "c3", goalId = "g2")
            )
        )

        viewModel.loadGoalHistory("g1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.goalHistory.value.size)
    }

    // ─── getGoalById ─────────────────────────────────────────────────────────

    @Test
    fun `getGoalById returns correct goal from state`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(
            listOf(
                testGoal(id = "g1", title = "First"),
                testGoal(id = "g2", title = "Second")
            )
        )

        // Subscribe and wait for goals to flow through
        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // [g1, g2]

            val found = viewModel.getGoalById("g2")
            assertNotNull(found)
            assertEquals("Second", found.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getGoalById returns null for non-existent id`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(listOf(testGoal(id = "g1")))

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()
            awaitItem() // [g1]

            assertNull(viewModel.getGoalById("non-existent"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── Questionnaire State ─────────────────────────────────────────────────

    @Test
    fun `initial questionnaire step is INPUT`() = runTest(testDispatcher) {
        assertEquals(QuestionnaireStep.INPUT, viewModel.questionnaireStep.value)
    }

    @Test
    fun `generateQuestionnaire transitions to ANSWERING on success`() = runTest(testDispatcher) {
        fakeGeminiRepository.questionnaireResult = Result.Success(
            listOf(
                GoalTypeQuestions(
                    goalType = "fitness",
                    questions = listOf(Question("What level?", listOf("Beginner", "Advanced")))
                )
            )
        )

        viewModel.generateQuestionnaire("I want to get fit")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(QuestionnaireStep.ANSWERING, viewModel.questionnaireStep.value)
        assertEquals(1, viewModel.questions.value.size)
        assertFalse(viewModel.isLoadingQuestions.value)
    }

    @Test
    fun `generateQuestionnaire sets error and reverts to INPUT on failure`() = runTest(testDispatcher) {
        fakeGeminiRepository.questionnaireResult = Result.Error(DataError.Remote.SERVER_ERROR)

        viewModel.generateQuestionnaire("prompt")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(QuestionnaireStep.INPUT, viewModel.questionnaireStep.value)
        assertNotNull(viewModel.error.value)
        assertFalse(viewModel.isLoadingQuestions.value)
    }

    @Test
    fun `generateQuestionnaire updates userPrompt`() = runTest(testDispatcher) {
        fakeGeminiRepository.questionnaireResult = Result.Success(emptyList())

        viewModel.generateQuestionnaire("my prompt")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("my prompt", viewModel.userPrompt.value)
    }

    @Test
    fun `answerQuestion adds new answer`() = runTest(testDispatcher) {
        viewModel.answerQuestion("What level?", "Beginner")

        val answers = viewModel.userAnswers.value.answers
        assertEquals(1, answers.size)
        assertEquals("What level?", answers.first().questionTitle)
        assertEquals("Beginner", answers.first().selectedOption)
    }

    @Test
    fun `answerQuestion replaces existing answer for same question`() = runTest(testDispatcher) {
        viewModel.answerQuestion("What level?", "Beginner")
        viewModel.answerQuestion("What level?", "Advanced")

        val answers = viewModel.userAnswers.value.answers
        assertEquals(1, answers.size)
        assertEquals("Advanced", answers.first().selectedOption)
    }

    @Test
    fun `isQuestionnaireComplete returns false when no questions`() = runTest(testDispatcher) {
        assertFalse(viewModel.isQuestionnaireComplete())
    }

    @Test
    fun `isQuestionnaireComplete returns true when all questions answered`() = runTest(testDispatcher) {
        fakeGeminiRepository.questionnaireResult = Result.Success(
            listOf(
                GoalTypeQuestions(
                    goalType = "fitness",
                    questions = listOf(
                        Question("Q1", listOf("A", "B")),
                        Question("Q2", listOf("C", "D"))
                    )
                )
            )
        )

        viewModel.generateQuestionnaire("prompt")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.answerQuestion("Q1", "A")
        viewModel.answerQuestion("Q2", "C")

        assertTrue(viewModel.isQuestionnaireComplete())
    }

    @Test
    fun `isQuestionnaireComplete returns false when partially answered`() = runTest(testDispatcher) {
        fakeGeminiRepository.questionnaireResult = Result.Success(
            listOf(
                GoalTypeQuestions(
                    goalType = "fitness",
                    questions = listOf(
                        Question("Q1", listOf("A", "B")),
                        Question("Q2", listOf("C", "D"))
                    )
                )
            )
        )

        viewModel.generateQuestionnaire("prompt")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.answerQuestion("Q1", "A")

        assertFalse(viewModel.isQuestionnaireComplete())
    }

    @Test
    fun `resetQuestionnaire resets all questionnaire state`() = runTest(testDispatcher) {
        fakeGeminiRepository.questionnaireResult = Result.Success(
            listOf(
                GoalTypeQuestions(
                    goalType = "fitness",
                    questions = listOf(Question("Q1", listOf("A")))
                )
            )
        )

        viewModel.generateQuestionnaire("my prompt")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.answerQuestion("Q1", "A")

        viewModel.resetQuestionnaire()

        assertEquals("", viewModel.userPrompt.value)
        assertEquals(emptyList(), viewModel.questions.value)
        assertEquals(0, viewModel.userAnswers.value.answers.size)
        assertEquals(QuestionnaireStep.INPUT, viewModel.questionnaireStep.value)
        assertNull(viewModel.error.value)
    }

    // ─── AI Generation ───────────────────────────────────────────────────────

    @Test
    fun `generateGoalsDirectly populates generatedGoalsFromAI on success`() = runTest(testDispatcher) {
        val generatedGoal = testGoal(id = "ai-1", title = "AI Goal")
        fakeGeminiRepository.directGoalsResult = Result.Success(listOf(generatedGoal))

        viewModel.generateGoalsDirectly("I want to be healthy")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.generatedGoalsFromAI.value.size)
        assertEquals("AI Goal", viewModel.generatedGoalsFromAI.value.first().title)
        assertEquals(QuestionnaireStep.RESULTS, viewModel.questionnaireStep.value)
        assertFalse(viewModel.isGeneratingPersonalizedGoals.value)
    }

    @Test
    fun `generateGoalsDirectly sets error on failure`() = runTest(testDispatcher) {
        fakeGeminiRepository.directGoalsResult = Result.Error(DataError.Remote.SERVER_ERROR)

        viewModel.generateGoalsDirectly("prompt")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.error.value)
        assertEquals(QuestionnaireStep.INPUT, viewModel.questionnaireStep.value)
        assertFalse(viewModel.isGeneratingPersonalizedGoals.value)
    }

    @Test
    fun `generateGoalsDirectly sets error when AI returns empty list`() = runTest(testDispatcher) {
        fakeGeminiRepository.directGoalsResult = Result.Success(emptyList())

        viewModel.generateGoalsDirectly("prompt")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.error.value)
        assertEquals(QuestionnaireStep.INPUT, viewModel.questionnaireStep.value)
    }

    @Test
    fun `addGeneratedGoalToList adds goal to repo`() = runTest(testDispatcher) {
        val goal = testGoal(id = "gen-1", title = "Generated")

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.addGeneratedGoalToList(goal)
            testDispatcher.scheduler.advanceUntilIdle()

            val goals = awaitItem()
            assertEquals(1, goals.size)
            assertEquals("Generated", goals.first().title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addAllGeneratedGoalsToList adds all generated goals`() = runTest(testDispatcher) {
        val g1 = testGoal(id = "gen-1", title = "Goal 1")
        val g2 = testGoal(id = "gen-2", title = "Goal 2")
        fakeGeminiRepository.directGoalsResult = Result.Success(listOf(g1, g2))

        viewModel.generateGoalsDirectly("prompt")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.goals.test {
            awaitItem() // initial emptyList
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.addAllGeneratedGoalsToList()
            testDispatcher.scheduler.advanceUntilIdle()

            val goals = awaitItem()
            assertEquals(2, goals.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

// ─── Fake GeminiRepository ──────────────────────────────────────────────────

class FakeGeminiRepository : GeminiRepository {
    var questionnaireResult: Result<List<GoalTypeQuestions>, DataError.Remote> =
        Result.Success(emptyList())
    var personalizedGoalsResult: Result<List<Goal>, DataError.Remote> =
        Result.Success(emptyList())
    var directGoalsResult: Result<List<Goal>, DataError.Remote> =
        Result.Success(emptyList())

    override suspend fun generateQuestionnaire(userPrompt: String) = questionnaireResult
    override suspend fun generatePersonalizedGoals(
        originalPrompt: String,
        userAnswers: UserQuestionnaireAnswers
    ) = personalizedGoalsResult
    override suspend fun generateGoalsDirect(prompt: String) = directGoalsResult
}
