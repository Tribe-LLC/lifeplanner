package az.tribe.lifeplanner.notification

import az.tribe.lifeplanner.domain.model.Reminder

interface NotificationSchedulerInterface {
    fun schedule(reminder: Reminder)
    fun cancel(reminderId: String)
    fun cancelAll()
}

expect fun getNotificationScheduler(): NotificationSchedulerInterface
