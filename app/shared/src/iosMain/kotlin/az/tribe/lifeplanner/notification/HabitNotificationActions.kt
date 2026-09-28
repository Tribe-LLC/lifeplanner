@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package az.tribe.lifeplanner.notification

import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.habits.HabitService
import az.tribe.lifeplanner.data.mind.MindService
import az.tribe.lifeplanner.data.mind.MoodNudges
import co.touchlab.kermit.Logger
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import platform.Foundation.NSSelectorFromString
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionNone
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * Done / Not today / In 1 hour on iOS habit reminders, the same three buttons as Android. KMPNotifier
 * owns the notification delegate, so this wraps it: the three habit buttons are handled here and
 * everything else (taps, deep links, foreground banners) goes on to KMPNotifier unchanged.
 * The daily mood reminder gets Low / Okay / Good here too, as on Android.
 */
object HabitNotificationActions {
    const val CATEGORY = "HABIT_REMINDER"
    const val HABIT_ID = "habit_id"
    private const val DONE = "habit_done"
    private const val SKIP = "habit_skip"
    private const val LATER = "habit_later"
    private val ours = setOf(DONE, SKIP, LATER)

    const val MOOD_CATEGORY = "MOOD_REMINDER"
    /** Low, Okay and Good on the same 1 to 5 scale as the faces on Sleep and mind. */
    private val moods = mapOf("mood_low" to 2, "mood_okay" to 3, "mood_good" to 4)

    /** The center holds its delegate weakly, so keep ours alive here. */
    private var delegate: Wrapper? = null

    /** Call after NotifierManager.initialize, which sets the delegate being wrapped. */
    fun install() {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val category = UNNotificationCategory.categoryWithIdentifier(
            CATEGORY,
            listOf(
                UNNotificationAction.actionWithIdentifier(DONE, "Done", UNNotificationActionOptionNone),
                UNNotificationAction.actionWithIdentifier(SKIP, "Not today", UNNotificationActionOptionNone),
                UNNotificationAction.actionWithIdentifier(LATER, "In 1 hour", UNNotificationActionOptionNone),
            ),
            emptyList<String>(),
            UNNotificationCategoryOptionNone,
        )
        val mood = UNNotificationCategory.categoryWithIdentifier(
            MOOD_CATEGORY,
            listOf(
                UNNotificationAction.actionWithIdentifier("mood_low", "Low", UNNotificationActionOptionNone),
                UNNotificationAction.actionWithIdentifier("mood_okay", "Okay", UNNotificationActionOptionNone),
                UNNotificationAction.actionWithIdentifier("mood_good", "Good", UNNotificationActionOptionNone),
            ),
            emptyList<String>(),
            UNNotificationCategoryOptionNone,
        )
        center.getNotificationCategoriesWithCompletionHandler { existing ->
            val keep = existing.orEmpty().filterIsInstance<UNNotificationCategory>()
                .filter { it.identifier != CATEGORY && it.identifier != MOOD_CATEGORY }
            center.setNotificationCategories((keep + category + mood).toSet())
        }
        if (center.delegate is Wrapper) return
        val w = Wrapper(center.delegate)
        delegate = w
        center.delegate = w
    }

    private fun answerMood(score: Int, done: () -> Unit) {
        MainScope().launch {
            try {
                val koin = KoinPlatform.getKoinOrNull()
                val mind = koin?.getOrNull<MindService>()
                if (mind != null) {
                    mind.sendToHealth(mind.checkIn(score))
                    PostHogAnalytics.capture("v4_mind_reminder_answered", mapOf("score" to score))
                    // Keeps the week ahead planned for someone who only ever answers from here.
                    koin.getOrNull<MoodNudges>()?.replan()
                }
            } catch (e: Exception) {
                Logger.w("HabitNotificationActions") { "mood $score failed: ${e.message}" }
            } finally {
                done()
            }
        }
    }

    private fun handle(action: String, habitId: String, response: UNNotificationResponse, done: () -> Unit) {
        if (action == LATER) {
            val old = response.notification.request
            val content = UNMutableNotificationContent().apply {
                setTitle(old.content.title)
                setBody(old.content.body)
                setSound(old.content.sound)
                setUserInfo(old.content.userInfo)
                setCategoryIdentifier(CATEGORY)
            }
            val again = UNNotificationRequest.requestWithIdentifier(
                "${old.identifier.removeSuffix("_later")}_later", content,
                UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(3600.0, repeats = false),
            )
            UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(again, null)
            PostHogAnalytics.capture("v4_reminder_action", mapOf("action" to "later"))
            done()
            return
        }
        MainScope().launch {
            try {
                val service = KoinPlatform.getKoinOrNull()?.getOrNull<HabitService>()
                if (action == DONE) service?.tickToday(habitId) else service?.skipToday(habitId)
                PostHogAnalytics.capture("v4_reminder_action", mapOf("action" to if (action == DONE) "done" else "skip"))
            } catch (e: Exception) {
                Logger.w("HabitNotificationActions") { "$action failed: ${e.message}" }
            } finally {
                done()
            }
        }
    }

    private class Wrapper(private val next: UNUserNotificationCenterDelegateProtocol?) : NSObject(), UNUserNotificationCenterDelegateProtocol {

        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            didReceiveNotificationResponse: UNNotificationResponse,
            withCompletionHandler: () -> Unit,
        ) {
            val action = didReceiveNotificationResponse.actionIdentifier
            val habitId = didReceiveNotificationResponse.notification.request.content.userInfo[HABIT_ID] as? String
            if (habitId != null && action in ours) {
                handle(action, habitId, didReceiveNotificationResponse, withCompletionHandler)
                return
            }
            moods[action]?.let { score ->
                answerMood(score, withCompletionHandler)
                return
            }
            val sel = NSSelectorFromString("userNotificationCenter:didReceiveNotificationResponse:withCompletionHandler:")
            val n = next
            if (n != null && (n as NSObject).respondsToSelector(sel)) {
                n.userNotificationCenter(center, didReceiveNotificationResponse, withCompletionHandler)
            } else {
                withCompletionHandler()
            }
        }

        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            willPresentNotification: UNNotification,
            withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
        ) {
            val sel = NSSelectorFromString("userNotificationCenter:willPresentNotification:withCompletionHandler:")
            val n = next
            if (n != null && (n as NSObject).respondsToSelector(sel)) {
                n.userNotificationCenter(center, willPresentNotification, withCompletionHandler)
            } else {
                withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionSound or UNNotificationPresentationOptionList)
            }
        }
    }
}
