package az.tribe.lifeplanner.ui.reminder

import app.cash.turbine.test
import az.tribe.lifeplanner.domain.model.DayOfWeek
import az.tribe.lifeplanner.domain.model.ReminderFrequency
import az.tribe.lifeplanner.domain.model.ReminderType
import az.tribe.lifeplanner.testutil.FakeReminderRepository
import az.tribe.lifeplanner.testutil.testReminder
import az.tribe.lifeplanner.testutil.testReminderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderViewModelTest {

    private lateinit var viewModel: ReminderViewModel
    private lateinit var fakeRepository: FakeReminderRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeReminderRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ReminderViewModel {
        return ReminderViewModel(reminderRepository = fakeRepository)
    }

    @Test
    fun `init loads reminders from repository`() = runTest(testDispatcher) {
        fakeRepository.setReminders(listOf(
            testReminder(id = "r1", title = "Morning"),
            testReminder(id = "r2", title = "Evening")
        ))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.reminders.size)
    }

    @Test
    fun `init loads settings from repository`() = runTest(testDispatcher) {
        val customSettings = testReminderSettings(maxRemindersPerDay = 10)
        fakeRepository.settings = customSettings
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(10, viewModel.uiState.value.settings.maxRemindersPerDay)
    }

    @Test
    fun `isLoading is false after init completes`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `loadReminders refreshes reminders`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.reminders.isEmpty())

        fakeRepository.setReminders(listOf(testReminder(id = "r1")))
        viewModel.loadReminders()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.reminders.size)
    }

    @Test
    fun `createReminder adds reminder and hides dialog`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showAddDialog()
        assertTrue(viewModel.uiState.value.showAddDialog)

        viewModel.createReminder(
            title = "New Reminder",
            message = "Do it!",
            type = ReminderType.CUSTOM,
            frequency = ReminderFrequency.DAILY,
            scheduledTime = LocalTime(9, 0)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showAddDialog)
        assertEquals(1, viewModel.uiState.value.reminders.size)
    }

    @Test
    fun `createReminder with all params`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.createReminder(
            title = "Full Reminder",
            message = "All params",
            type = ReminderType.HABIT_REMINDER,
            frequency = ReminderFrequency.WEEKLY,
            scheduledTime = LocalTime(8, 30),
            scheduledDays = listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            linkedHabitId = "habit-1",
            isSmartTiming = true
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.reminders.size)
        val reminder = viewModel.uiState.value.reminders.first()
        assertEquals("Full Reminder", reminder.title)
        assertTrue(reminder.isEnabled)
    }

    @Test
    fun `updateReminder modifies existing reminder and hides dialog`() = runTest(testDispatcher) {
        val reminder = testReminder(id = "r1", title = "Old")
        fakeRepository.setReminders(listOf(reminder))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateReminder(reminder.copy(title = "Updated"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showEditDialog)
        assertNull(viewModel.uiState.value.selectedReminder)
        assertEquals("Updated", viewModel.uiState.value.reminders.first().title)
    }

    @Test
    fun `deleteReminder removes reminder from repository`() = runTest(testDispatcher) {
        fakeRepository.setReminders(listOf(
            testReminder(id = "r1", title = "Delete Me"),
            testReminder(id = "r2", title = "Keep Me")
        ))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteReminder("r1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.reminders.size)
        assertEquals("Keep Me", viewModel.uiState.value.reminders.first().title)
    }

    @Test
    fun `toggleReminder disables enabled reminder`() = runTest(testDispatcher) {
        val reminder = testReminder(id = "r1", isEnabled = true)
        fakeRepository.setReminders(listOf(reminder))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleReminder(reminder)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.reminders.first().isEnabled)
    }

    @Test
    fun `toggleReminder enables disabled reminder`() = runTest(testDispatcher) {
        val reminder = testReminder(id = "r1", isEnabled = false)
        fakeRepository.setReminders(listOf(reminder))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleReminder(reminder)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.reminders.first().isEnabled)
    }

    @Test
    fun `toggleGlobalReminders enables all reminders`() = runTest(testDispatcher) {
        fakeRepository.setReminders(listOf(
            testReminder(id = "r1", isEnabled = false),
            testReminder(id = "r2", isEnabled = false)
        ))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleGlobalReminders(true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.reminders.all { it.isEnabled })
        assertTrue(viewModel.uiState.value.settings.isEnabled)
    }

    @Test
    fun `toggleGlobalReminders disables all reminders`() = runTest(testDispatcher) {
        fakeRepository.setReminders(listOf(
            testReminder(id = "r1", isEnabled = true),
            testReminder(id = "r2", isEnabled = true)
        ))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleGlobalReminders(false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.reminders.all { !it.isEnabled })
        assertFalse(viewModel.uiState.value.settings.isEnabled)
    }

    @Test
    fun `updateSettings updates settings in state`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val newSettings = testReminderSettings(maxRemindersPerDay = 20, smartTimingEnabled = false)
        viewModel.updateSettings(newSettings)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(20, viewModel.uiState.value.settings.maxRemindersPerDay)
        assertFalse(viewModel.uiState.value.settings.smartTimingEnabled)
    }

    @Test
    fun `showAddDialog sets showAddDialog to true`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showAddDialog)
        viewModel.showAddDialog()
        assertTrue(viewModel.uiState.value.showAddDialog)
    }

    @Test
    fun `hideAddDialog sets showAddDialog to false`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showAddDialog()
        viewModel.hideAddDialog()
        assertFalse(viewModel.uiState.value.showAddDialog)
    }

    @Test
    fun `selectReminder sets selected reminder and shows edit dialog`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val reminder = testReminder(id = "r1", title = "Selected")
        viewModel.selectReminder(reminder)

        assertEquals(reminder, viewModel.uiState.value.selectedReminder)
        assertTrue(viewModel.uiState.value.showEditDialog)
    }

    @Test
    fun `hideEditDialog clears selected reminder and hides dialog`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectReminder(testReminder())
        assertTrue(viewModel.uiState.value.showEditDialog)

        viewModel.hideEditDialog()
        assertFalse(viewModel.uiState.value.showEditDialog)
        assertNull(viewModel.uiState.value.selectedReminder)
    }

    @Test
    fun `showSettingsSheet sets showSettingsSheet to true`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showSettingsSheet)
        viewModel.showSettingsSheet()
        assertTrue(viewModel.uiState.value.showSettingsSheet)
    }

    @Test
    fun `hideSettingsSheet sets showSettingsSheet to false`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showSettingsSheet()
        viewModel.hideSettingsSheet()
        assertFalse(viewModel.uiState.value.showSettingsSheet)
    }

    @Test
    fun `clearError resets error to null`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
    }
}
