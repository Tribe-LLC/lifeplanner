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
import az.tribe.lifeplanner.R
import az.tribe.lifeplanner.domain.model.DayOfWeek
import az.tribe.lifeplanner.domain.model.ReminderFrequency
import co.touchlab.kermit.Logger
import kotlinx.datetime.Clock
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
        private const val CHANNEL_ID = "reminders"
        private const val CHANNEL_NAME = "Reminders"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val frequencyName = intent.getStringExtra(EXTRA_FREQUENCY) ?: return
        val hour = intent.getIntExtra(EXTRA_HOUR, -1)
        val minute = intent.getIntExtra(EXTRA_MINUTE, -1)
        val scheduledDaysStr = intent.getStringExtra(EXTRA_SCHEDULED_DAYS) ?: ""

        Logger.i("ReminderAlarmReceiver") { "Firing reminder: $title" }

        showNotification(context, reminderId, title, message)

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

    private fun showNotification(context: Context, reminderId: String, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel (required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Life Planner reminder notifications"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tap action opens the app
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingTapIntent = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message.ifEmpty { title })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingTapIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        try {
            notificationManager.notify(reminderId.hashCode(), notification)
            Logger.i("ReminderAlarmReceiver") { "Notification shown: $title" }
        } catch (e: SecurityException) {
            Logger.e("ReminderAlarmReceiver") { "No notification permission: ${e.message}" }
        }
    }
}
