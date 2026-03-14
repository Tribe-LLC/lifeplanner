package az.tribe.lifeplanner.notification

import az.tribe.lifeplanner.domain.model.DayOfWeek
import az.tribe.lifeplanner.domain.model.Reminder
import az.tribe.lifeplanner.domain.model.ReminderFrequency
import co.touchlab.kermit.Logger
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter
import platform.Foundation.NSCalendar
import platform.Foundation.NSDateComponents

object IOSNotificationScheduler : NotificationSchedulerInterface {

    private const val TAG = "NotificationScheduler"

    override fun schedule(reminder: Reminder) {
        if (!reminder.isEnabled) return

        val center = UNUserNotificationCenter.currentNotificationCenter()

        // Cancel existing notification for this reminder
        center.removePendingNotificationRequestsWithIdentifiers(listOf(reminder.id))

        val content = UNMutableNotificationContent().apply {
            setTitle(reminder.title)
            setBody(reminder.message.ifEmpty { reminder.title })
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound())
        }

        val triggers = createTriggers(reminder)
        if (triggers.isEmpty()) {
            Logger.w(TAG) { "No triggers created for reminder: ${reminder.id}" }
            return
        }

        triggers.forEachIndexed { index, trigger ->
            val requestId = if (index == 0) reminder.id else "${reminder.id}_$index"
            val request = UNNotificationRequest.requestWithIdentifier(
                identifier = requestId,
                content = content,
                trigger = trigger
            )
            center.addNotificationRequest(request) { error ->
                if (error != null) {
                    Logger.e(TAG) { "Failed to schedule notification: ${error.localizedDescription}" }
                } else {
                    Logger.i(TAG) { "Scheduled reminder '${reminder.title}' (trigger $index)" }
                }
            }
        }
    }

    override fun cancel(reminderId: String) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        // Cancel main + any day-specific triggers (up to 7 for weekly)
        val ids = listOf(reminderId) + (0..6).map { "${reminderId}_$it" }
        center.removePendingNotificationRequestsWithIdentifiers(ids)
        Logger.i(TAG) { "Cancelled reminder: $reminderId" }
    }

    override fun cancelAll() {
        UNUserNotificationCenter.currentNotificationCenter().removeAllPendingNotificationRequests()
        Logger.i(TAG) { "Cancelled all reminders" }
    }

    private fun createTriggers(reminder: Reminder): List<UNCalendarNotificationTrigger> {
        return when (reminder.frequency) {
            ReminderFrequency.ONCE -> {
                val components = NSDateComponents().apply {
                    hour = reminder.scheduledTime.hour.toLong()
                    minute = reminder.scheduledTime.minute.toLong()
                }
                listOf(
                    UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                        components, repeats = false
                    )
                )
            }

            ReminderFrequency.DAILY, ReminderFrequency.SMART -> {
                val components = NSDateComponents().apply {
                    hour = reminder.scheduledTime.hour.toLong()
                    minute = reminder.scheduledTime.minute.toLong()
                }
                listOf(
                    UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                        components, repeats = true
                    )
                )
            }

            ReminderFrequency.WEEKDAYS -> {
                // Schedule for Mon-Fri (weekday numbers 2-6 in NSCalendar)
                (2..6).map { weekday ->
                    val components = NSDateComponents().apply {
                        hour = reminder.scheduledTime.hour.toLong()
                        minute = reminder.scheduledTime.minute.toLong()
                        this.weekday = weekday.toLong()
                    }
                    UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                        components, repeats = true
                    )
                }
            }

            ReminderFrequency.WEEKENDS -> {
                // Schedule for Sat-Sun (weekday numbers 1 and 7 in NSCalendar)
                listOf(1, 7).map { weekday ->
                    val components = NSDateComponents().apply {
                        hour = reminder.scheduledTime.hour.toLong()
                        minute = reminder.scheduledTime.minute.toLong()
                        this.weekday = weekday.toLong()
                    }
                    UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                        components, repeats = true
                    )
                }
            }

            ReminderFrequency.WEEKLY -> {
                reminder.scheduledDays.map { day ->
                    val components = NSDateComponents().apply {
                        hour = reminder.scheduledTime.hour.toLong()
                        minute = reminder.scheduledTime.minute.toLong()
                        weekday = day.toNSCalendarWeekday().toLong()
                    }
                    UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                        components, repeats = true
                    )
                }
            }

            ReminderFrequency.MONTHLY -> {
                val now = NSCalendar.currentCalendar.components(
                    platform.Foundation.NSCalendarUnitDay,
                    fromDate = platform.Foundation.NSDate()
                )
                val components = NSDateComponents().apply {
                    hour = reminder.scheduledTime.hour.toLong()
                    minute = reminder.scheduledTime.minute.toLong()
                    day = now.day
                }
                listOf(
                    UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                        components, repeats = true
                    )
                )
            }
        }
    }

    private fun DayOfWeek.toNSCalendarWeekday(): Int = when (this) {
        DayOfWeek.SUNDAY -> 1
        DayOfWeek.MONDAY -> 2
        DayOfWeek.TUESDAY -> 3
        DayOfWeek.WEDNESDAY -> 4
        DayOfWeek.THURSDAY -> 5
        DayOfWeek.FRIDAY -> 6
        DayOfWeek.SATURDAY -> 7
    }
}

actual fun getNotificationScheduler(): NotificationSchedulerInterface = IOSNotificationScheduler
