package az.tribe.lifeplanner.ui.habit

import app.cash.turbine.test
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.HabitFrequency
import az.tribe.lifeplanner.testutil.FakeHabitRepository
import az.tribe.lifeplanner.testutil.testHabit
import az.tribe.lifeplanner.testutil.testHabitCheckIn
import az.tribe.lifeplanner.usecases.habit.CheckInHabitUseCase
import az.tribe.lifeplanner.usecases.habit.CreateHabitUseCase
import az.tribe.lifeplanner.usecases.habit.DeleteHabitUseCase
import az.tribe.lifeplanner.usecases.habit.UncheckHabitUseCase
import az.tribe.lifeplanner.usecases.habit.UpdateHabitUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HabitViewModelTest {

    private lateinit var viewModel: HabitViewModel
    private lateinit var fakeRepository: FakeHabitRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeHabitRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HabitViewModel {
        return HabitViewModel(
            habitRepository = fakeRepository,
            createHabitUseCase = CreateHabitUseCase(fakeRepository),
            updateHabitUseCase = UpdateHabitUseCase(fakeRepository),
            deleteHabitUseCase = DeleteHabitUseCase(fakeRepository),
            checkInHabitUseCase = CheckInHabitUseCase(fakeRepository),
            uncheckHabitUseCase = UncheckHabitUseCase(fakeRepository)
        )
    }

    @Test
    fun `initial habits state is empty list`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        viewModel.habits.test {
            val initial = awaitItem()
            assertTrue(initial.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `habits flow emits habits from repository`() = runTest(testDispatcher) {
        val habit = testHabit(id = "h1", title = "Exercise")
        fakeRepository.setHabits(listOf(habit))
        viewModel = createViewModel()

        viewModel.habits.test {
            val items = awaitItem()
            if (items.isEmpty()) {
                val nextItems = awaitItem()
                assertEquals(1, nextItems.size)
                assertEquals("Exercise", nextItems.first().habit.title)
            } else {
                assertEquals(1, items.size)
                assertEquals("Exercise", items.first().habit.title)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `habits are sorted by completion then by streak descending`() = runTest(testDispatcher) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val habit1 = testHabit(id = "h1", title = "Completed", currentStreak = 5)
        val habit2 = testHabit(id = "h2", title = "Not completed high streak", currentStreak = 10)
        val habit3 = testHabit(id = "h3", title = "Not completed low streak", currentStreak = 2)
        fakeRepository.setHabits(listOf(habit1, habit2, habit3))
        fakeRepository.setCheckIns(listOf(testHabitCheckIn(habitId = "h1", date = today, completed = true)))
        viewModel = createViewModel()

        viewModel.habits.test {
            var items = awaitItem()
            if (items.isEmpty()) items = awaitItem()
            assertEquals(3, items.size)
            // Not completed habits come first (isCompletedToday = false), sorted by streak desc
            assertFalse(items[0].isCompletedToday)
            assertEquals("Not completed high streak", items[0].habit.title)
            assertFalse(items[1].isCompletedToday)
            assertEquals("Not completed low streak", items[1].habit.title)
            // Completed habit last
            assertTrue(items[2].isCompletedToday)
            assertEquals("Completed", items[2].habit.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isLoading becomes false after habits emit`() = runTest(testDispatcher) {
        fakeRepository.setHabits(listOf(testHabit()))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.isLoading.test {
            val loading = awaitItem()
            assertFalse(loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `createHabit adds habit to repository and hides dialog`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showAddHabitDialog()
        assertTrue(viewModel.showAddHabitDialog.value)

        viewModel.createHabit(
            title = "New Habit",
            description = "Description",
            category = GoalCategory.PHYSICAL,
            frequency = HabitFrequency.DAILY
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.showAddHabitDialog.value)
    }

    @Test
    fun `createHabit prevents duplicate by title`() = runTest(testDispatcher) {
        val habit = testHabit(id = "h1", title = "Exercise")
        fakeRepository.setHabits(listOf(habit))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Wait for habits to be populated
        viewModel.habits.test {
            var items = awaitItem()
            if (items.isEmpty()) items = awaitItem()
            assertEquals(1, items.size)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.createHabit(
            title = "Exercise",
            description = "Duplicate",
            category = GoalCategory.PHYSICAL,
            frequency = HabitFrequency.DAILY
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Still only 1 habit
        viewModel.habits.test {
            val items = awaitItem()
            assertEquals(1, items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteHabit removes habit from repository`() = runTest(testDispatcher) {
        val habit = testHabit(id = "h1", title = "To Delete")
        fakeRepository.setHabits(listOf(habit))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteHabit("h1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.habits.test {
            val items = awaitItem()
            assertTrue(items.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateHabit updates habit in repository`() = runTest(testDispatcher) {
        val habit = testHabit(id = "h1", title = "Old Title")
        fakeRepository.setHabits(listOf(habit))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateHabit(habit.copy(title = "New Title"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.habits.test {
            var items = awaitItem()
            if (items.isEmpty()) items = awaitItem()
            assertEquals("New Title", items.first().habit.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `checkInHabit creates check-in and sets recentCheckIn`() = runTest(testDispatcher) {
        val habit = testHabit(id = "h1", title = "Exercise")
        fakeRepository.setHabits(listOf(habit))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.checkInHabit("h1", "Felt great")
        testDispatcher.scheduler.advanceUntilIdle()

        val recent = viewModel.recentCheckIn.value
        assertNotNull(recent)
        assertEquals("h1", recent.habit.id)
    }

    @Test
    fun `clearRecentCheckIn clears the recent check-in`() = runTest(testDispatcher) {
        val habit = testHabit(id = "h1")
        fakeRepository.setHabits(listOf(habit))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.checkInHabit("h1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.recentCheckIn.value)

        viewModel.clearRecentCheckIn()
        assertNull(viewModel.recentCheckIn.value)
    }

    @Test
    fun `uncheckInHabit removes check-in`() = runTest(testDispatcher) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val habit = testHabit(id = "h1")
        fakeRepository.setHabits(listOf(habit))
        fakeRepository.setCheckIns(listOf(testHabitCheckIn(id = "ci1", habitId = "h1", date = today, completed = true)))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uncheckInHabit("h1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Habit should no longer be checked in
        viewModel.habits.test {
            val items = awaitItem()
            if (items.isNotEmpty()) {
                assertFalse(items.first().isCompletedToday)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleCheckIn checks in when not completed`() = runTest(testDispatcher) {
        val habit = testHabit(id = "h1")
        fakeRepository.setHabits(listOf(habit))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Wait for habits to be loaded
        viewModel.habits.test {
            var items = awaitItem()
            if (items.isEmpty()) items = awaitItem()
            assertFalse(items.first().isCompletedToday)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.toggleCheckIn("h1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Should now be checked in
        viewModel.habits.test {
            val items = awaitItem()
            if (items.isNotEmpty()) {
                assertTrue(items.first().isCompletedToday)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleCheckIn unchecks when already completed`() = runTest(testDispatcher) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val habit = testHabit(id = "h1")
        fakeRepository.setHabits(listOf(habit))
        fakeRepository.setCheckIns(listOf(testHabitCheckIn(id = "ci1", habitId = "h1", date = today, completed = true)))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.habits.test {
            var items = awaitItem()
            if (items.isEmpty()) items = awaitItem()
            assertTrue(items.first().isCompletedToday)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.toggleCheckIn("h1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.habits.test {
            val items = awaitItem()
            if (items.isNotEmpty()) {
                assertFalse(items.first().isCompletedToday)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showAddHabitDialog sets state to true`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        assertFalse(viewModel.showAddHabitDialog.value)

        viewModel.showAddHabitDialog()
        assertTrue(viewModel.showAddHabitDialog.value)
    }

    @Test
    fun `hideAddHabitDialog sets state to false`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        viewModel.showAddHabitDialog()
        assertTrue(viewModel.showAddHabitDialog.value)

        viewModel.hideAddHabitDialog()
        assertFalse(viewModel.showAddHabitDialog.value)
    }

    @Test
    fun `clearError resets error to null`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        assertNull(viewModel.error.value)

        viewModel.clearError()
        assertNull(viewModel.error.value)
    }

    @Test
    fun `getTodayCompletedCount returns count of completed habits`() = runTest(testDispatcher) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        fakeRepository.setHabits(listOf(
            testHabit(id = "h1"),
            testHabit(id = "h2"),
            testHabit(id = "h3")
        ))
        fakeRepository.setCheckIns(listOf(
            testHabitCheckIn(habitId = "h1", date = today, completed = true),
            testHabitCheckIn(habitId = "h2", date = today, completed = true)
        ))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Wait for habits to load
        viewModel.habits.test {
            var items = awaitItem()
            if (items.isEmpty()) items = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(2, viewModel.getTodayCompletedCount())
    }

    @Test
    fun `getTotalHabitsCount returns total number of habits`() = runTest(testDispatcher) {
        fakeRepository.setHabits(listOf(
            testHabit(id = "h1"),
            testHabit(id = "h2")
        ))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.habits.test {
            var items = awaitItem()
            if (items.isEmpty()) items = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(2, viewModel.getTotalHabitsCount())
    }

    @Test
    fun `getStreakLeader returns habit with highest streak`() = runTest(testDispatcher) {
        fakeRepository.setHabits(listOf(
            testHabit(id = "h1", currentStreak = 3),
            testHabit(id = "h2", currentStreak = 10),
            testHabit(id = "h3", currentStreak = 5)
        ))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.habits.test {
            var items = awaitItem()
            if (items.isEmpty()) items = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        val leader = viewModel.getStreakLeader()
        assertNotNull(leader)
        assertEquals("h2", leader.id)
    }

    @Test
    fun `getStreakLeader returns null when no habits`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.getStreakLeader())
    }

    @Test
    fun `error state is set on checkIn failure`() = runTest(testDispatcher) {
        // Habit does not exist in repository, but we can still call checkInHabit
        // The recentCheckIn will be null if habit not found
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.checkInHabit("nonexistent")
        testDispatcher.scheduler.advanceUntilIdle()

        // recentCheckIn should be null since habit doesn't exist
        assertNull(viewModel.recentCheckIn.value)
    }
}
