package az.tribe.lifeplanner.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import az.tribe.lifeplanner.domain.repository.ReminderRepository
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BootReceiver : BroadcastReceiver(), KoinComponent {

    private val reminderRepository: ReminderRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Logger.i("BootReceiver") { "Device rebooted — rescheduling reminders" }
            AndroidNotificationScheduler.init(context.applicationContext)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val reminders = reminderRepository.getEnabledReminders()
                    reminders.forEach { AndroidNotificationScheduler.schedule(it) }
                    Logger.i("BootReceiver") { "Rescheduled ${reminders.size} reminders" }
                } catch (e: Exception) {
                    Logger.e("BootReceiver") { "Failed to reschedule: ${e.message}" }
                }
            }
        }
    }
}
