package az.tribe.lifeplanner.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.mapper.createNewHabit
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.HabitFrequency
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.XpRewards
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.usecases.habit.CheckInHabitUseCase
import az.tribe.lifeplanner.usecases.habit.CreateHabitUseCase
import az.tribe.lifeplanner.usecases.habit.DeleteHabitUseCase
import az.tribe.lifeplanner.usecases.habit.UncheckHabitUseCase
import az.tribe.lifeplanner.usecases.habit.UpdateHabitUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class HabitWithStatus(
    val habit: Habit,
    val isCompletedToday: Boolean
)

/**
 * Represents a habit that was just checked in, for showing reflection prompt
 */
data class RecentCheckIn(
    val habit: Habit,
    val timestamp: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
)

class HabitViewModel(
    private val habitRepository: HabitRepository,
    private val createHabitUseCase: CreateHabitUseCase,
    private val updateHabitUseCase: UpdateHabitUseCase,
    private val deleteHabitUseCase: DeleteHabitUseCase,
    private val checkInHabitUseCase: CheckInHabitUseCase,
    private val uncheckHabitUseCase: UncheckHabitUseCase,
    private val gamificationRepository: GamificationRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val habits: StateFlow<List<HabitWithStatus>> = habitRepository.observeHabitsWithTodayStatus()
        .map { pairs ->
            pairs.map { (habit, isCompleted) -> HabitWithStatus(habit, isCompleted) }
                .sortedWith(compareBy<HabitWithStatus> { it.isCompletedToday }.thenByDescending { it.habit.currentStreak })
        }
        .onEach { _isLoading.value = false }
        .catch { e ->
            _error.value = "Failed to load habits: ${e.message}"
            _isLoading.value = false
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showAddHabitDialog = MutableStateFlow(false)
    val showAddHabitDialog: StateFlow<Boolean> = _showAddHabitDialog.asStateFlow()

    // Track recently checked-in habit for reflection prompt
    private val _recentCheckIn = MutableStateFlow<RecentCheckIn?>(null)
    val recentCheckIn: StateFlow<RecentCheckIn?> = _recentCheckIn.asStateFlow()

    @Deprecated("No-op: data flows reactively via SQLDelight Flows", level = DeprecationLevel.WARNING)
    fun loadHabits() { }

    private var isCreatingHabit = false

    fun createHabit(
        title: String,
        description: String,
        category: GoalCategory,
        frequency: HabitFrequency,
        targetCount: Int = 1,
        linkedGoalId: String? = null,
        reminderTime: String? = null
    ) {
        if (isCreatingHabit) return

        // Prevent duplicate: skip if a habit with the same title already exists
        if (habits.value.any { it.habit.title.equals(title, ignoreCase = true) }) return

        viewModelScope.launch {
            isCreatingHabit = true
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
                _showAddHabitDialog.value = false
            } catch (e: Exception) {
                _error.value = "Failed to create habit: ${e.message}"
            } finally {
                isCreatingHabit = false
            }
        }
    }

    fun checkInHabit(habitId: String, notes: String = "") {
        viewModelScope.launch {
            try {
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                checkInHabitUseCase(habitId, today, notes)

                // Read updated habit directly from repository for accurate streak data
                val updatedHabit = habitRepository.getHabitById(habitId)
                val streakDays = updatedHabit?.currentStreak ?: 0

                // Update gamification: XP and challenges
                val xp = XpRewards.HABIT_CHECK_IN + (XpRewards.HABIT_STREAK_BONUS * streakDays)
                gamificationRepository.incrementHabitsCompleted()
                gamificationRepository.addXp(xp)
                gamificationRepository.onHabitCheckedIn()
                gamificationRepository.checkAndAwardBadges()

                // Emit recent check-in for reflection prompt
                updatedHabit?.let {
                    _recentCheckIn.value = RecentCheckIn(it)
                }
            } catch (e: Exception) {
                _error.value = "Failed to check in: ${e.message}"
            }
        }
    }

    fun clearRecentCheckIn() {
        _recentCheckIn.value = null
    }

    fun uncheckInHabit(habitId: String) {
        viewModelScope.launch {
            try {
                // Read habit before uncheck to get streak for XP calculation
                val habit = habitRepository.getHabitById(habitId)
                val streakDays = habit?.currentStreak ?: 0

                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                val wasUnchecked = uncheckHabitUseCase(habitId, today)

                if (wasUnchecked) {
                    // Deduct the same XP that was awarded on check-in
                    val xp = XpRewards.HABIT_CHECK_IN + (XpRewards.HABIT_STREAK_BONUS * streakDays)
                    gamificationRepository.decrementHabitsCompleted()
                    gamificationRepository.deductXp(xp)
                }
            } catch (e: Exception) {
                _error.value = "Failed to uncheck: ${e.message}"
            }
        }
    }

    fun toggleCheckIn(habitId: String) {
        val habitWithStatus = habits.value.find { it.habit.id == habitId }
        if (habitWithStatus != null) {
            if (habitWithStatus.isCompletedToday) {
                uncheckInHabit(habitId)
            } else {
                checkInHabit(habitId)
            }
        }
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch {
            try {
                deleteHabitUseCase(habitId)
            } catch (e: Exception) {
                _error.value = "Failed to delete habit: ${e.message}"
            }
        }
    }

    fun updateHabit(habit: Habit) {
        viewModelScope.launch {
            try {
                updateHabitUseCase(habit)
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
        return habits.value.count { it.isCompletedToday }
    }

    fun getTotalHabitsCount(): Int {
        return habits.value.size
    }

    fun getStreakLeader(): Habit? {
        return habits.value.maxByOrNull { it.habit.currentStreak }?.habit
    }

}
