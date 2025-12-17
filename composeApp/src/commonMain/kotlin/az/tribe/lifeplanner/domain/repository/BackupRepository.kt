package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.BackupData
import az.tribe.lifeplanner.domain.model.ExportResult
import az.tribe.lifeplanner.domain.model.ImportResult

interface BackupRepository {
    /**
     * Export all user data to a JSON string
     */
    suspend fun exportData(): ExportResult

    /**
     * Import data from a JSON string
     * @param jsonData The JSON string containing backup data
     * @param mergeStrategy How to handle conflicts with existing data
     */
    suspend fun importData(jsonData: String, mergeStrategy: MergeStrategy = MergeStrategy.SKIP_EXISTING): ImportResult

    /**
     * Validate backup data without importing
     */
    suspend fun validateBackup(jsonData: String): ValidationResult

    /**
     * Get the last backup date if available
     */
    suspend fun getLastBackupDate(): String?

    /**
     * Save last backup timestamp
     */
    suspend fun saveLastBackupDate(date: String)
}

enum class MergeStrategy {
    SKIP_EXISTING,      // Skip items that already exist
    OVERWRITE_EXISTING, // Overwrite existing items with backup data
    KEEP_NEWEST         // Keep whichever version is newer
}

sealed class ValidationResult {
    data class Valid(val data: BackupData) : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()
}
