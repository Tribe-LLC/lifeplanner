package az.tribe.lifeplanner.data.study

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import az.tribe.lifeplanner.MainActivity
import az.tribe.lifeplanner.data.habits.NudgePlan
import az.tribe.lifeplanner.notification.AndroidNotificationScheduler
import az.tribe.lifeplanner.notification.StudyTimerReceiver
import az.tribe.lifeplanner.shared.R
import co.touchlab.kermit.Logger

/**
 * The ongoing "Studying Maths" notification: a stopwatch that skips paused time, and Pause or
 * Resume and Stop that work from the shade through [StudyTimerReceiver]. Quiet: it never buzzes,
 * the "minutes are up" alarm does that.
 */
actual object StudyTimerNotice {
    private const val CHANNEL_ID = "study_timer"
    private const val NOTIFICATION_ID = 0x5717

    private fun context(): Context? = AndroidNotificationScheduler.appContext

    actual fun show(active: ActiveStudy) {
        val context = context() ?: return
        val now = System.currentTimeMillis()
        val builder = base(context)
            .setOngoing(true)
            .setContentTitle(if (active.paused) "${active.subject}, paused at ${clock(active.elapsedMs(now))}" else "Studying ${active.subject}")
            .setContentText(if (active.paused) "Resume when you are back." else "Aiming for ${active.targetMin} minutes")
            .addAction(
                0, if (active.paused) "Resume" else "Pause",
                action(context, if (active.paused) StudyTimerReceiver.ACTION_RESUME else StudyTimerReceiver.ACTION_PAUSE),
            )
            .addAction(0, "Stop and save", action(context, StudyTimerReceiver.ACTION_STOP))
        if (active.paused) builder.setShowWhen(false).setUsesChronometer(false)
        else builder.setShowWhen(true).setUsesChronometer(true).setWhen(active.chronoBaseMs)
        post(context, builder)
    }

    actual fun clear() {
        val context = context() ?: return
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    actual fun saved(subject: String, minutes: Int) {
        val context = context() ?: return
        post(
            context,
            base(context).setOngoing(false).setAutoCancel(true).setTimeoutAfter(60_000)
                .setContentTitle("Saved $minutes min of $subject")
                .setContentText("It counts toward your week."),
        )
    }

    private fun base(context: Context): NotificationCompat.Builder {
        channel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("lifeplanner://nudge/${NudgePlan.STUDY}")
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(PendingIntent.getActivity(context, NOTIFICATION_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    private fun action(context: Context, what: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, what.hashCode(),
            Intent(context, StudyTimerReceiver::class.java).setAction(what),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun channel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Study timer", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "The study timer while it runs, with pause and stop"
                    setShowBadge(false)
                },
            )
        }
    }

    private fun post(context: Context, builder: NotificationCompat.Builder) {
        try {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Logger.w("StudyTimerNotice") { "No notification permission: ${e.message}" }
        }
    }

    private fun clock(ms: Long): String {
        val total = ms / 1000
        fun two(n: Long) = n.toString().padStart(2, '0')
        val h = total / 3600
        return if (h > 0) "$h:${two((total % 3600) / 60)}:${two(total % 60)}" else "${two(total / 60)}:${two(total % 60)}"
    }
}
