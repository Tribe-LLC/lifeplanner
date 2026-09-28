package az.tribe.lifeplanner.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import az.tribe.lifeplanner.MainActivity
import az.tribe.lifeplanner.shared.R
import az.tribe.lifeplanner.domain.model.DayOfWeek
import az.tribe.lifeplanner.domain.model.ReminderFrequency
import az.tribe.lifeplanner.data.habits.HabitService
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import kotlin.time.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_FREQUENCY = "frequency"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_SCHEDULED_DAYS = "scheduled_days"
        const val EXTRA_LINKED_GOAL_ID = "linked_goal_id"
        /** A nudge's destination (NudgePlan.CHECK_IN / REVIEW); opens it on tap. */
        const val EXTRA_OPEN = "open"
        /** Set for habit reminders: the reminder is skipped when the habit is already handled, and gets Done / Not today / Later. */
        const val EXTRA_HABIT_ID = "habit_id"
        internal const val CHANNEL_ID = "reminders"
        private const val CHANNEL_NAME = "Reminders"

        internal fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Life Planner reminder notifications"
                        enableVibration(true)
                    }
                )
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val frequencyName = intent.getStringExtra(EXTRA_FREQUENCY) ?: return
        val hour = intent.getIntExtra(EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(EXTRA_MINUTE, -1)
        val scheduledDaysStr = intent.getStringExtra(EXTRA_SCHEDULED_DAYS) ?: ""
        val linkedGoalId = intent.getStringExtra(EXTRA_LINKED_GOAL_ID)
        val open = intent.getStringExtra(EXTRA_OPEN)
        val habitId = intent.getStringExtra(EXTRA_HABIT_ID)

        Logger.i("ReminderAlarmReceiver") { "Firing reminder: $title" }

        if (habitId != null) {
            // Ask the habit first: a reminder for something already done or skipped stays silent.
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val service = GlobalContext.getOrNull()?.getOrNull<HabitService>()
                    val needed = service?.let { runCatching { it.needsReminder(habitId) }.getOrDefault(true) } ?: true
                    if (needed) {
                        val progress = service?.let { runCatching { it.progressText(habitId) }.getOrNull() }
                        showNotification(context, reminderId, title, progress?.let { "$it so far" } ?: message, linkedGoalId, open, habitId, intent, counted = progress != null)
                    } else {
                        Logger.i("ReminderAlarmReceiver") { "Already handled today, staying quiet: $title" }
                    }
                } finally {
                    pending.finish()
                }
            }
        } else {
            showNotification(context, reminderId, title, message, linkedGoalId, open)
        }

        // Reschedule for recurring reminders
        val frequency = try {
            ReminderFrequency.valueOf(frequencyName)
        } catch (e: Exception) {
            return
        }

        if (frequency != ReminderFrequency.ONCE && hour >= 0 && minute >= 0) {
            val scheduledDays = if (scheduledDaysStr.isNotBlank()) {
                scheduledDaysStr.split(",").mapNotNull { name ->
                    try { DayOfWeek.valueOf(name.trim()) } catch (e: Exception) { null }
                }
            } else {
                emptyList()
            }

            val nextReminder = az.tribe.lifeplanner.domain.model.Reminder(
                id = reminderId,
                title = title,
                message = message,
                type = az.tribe.lifeplanner.domain.model.ReminderType.CUSTOM,
                frequency = frequency,
                scheduledTime = LocalTime(hour, minute),
                scheduledDays = scheduledDays,
                isEnabled = true,
                createdAt = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
            )

            AndroidNotificationScheduler.schedule(nextReminder)
        }
    }

    private fun showNotification(
        context: Context,
        reminderId: String,
        title: String,
        message: String,
        linkedGoalId: String? = null,
        open: String? = null,
        habitId: String? = null,
        source: Intent? = null,
        counted: Boolean = false,
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(context)

        // Tap action opens the app with a deep link if goal ID is present
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (open != null) {
                data = android.net.Uri.parse("lifeplanner://nudge/$open")
            } else if (linkedGoalId != null) {
                data = android.net.Uri.parse("https://tribe.az/lifeplanner/goal/$linkedGoalId")
            }
        }
        val pendingTapIntent = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message.ifEmpty { title })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingTapIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        if (habitId != null) {
            HabitReminderActions.all.forEach { a ->
                builder.addAction(0, if (counted && a == HabitReminderActions.Action.DONE) "+1" else a.label, HabitReminderActions.pending(context, a, habitId, reminderId, title, source))
            }
        }
        val notification = builder.build()

        try {
            notificationManager.notify(reminderId.hashCode(), notification)
            Logger.i("ReminderAlarmReceiver") { "Notification shown: $title" }
        } catch (e: SecurityException) {
            Logger.e("ReminderAlarmReceiver") { "No notification permission: ${e.message}" }
        }
    }
}
