package az.tribe.lifeplanner.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.mind.MindService
import az.tribe.lifeplanner.data.mind.MoodNudges
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * The buttons on the daily mood reminder: Low, Okay and Good save a check-in without opening the
 * app, on the same 1 to 5 scale as the faces on Sleep and mind. They write through MindService,
 * the same path the page uses, so the year grid, What lifts you, Health and sync all see it.
 */
class MoodReminderActions : BroadcastReceiver() {

    enum class Answer(val id: String, val label: String, val score: Int) {
        LOW("low", "Low", 2),
        OKAY("okay", "Okay", 3),
        GOOD("good", "Good", 4),
    }

    override fun onReceive(context: Context, intent: Intent) {
        val answer = Answer.entries.firstOrNull { it.id == intent.getStringExtra(EXTRA_ANSWER) } ?: return
        val reminderId = intent.getStringExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID) ?: return
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(reminderId.hashCode())

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val koin = GlobalContext.getOrNull() ?: return@launch
                val mind = koin.getOrNull<MindService>() ?: return@launch
                val log = mind.checkIn(answer.score)
                mind.sendToHealth(log)
                PostHogAnalytics.capture("v4_mind_reminder_answered", mapOf("score" to answer.score))
                // Keeps the week ahead planned for someone who only ever answers from here.
                koin.getOrNull<MoodNudges>()?.replan()
            } catch (e: Exception) {
                Logger.w("MoodReminderActions") { "${answer.id} failed: ${e.message}" }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_ANSWER = "mood_answer"

        val all = Answer.entries

        fun pending(context: Context, answer: Answer, reminderId: String): PendingIntent {
            val intent = Intent(context, MoodReminderActions::class.java).apply {
                putExtra(EXTRA_ANSWER, answer.id)
                putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
            }
            return PendingIntent.getBroadcast(
                context, "$reminderId:${answer.id}".hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
