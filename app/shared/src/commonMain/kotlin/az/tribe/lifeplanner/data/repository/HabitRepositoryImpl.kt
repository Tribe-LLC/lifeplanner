package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.domain.service.StreakPauses
import az.tribe.lifeplanner.domain.service.TravelMode
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
import az.tribe.lifeplanner.infrastructure.*
import az.tribe.lifeplanner.widget.WidgetDataSyncService
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

class HabitRepositoryImpl(
    private val database: SharedDatabase,
    private val widgetSyncService: WidgetDataSyncService,
    private val syncManager: SyncManager,
    private val pauses: StreakPauses = StreakPauses.None,
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
        notifyWidgets()
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
            reminderTime = habit.reminderTime,
            type = habit.type.name,
            unit = habit.unit,
            healthMetricType = habit.healthMetricType?.name,
            healthTarget = habit.healthTarget,
            completionSource = habit.completionSource.name
        )
        notifyWidgets()
        syncManager.requestSync()
    }

    override suspend fun deleteHabit(id: String) {
        database.deleteHabit(id)
        notifyWidgets()
        syncManager.requestSync()
    }

    override suspend fun deactivateHabit(id: String) {
        database.deactivateHabit(id)
        notifyWidgets()
        syncManager.requestSync()
    }

    override suspend fun incrementCount(habitId: String, date: LocalDate): HabitCheckIn =
        addCount(habitId, date, delta = 1)

    override suspend fun addCount(habitId: String, date: LocalDate, delta: Int): HabitCheckIn {
        val habit = getHabitById(habitId)
        val existing = getCheckInByHabitAndDate(habitId, date)
        val newCount = ((existing?.count ?: 0) + delta).coerceAtLeast(0)
        val completed = habit != null && newCount >= habit.targetCount

        if (existing == null) {
            // A soft-deleted row (from an undo) still occupies the UNIQUE (habitId, date)
            // slot and would silently swallow INSERT OR IGNORE; restore it instead.
            val softDeleted = database.getSoftDeletedCheckIn(habitId, date.toString())
            if (softDeleted != null) {
                database.restoreHabitCheckIn(softDeleted.id)
                database.updateHabitCheckInCount(
                    habitId = habitId,
                    date = date.toString(),
                    count = newCount.toLong(),
                    completed = if (completed) 1L else 0L
                )
            } else {
                val checkIn = createNewCheckIn(
                    habitId = habitId,
                    date = date,
                    completed = completed,
                    count = newCount
                )
                database.insertHabitCheckInOrIgnore(checkIn.toEntity())
            }
        } else {
            database.updateHabitCheckInCount(
                habitId = habitId,
                date = date.toString(),
                count = newCount.toLong(),
                completed = if (completed) 1L else 0L
            )
        }
        if (completed) {
            updateStreakAfterCheckIn(habitId)
        }
        notifyWidgets()
        syncManager.requestSync()
        return getCheckInByHabitAndDate(habitId, date)
            ?: createNewCheckIn(habitId = habitId, date = date, completed = completed, count = newCount)
    }

    override suspend fun checkIn(habitId: String, date: LocalDate, notes: String): HabitCheckIn {
        // A UNIQUE INDEX on (habitId, date) means INSERT OR IGNORE silently fails when a
        // soft-deleted row already occupies that slot (e.g. after undo). Restore the existing
        // row instead of inserting a new one, so the unique constraint is never violated.
        val softDeleted = database.getSoftDeletedCheckIn(habitId, date.toString())
        if (softDeleted != null) {
            database.restoreHabitCheckIn(softDeleted.id)
        } else {
            val checkIn = createNewCheckIn(
                habitId = habitId,
                date = date,
                completed = true,
                notes = notes
            )
            // INSERT OR IGNORE: idempotent, won't duplicate if (habitId, date) already exists
            database.insertHabitCheckInOrIgnore(checkIn.toEntity())
        }
        updateStreakAfterCheckIn(habitId)
        notifyWidgets()
        syncManager.requestSync()
        return getCheckInByHabitAndDate(habitId, date)
            ?: createNewCheckIn(habitId = habitId, date = date, completed = true, notes = notes)
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

    override suspend fun getAllCheckInsInRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<HabitCheckIn> {
        return database.getAllCheckInsInRange(startDate.toString(), endDate.toString())
            .toDomainCheckIns()
    }

    override suspend fun deleteCheckIn(id: String) {
        database.deleteCheckIn(id)
        notifyWidgets()
        syncManager.requestSync()
    }

    override suspend fun calculateStreak(habitId: String): Int {
        getHabitById(habitId) ?: return 0
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        // Single query: fetch all completed check-in dates in descending order
        val completedDates = database.getCompletedCheckInDatesDesc(habitId)
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()

        // Days on a trip with travel mode on neither keep nor break a streak.
        val paused = runCatching { pauses.pausedDays() }.getOrDefault(emptySet())
        return TravelMode.streak(completedDates, paused, today)
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

        // Batch: fetch all habits + today's check-ins in 2 queries instead of 1+N
        val habits = getAllHabits()
        val todayCheckIns = getCheckInsByDate(today)
        val completedHabitIds = todayCheckIns
            .filter { it.completed }
            .map { it.habitId }
            .toSet()

        return habits.map { habit ->
            habit to (habit.id in completedHabitIds)
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
