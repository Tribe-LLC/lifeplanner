package az.tribe.lifeplanner.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import az.tribe.lifeplanner.domain.model.DayOfWeek
import az.tribe.lifeplanner.domain.model.Reminder
import az.tribe.lifeplanner.domain.model.ReminderFrequency
import co.touchlab.kermit.Logger
import java.util.Calendar

object AndroidNotificationScheduler : NotificationSchedulerInterface {

    private const val TAG = "NotificationScheduler"
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    override fun schedule(reminder: Reminder) {
        if (!reminder.isEnabled) return
        val context = appContext ?: run {
            Logger.e(TAG) { "NotificationScheduler not initialized" }
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(ReminderAlarmReceiver.EXTRA_TITLE, reminder.title)
            putExtra(ReminderAlarmReceiver.EXTRA_MESSAGE, reminder.message)
            putExtra(ReminderAlarmReceiver.EXTRA_FREQUENCY, reminder.frequency.name)
            putExtra(ReminderAlarmReceiver.EXTRA_HOUR, reminder.scheduledTime.hour)
            putExtra(ReminderAlarmReceiver.EXTRA_MINUTE, reminder.scheduledTime.minute)
            putExtra(
                ReminderAlarmReceiver.EXTRA_SCHEDULED_DAYS,
                reminder.scheduledDays.joinToString(",") { it.name }
            )
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTimeMillis = calculateNextTriggerTimeMillis(reminder)
        if (triggerTimeMillis == null) {
            Logger.w(TAG) { "Could not calculate trigger time for reminder: ${reminder.id}" }
            return
        }

        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent
            )
            Logger.i(TAG) { "Scheduled reminder '${reminder.title}' at ${java.util.Date(triggerTimeMillis)}" }
        } catch (e: Exception) {
            Logger.e(TAG) { "Failed to schedule reminder: ${e.message}" }
        }
    }

    override fun cancel(reminderId: String) {
        val context = appContext ?: return
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Logger.i(TAG) { "Cancelled reminder: $reminderId" }
        }
    }

    override fun cancelAll() {
        Logger.i(TAG) { "cancelAll called — individual alarms must be cancelled by ID" }
    }

    private fun calculateNextTriggerTimeMillis(reminder: Reminder): Long? {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, reminder.scheduledTime.hour)
            set(Calendar.MINUTE, reminder.scheduledTime.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return when (reminder.frequency) {
            ReminderFrequency.ONCE -> {
                if (target.after(now)) target.timeInMillis else null
            }

            ReminderFrequency.DAILY, ReminderFrequency.SMART -> {
                if (target.before(now) || target == now) {
                    target.add(Calendar.DAY_OF_MONTH, 1)
                }
                target.timeInMillis
            }

            ReminderFrequency.WEEKDAYS -> {
                if (target.before(now) || target == now) {
                    target.add(Calendar.DAY_OF_MONTH, 1)
                }
                while (target.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY ||
                    target.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                ) {
                    target.add(Calendar.DAY_OF_MONTH, 1)
                }
                target.timeInMillis
            }

            ReminderFrequency.WEEKENDS -> {
                if (target.before(now) || target == now) {
                    target.add(Calendar.DAY_OF_MONTH, 1)
                }
                while (target.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY &&
                    target.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY
                ) {
                    target.add(Calendar.DAY_OF_MONTH, 1)
                }
                target.timeInMillis
            }

            ReminderFrequency.WEEKLY -> {
                if (reminder.scheduledDays.isEmpty()) return null
                if (target.before(now) || target == now) {
                    target.add(Calendar.DAY_OF_MONTH, 1)
                }
                val targetDays = reminder.scheduledDays.map { it.toCalendarDay() }.toSet()
                repeat(7) {
                    if (target.get(Calendar.DAY_OF_WEEK) in targetDays) {
                        return target.timeInMillis
                    }
                    target.add(Calendar.DAY_OF_MONTH, 1)
                }
                null
            }

            ReminderFrequency.MONTHLY -> {
                if (target.before(now) || target == now) {
                    target.add(Calendar.MONTH, 1)
                }
                target.timeInMillis
            }
        }
    }

    private fun DayOfWeek.toCalendarDay(): Int = when (this) {
        DayOfWeek.MONDAY -> Calendar.MONDAY
        DayOfWeek.TUESDAY -> Calendar.TUESDAY
        DayOfWeek.WEDNESDAY -> Calendar.WEDNESDAY
        DayOfWeek.THURSDAY -> Calendar.THURSDAY
        DayOfWeek.FRIDAY -> Calendar.FRIDAY
        DayOfWeek.SATURDAY -> Calendar.SATURDAY
        DayOfWeek.SUNDAY -> Calendar.SUNDAY
    }
}

actual fun getNotificationScheduler(): NotificationSchedulerInterface = AndroidNotificationScheduler
