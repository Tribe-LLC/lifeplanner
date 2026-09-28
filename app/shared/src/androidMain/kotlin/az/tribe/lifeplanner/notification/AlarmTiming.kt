package az.tribe.lifeplanner.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build

/**
 * Plans an alarm close to its time. setAndAllowWhileIdle lets Android fire up to an hour late,
 * which is wrong for a 07:30 reminder. Exact alarms need a permission Play reserves for alarm
 * clocks, so: exact when the phone allows it, otherwise a 10-minute window.
 */
internal fun AlarmManager.planNear(triggerAtMillis: Long, pending: PendingIntent) {
    val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms()
    if (exact) setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
    else setWindow(AlarmManager.RTC_WAKEUP, triggerAtMillis, 10 * 60 * 1000L, pending)
}
