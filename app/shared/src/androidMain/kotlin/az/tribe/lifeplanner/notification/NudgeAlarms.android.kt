package az.tribe.lifeplanner.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import az.tribe.lifeplanner.data.habits.Nudge
import az.tribe.lifeplanner.domain.model.ReminderFrequency
import co.touchlab.kermit.Logger
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

actual object NudgeAlarms {
    private const val TAG = "NudgeAlarms"

    actual fun schedule(nudge: Nudge) {
        val context = AndroidNotificationScheduler.appContext ?: return
        val millis = nudge.at.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        if (millis <= System.currentTimeMillis()) return
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, nudge.id)
            putExtra(ReminderAlarmReceiver.EXTRA_TITLE, nudge.title)
            putExtra(ReminderAlarmReceiver.EXTRA_MESSAGE, nudge.body)
            putExtra(ReminderAlarmReceiver.EXTRA_FREQUENCY, ReminderFrequency.ONCE.name)
            putExtra(ReminderAlarmReceiver.EXTRA_OPEN, nudge.open)
        }
        val pending = PendingIntent.getBroadcast(
            context, nudge.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .planNear(millis, pending)
        }.onFailure { Logger.e(TAG) { "Could not plan ${nudge.id}: ${it.message}" } }
    }

    actual fun cancel(id: String) = AndroidNotificationScheduler.cancel(id)
}
