package az.tribe.lifeplanner.notification

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.habits.HabitService
import az.tribe.lifeplanner.domain.model.ReminderFrequency
import az.tribe.lifeplanner.widget.WidgetUpdateHelper
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * The buttons on a habit reminder: Done (or +1 for a counted habit), Not today (a skip, so the
 * streak waits), and Later (the same reminder again in an hour). All write through HabitService,
 * the same path the app uses, so Today, the streak and sync see it.
 */
class HabitReminderActions : BroadcastReceiver() {

    enum class Action(val id: String, val label: String) {
        DONE("done", "Done"),
        SKIP("skip", "Not today"),
        LATER("later", "In 1 hour"),
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = Action.entries.firstOrNull { it.id == intent.getStringExtra(EXTRA_ACTION) } ?: return
        val habitId = intent.getStringExtra(ReminderAlarmReceiver.EXTRA_HABIT_ID) ?: return
        val reminderId = intent.getStringExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID) ?: return
        val title = intent.getStringExtra(ReminderAlarmReceiver.EXTRA_TITLE).orEmpty()
        val message = intent.getStringExtra(ReminderAlarmReceiver.EXTRA_MESSAGE).orEmpty()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(reminderId.hashCode())

        if (action == Action.LATER) {
            later(context, reminderId, habitId, title, message)
            PostHogAnalytics.capture("v4_reminder_action", mapOf("action" to action.id))
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val service = GlobalContext.getOrNull()?.getOrNull<HabitService>() ?: return@launch
                when (action) {
                    Action.DONE -> service.tickToday(habitId)
                    Action.SKIP -> service.skipToday(habitId)
                    Action.LATER -> Unit
                }
                PostHogAnalytics.capture("v4_reminder_action", mapOf("action" to action.id))
                WidgetUpdateHelper.updateHabitWidgets(context)
            } catch (e: Exception) {
                Logger.w("HabitReminderActions") { "${action.id} failed: ${e.message}" }
            } finally {
                pending.finish()
            }
        }
    }

    /** The same reminder once more in an hour, as a one-off that asks the habit again before showing. */
    private fun later(context: Context, reminderId: String, habitId: String, title: String, message: String) {
        val again = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, "$reminderId$LATER_SUFFIX")
            putExtra(ReminderAlarmReceiver.EXTRA_TITLE, title)
            putExtra(ReminderAlarmReceiver.EXTRA_MESSAGE, message)
            putExtra(ReminderAlarmReceiver.EXTRA_FREQUENCY, ReminderFrequency.ONCE.name)
            putExtra(ReminderAlarmReceiver.EXTRA_HABIT_ID, habitId)
        }
        val pi = PendingIntent.getBroadcast(
            context, "$reminderId$LATER_SUFFIX".hashCode(), again,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).planNear(System.currentTimeMillis() + LATER_MS, pi)
    }

    companion object {
        private const val EXTRA_ACTION = "habit_action"
        private const val LATER_SUFFIX = "_later"
        private const val LATER_MS = 60 * 60 * 1000L

        val all = Action.entries

        fun pending(context: Context, action: Action, habitId: String, reminderId: String, title: String, source: Intent?): PendingIntent {
            // A later reminder carries the original id plus a suffix; its buttons should act on the original.
            val baseId = reminderId.removeSuffix(LATER_SUFFIX)
            val intent = Intent(context, HabitReminderActions::class.java).apply {
                putExtra(EXTRA_ACTION, action.id)
                putExtra(ReminderAlarmReceiver.EXTRA_HABIT_ID, habitId)
                putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
                putExtra(ReminderAlarmReceiver.EXTRA_TITLE, title)
                putExtra(ReminderAlarmReceiver.EXTRA_MESSAGE, source?.getStringExtra(ReminderAlarmReceiver.EXTRA_MESSAGE).orEmpty())
            }
            return PendingIntent.getBroadcast(
                context, "$baseId:${action.id}".hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
