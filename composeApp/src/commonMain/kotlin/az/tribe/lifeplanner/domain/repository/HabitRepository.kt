package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.HabitCheckIn
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface HabitRepository {
    fun observeHabitsWithTodayStatus(): Flow<List<Pair<Habit, Boolean>>>
    suspend fun getAllHabits(): List<Habit>
    suspend fun getHabitById(id: String): Habit?
    suspend fun getHabitsByCategory(category: GoalCategory): List<Habit>
    suspend fun getHabitsByGoalId(goalId: String): List<Habit>

    suspend fun insertHabit(habit: Habit)
    suspend fun updateHabit(habit: Habit)
    suspend fun deleteHabit(id: String)
    suspend fun deactivateHabit(id: String)

    // Check-in operations
    suspend fun checkIn(habitId: String, date: LocalDate, notes: String = ""): HabitCheckIn
    suspend fun getCheckInsByHabitId(habitId: String): List<HabitCheckIn>
    suspend fun getCheckInsByDate(date: LocalDate): List<HabitCheckIn>
    suspend fun getCheckInByHabitAndDate(habitId: String, date: LocalDate): HabitCheckIn?
    suspend fun getCheckInsInRange(habitId: String, startDate: LocalDate, endDate: LocalDate): List<HabitCheckIn>
    suspend fun deleteCheckIn(id: String)

    // Streak calculations
    suspend fun calculateStreak(habitId: String): Int
    suspend fun updateStreakAfterCheckIn(habitId: String)

    // Analytics
    suspend fun getHabitsWithTodayStatus(today: LocalDate): List<Pair<Habit, Boolean>>
    suspend fun getHabitCompletionRate(habitId: String, days: Int = 30): Float

    // Cache invalidation (for external DB writers like Glance widgets)
    suspend fun invalidateCache()
}
