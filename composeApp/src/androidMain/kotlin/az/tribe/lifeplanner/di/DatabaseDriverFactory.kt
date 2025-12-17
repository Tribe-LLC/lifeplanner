@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package az.tribe.lifeplanner.di

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import az.tribe.lifeplanner.database.LifePlannerDB
import org.koin.mp.KoinPlatform


actual class DatabaseDriverFactory {
    actual suspend fun createDriver(): SqlDriver {
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
                            summary TEXT
                        )
                        """.trimIndent()
                    )

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
            }
        )
    }
}