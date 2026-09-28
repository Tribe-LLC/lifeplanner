package az.tribe.lifeplanner.notification

import az.tribe.lifeplanner.data.habits.Nudge
import co.touchlab.kermit.Logger
import kotlinx.datetime.number
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

actual object NudgeAlarms {
    actual fun schedule(nudge: Nudge) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(nudge.id))
        val content = UNMutableNotificationContent().apply {
            setTitle(nudge.title)
            setBody(nudge.body)
            setSound(UNNotificationSound.defaultSound())
            setUserInfo(mapOf("open" to nudge.open))
            // The daily mood reminder answers Low / Okay / Good from the banner.
            if (nudge.open == az.tribe.lifeplanner.data.habits.NudgePlan.MOOD) setCategoryIdentifier(HabitNotificationActions.MOOD_CATEGORY)
        }
        val at = nudge.at
        val parts = NSDateComponents().apply {
            year = at.year.toLong()
            month = at.month.number.toLong()
            day = at.day.toLong()
            hour = at.hour.toLong()
            minute = at.minute.toLong()
        }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(parts, repeats = false)
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(nudge.id, content, trigger)) { error ->
            if (error != null) Logger.e("NudgeAlarms") { "Could not plan ${nudge.id}: ${error.localizedDescription}" }
        }
    }

    actual fun cancel(id: String) {
        UNUserNotificationCenter.currentNotificationCenter().removePendingNotificationRequestsWithIdentifiers(listOf(id))
    }
}
