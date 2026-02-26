package az.tribe.lifeplanner.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import az.tribe.lifeplanner.domain.model.ExportResult
import az.tribe.lifeplanner.domain.repository.BackupRepository
import co.touchlab.kermit.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Background worker that performs automatic daily backup at 01:00 AM
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val backupRepository: BackupRepository by inject()

    override suspend fun doWork(): Result {
        Logger.i("BackupWorker") { "Starting automatic backup..." }

        return try {
            when (val result = backupRepository.exportData()) {
                is ExportResult.Success -> {
                    Logger.i("BackupWorker") { "Backup completed successfully: ${result.fileName}" }
                    Result.success()
                }
                is ExportResult.Error -> {
                    Logger.e("BackupWorker") { "Backup failed: ${result.message}" }
                    // Retry on failure (up to 3 times by default)
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Logger.e("BackupWorker") { "Backup error: ${e.message}" }
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "daily_backup_worker"
    }
}
