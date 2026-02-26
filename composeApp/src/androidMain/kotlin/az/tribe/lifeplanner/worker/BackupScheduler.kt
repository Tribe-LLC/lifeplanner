package az.tribe.lifeplanner.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import co.touchlab.kermit.Logger
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Scheduler for automatic daily backup at 01:00 AM
 */
object BackupScheduler : BackupSchedulerInterface {

    private const val TAG = "BackupScheduler"
    private var appContext: Context? = null

    /**
     * Initialize with application context - call from MainApplication
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Schedule daily backup to run at 01:00 AM
     */
    override fun scheduleDailyBackup() {
        val context = appContext ?: run {
            Logger.e(TAG) { "BackupScheduler not initialized" }
            return
        }

        val workManager = WorkManager.getInstance(context)

        // Calculate initial delay to 01:00 AM
        val initialDelay = calculateDelayUntil1AM()

        Logger.i(TAG) { "Scheduling daily backup with initial delay: ${initialDelay / 1000 / 60} minutes" }

        // Constraints: prefer when device is idle and charging, but not required
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // Backup is local, no network needed
            .setRequiresBatteryNotLow(true) // Don't backup when battery is critically low
            .build()

        // Create periodic work request - runs every 24 hours
        val backupRequest = PeriodicWorkRequestBuilder<BackupWorker>(
            repeatInterval = 24,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(BackupWorker.WORK_NAME)
            .build()

        // Enqueue the work, keeping existing if already scheduled
        workManager.enqueueUniquePeriodicWork(
            BackupWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP, // Keep existing schedule if already running
            backupRequest
        )

        Logger.i(TAG) { "Daily backup scheduled successfully" }
    }

    /**
     * Cancel the daily backup schedule
     */
    override fun cancelDailyBackup() {
        val context = appContext ?: run {
            Logger.e(TAG) { "BackupScheduler not initialized" }
            return
        }

        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(BackupWorker.WORK_NAME)
        Logger.i(TAG) { "Daily backup cancelled" }
    }

    /**
     * Calculate milliseconds until next 01:00 AM
     */
    private fun calculateDelayUntil1AM(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If 01:00 has already passed today, schedule for tomorrow
            if (before(now)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        return target.timeInMillis - now.timeInMillis
    }
}

actual fun getBackupScheduler(): BackupSchedulerInterface = BackupScheduler
