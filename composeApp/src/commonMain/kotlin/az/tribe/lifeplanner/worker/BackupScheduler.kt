package az.tribe.lifeplanner.worker

/**
 * Platform-specific backup scheduler interface
 */
interface BackupSchedulerInterface {
    /**
     * Schedule the daily backup at 01:00 AM
     */
    fun scheduleDailyBackup()

    /**
     * Cancel the scheduled daily backup
     */
    fun cancelDailyBackup()
}

/**
 * Platform-specific function to get the backup scheduler
 */
expect fun getBackupScheduler(): BackupSchedulerInterface
