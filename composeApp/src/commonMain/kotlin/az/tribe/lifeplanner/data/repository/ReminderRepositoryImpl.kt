package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.data.mapper.toDomain
import az.tribe.lifeplanner.data.mapper.toDomainNotifications
import az.tribe.lifeplanner.data.mapper.toDomainReminders
import az.tribe.lifeplanner.data.mapper.toHoursStorageString
import az.tribe.lifeplanner.data.mapper.toStorageString
import az.tribe.lifeplanner.data.mapper.toTimesStorageString
import az.tribe.lifeplanner.domain.model.DayOfWeek
import az.tribe.lifeplanner.domain.model.Reminder
import az.tribe.lifeplanner.domain.model.ReminderFrequency
import az.tribe.lifeplanner.domain.model.ReminderSettings
import az.tribe.lifeplanner.domain.model.ReminderType
import az.tribe.lifeplanner.domain.model.ScheduledNotification
import az.tribe.lifeplanner.domain.model.UserActivityPattern
import az.tribe.lifeplanner.domain.repository.ReminderRepository
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import az.tribe.lifeplanner.notification.NotificationSchedulerInterface
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ReminderRepositoryImpl(
    private val database: SharedDatabase,
    private val syncManager: SyncManager,
    private val notificationScheduler: NotificationSchedulerInterface
) : ReminderRepository {

    override suspend fun createReminder(reminder: Reminder): Reminder {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        database.insertReminder(
            id = reminder.id,
            title = reminder.title,
            message = reminder.message,
            type = reminder.type.name,
            frequency = reminder.frequency.name,
            scheduledTime = reminder.scheduledTime.toString(),
            scheduledDays = reminder.scheduledDays.toStorageString(),
            linkedGoalId = reminder.linkedGoalId,
            linkedHabitId = reminder.linkedHabitId,
            isEnabled = if (reminder.isEnabled) 1L else 0L,
            isSmartTiming = if (reminder.isSmartTiming) 1L else 0L,
            lastTriggeredAt = reminder.lastTriggeredAt?.toString(),
            snoozedUntil = reminder.snoozedUntil?.toString(),
            createdAt = now.toString(),
            updatedAt = null
        )
        syncManager.requestSync()
        notificationScheduler.schedule(reminder)
        return reminder.copy(createdAt = now)
    }

    override suspend fun updateReminder(reminder: Reminder) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        database.updateReminder(
            id = reminder.id,
            title = reminder.title,
            message = reminder.message,
            type = reminder.type.name,
            frequency = reminder.frequency.name,
            scheduledTime = reminder.scheduledTime.toString(),
            scheduledDays = reminder.scheduledDays.toStorageString(),
            linkedGoalId = reminder.linkedGoalId,
            linkedHabitId = reminder.linkedHabitId,
            isEnabled = if (reminder.isEnabled) 1L else 0L,
            isSmartTiming = if (reminder.isSmartTiming) 1L else 0L,
            updatedAt = now.toString()
        )
        syncManager.requestSync()
        if (reminder.isEnabled) {
            notificationScheduler.schedule(reminder)
        } else {
            notificationScheduler.cancel(reminder.id)
        }
    }

    override suspend fun deleteReminder(reminderId: String) {
        notificationScheduler.cancel(reminderId)
        database.deleteScheduledNotificationsByReminder(reminderId)
        database.deleteReminder(reminderId)
        syncManager.requestSync()
    }

    override suspend fun getReminderById(id: String): Reminder? {
        return database.getReminderById(id)?.toDomain()
    }

    override suspend fun getAllReminders(): List<Reminder> {
        return database.getAllReminders().toDomainReminders()
    }

    override suspend fun getEnabledReminders(): List<Reminder> {
        return database.getEnabledReminders().toDomainReminders()
    }

    override suspend fun getRemindersByGoal(goalId: String): List<Reminder> {
        return database.getRemindersByGoalId(goalId).toDomainReminders()
    }

    override suspend fun getRemindersByHabit(habitId: String): List<Reminder> {
        return database.getRemindersByHabitId(habitId).toDomainReminders()
    }

    override suspend fun getRemindersByType(type: ReminderType): List<Reminder> {
        return database.getRemindersByType(type.name).toDomainReminders()
    }

    override suspend fun getUpcomingReminders(limit: Int): List<Reminder> {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return getEnabledReminders()
            .filter { reminder ->
                reminder.snoozedUntil?.let { it > now } != true
            }
            .sortedBy { it.scheduledTime }
            .take(limit)
    }

    override suspend fun getSettings(): ReminderSettings {
        val settings = database.getReminderSettings()
        return settings?.toDomain() ?: createDefaultSettings()
    }

    private suspend fun createDefaultSettings(): ReminderSettings {
        val default = ReminderSettings()
        database.insertReminderSettings(
            id = default.id,
            isEnabled = if (default.isEnabled) 1L else 0L,
            quietHoursStart = default.quietHoursStart.toString(),
            quietHoursEnd = default.quietHoursEnd.toString(),
            preferredMorningTime = default.preferredMorningTime.toString(),
            preferredEveningTime = default.preferredEveningTime.toString(),
            smartTimingEnabled = if (default.smartTimingEnabled) 1L else 0L,
            maxRemindersPerDay = default.maxRemindersPerDay.toLong(),
            weeklyReviewDay = default.weeklyReviewDay.name,
            weeklyReviewTime = default.weeklyReviewTime.toString()
        )
        return default
    }

    override suspend fun updateSettings(settings: ReminderSettings) {
        database.updateReminderSettings(
            isEnabled = if (settings.isEnabled) 1L else 0L,
            quietHoursStart = settings.quietHoursStart.toString(),
            quietHoursEnd = settings.quietHoursEnd.toString(),
            preferredMorningTime = settings.preferredMorningTime.toString(),
            preferredEveningTime = settings.preferredEveningTime.toString(),
            smartTimingEnabled = if (settings.smartTimingEnabled) 1L else 0L,
            maxRemindersPerDay = settings.maxRemindersPerDay.toLong(),
            weeklyReviewDay = settings.weeklyReviewDay.name,
            weeklyReviewTime = settings.weeklyReviewTime.toString()
        )
        syncManager.requestSync()
    }

    override suspend fun getUserActivityPattern(): UserActivityPattern {
        val pattern = database.getUserActivityPattern()
        return pattern?.toDomain() ?: createDefaultPattern()
    }

    private suspend fun createDefaultPattern(): UserActivityPattern {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val default = UserActivityPattern(
            mostActiveHours = listOf(9, 10, 19, 20), // Default active hours
            mostActiveDays = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
            averageResponseTime = 30,
            bestCheckInTimes = listOf(LocalTime(9, 0), LocalTime(20, 0))
        )
        database.insertUserActivityPattern(
            id = "default",
            mostActiveHours = default.mostActiveHours.toHoursStorageString(),
            mostActiveDays = default.mostActiveDays.toStorageString(),
            averageResponseTime = default.averageResponseTime,
            bestCheckInTimes = default.bestCheckInTimes.toTimesStorageString(),
            lastUpdated = now.toString()
        )
        return default
    }

    override suspend fun updateUserActivityPattern(pattern: UserActivityPattern) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        database.updateUserActivityPattern(
            mostActiveHours = pattern.mostActiveHours.toHoursStorageString(),
            mostActiveDays = pattern.mostActiveDays.toStorageString(),
            averageResponseTime = pattern.averageResponseTime,
            bestCheckInTimes = pattern.bestCheckInTimes.toTimesStorageString(),
            lastUpdated = now.toString()
        )
    }

    override suspend fun calculateOptimalTime(reminderType: ReminderType): LocalTime {
        val settings = getSettings()
        val pattern = getUserActivityPattern()

        // If smart timing is disabled, use preferred times
        if (!settings.smartTimingEnabled) {
            return when (reminderType) {
                ReminderType.DAILY_REFLECTION, ReminderType.MOTIVATION -> settings.preferredMorningTime
                ReminderType.WEEKLY_REVIEW -> settings.weeklyReviewTime
                else -> settings.preferredEveningTime
            }
        }

        // Use activity patterns for smart timing
        return when (reminderType) {
            ReminderType.GOAL_CHECK_IN, ReminderType.HABIT_REMINDER -> {
                // Best time for habits/goals - usually early morning or evening
                pattern.bestCheckInTimes.firstOrNull() ?: LocalTime(9, 0)
            }
            ReminderType.MILESTONE_DUE, ReminderType.GOAL_DUE -> {
                // Deadlines - remind during most active hours
                val activeHour = pattern.mostActiveHours.filter { it in 8..20 }.firstOrNull() ?: 9
                LocalTime(activeHour, 0)
            }
            ReminderType.DAILY_REFLECTION -> {
                // Evening reflection
                pattern.bestCheckInTimes.lastOrNull() ?: LocalTime(20, 0)
            }
            ReminderType.WEEKLY_REVIEW -> {
                settings.weeklyReviewTime
            }
            ReminderType.MOTIVATION -> {
                // Morning motivation
                LocalTime(8, 0)
            }
            ReminderType.CUSTOM -> {
                settings.preferredMorningTime
            }
        }
    }

    override suspend fun recordUserActivity(activityTime: LocalDateTime) {
        val pattern = getUserActivityPattern()
        val hour = activityTime.hour
        val dayOfWeek = when (activityTime.dayOfWeek) {
            kotlinx.datetime.DayOfWeek.MONDAY -> DayOfWeek.MONDAY
            kotlinx.datetime.DayOfWeek.TUESDAY -> DayOfWeek.TUESDAY
            kotlinx.datetime.DayOfWeek.WEDNESDAY -> DayOfWeek.WEDNESDAY
            kotlinx.datetime.DayOfWeek.THURSDAY -> DayOfWeek.THURSDAY
            kotlinx.datetime.DayOfWeek.FRIDAY -> DayOfWeek.FRIDAY
            kotlinx.datetime.DayOfWeek.SATURDAY -> DayOfWeek.SATURDAY
            kotlinx.datetime.DayOfWeek.SUNDAY -> DayOfWeek.SUNDAY
            else -> DayOfWeek.MONDAY
        }

        // Update most active hours (keep top 5)
        val updatedHours = (pattern.mostActiveHours + hour)
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }

        // Update most active days
        val updatedDays = (pattern.mostActiveDays + dayOfWeek)
            .distinct()
            .take(5)

        val updatedPattern = pattern.copy(
            mostActiveHours = updatedHours,
            mostActiveDays = updatedDays
        )

        updateUserActivityPattern(updatedPattern)
    }

    override suspend fun scheduleNotification(notification: ScheduledNotification) {
        database.insertScheduledNotification(
            id = notification.id,
            reminderId = notification.reminderId,
            title = notification.title,
            message = notification.message,
            scheduledAt = notification.scheduledAt.toString(),
            isDelivered = if (notification.isDelivered) 1L else 0L,
            deliveredAt = notification.deliveredAt?.toString(),
            isSnoozed = if (notification.isSnoozed) 1L else 0L,
            isDismissed = if (notification.isDismissed) 1L else 0L
        )
        syncManager.requestSync()
    }

    override suspend fun cancelScheduledNotification(notificationId: String) {
        database.deleteScheduledNotification(notificationId)
        syncManager.requestSync()
    }

    override suspend fun getScheduledNotifications(): List<ScheduledNotification> {
        return database.getScheduledNotifications().toDomainNotifications()
    }

    override suspend fun markNotificationDelivered(notificationId: String) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        database.markNotificationDelivered(notificationId, now.toString())
        syncManager.requestSync()
    }

    override suspend fun snoozeReminder(reminderId: String, snoozeUntil: LocalDateTime) {
        database.snoozeReminder(reminderId, snoozeUntil.toString())
        syncManager.requestSync()
    }

    override suspend fun enableAllReminders() {
        database.enableAllReminders()
        syncManager.requestSync()
        // Reschedule all reminders as OS notifications
        getAllReminders().forEach { notificationScheduler.schedule(it) }
    }

    override suspend fun disableAllReminders() {
        // Cancel all OS notifications first
        getAllReminders().forEach { notificationScheduler.cancel(it.id) }
        database.disableAllReminders()
        syncManager.requestSync()
    }

    override suspend fun rescheduleAllReminders() {
        val reminders = getEnabledReminders()
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        reminders.forEach { reminder ->
            // Clear existing scheduled notifications
            database.deleteScheduledNotificationsByReminder(reminder.id)

            // Schedule new notification based on frequency
            val nextTriggerTime = calculateNextTriggerTime(reminder, now)
            if (nextTriggerTime != null) {
                val notification = ScheduledNotification(
                    id = Uuid.random().toString(),
                    reminderId = reminder.id,
                    title = reminder.title,
                    message = reminder.message,
                    scheduledAt = nextTriggerTime
                )
                scheduleNotification(notification)
            }
        }
    }

    private fun calculateNextTriggerTime(reminder: Reminder, now: LocalDateTime): LocalDateTime? {
        val today = now.date
        val scheduledTime = reminder.scheduledTime

        return when (reminder.frequency) {
            ReminderFrequency.ONCE -> {
                // One-time reminder - only if in the future
                val scheduledDateTime = LocalDateTime(today, scheduledTime)
                if (scheduledDateTime > now) scheduledDateTime else null
            }
            ReminderFrequency.DAILY -> {
                // Daily - next occurrence
                val todayScheduled = LocalDateTime(today, scheduledTime)
                if (todayScheduled > now) todayScheduled
                else LocalDateTime(today.plus(1, DateTimeUnit.DAY), scheduledTime)
            }
            ReminderFrequency.WEEKDAYS -> {
                // Weekdays only
                var nextDate = if (LocalDateTime(today, scheduledTime) > now) today
                    else today.plus(1, DateTimeUnit.DAY)
                while (nextDate.dayOfWeek == kotlinx.datetime.DayOfWeek.SATURDAY ||
                       nextDate.dayOfWeek == kotlinx.datetime.DayOfWeek.SUNDAY) {
                    nextDate = nextDate.plus(1, DateTimeUnit.DAY)
                }
                LocalDateTime(nextDate, scheduledTime)
            }
            ReminderFrequency.WEEKENDS -> {
                // Weekends only
                var nextDate = if (LocalDateTime(today, scheduledTime) > now) today
                    else today.plus(1, DateTimeUnit.DAY)
                while (nextDate.dayOfWeek != kotlinx.datetime.DayOfWeek.SATURDAY &&
                       nextDate.dayOfWeek != kotlinx.datetime.DayOfWeek.SUNDAY) {
                    nextDate = nextDate.plus(1, DateTimeUnit.DAY)
                }
                LocalDateTime(nextDate, scheduledTime)
            }
            ReminderFrequency.WEEKLY -> {
                // Specific days of the week
                if (reminder.scheduledDays.isEmpty()) return null
                var nextDate = if (LocalDateTime(today, scheduledTime) > now) today
                    else today.plus(1, DateTimeUnit.DAY)
                val targetDays = reminder.scheduledDays.map {
                    when (it) {
                        DayOfWeek.MONDAY -> kotlinx.datetime.DayOfWeek.MONDAY
                        DayOfWeek.TUESDAY -> kotlinx.datetime.DayOfWeek.TUESDAY
                        DayOfWeek.WEDNESDAY -> kotlinx.datetime.DayOfWeek.WEDNESDAY
                        DayOfWeek.THURSDAY -> kotlinx.datetime.DayOfWeek.THURSDAY
                        DayOfWeek.FRIDAY -> kotlinx.datetime.DayOfWeek.FRIDAY
                        DayOfWeek.SATURDAY -> kotlinx.datetime.DayOfWeek.SATURDAY
                        DayOfWeek.SUNDAY -> kotlinx.datetime.DayOfWeek.SUNDAY
                    }
                }
                repeat(7) {
                    if (nextDate.dayOfWeek in targetDays) {
                        return LocalDateTime(nextDate, scheduledTime)
                    }
                    nextDate = nextDate.plus(1, DateTimeUnit.DAY)
                }
                null
            }
            ReminderFrequency.MONTHLY -> {
                // Monthly - same day next month
                val todayScheduled = LocalDateTime(today, scheduledTime)
                if (todayScheduled > now) todayScheduled
                else LocalDateTime(today.plus(1, DateTimeUnit.MONTH), scheduledTime)
            }
            ReminderFrequency.SMART -> {
                // Smart timing - use optimal time calculation
                val todayScheduled = LocalDateTime(today, scheduledTime)
                if (todayScheduled > now) todayScheduled
                else LocalDateTime(today.plus(1, DateTimeUnit.DAY), scheduledTime)
            }
        }
    }
}
