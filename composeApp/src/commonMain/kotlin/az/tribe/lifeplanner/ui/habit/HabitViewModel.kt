package az.tribe.lifeplanner.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.mapper.createNewHabit
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.HabitFrequency
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.usecases.habit.CheckInHabitUseCase
import az.tribe.lifeplanner.usecases.habit.CreateHabitUseCase
import az.tribe.lifeplanner.usecases.habit.DeleteHabitUseCase
import az.tribe.lifeplanner.usecases.habit.GetAllHabitsUseCase
import az.tribe.lifeplanner.usecases.habit.GetHabitsWithTodayStatusUseCase
import az.tribe.lifeplanner.usecases.habit.UpdateHabitUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class HabitWithStatus(
    val habit: Habit,
    val isCompletedToday: Boolean
)

class HabitViewModel(
    private val getAllHabitsUseCase: GetAllHabitsUseCase,
    private val createHabitUseCase: CreateHabitUseCase,
    private val updateHabitUseCase: UpdateHabitUseCase,
    private val deleteHabitUseCase: DeleteHabitUseCase,
    private val checkInHabitUseCase: CheckInHabitUseCase,
    private val getHabitsWithTodayStatusUseCase: GetHabitsWithTodayStatusUseCase
) : ViewModel() {

    private val _habits = MutableStateFlow<List<HabitWithStatus>>(emptyList())
    val habits: StateFlow<List<HabitWithStatus>> = _habits.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _showAddHabitDialog = MutableStateFlow(false)
    val showAddHabitDialog: StateFlow<Boolean> = _showAddHabitDialog.asStateFlow()

    init {
        loadHabits()
    }

    fun loadHabits() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val habitsWithStatus = getHabitsWithTodayStatusUseCase()
                _habits.value = habitsWithStatus.map { (habit, isCompleted) ->
                    HabitWithStatus(habit, isCompleted)
                }
            } catch (e: Exception) {
                _error.value = "Failed to load habits: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createHabit(
        title: String,
        description: String,
        category: GoalCategory,
        frequency: HabitFrequency,
        targetCount: Int = 1,
        linkedGoalId: String? = null,
        reminderTime: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val habit = createNewHabit(
                    title = title,
                    description = description,
                    category = category,
                    frequency = frequency,
                    targetCount = targetCount,
                    linkedGoalId = linkedGoalId,
                    reminderTime = reminderTime
                )
                createHabitUseCase(habit)
                loadHabits()
                _showAddHabitDialog.value = false
            } catch (e: Exception) {
                _error.value = "Failed to create habit: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun checkInHabit(habitId: String, notes: String = "") {
        viewModelScope.launch {
            try {
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                checkInHabitUseCase(habitId, today, notes)
                loadHabits()
            } catch (e: Exception) {
                _error.value = "Failed to check in: ${e.message}"
            }
        }
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch {
            try {
                deleteHabitUseCase(habitId)
                loadHabits()
            } catch (e: Exception) {
                _error.value = "Failed to delete habit: ${e.message}"
            }
        }
    }

    fun updateHabit(habit: Habit) {
        viewModelScope.launch {
            try {
                updateHabitUseCase(habit)
                loadHabits()
            } catch (e: Exception) {
                _error.value = "Failed to update habit: ${e.message}"
            }
        }
    }

    fun showAddHabitDialog() {
        _showAddHabitDialog.value = true
    }

    fun hideAddHabitDialog() {
        _showAddHabitDialog.value = false
    }

    fun clearError() {
        _error.value = null
    }

    fun getTodayCompletedCount(): Int {
        return _habits.value.count { it.isCompletedToday }
    }

    fun getTotalHabitsCount(): Int {
        return _habits.value.size
    }

    fun getStreakLeader(): Habit? {
        return _habits.value.maxByOrNull { it.habit.currentStreak }?.habit
    }
}
