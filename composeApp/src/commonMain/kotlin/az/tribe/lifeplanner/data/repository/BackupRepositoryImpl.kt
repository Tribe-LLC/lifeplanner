package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.domain.model.*
import az.tribe.lifeplanner.domain.repository.BackupRepository
import az.tribe.lifeplanner.domain.repository.MergeStrategy
import az.tribe.lifeplanner.domain.repository.ValidationResult
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import co.touchlab.kermit.Logger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupRepositoryImpl(
    private val database: SharedDatabase,
    private val settings: Settings
) : BackupRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun exportData(): ExportResult {
        return try {
            val now = Clock.System.now()
            val timestamp = now.toLocalDateTime(TimeZone.currentSystemDefault())

            // Gather all data from database
            val goals = database.getAllGoals().map { goal ->
                GoalBackup(
                    id = goal.id,
                    title = goal.title,
                    description = goal.description,
                    category = goal.category,
                    priority = goal.timeline, // Using timeline as priority
                    status = goal.status,
                    progress = goal.progress.toInt(),
                    targetDate = goal.dueDate,
                    createdAt = goal.createdAt,
                    updatedAt = goal.sync_updated_at ?: goal.createdAt,
                    completedAt = if (goal.status == "COMPLETED") goal.createdAt else null,
                    notes = goal.notes,
                    reminderEnabled = false,
                    reminderTime = null
                )
            }

            // Get all milestones
            val milestones = mutableListOf<MilestoneBackup>()
            goals.forEach { goal ->
                val goalMilestones = database.getMilestonesByGoalId(goal.id)
                milestones.addAll(goalMilestones.mapIndexed { index, m ->
                    MilestoneBackup(
                        id = m.id,
                        goalId = m.goalId,
                        title = m.title,
                        isCompleted = m.isCompleted == 1L,
                        completedAt = if (m.isCompleted == 1L) m.dueDate else null,
                        orderIndex = index
                    )
                })
            }

            // Get all habits
            val habits = database.getAllHabits().map { habit ->
                HabitBackup(
                    id = habit.id,
                    title = habit.title,
                    description = habit.description,
                    category = habit.category,
                    frequency = habit.frequency,
                    targetDaysPerWeek = habit.targetCount.toInt(),
                    reminderTime = habit.reminderTime,
                    isActive = habit.isActive == 1L,
                    createdAt = habit.createdAt,
                    linkedGoalId = habit.linkedGoalId,
                    currentStreak = habit.currentStreak.toInt(),
                    longestStreak = habit.longestStreak.toInt(),
                    totalCompletions = habit.totalCompletions.toInt()
                )
            }

            // Get habit completions
            val habitCompletions = mutableListOf<HabitCompletionBackup>()
            habits.forEach { habit ->
                val checkIns = database.getCheckInsByHabitId(habit.id)
                habitCompletions.addAll(checkIns.filter { it.completed == 1L }.map { checkIn ->
                    HabitCompletionBackup(
                        id = checkIn.id,
                        habitId = checkIn.habitId,
                        completedAt = checkIn.date,
                        notes = checkIn.notes
                    )
                })
            }

            // Get journal entries
            val journalEntries = database.getAllJournalEntries().map { entry ->
                JournalEntryBackup(
                    id = entry.id,
                    content = entry.content,
                    mood = entry.mood,
                    linkedGoalId = entry.linkedGoalId,
                    promptUsed = entry.promptUsed,
                    createdAt = entry.createdAt,
                    updatedAt = entry.updatedAt
                )
            }

            // Get user progress
            val userProgressEntity = database.getUserProgressEntity()
            val userProgress = userProgressEntity?.let {
                UserProgressBackup(
                    odysxp = it.totalXp.toInt(),
                    level = it.currentLevel.toInt(),
                    totalGoalsCompleted = it.goalsCompleted.toInt(),
                    currentStreak = it.currentStreak.toInt(),
                    longestStreak = it.longestStreak.toInt(),
                    lastActivityDate = it.lastCheckInDate
                )
            }

            // Get badges
            val badges = database.getAllBadges().map { badge ->
                BadgeBackup(
                    id = badge.id,
                    type = badge.badgeType,
                    unlockedAt = badge.earnedAt
                )
            }

            // Get challenges
            val challenges = database.getAllChallenges().map { challenge ->
                ChallengeBackup(
                    id = challenge.id,
                    type = challenge.challengeType,
                    title = challenge.challengeType, // Type used as title
                    description = "",
                    targetValue = challenge.targetProgress.toInt(),
                    currentValue = challenge.currentProgress.toInt(),
                    xpReward = challenge.xpEarned.toInt(),
                    startDate = challenge.startDate,
                    endDate = challenge.endDate,
                    isCompleted = challenge.isCompleted == 1L,
                    completedAt = challenge.completedAt
                )
            }

            // Get settings
            val backupSettings = SettingsBackup(
                notificationsEnabled = settings.getBoolean("notifications_enabled", true),
                dailyReminderTime = settings.getStringOrNull("daily_reminder_time"),
                theme = settings.getStringOrNull("theme"),
                hasCompletedOnboarding = settings.getBoolean("has_completed_onboarding", false)
            )

            val backupData = BackupData(
                createdAt = timestamp.toString(),
                goals = goals,
                milestones = milestones,
                habits = habits,
                habitCompletions = habitCompletions,
                journalEntries = journalEntries,
                userProgress = userProgress,
                badges = badges,
                challenges = challenges,
                settings = backupSettings
            )

            val jsonString = json.encodeToString(backupData)
            val fileName = "lifeplanner_backup_${timestamp.date}_${timestamp.hour}${timestamp.minute}.json"

            // Save last backup date
            saveLastBackupDate(timestamp.toString())

            ExportResult.Success(jsonData = jsonString, fileName = fileName)
        } catch (e: Exception) {
            Logger.e("BackupRepository", e) { "Export failed: ${e.message}" }
            ExportResult.Error("Failed to export data: ${e.message}")
        }
    }

    override suspend fun importData(jsonData: String, mergeStrategy: MergeStrategy): ImportResult {
        return try {
            val validationResult = validateBackup(jsonData)
            if (validationResult is ValidationResult.Invalid) {
                return ImportResult.Error(validationResult.reason)
            }

            val backupData = (validationResult as ValidationResult.Valid).data

            // Check version compatibility
            if (backupData.version > BackupData.CURRENT_BACKUP_VERSION) {
                return ImportResult.VersionMismatch(
                    backupVersion = backupData.version,
                    currentVersion = BackupData.CURRENT_BACKUP_VERSION
                )
            }

            var goalsImported = 0
            var habitsImported = 0
            var journalEntriesImported = 0

            // Import goals
            val existingGoalIds = database.getAllGoals().map { it.id }.toSet()
            backupData.goals.forEach { goalBackup ->
                val shouldImport = when (mergeStrategy) {
                    MergeStrategy.SKIP_EXISTING -> goalBackup.id !in existingGoalIds
                    MergeStrategy.OVERWRITE_EXISTING -> true
                    MergeStrategy.KEEP_NEWEST -> goalBackup.id !in existingGoalIds
                }

                if (shouldImport) {
                    database.insertGoal(
                        az.tribe.lifeplanner.database.GoalEntity(
                            id = goalBackup.id,
                            title = goalBackup.title,
                            description = goalBackup.description ?: "",
                            category = goalBackup.category,
                            status = goalBackup.status,
                            timeline = goalBackup.priority,
                            dueDate = goalBackup.targetDate ?: "",
                            progress = goalBackup.progress.toLong(),
                            notes = goalBackup.notes ?: "",
                            createdAt = goalBackup.createdAt,
                            completionRate = 0.0,
                            isArchived = 0L,
                            aiReasoning = null,
                            sync_updated_at = Clock.System.now().toString(),
                            is_deleted = 0L,
                            sync_version = 0L, last_synced_at = null
                        )
                    )
                    goalsImported++
                }
            }

            // Import habits
            val existingHabitIds = database.getAllHabits().map { it.id }.toSet()
            backupData.habits.forEach { habitBackup ->
                val shouldImport = when (mergeStrategy) {
                    MergeStrategy.SKIP_EXISTING -> habitBackup.id !in existingHabitIds
                    MergeStrategy.OVERWRITE_EXISTING -> true
                    MergeStrategy.KEEP_NEWEST -> habitBackup.id !in existingHabitIds
                }

                if (shouldImport) {
                    database.insertHabit(
                        az.tribe.lifeplanner.database.HabitEntity(
                            id = habitBackup.id,
                            title = habitBackup.title,
                            description = habitBackup.description ?: "",
                            category = habitBackup.category,
                            frequency = habitBackup.frequency,
                            targetCount = habitBackup.targetDaysPerWeek.toLong(),
                            currentStreak = habitBackup.currentStreak.toLong(),
                            longestStreak = habitBackup.longestStreak.toLong(),
                            totalCompletions = habitBackup.totalCompletions.toLong(),
                            lastCompletedDate = null,
                            linkedGoalId = habitBackup.linkedGoalId,
                            correlationScore = 0.0,
                            isActive = if (habitBackup.isActive) 1L else 0L,
                            createdAt = habitBackup.createdAt,
                            reminderTime = habitBackup.reminderTime,
                            type = "BUILD",
                            sync_updated_at = Clock.System.now().toString(),
                            is_deleted = 0L,
                            sync_version = 0L,
                            last_synced_at = null
                        )
                    )
                    habitsImported++
                }
            }

            // Import journal entries
            val existingJournalIds = database.getAllJournalEntries().map { it.id }.toSet()
            backupData.journalEntries.forEach { journalBackup ->
                val shouldImport = when (mergeStrategy) {
                    MergeStrategy.SKIP_EXISTING -> journalBackup.id !in existingJournalIds
                    MergeStrategy.OVERWRITE_EXISTING -> true
                    MergeStrategy.KEEP_NEWEST -> journalBackup.id !in existingJournalIds
                }

                if (shouldImport) {
                    database.insertJournalEntry(
                        az.tribe.lifeplanner.database.JournalEntryEntity(
                            id = journalBackup.id,
                            title = "",
                            content = journalBackup.content,
                            mood = journalBackup.mood ?: "NEUTRAL",
                            linkedGoalId = journalBackup.linkedGoalId,
                            linkedHabitId = null,
                            promptUsed = journalBackup.promptUsed,
                            tags = "",
                            date = journalBackup.createdAt.take(10),
                            createdAt = journalBackup.createdAt,
                            updatedAt = journalBackup.updatedAt ?: journalBackup.createdAt,
                            sync_updated_at = Clock.System.now().toString(),
                            is_deleted = 0L,
                            sync_version = 0L,
                            last_synced_at = null
                        )
                    )
                    journalEntriesImported++
                }
            }

            // Import settings if present
            backupData.settings?.let { settingsBackup ->
                settings.putBoolean("notifications_enabled", settingsBackup.notificationsEnabled)
                settingsBackup.dailyReminderTime?.let { settings.putString("daily_reminder_time", it) }
                settingsBackup.theme?.let { settings.putString("theme", it) }
                settings.putBoolean("has_completed_onboarding", settingsBackup.hasCompletedOnboarding)
            }

            ImportResult.Success(
                goalsImported = goalsImported,
                habitsImported = habitsImported,
                journalEntriesImported = journalEntriesImported
            )
        } catch (e: Exception) {
            Logger.e("BackupRepository", e) { "Import failed: ${e.message}" }
            ImportResult.Error("Failed to import data: ${e.message}")
        }
    }

    override suspend fun validateBackup(jsonData: String): ValidationResult {
        return try {
            val backupData = json.decodeFromString<BackupData>(jsonData)
            // Validate basic data integrity
            if (backupData.goals.any { it.id.isBlank() }) {
                return ValidationResult.Invalid("Backup contains goals with missing IDs")
            }
            if (backupData.habits.any { it.id.isBlank() }) {
                return ValidationResult.Invalid("Backup contains habits with missing IDs")
            }
            ValidationResult.Valid(backupData)
        } catch (e: Exception) {
            Logger.e("BackupRepository", e) { "Backup validation failed: ${e.message}" }
            ValidationResult.Invalid("Invalid backup format: ${e.message}")
        }
    }

    override suspend fun getLastBackupDate(): String? {
        return settings.getStringOrNull("last_backup_date")
    }

    override suspend fun saveLastBackupDate(date: String) {
        settings.putString("last_backup_date", date)
    }

    override fun isAutoBackupEnabled(): Boolean {
        return settings.getBoolean("auto_backup_enabled", false)
    }

    override fun setAutoBackupEnabled(enabled: Boolean) {
        settings.putBoolean("auto_backup_enabled", enabled)
    }
}
