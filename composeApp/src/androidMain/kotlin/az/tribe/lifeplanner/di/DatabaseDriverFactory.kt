@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package az.tribe.lifeplanner.di

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import az.tribe.lifeplanner.database.LifePlannerDB
import co.touchlab.kermit.Logger
import org.koin.mp.KoinPlatform

private const val PREFS_NAME = "lifeplanner_db_prefs"
private const val KEY_DB_SCHEMA_VERSION = "db_schema_version"
private const val CURRENT_SCHEMA_VERSION = 2 // Increment this when you want to force a fresh DB for all users

actual class DatabaseDriverFactory {
    actual suspend fun createDriver(): SqlDriver {
        val context: Context = KoinPlatform.getKoin().get()

        // Check if we need to reset the database for users upgrading from v1
        resetDatabaseIfNeeded(context)

        return AndroidSqliteDriver(
            schema = LifePlannerDB.Schema.synchronous(),
            context = KoinPlatform.getKoin().get(),
            name = DB_NAME,
            callback = object : AndroidSqliteDriver.Callback(LifePlannerDB.Schema.synchronous()) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // Run migrations on every open to ensure schema is up to date
                    migrateToVersion5(db)
                    migrateToVersion6(db)
                    migrateToVersion7(db)
                    migrateToVersion8(db)
                    migrateToVersion9(db)
                    migrateToVersion10(db)
                    migrateToVersion11(db)
                    migrateToVersion12(db)
                    migrateToSyncColumns(db)
                    migrateToVersion13(db)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    // Migration from version 3 to 4: Add UserEntity table
                    if (oldVersion < 4) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS UserEntity (
                                id TEXT PRIMARY KEY NOT NULL,
                                firebaseUid TEXT UNIQUE,
                                email TEXT,
                                displayName TEXT,
                                isGuest INTEGER NOT NULL DEFAULT 0,
                                selectedSymbol TEXT,
                                priorities TEXT,
                                ageRange TEXT,
                                profession TEXT,
                                relationshipStatus TEXT,
                                mindset TEXT,
                                hasCompletedOnboarding INTEGER NOT NULL DEFAULT 0,
                                createdAt TEXT NOT NULL,
                                lastSyncedAt TEXT
                            )
                            """.trimIndent()
                        )

                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS idx_user_firebase_uid ON UserEntity(firebaseUid)"
                        )
                    }

                    // Migration from version 4 to 5: Add Gamification tables and columns
                    if (oldVersion < 5) {
                        // Add new columns to UserProgressEntity
                        db.execSQL("ALTER TABLE UserProgressEntity ADD COLUMN totalXp INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE UserProgressEntity ADD COLUMN currentLevel INTEGER NOT NULL DEFAULT 1")
                        db.execSQL("ALTER TABLE UserProgressEntity ADD COLUMN goalsCompleted INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE UserProgressEntity ADD COLUMN habitsCompleted INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE UserProgressEntity ADD COLUMN journalEntriesCount INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE UserProgressEntity ADD COLUMN longestStreak INTEGER NOT NULL DEFAULT 0")

                        // Create BadgeEntity table
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS BadgeEntity (
                                id TEXT PRIMARY KEY NOT NULL,
                                badgeType TEXT NOT NULL,
                                earnedAt TEXT NOT NULL,
                                isNew INTEGER NOT NULL DEFAULT 1
                            )
                            """.trimIndent()
                        )

                        // Create ChallengeEntity table
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS ChallengeEntity (
                                id TEXT PRIMARY KEY NOT NULL,
                                challengeType TEXT NOT NULL,
                                startDate TEXT NOT NULL,
                                endDate TEXT NOT NULL,
                                currentProgress INTEGER NOT NULL DEFAULT 0,
                                targetProgress INTEGER NOT NULL,
                                isCompleted INTEGER NOT NULL DEFAULT 0,
                                completedAt TEXT,
                                xpEarned INTEGER NOT NULL DEFAULT 0
                            )
                            """.trimIndent()
                        )

                        // Create indexes for gamification
                        db.execSQL("CREATE INDEX IF NOT EXISTS idx_badge_type ON BadgeEntity(badgeType)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS idx_badge_new ON BadgeEntity(isNew)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS idx_challenge_type ON ChallengeEntity(challengeType)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS idx_challenge_completed ON ChallengeEntity(isCompleted)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS idx_challenge_end_date ON ChallengeEntity(endDate)")
                    }
                }

                private fun migrateToVersion5(db: SupportSQLiteDatabase) {
                    // Create HabitEntity table if not exists
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS HabitEntity (
                            id TEXT PRIMARY KEY NOT NULL,
                            title TEXT NOT NULL,
                            description TEXT NOT NULL DEFAULT '',
                            category TEXT NOT NULL,
                            frequency TEXT NOT NULL DEFAULT 'DAILY',
                            targetCount INTEGER NOT NULL DEFAULT 1,
                            currentStreak INTEGER NOT NULL DEFAULT 0,
                            longestStreak INTEGER NOT NULL DEFAULT 0,
                            totalCompletions INTEGER NOT NULL DEFAULT 0,
                            lastCompletedDate TEXT,
                            linkedGoalId TEXT,
                            correlationScore REAL NOT NULL DEFAULT 0.0,
                            isActive INTEGER NOT NULL DEFAULT 1,
                            createdAt TEXT NOT NULL,
                            reminderTime TEXT,
                            FOREIGN KEY (linkedGoalId) REFERENCES GoalEntity(id) ON DELETE SET NULL
                        )
                        """.trimIndent()
                    )

                    // Create HabitCheckInEntity table if not exists
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS HabitCheckInEntity (
                            id TEXT PRIMARY KEY NOT NULL,
                            habitId TEXT NOT NULL,
                            date TEXT NOT NULL,
                            completed INTEGER NOT NULL DEFAULT 1,
                            notes TEXT NOT NULL DEFAULT '',
                            FOREIGN KEY (habitId) REFERENCES HabitEntity(id) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )

                    // Create JournalEntryEntity table if not exists
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS JournalEntryEntity (
                            id TEXT PRIMARY KEY NOT NULL,
                            title TEXT NOT NULL,
                            content TEXT NOT NULL,
                            mood TEXT NOT NULL DEFAULT 'NEUTRAL',
                            linkedGoalId TEXT,
                            promptUsed TEXT,
                            tags TEXT NOT NULL DEFAULT '',
                            date TEXT NOT NULL,
                            createdAt TEXT NOT NULL,
                            updatedAt TEXT,
                            FOREIGN KEY (linkedGoalId) REFERENCES GoalEntity(id) ON DELETE SET NULL
                        )
                        """.trimIndent()
                    )

                    // Safely add columns to UserProgressEntity if they don't exist
                    val columnsToAdd = listOf(
                        "totalXp" to "INTEGER NOT NULL DEFAULT 0",
                        "currentLevel" to "INTEGER NOT NULL DEFAULT 1",
                        "goalsCompleted" to "INTEGER NOT NULL DEFAULT 0",
                        "habitsCompleted" to "INTEGER NOT NULL DEFAULT 0",
                        "journalEntriesCount" to "INTEGER NOT NULL DEFAULT 0",
                        "longestStreak" to "INTEGER NOT NULL DEFAULT 0"
                    )

                    columnsToAdd.forEach { (columnName, columnDef) ->
                        if (!columnExists(db, "UserProgressEntity", columnName)) {
                            try {
                                db.execSQL("ALTER TABLE UserProgressEntity ADD COLUMN $columnName $columnDef")
                            } catch (e: Exception) {
                                // Column might already exist or table doesn't exist yet
                            }
                        }
                    }

                    // Create BadgeEntity table if not exists
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS BadgeEntity (
                            id TEXT PRIMARY KEY NOT NULL,
                            badgeType TEXT NOT NULL,
                            earnedAt TEXT NOT NULL,
                            isNew INTEGER NOT NULL DEFAULT 1
                        )
                        """.trimIndent()
                    )

                    // Create ChallengeEntity table if not exists
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS ChallengeEntity (
                            id TEXT PRIMARY KEY NOT NULL,
                            challengeType TEXT NOT NULL,
                            startDate TEXT NOT NULL,
                            endDate TEXT NOT NULL,
                            currentProgress INTEGER NOT NULL DEFAULT 0,
                            targetProgress INTEGER NOT NULL,
                            isCompleted INTEGER NOT NULL DEFAULT 0,
                            completedAt TEXT,
                            xpEarned INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent()
                    )

                    // Create indexes if not exist
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_habits_category ON HabitEntity(category)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_habits_linked_goal ON HabitEntity(linkedGoalId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_habits_active ON HabitEntity(isActive)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_checkins_habit ON HabitCheckInEntity(habitId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_checkins_date ON HabitCheckInEntity(date)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_journal_date ON JournalEntryEntity(date)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_journal_mood ON JournalEntryEntity(mood)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_journal_goal ON JournalEntryEntity(linkedGoalId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_badge_type ON BadgeEntity(badgeType)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_badge_new ON BadgeEntity(isNew)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_challenge_type ON ChallengeEntity(challengeType)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_challenge_completed ON ChallengeEntity(isCompleted)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_challenge_end_date ON ChallengeEntity(endDate)")
                }

                private fun columnExists(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
                    return try {
                        val cursor = db.query("PRAGMA table_info($tableName)")
                        cursor.use {
                            while (it.moveToNext()) {
                                val nameIndex = it.getColumnIndex("name")
                                if (nameIndex >= 0 && it.getString(nameIndex) == columnName) {
                                    return true
                                }
                            }
                        }
                        false
                    } catch (e: Exception) {
                        false
                    }
                }

                private fun migrateToVersion6(db: SupportSQLiteDatabase) {
                    // Create GoalDependencyEntity table if not exists
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS GoalDependencyEntity (
                            id TEXT PRIMARY KEY NOT NULL,
                            sourceGoalId TEXT NOT NULL,
                            targetGoalId TEXT NOT NULL,
                            dependencyType TEXT NOT NULL,
                            createdAt TEXT NOT NULL,
                            FOREIGN KEY (sourceGoalId) REFERENCES GoalEntity(id) ON DELETE CASCADE,
                            FOREIGN KEY (targetGoalId) REFERENCES GoalEntity(id) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )

                    // Create indexes for goal dependencies
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_dependency_source ON GoalDependencyEntity(sourceGoalId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_dependency_target ON GoalDependencyEntity(targetGoalId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_dependency_type ON GoalDependencyEntity(dependencyType)")
                }

                private fun migrateToVersion7(db: SupportSQLiteDatabase) {
                    // Create ChatSessionEntity table if not exists
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS ChatSessionEntity (
                            id TEXT PRIMARY KEY NOT NULL,
                            title TEXT NOT NULL,
                            createdAt TEXT NOT NULL,
                            lastMessageAt TEXT NOT NULL,
                            summary TEXT,
                            coachId TEXT NOT NULL DEFAULT 'luna_general'
                        )
                        """.trimIndent()
                    )

                    // Add coachId column if table was created without it (defensive migration)
                    if (!columnExists(db, "ChatSessionEntity", "coachId")) {
                        try {
                            db.execSQL("ALTER TABLE ChatSessionEntity ADD COLUMN coachId TEXT NOT NULL DEFAULT 'luna_general'")
                        } catch (e: Exception) {
                            // Column might already exist
                        }
                    }

                    // Create ChatMessageEntity table if not exists
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS ChatMessageEntity (
                            id TEXT PRIMARY KEY NOT NULL,
                            sessionId TEXT NOT NULL,
                            content TEXT NOT NULL,
                            role TEXT NOT NULL,
                            timestamp TEXT NOT NULL,
                            relatedGoalId TEXT,
                            metadata TEXT,
                            FOREIGN KEY (sessionId) REFERENCES ChatSessionEntity(id) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )

                    // Create ReviewReportEntity table if not exists
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS ReviewReportEntity (
                            id TEXT PRIMARY KEY NOT NULL,
                            type TEXT NOT NULL,
                            periodStart TEXT NOT NULL,
                            periodEnd TEXT NOT NULL,
                            generatedAt TEXT NOT NULL,
                            summary TEXT NOT NULL,
                            highlightsJson TEXT NOT NULL,
                            insightsJson TEXT NOT NULL,
                            recommendationsJson TEXT NOT NULL,
                            statsJson TEXT NOT NULL,
                            feedbackRating TEXT,
                            feedbackComment TEXT,
                            feedbackAt TEXT,
                            isRead INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent()
                    )

                    // Create indexes for chat
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_session_last_message ON ChatSessionEntity(lastMessageAt)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_session_coach ON ChatSessionEntity(coachId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_message_session ON ChatMessageEntity(sessionId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_message_timestamp ON ChatMessageEntity(timestamp)")

                    // Create indexes for reviews
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_review_type ON ReviewReportEntity(type)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_review_generated ON ReviewReportEntity(generatedAt)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_review_unread ON ReviewReportEntity(isRead)")
                }

                private fun migrateToVersion8(db: SupportSQLiteDatabase) {
                    // Create ReminderEntity table if not exists
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS ReminderEntity (
                            id TEXT PRIMARY KEY NOT NULL,
                            title TEXT NOT NULL,
                            message TEXT NOT NULL,
                            type TEXT NOT NULL,
                            frequency TEXT NOT NULL,
                            scheduledTime TEXT NOT NULL,
                            scheduledDays TEXT NOT NULL DEFAULT '',
                            linkedGoalId TEXT,
                            linkedHabitId TEXT,
                            isEnabled INTEGER NOT NULL DEFAULT 1,
                            isSmartTiming INTEGER NOT NULL DEFAULT 0,
                            lastTriggeredAt TEXT,
                            snoozedUntil TEXT,
                            createdAt TEXT NOT NULL,
                            updatedAt TEXT,
                            FOREIGN KEY (linkedGoalId) REFERENCES GoalEntity(id) ON DELETE SET NULL,
                            FOREIGN KEY (linkedHabitId) REFERENCES HabitEntity(id) ON DELETE SET NULL
                        )
                        """.trimIndent()
                    )

                    // Create ReminderSettingsEntity table if not exists
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS ReminderSettingsEntity (
                            id TEXT PRIMARY KEY NOT NULL DEFAULT 'default',
                            isEnabled INTEGER NOT NULL DEFAULT 1,
                            quietHoursStart TEXT NOT NULL DEFAULT '22:00',
                            quietHoursEnd TEXT NOT NULL DEFAULT '07:00',
                            preferredMorningTime TEXT NOT NULL DEFAULT '08:00',
                            preferredEveningTime TEXT NOT NULL DEFAULT '20:00',
                            smartTimingEnabled INTEGER NOT NULL DEFAULT 1,
                            maxRemindersPerDay INTEGER NOT NULL DEFAULT 5,
                            weeklyReviewDay TEXT NOT NULL DEFAULT 'SUNDAY',
                            weeklyReviewTime TEXT NOT NULL DEFAULT '10:00'
                        )
                        """.trimIndent()
                    )

                    // Create UserActivityPatternEntity table if not exists
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS UserActivityPatternEntity (
                            id TEXT PRIMARY KEY NOT NULL DEFAULT 'default',
                            mostActiveHours TEXT NOT NULL DEFAULT '',
                            mostActiveDays TEXT NOT NULL DEFAULT '',
                            averageResponseTime INTEGER NOT NULL DEFAULT 0,
                            bestCheckInTimes TEXT NOT NULL DEFAULT '',
                            lastUpdated TEXT NOT NULL
                        )
                        """.trimIndent()
                    )

                    // Create ScheduledNotificationEntity table if not exists
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS ScheduledNotificationEntity (
                            id TEXT PRIMARY KEY NOT NULL,
                            reminderId TEXT NOT NULL,
                            title TEXT NOT NULL,
                            message TEXT NOT NULL,
                            scheduledAt TEXT NOT NULL,
                            isDelivered INTEGER NOT NULL DEFAULT 0,
                            deliveredAt TEXT,
                            isSnoozed INTEGER NOT NULL DEFAULT 0,
                            isDismissed INTEGER NOT NULL DEFAULT 0,
                            FOREIGN KEY (reminderId) REFERENCES ReminderEntity(id) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )

                    // Create indexes for reminders
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_reminder_type ON ReminderEntity(type)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_reminder_enabled ON ReminderEntity(isEnabled)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_reminder_goal ON ReminderEntity(linkedGoalId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_reminder_habit ON ReminderEntity(linkedHabitId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_scheduled_reminder ON ScheduledNotificationEntity(reminderId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_scheduled_time ON ScheduledNotificationEntity(scheduledAt)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_scheduled_delivered ON ScheduledNotificationEntity(isDelivered)")
                }

                private fun migrateToVersion9(db: SupportSQLiteDatabase) {
                    // Add linkedHabitId column to JournalEntryEntity if it doesn't exist
                    if (!columnExists(db, "JournalEntryEntity", "linkedHabitId")) {
                        try {
                            db.execSQL("ALTER TABLE JournalEntryEntity ADD COLUMN linkedHabitId TEXT")
                            db.execSQL("CREATE INDEX IF NOT EXISTS idx_journal_habit ON JournalEntryEntity(linkedHabitId)")
                        } catch (e: Exception) {
                            // Column might already exist
                        }
                    }
                }

                private fun migrateToVersion10(db: SupportSQLiteDatabase) {
                    // Create CustomCoachEntity table
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS CustomCoachEntity (
                            id TEXT PRIMARY KEY NOT NULL,
                            name TEXT NOT NULL,
                            icon TEXT NOT NULL,
                            iconBackgroundColor TEXT NOT NULL DEFAULT '#6366F1',
                            iconAccentColor TEXT NOT NULL DEFAULT '#818CF8',
                            systemPrompt TEXT NOT NULL,
                            characteristics TEXT NOT NULL DEFAULT '',
                            isFromTemplate INTEGER NOT NULL DEFAULT 0,
                            templateId TEXT,
                            createdAt TEXT NOT NULL,
                            updatedAt TEXT
                        )
                        """.trimIndent()
                    )

                    // Create CoachGroupEntity table
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS CoachGroupEntity (
                            id TEXT PRIMARY KEY NOT NULL,
                            name TEXT NOT NULL,
                            icon TEXT NOT NULL,
                            description TEXT NOT NULL DEFAULT '',
                            createdAt TEXT NOT NULL,
                            updatedAt TEXT
                        )
                        """.trimIndent()
                    )

                    // Create CoachGroupMemberEntity table
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS CoachGroupMemberEntity (
                            id TEXT PRIMARY KEY NOT NULL,
                            groupId TEXT NOT NULL,
                            coachType TEXT NOT NULL,
                            coachId TEXT NOT NULL,
                            displayOrder INTEGER NOT NULL DEFAULT 0,
                            FOREIGN KEY (groupId) REFERENCES CoachGroupEntity(id) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )

                    // Create indexes
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_custom_coach_template ON CustomCoachEntity(templateId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_coach_group_member_group ON CoachGroupMemberEntity(groupId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_coach_group_member_coach ON CoachGroupMemberEntity(coachId)")
                }

                private fun migrateToVersion12(db: SupportSQLiteDatabase) {
                    // Add mood, ambientSound, focusTheme columns to FocusSessionEntity
                    try {
                        db.execSQL("ALTER TABLE FocusSessionEntity ADD COLUMN mood TEXT")
                    } catch (_: Exception) { /* Column already exists */ }
                    try {
                        db.execSQL("ALTER TABLE FocusSessionEntity ADD COLUMN ambientSound TEXT")
                    } catch (_: Exception) { /* Column already exists */ }
                    try {
                        db.execSQL("ALTER TABLE FocusSessionEntity ADD COLUMN focusTheme TEXT")
                    } catch (_: Exception) { /* Column already exists */ }
                }

                private fun migrateToSyncColumns(db: SupportSQLiteDatabase) {
                    val tablesWithLastSynced = listOf(
                        "GoalEntity", "MilestoneEntity", "GoalHistoryEntity",
                        "UserProgressEntity", "HabitEntity", "HabitCheckInEntity",
                        "JournalEntryEntity", "BadgeEntity", "ChallengeEntity",
                        "GoalDependencyEntity", "ChatSessionEntity", "ChatMessageEntity",
                        "ReviewReportEntity", "ReminderEntity", "CustomCoachEntity",
                        "CoachGroupEntity", "CoachGroupMemberEntity", "FocusSessionEntity"
                    )
                    for (table in tablesWithLastSynced) {
                        addColumnSafe(db, table, "sync_updated_at", "TEXT")
                        addColumnSafe(db, table, "is_deleted", "INTEGER NOT NULL DEFAULT 0")
                        addColumnSafe(db, table, "sync_version", "INTEGER NOT NULL DEFAULT 0")
                        addColumnSafe(db, table, "last_synced_at", "TEXT")
                    }
                    // UserEntity gets the same minus last_synced_at (already has lastSyncedAt)
                    addColumnSafe(db, "UserEntity", "sync_updated_at", "TEXT")
                    addColumnSafe(db, "UserEntity", "is_deleted", "INTEGER NOT NULL DEFAULT 0")
                    addColumnSafe(db, "UserEntity", "sync_version", "INTEGER NOT NULL DEFAULT 0")
                }

                private fun addColumnSafe(db: SupportSQLiteDatabase, table: String, column: String, def: String) {
                    if (!columnExists(db, table, column)) {
                        try {
                            db.execSQL("ALTER TABLE $table ADD COLUMN $column $def")
                        } catch (_: Exception) { }
                    }
                }

                private fun migrateToVersion13(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS CoachPersonaOverrideEntity (
                            coachId TEXT PRIMARY KEY NOT NULL,
                            userPersona TEXT NOT NULL DEFAULT '',
                            updatedAt TEXT NOT NULL,
                            sync_updated_at TEXT,
                            is_deleted INTEGER NOT NULL DEFAULT 0,
                            sync_version INTEGER NOT NULL DEFAULT 0,
                            last_synced_at TEXT
                        )
                        """.trimIndent()
                    )
                }

                private fun migrateToVersion11(db: SupportSQLiteDatabase) {
                    // Create FocusSessionEntity table
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS FocusSessionEntity (
                            id TEXT PRIMARY KEY NOT NULL,
                            goalId TEXT NOT NULL,
                            milestoneId TEXT NOT NULL,
                            plannedDurationMinutes INTEGER NOT NULL,
                            actualDurationSeconds INTEGER NOT NULL DEFAULT 0,
                            wasCompleted INTEGER NOT NULL DEFAULT 0,
                            xpEarned INTEGER NOT NULL DEFAULT 0,
                            startedAt TEXT NOT NULL,
                            completedAt TEXT,
                            createdAt TEXT NOT NULL,
                            FOREIGN KEY (goalId) REFERENCES GoalEntity(id) ON DELETE CASCADE,
                            FOREIGN KEY (milestoneId) REFERENCES MilestoneEntity(id) ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )

                    // Create indexes
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_focus_goal ON FocusSessionEntity(goalId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_focus_milestone ON FocusSessionEntity(milestoneId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_focus_completed ON FocusSessionEntity(wasCompleted)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_focus_started ON FocusSessionEntity(startedAt)")
                }
            }
        )
    }

    /**
     * Reset database for users upgrading from older versions (v1).
     * This ensures a clean slate without migration errors.
     */
    private fun resetDatabaseIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedVersion = prefs.getInt(KEY_DB_SCHEMA_VERSION, 0)

        if (storedVersion < CURRENT_SCHEMA_VERSION) {
            Logger.i("DatabaseDriverFactory") { "Upgrading from schema version $storedVersion to $CURRENT_SCHEMA_VERSION" }

            // Delete the old database file
            val dbFile = context.getDatabasePath(DB_NAME)
            if (dbFile.exists()) {
                Logger.i("DatabaseDriverFactory") { "Deleting old database for clean upgrade" }
                context.deleteDatabase(DB_NAME)
            }

            // Also delete any journal/wal files
            val dbDir = dbFile.parentFile
            dbDir?.listFiles()?.forEach { file ->
                if (file.name.startsWith(DB_NAME)) {
                    file.delete()
                }
            }

            // Update stored version
            prefs.edit().putInt(KEY_DB_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION).apply()
            Logger.i("DatabaseDriverFactory") { "Database reset complete, now at schema version $CURRENT_SCHEMA_VERSION" }
        }
    }
}