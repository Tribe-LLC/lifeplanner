package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.Reminder
import az.tribe.lifeplanner.domain.model.ReminderSettings
import az.tribe.lifeplanner.domain.model.ScheduledNotification
import az.tribe.lifeplanner.domain.model.UserActivityPattern
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

interface ReminderRepository {
    // CRUD Operations
    suspend fun createReminder(reminder: Reminder): Reminder
    suspend fun updateReminder(reminder: Reminder)
    suspend fun deleteReminder(reminderId: String)
    suspend fun getReminderById(id: String): Reminder?
    suspend fun getAllReminders(): List<Reminder>
    suspend fun getEnabledReminders(): List<Reminder>

    // Filtered queries
    suspend fun getRemindersByGoal(goalId: String): List<Reminder>
    suspend fun getRemindersByHabit(habitId: String): List<Reminder>
    suspend fun getRemindersByType(type: az.tribe.lifeplanner.domain.model.ReminderType): List<Reminder>
    suspend fun getUpcomingReminders(limit: Int = 10): List<Reminder>

    // Settings
    suspend fun getSettings(): ReminderSettings
    suspend fun updateSettings(settings: ReminderSettings)

    // Smart Timing
    suspend fun getUserActivityPattern(): UserActivityPattern
    suspend fun updateUserActivityPattern(pattern: UserActivityPattern)
    suspend fun calculateOptimalTime(reminderType: az.tribe.lifeplanner.domain.model.ReminderType): LocalTime
    suspend fun recordUserActivity(activityTime: LocalDateTime)
    suspend fun findAvailableTimeSlot(preferredTime: LocalTime, excludeReminderId: String? = null): LocalTime

    // Scheduled Notifications
    suspend fun scheduleNotification(notification: ScheduledNotification)
    suspend fun cancelScheduledNotification(notificationId: String)
    suspend fun getScheduledNotifications(): List<ScheduledNotification>
    suspend fun markNotificationDelivered(notificationId: String)
    suspend fun snoozeReminder(reminderId: String, snoozeUntil: LocalDateTime)

    // Bulk operations
    suspend fun enableAllReminders()
    suspend fun disableAllReminders()
    suspend fun rescheduleAllReminders()
}
