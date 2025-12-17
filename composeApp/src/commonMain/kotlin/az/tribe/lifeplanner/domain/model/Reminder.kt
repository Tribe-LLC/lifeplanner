package az.tribe.lifeplanner.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

data class Reminder(
    val id: String,
    val title: String,
    val message: String,
    val type: ReminderType,
    val frequency: ReminderFrequency,
    val scheduledTime: LocalTime,
    val scheduledDays: List<DayOfWeek> = emptyList(), // For weekly reminders
    val linkedGoalId: String? = null,
    val linkedHabitId: String? = null,
    val isEnabled: Boolean = true,
    val isSmartTiming: Boolean = false, // Use optimal timing based on user activity
    val lastTriggeredAt: LocalDateTime? = null,
    val snoozedUntil: LocalDateTime? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime? = null
)

enum class ReminderType {
    GOAL_CHECK_IN,      // Check in on goal progress
    HABIT_REMINDER,     // Reminder to complete habit
    MILESTONE_DUE,      // Upcoming milestone deadline
    GOAL_DUE,           // Upcoming goal deadline
    DAILY_REFLECTION,   // Daily journal/reflection prompt
    WEEKLY_REVIEW,      // Weekly review reminder
    MOTIVATION,         // Motivational reminder
    CUSTOM              // User-defined reminder
}

enum class ReminderFrequency {
    ONCE,           // One-time reminder
    DAILY,          // Every day
    WEEKDAYS,       // Monday to Friday
    WEEKENDS,       // Saturday and Sunday
    WEEKLY,         // Specific days of the week
    MONTHLY,        // Monthly on a specific day
    SMART           // AI-determined optimal frequency
}

enum class DayOfWeek {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
}

data class ReminderSettings(
    val id: String = "default",
    val isEnabled: Boolean = true,
    val quietHoursStart: LocalTime = LocalTime(22, 0), // 10 PM
    val quietHoursEnd: LocalTime = LocalTime(7, 0),    // 7 AM
    val preferredMorningTime: LocalTime = LocalTime(8, 0),
    val preferredEveningTime: LocalTime = LocalTime(20, 0),
    val smartTimingEnabled: Boolean = true,
    val maxRemindersPerDay: Int = 5,
    val weeklyReviewDay: DayOfWeek = DayOfWeek.SUNDAY,
    val weeklyReviewTime: LocalTime = LocalTime(10, 0)
)

data class UserActivityPattern(
    val mostActiveHours: List<Int> = emptyList(), // Hours of the day (0-23)
    val mostActiveDays: List<DayOfWeek> = emptyList(),
    val averageResponseTime: Long = 0, // In minutes
    val bestCheckInTimes: List<LocalTime> = emptyList()
)

data class ScheduledNotification(
    val id: String,
    val reminderId: String,
    val title: String,
    val message: String,
    val scheduledAt: LocalDateTime,
    val isDelivered: Boolean = false,
    val deliveredAt: LocalDateTime? = null,
    val isSnoozed: Boolean = false,
    val isDismissed: Boolean = false
)
