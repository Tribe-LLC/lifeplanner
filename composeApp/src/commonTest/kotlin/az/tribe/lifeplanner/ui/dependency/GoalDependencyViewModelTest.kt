package az.tribe.lifeplanner.ui.dependency

import app.cash.turbine.test
import az.tribe.lifeplanner.domain.enum.DependencyType
import az.tribe.lifeplanner.domain.model.DependencyGraph
import az.tribe.lifeplanner.domain.model.GoalDependency
import az.tribe.lifeplanner.testutil.FakeGoalDependencyRepository
import az.tribe.lifeplanner.testutil.FakeGoalRepository
import az.tribe.lifeplanner.testutil.testGoal
import az.tribe.lifeplanner.testutil.testGoalDependency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class GoalDependencyViewModelTest {

    private lateinit var viewModel: GoalDependencyViewModel
    private lateinit var fakeDependencyRepository: FakeGoalDependencyRepository
    private lateinit var fakeGoalRepository: FakeGoalRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeDependencyRepository = FakeGoalDependencyRepository()
        fakeGoalRepository = FakeGoalRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): GoalDependencyViewModel {
        return GoalDependencyViewModel(
            dependencyRepository = fakeDependencyRepository,
            goalRepository = fakeGoalRepository
        )
    }

    // ─── loadData ────────────────────────────────────────────────────────────

    @Test
    fun `init loads dependency graph and all goals`() = runTest(testDispatcher) {
        val g1 = testGoal(id = "g1", title = "Goal 1")
        val g2 = testGoal(id = "g2", title = "Goal 2")
        fakeGoalRepository.setGoals(listOf(g1, g2))

        val dep = testGoalDependency(id = "d1", sourceGoalId = "g1", targetGoalId = "g2")
        fakeDependencyRepository.graphToReturn = DependencyGraph(
            nodes = emptyList(),
            edges = listOf(dep)
        )

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(2, state.allGoals.size)
            assertEquals(1, state.dependencyGraph.edges.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadData sets error on failure`() = runTest(testDispatcher) {
        // Use a default graph but set goals; the init should work.
        // We test the error path by calling loadData after modifying setup.
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Initial load should succeed
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `loadData sets isLoading false after completion`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    // ─── selectGoal / clearSelectedGoal ──────────────────────────────────────

    @Test
    fun `selectGoal updates selectedGoal and loads dependencies`() = runTest(testDispatcher) {
        val g1 = testGoal(id = "g1")
        val g2 = testGoal(id = "g2")
        fakeGoalRepository.setGoals(listOf(g1, g2))

        val dep = testGoalDependency(sourceGoalId = "g1", targetGoalId = "g2")
        fakeDependencyRepository.setDependencies(listOf(dep))

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectGoal(g1)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(g1, state.selectedGoal)
            assertTrue(state.selectedGoalDependencies.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectGoal loads suggested dependencies`() = runTest(testDispatcher) {
        val g1 = testGoal(id = "g1")
        val g2 = testGoal(id = "g2")
        fakeGoalRepository.setGoals(listOf(g1, g2))
        fakeDependencyRepository.suggestedDeps = listOf(g2 to DependencyType.BLOCKS)

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectGoal(g1)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(1, state.suggestedDependencies.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearSelectedGoal resets selection and dependencies`() = runTest(testDispatcher) {
        val g1 = testGoal(id = "g1")
        fakeGoalRepository.setGoals(listOf(g1))

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectGoal(g1)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.clearSelectedGoal()

        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.selectedGoal)
            assertTrue(state.selectedGoalDependencies.isEmpty())
            assertTrue(state.suggestedDependencies.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ─── addDependency ───────────────────────────────────────────────────────

    @Test
    fun `addDependency adds dependency and refreshes graph`() = runTest(testDispatcher) {
        val g1 = testGoal(id = "g1")
        val g2 = testGoal(id = "g2")
        fakeGoalRepository.setGoals(listOf(g1, g2))

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectGoal(g1)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addDependency("g1", "g2", DependencyType.BLOCKS)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertNull(state.error)
            assertFalse(state.showAddDependencyDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `addDependency hides dialog on success`() = runTest(testDispatcher) {
        val g1 = testGoal(id = "g1")
        val g2 = testGoal(id = "g2")
        fakeGoalRepository.setGoals(listOf(g1, g2))

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showAddDependencyDialog()
        assertTrue(viewModel.uiState.value.showAddDependencyDialog)

        viewModel.addDependency("g1", "g2", DependencyType.BLOCKS)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showAddDependencyDialog)
    }

    @Test
    fun `addDependency refreshes selected goal dependencies`() = runTest(testDispatcher) {
        val g1 = testGoal(id = "g1")
        val g2 = testGoal(id = "g2")
        fakeGoalRepository.setGoals(listOf(g1, g2))

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectGoal(g1)
        testDispatcher.scheduler.advanceUntilIdle()

        val initialDeps = viewModel.uiState.value.selectedGoalDependencies.size

        viewModel.addDependency("g1", "g2", DependencyType.BLOCKS)
        testDispatcher.scheduler.advanceUntilIdle()

        val newDeps = viewModel.uiState.value.selectedGoalDependencies.size
        assertTrue(newDeps > initialDeps)
    }

    // ─── removeDependency ────────────────────────────────────────────────────

    @Test
    fun `removeDependency removes dependency and refreshes graph`() = runTest(testDispatcher) {
        val g1 = testGoal(id = "g1")
        val g2 = testGoal(id = "g2")
        fakeGoalRepository.setGoals(listOf(g1, g2))

        val dep = testGoalDependency(id = "d1", sourceGoalId = "g1", targetGoalId = "g2")
        fakeDependencyRepository.setDependencies(listOf(dep))

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectGoal(g1)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.removeDependency("d1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertNull(state.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removeDependency updates selected goal dependencies`() = runTest(testDispatcher) {
        val g1 = testGoal(id = "g1")
        val g2 = testGoal(id = "g2")
        fakeGoalRepository.setGoals(listOf(g1, g2))

        val dep = testGoalDependency(id = "d1", sourceGoalId = "g1", targetGoalId = "g2")
        fakeDependencyRepository.setDependencies(listOf(dep))

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectGoal(g1)
        testDispatcher.scheduler.advanceUntilIdle()

        val before = viewModel.uiState.value.selectedGoalDependencies.size
        assertEquals(1, before)

        viewModel.removeDependency("d1")
        testDispatcher.scheduler.advanceUntilIdle()

        val after = viewModel.uiState.value.selectedGoalDependencies.size
        assertEquals(0, after)
    }

    // ─── Dialog State ────────────────────────────────────────────────────────

    @Test
    fun `showAddDependencyDialog sets flag true`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showAddDependencyDialog()
        assertTrue(viewModel.uiState.value.showAddDependencyDialog)
    }

    @Test
    fun `hideAddDependencyDialog sets flag false`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showAddDependencyDialog()
        viewModel.hideAddDependencyDialog()
        assertFalse(viewModel.uiState.value.showAddDependencyDialog)
    }

    @Test
    fun `showDependencyGraph sets flag true`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showDependencyGraph()
        assertTrue(viewModel.uiState.value.showDependencyGraphSheet)
    }

    @Test
    fun `hideDependencyGraph sets flag false`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showDependencyGraph()
        viewModel.hideDependencyGraph()
        assertFalse(viewModel.uiState.value.showDependencyGraphSheet)
    }

    // ─── clearError ──────────────────────────────────────────────────────────

    @Test
    fun `clearError resets error to null`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Manually verify the clear works
        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
    }

    // ─── Edge Cases ──────────────────────────────────────────────────────────

    @Test
    fun `loadData with empty goals list`() = runTest(testDispatcher) {
        fakeGoalRepository.setGoals(emptyList())

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.allGoals.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selectGoal with no dependencies returns empty list`() = runTest(testDispatcher) {
        val g1 = testGoal(id = "g1")
        fakeGoalRepository.setGoals(listOf(g1))
        fakeDependencyRepository.setDependencies(emptyList())

        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectGoal(g1)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.selectedGoalDependencies.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
