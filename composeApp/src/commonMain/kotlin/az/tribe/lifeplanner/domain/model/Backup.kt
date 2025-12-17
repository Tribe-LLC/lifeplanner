package az.tribe.lifeplanner.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/**
 * Complete backup data structure containing all user data
 */
@Serializable
data class BackupData(
    val version: Int = CURRENT_BACKUP_VERSION,
    val createdAt: String, // ISO-8601 format
    val goals: List<GoalBackup>,
    val milestones: List<MilestoneBackup>,
    val habits: List<HabitBackup>,
    val habitCompletions: List<HabitCompletionBackup>,
    val journalEntries: List<JournalEntryBackup>,
    val userProgress: UserProgressBackup?,
    val badges: List<BadgeBackup>,
    val challenges: List<ChallengeBackup>,
    val settings: SettingsBackup?
) {
    companion object {
        const val CURRENT_BACKUP_VERSION = 1
    }
}

@Serializable
data class GoalBackup(
    val id: String,
    val title: String,
    val description: String?,
    val category: String,
    val priority: String,
    val status: String,
    val progress: Int,
    val targetDate: String?,
    val createdAt: String,
    val updatedAt: String,
    val completedAt: String?,
    val notes: String?,
    val reminderEnabled: Boolean,
    val reminderTime: String?
)

@Serializable
data class MilestoneBackup(
    val id: String,
    val goalId: String,
    val title: String,
    val isCompleted: Boolean,
    val completedAt: String?,
    val orderIndex: Int
)

@Serializable
data class HabitBackup(
    val id: String,
    val title: String,
    val description: String?,
    val category: String,
    val frequency: String,
    val targetDaysPerWeek: Int,
    val reminderTime: String?,
    val isActive: Boolean,
    val createdAt: String,
    val linkedGoalId: String?,
    val currentStreak: Int,
    val longestStreak: Int,
    val totalCompletions: Int
)

@Serializable
data class HabitCompletionBackup(
    val id: String,
    val habitId: String,
    val completedAt: String,
    val notes: String?
)

@Serializable
data class JournalEntryBackup(
    val id: String,
    val content: String,
    val mood: String?,
    val linkedGoalId: String?,
    val promptUsed: String?,
    val createdAt: String,
    val updatedAt: String?
)

@Serializable
data class UserProgressBackup(
    val odysxp: Int,
    val level: Int,
    val totalGoalsCompleted: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val lastActivityDate: String?
)

@Serializable
data class BadgeBackup(
    val id: String,
    val type: String,
    val unlockedAt: String
)

@Serializable
data class ChallengeBackup(
    val id: String,
    val type: String,
    val title: String,
    val description: String,
    val targetValue: Int,
    val currentValue: Int,
    val xpReward: Int,
    val startDate: String,
    val endDate: String,
    val isCompleted: Boolean,
    val completedAt: String?
)

@Serializable
data class SettingsBackup(
    val notificationsEnabled: Boolean,
    val dailyReminderTime: String?,
    val theme: String?,
    val hasCompletedOnboarding: Boolean
)

/**
 * Result of an export operation
 */
sealed class ExportResult {
    data class Success(val jsonData: String, val fileName: String) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

/**
 * Result of an import operation
 */
sealed class ImportResult {
    data class Success(
        val goalsImported: Int,
        val habitsImported: Int,
        val journalEntriesImported: Int
    ) : ImportResult()
    data class Error(val message: String) : ImportResult()
    data class VersionMismatch(val backupVersion: Int, val currentVersion: Int) : ImportResult()
}
