package az.tribe.lifeplanner.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import az.tribe.lifeplanner.data.study.StudyService
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * The Study timer notification's buttons. Pause and Resume only move the clock; Stop saves the
 * session (or ticks its block) exactly like Stop and save in the app, without opening it.
 */
class StudyTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val study = runCatching { KoinPlatform.getKoin().get<StudyService>() }.getOrNull() ?: return
        when (intent.action) {
            ACTION_PAUSE -> study.pause()
            ACTION_RESUME -> study.resume()
            ACTION_STOP -> {
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                    try {
                        study.stop(fromNotification = true)
                    } catch (e: Exception) {
                        Logger.e("StudyTimerReceiver") { "Could not save the session: ${e.message}" }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "az.tribe.lifeplanner.study.PAUSE"
        const val ACTION_RESUME = "az.tribe.lifeplanner.study.RESUME"
        const val ACTION_STOP = "az.tribe.lifeplanner.study.STOP"
    }
}
