package az.tribe.lifeplanner.data.mapper

import az.tribe.lifeplanner.database.ReminderEntity
import az.tribe.lifeplanner.database.ReminderSettingsEntity
import az.tribe.lifeplanner.database.ScheduledNotificationEntity
import az.tribe.lifeplanner.database.UserActivityPatternEntity
import az.tribe.lifeplanner.domain.model.DayOfWeek
import az.tribe.lifeplanner.domain.model.Reminder
import az.tribe.lifeplanner.domain.model.ReminderFrequency
import az.tribe.lifeplanner.domain.model.ReminderSettings
import az.tribe.lifeplanner.domain.model.ReminderType
import az.tribe.lifeplanner.domain.model.ScheduledNotification
import az.tribe.lifeplanner.domain.model.UserActivityPattern
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

fun ReminderEntity.toDomain(): Reminder {
    return Reminder(
        id = id,
        title = title,
        message = message,
        type = ReminderType.valueOf(type),
        frequency = ReminderFrequency.valueOf(frequency),
        scheduledTime = LocalTime.parse(scheduledTime),
        scheduledDays = scheduledDays.split(",")
            .filter { it.isNotBlank() }
            .map { DayOfWeek.valueOf(it) },
        linkedGoalId = linkedGoalId,
        linkedHabitId = linkedHabitId,
        isEnabled = isEnabled == 1L,
        isSmartTiming = isSmartTiming == 1L,
        lastTriggeredAt = lastTriggeredAt?.let { parseLocalDateTime(it) },
        snoozedUntil = snoozedUntil?.let { parseLocalDateTime(it) },
        createdAt = parseLocalDateTime(createdAt),
        updatedAt = updatedAt?.let { parseLocalDateTime(it) }
    )
}

fun List<ReminderEntity>.toDomainReminders(): List<Reminder> = map { it.toDomain() }

fun ReminderSettingsEntity.toDomain(): ReminderSettings {
    return ReminderSettings(
        id = id,
        isEnabled = isEnabled == 1L,
        quietHoursStart = LocalTime.parse(quietHoursStart),
        quietHoursEnd = LocalTime.parse(quietHoursEnd),
        preferredMorningTime = LocalTime.parse(preferredMorningTime),
        preferredEveningTime = LocalTime.parse(preferredEveningTime),
        smartTimingEnabled = smartTimingEnabled == 1L,
        maxRemindersPerDay = maxRemindersPerDay.toInt(),
        weeklyReviewDay = DayOfWeek.valueOf(weeklyReviewDay),
        weeklyReviewTime = LocalTime.parse(weeklyReviewTime)
    )
}

fun UserActivityPatternEntity.toDomain(): UserActivityPattern {
    return UserActivityPattern(
        mostActiveHours = mostActiveHours.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { it.toIntOrNull() },
        mostActiveDays = mostActiveDays.split(",")
            .filter { it.isNotBlank() }
            .map { DayOfWeek.valueOf(it) },
        averageResponseTime = averageResponseTime,
        bestCheckInTimes = bestCheckInTimes.split(",")
            .filter { it.isNotBlank() }
            .map { LocalTime.parse(it) }
    )
}

fun ScheduledNotificationEntity.toDomain(): ScheduledNotification {
    return ScheduledNotification(
        id = id,
        reminderId = reminderId,
        title = title,
        message = message,
        scheduledAt = parseLocalDateTime(scheduledAt),
        isDelivered = isDelivered == 1L,
        deliveredAt = deliveredAt?.let { parseLocalDateTime(it) },
        isSnoozed = isSnoozed == 1L,
        isDismissed = isDismissed == 1L
    )
}

fun List<ScheduledNotificationEntity>.toDomainNotifications(): List<ScheduledNotification> = map { it.toDomain() }

// Helper extension for domain to string conversions
fun List<DayOfWeek>.toStorageString(): String = joinToString(",") { it.name }
fun List<LocalTime>.toTimesStorageString(): String = joinToString(",") { it.toString() }
fun List<Int>.toHoursStorageString(): String = joinToString(",") { it.toString() }
