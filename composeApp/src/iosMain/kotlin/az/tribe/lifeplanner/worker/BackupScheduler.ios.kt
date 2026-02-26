package az.tribe.lifeplanner.worker

import az.tribe.lifeplanner.domain.model.ExportResult
import az.tribe.lifeplanner.domain.repository.BackupRepository
import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import platform.BackgroundTasks.BGProcessingTask
import platform.BackgroundTasks.BGProcessingTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents

/**
 * iOS Background Task Scheduler for daily backup at 01:00 AM
 */
@OptIn(ExperimentalForeignApi::class)
object IOSBackupScheduler : BackupSchedulerInterface, KoinComponent {

    private const val TAG = "IOSBackupScheduler"
    const val TASK_IDENTIFIER = "az.tribe.lifeplanner.dailyBackup"

    private val backupRepository: BackupRepository by inject()

    /**
     * Check if auto backup is enabled in settings
     */
    fun isAutoBackupEnabled(): Boolean {
        return backupRepository.isAutoBackupEnabled()
    }
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Register the background task handler - call this in AppDelegate.didFinishLaunchingWithOptions
     */
    fun registerBackgroundTask() {
        Logger.i(TAG) { "Registering background task: $TASK_IDENTIFIER" }

        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            identifier = TASK_IDENTIFIER,
            usingQueue = null
        ) { task ->
            task?.let {
                handleBackupTask(it as BGProcessingTask)
            }
        }
    }

    /**
     * Schedule the daily backup task - call this when app goes to background
     */
    override fun scheduleDailyBackup() {
        Logger.i(TAG) { "Scheduling daily backup task" }

        val request = BGProcessingTaskRequest(identifier = TASK_IDENTIFIER)

        // Schedule for 01:00 AM
        request.earliestBeginDate = calculateNext1AM()

        // Prefer when device is connected to power
        request.requiresExternalPower = false
        request.requiresNetworkConnectivity = false

        try {
            BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
            Logger.i(TAG) { "Daily backup scheduled for ${request.earliestBeginDate}" }
        } catch (e: Exception) {
            Logger.e(TAG) { "Failed to schedule backup: ${e.message}" }
        }
    }

    /**
     * Cancel scheduled backup
     */
    override fun cancelDailyBackup() {
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(TASK_IDENTIFIER)
        Logger.i(TAG) { "Daily backup cancelled" }
    }

    /**
     * Handle the background task execution
     */
    private fun handleBackupTask(task: BGProcessingTask) {
        Logger.i(TAG) { "Starting backup task" }

        // Schedule the next backup before starting work
        scheduleDailyBackup()

        // Set expiration handler
        task.setExpirationHandler {
            Logger.w(TAG) { "Backup task expired" }
            task.setTaskCompletedWithSuccess(false)
        }

        // Perform backup
        scope.launch {
            try {
                when (val result = backupRepository.exportData()) {
                    is ExportResult.Success -> {
                        Logger.i(TAG) { "Backup completed: ${result.fileName}" }
                        task.setTaskCompletedWithSuccess(true)
                    }
                    is ExportResult.Error -> {
                        Logger.e(TAG) { "Backup failed: ${result.message}" }
                        task.setTaskCompletedWithSuccess(false)
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG) { "Backup error: ${e.message}" }
                task.setTaskCompletedWithSuccess(false)
            }
        }
    }

    /**
     * Calculate the next 01:00 AM date
     */
    private fun calculateNext1AM(): NSDate {
        val calendar = NSCalendar.currentCalendar
        val now = NSDate()

        // Get components for today at 01:00
        val components = NSDateComponents()
        components.hour = 1
        components.minute = 0
        components.second = 0

        // Get next date matching 01:00
        val targetDate = calendar.nextDateAfterDate(
            now,
            matchingComponents = components,
            options = 0u
        )

        // If null, return date 24 hours from now as fallback
        return targetDate ?: NSDate(timeIntervalSinceReferenceDate = NSDate().timeIntervalSinceReferenceDate + 24.0 * 60.0 * 60.0)
    }
}

actual fun getBackupScheduler(): BackupSchedulerInterface = IOSBackupScheduler
