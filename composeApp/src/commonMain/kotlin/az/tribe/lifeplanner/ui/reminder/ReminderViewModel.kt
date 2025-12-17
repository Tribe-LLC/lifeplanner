package az.tribe.lifeplanner.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.domain.model.DayOfWeek
import az.tribe.lifeplanner.domain.model.Reminder
import az.tribe.lifeplanner.domain.model.ReminderFrequency
import az.tribe.lifeplanner.domain.model.ReminderSettings
import az.tribe.lifeplanner.domain.model.ReminderType
import az.tribe.lifeplanner.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class ReminderUiState(
    val isLoading: Boolean = false,
    val reminders: List<Reminder> = emptyList(),
    val settings: ReminderSettings = ReminderSettings(),
    val selectedReminder: Reminder? = null,
    val showAddDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val error: String? = null
)

@OptIn(ExperimentalUuidApi::class)
class ReminderViewModel(
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReminderUiState())
    val uiState: StateFlow<ReminderUiState> = _uiState.asStateFlow()

    init {
        loadReminders()
        loadSettings()
    }

    fun loadReminders() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val reminders = reminderRepository.getAllReminders()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    reminders = reminders
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val settings = reminderRepository.getSettings()
                _uiState.value = _uiState.value.copy(settings = settings)
            } catch (e: Exception) {
                // Use defaults
            }
        }
    }

    fun createReminder(
        title: String,
        message: String,
        type: ReminderType,
        frequency: ReminderFrequency,
        scheduledTime: LocalTime,
        scheduledDays: List<DayOfWeek> = emptyList(),
        linkedGoalId: String? = null,
        linkedHabitId: String? = null,
        isSmartTiming: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val reminder = Reminder(
                    id = Uuid.random().toString(),
                    title = title,
                    message = message,
                    type = type,
                    frequency = frequency,
                    scheduledTime = scheduledTime,
                    scheduledDays = scheduledDays,
                    linkedGoalId = linkedGoalId,
                    linkedHabitId = linkedHabitId,
                    isEnabled = true,
                    isSmartTiming = isSmartTiming,
                    createdAt = now
                )
                reminderRepository.createReminder(reminder)
                loadReminders()
                _uiState.value = _uiState.value.copy(showAddDialog = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun updateReminder(reminder: Reminder) {
        viewModelScope.launch {
            try {
                reminderRepository.updateReminder(reminder)
                loadReminders()
                _uiState.value = _uiState.value.copy(
                    showEditDialog = false,
                    selectedReminder = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun deleteReminder(reminderId: String) {
        viewModelScope.launch {
            try {
                reminderRepository.deleteReminder(reminderId)
                loadReminders()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun toggleReminder(reminder: Reminder) {
        viewModelScope.launch {
            try {
                val updated = reminder.copy(isEnabled = !reminder.isEnabled)
                reminderRepository.updateReminder(updated)
                loadReminders()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun updateSettings(settings: ReminderSettings) {
        viewModelScope.launch {
            try {
                reminderRepository.updateSettings(settings)
                _uiState.value = _uiState.value.copy(settings = settings)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun toggleGlobalReminders(enabled: Boolean) {
        viewModelScope.launch {
            try {
                if (enabled) {
                    reminderRepository.enableAllReminders()
                } else {
                    reminderRepository.disableAllReminders()
                }
                loadReminders()
                val currentSettings = _uiState.value.settings
                updateSettings(currentSettings.copy(isEnabled = enabled))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = true)
    }

    fun hideAddDialog() {
        _uiState.value = _uiState.value.copy(showAddDialog = false)
    }

    fun selectReminder(reminder: Reminder) {
        _uiState.value = _uiState.value.copy(
            selectedReminder = reminder,
            showEditDialog = true
        )
    }

    fun hideEditDialog() {
        _uiState.value = _uiState.value.copy(
            showEditDialog = false,
            selectedReminder = null
        )
    }

    fun showSettingsSheet() {
        _uiState.value = _uiState.value.copy(showSettingsSheet = true)
    }

    fun hideSettingsSheet() {
        _uiState.value = _uiState.value.copy(showSettingsSheet = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun getOptimalTime(type: ReminderType) {
        viewModelScope.launch {
            try {
                val optimalTime = reminderRepository.calculateOptimalTime(type)
                // Could update UI to suggest this time
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun recordActivity() {
        viewModelScope.launch {
            try {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                reminderRepository.recordUserActivity(now)
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }
}
