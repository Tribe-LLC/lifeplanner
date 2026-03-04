package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.data.mapper.createNewCheckIn
import az.tribe.lifeplanner.data.mapper.toDomain
import az.tribe.lifeplanner.data.mapper.toDomainCheckIns
import az.tribe.lifeplanner.data.mapper.toDomainHabits
import az.tribe.lifeplanner.data.mapper.toEntity
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.HabitCheckIn
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import az.tribe.lifeplanner.widget.WidgetDataSyncService
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

class HabitRepositoryImpl(
    private val database: SharedDatabase,
    private val widgetSyncService: WidgetDataSyncService,
    private val syncManager: SyncManager
) : HabitRepository {

    private suspend fun notifyWidgets() {
        try {
            widgetSyncService.refreshWidgets()
        } catch (e: Exception) {
            Logger.w("HabitRepositoryImpl") { "Widget refresh failed: ${e.message}" }
        }
    }

    override fun observeHabitsWithTodayStatus(): Flow<List<Pair<Habit, Boolean>>> {
        return combine(
            database.observeAllHabits(),
            database.observeCheckInsByDate(
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            )
        ) { habitEntities, checkInEntities ->
            // Recalculate today on each emission so it stays fresh across midnight
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            habitEntities.toDomainHabits().map { habit ->
                val isCheckedIn = checkInEntities.any {
                    it.habitId == habit.id && it.completed == 1L && it.date == today
                }
                habit to isCheckedIn
            }
        }
    }

    override suspend fun getAllHabits(): List<Habit> {
        return database.getAllHabits().toDomainHabits()
    }

    override suspend fun getHabitById(id: String): Habit? {
        return database.getHabitById(id)?.toDomain()
    }

    override suspend fun getHabitsByCategory(category: GoalCategory): List<Habit> {
        return database.getHabitsByCategory(category.name).toDomainHabits()
    }

    override suspend fun getHabitsByGoalId(goalId: String): List<Habit> {
        return database.getHabitsByGoalId(goalId).toDomainHabits()
    }

    override suspend fun insertHabit(habit: Habit) {
        database.insertHabit(habit.toEntity())
        syncManager.requestSync()
    }

    override suspend fun updateHabit(habit: Habit) {
        database.updateHabit(
            id = habit.id,
            title = habit.title,
            description = habit.description,
            category = habit.category.name,
            frequency = habit.frequency.name,
            targetCount = habit.targetCount.toLong(),
            linkedGoalId = habit.linkedGoalId,
            reminderTime = habit.reminderTime
        )
        syncManager.requestSync()
    }

    override suspend fun deleteHabit(id: String) {
        database.deleteHabit(id)
        syncManager.requestSync()
    }

    override suspend fun deactivateHabit(id: String) {
        database.deactivateHabit(id)
        syncManager.requestSync()
    }

    override suspend fun checkIn(habitId: String, date: LocalDate, notes: String): HabitCheckIn {
        val checkIn = createNewCheckIn(
            habitId = habitId,
            date = date,
            completed = true,
            notes = notes
        )
        // INSERT OR IGNORE: idempotent — won't duplicate if (habitId, date) already exists
        database.insertHabitCheckInOrIgnore(checkIn.toEntity())
        updateStreakAfterCheckIn(habitId)
        notifyWidgets()
        syncManager.requestSync()
        return getCheckInByHabitAndDate(habitId, date) ?: checkIn
    }

    override suspend fun getCheckInsByHabitId(habitId: String): List<HabitCheckIn> {
        return database.getCheckInsByHabitId(habitId).toDomainCheckIns()
    }

    override suspend fun getCheckInsByDate(date: LocalDate): List<HabitCheckIn> {
        return database.getCheckInsByDate(date.toString()).toDomainCheckIns()
    }

    override suspend fun getCheckInByHabitAndDate(habitId: String, date: LocalDate): HabitCheckIn? {
        return database.getCheckInByHabitAndDate(habitId, date.toString())?.toDomain()
    }

    override suspend fun getCheckInsInRange(
        habitId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<HabitCheckIn> {
        return database.getCheckInsInRange(habitId, startDate.toString(), endDate.toString())
            .toDomainCheckIns()
    }

    override suspend fun deleteCheckIn(id: String) {
        database.deleteCheckIn(id)
        notifyWidgets()
        syncManager.requestSync()
    }

    override suspend fun calculateStreak(habitId: String): Int {
        val habit = getHabitById(habitId) ?: return 0
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        var streak = 0
        var currentDate = today

        while (true) {
            val checkIn = getCheckInByHabitAndDate(habitId, currentDate)
            if (checkIn != null && checkIn.completed) {
                streak++
                currentDate = currentDate.minus(DatePeriod(days = 1))
            } else {
                break
            }
        }

        return streak
    }

    override suspend fun updateStreakAfterCheckIn(habitId: String) {
        val habit = getHabitById(habitId) ?: return
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        val currentStreak = calculateStreak(habitId)
        val longestStreak = maxOf(habit.longestStreak, currentStreak)
        val totalCompletions = habit.totalCompletions + 1

        database.updateHabitStreak(
            id = habitId,
            currentStreak = currentStreak.toLong(),
            longestStreak = longestStreak.toLong(),
            totalCompletions = totalCompletions.toLong(),
            lastCompletedDate = today.toString()
        )
        syncManager.requestSync()
    }

    override suspend fun getHabitsWithTodayStatus(today: LocalDate): List<Pair<Habit, Boolean>> {
        // Clean up any duplicate check-ins first
        database.deleteDuplicateCheckIns()

        val habits = getAllHabits()
        return habits.map { habit ->
            val checkIn = getCheckInByHabitAndDate(habit.id, today)
            habit to (checkIn?.completed == true)
        }
    }

    override suspend fun getHabitCompletionRate(habitId: String, days: Int): Float {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val startDate = today.minus(DatePeriod(days = days))

        val checkIns = getCheckInsInRange(habitId, startDate, today)
        val completedDays = checkIns.count { it.completed }

        return if (days > 0) (completedDays.toFloat() / days) * 100f else 0f
    }

    override suspend fun invalidateCache() {
        database.invalidateHabitCache()
    }
}
