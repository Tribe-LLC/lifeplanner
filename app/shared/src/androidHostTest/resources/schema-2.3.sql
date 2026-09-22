-- The database a 2.3 install has, at schema version 18.
--
-- Copied verbatim from the `.sq` schema at commit 003b892 (2026-04-02), the last commit before
-- the first 2.3 event reached PostHog on 2026-04-03. At that commit the highest migration file
-- was 17.sqm, and SQLDelight derives Schema.version as the highest migration plus one, so a 2.3
-- device sits at 18 and migrates forward from there.
--
-- This file is history and must never be edited to make a test pass. If the `.sqm` chain cannot
-- carry this schema to the current one, that is the bug, on every phone still running 2.3.

CREATE TABLE GoalEntity (
    id TEXT NOT NULL PRIMARY KEY,
    category TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    status TEXT NOT NULL,
    timeline TEXT NOT NULL,
    dueDate TEXT NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    notes TEXT NOT NULL DEFAULT '',
    createdAt TEXT NOT NULL DEFAULT '2025-01-01T00:00:00',
    completionRate REAL NOT NULL DEFAULT 0.0,
    isArchived INTEGER NOT NULL DEFAULT 0,
    aiReasoning TEXT,
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT
);

CREATE TABLE IF NOT EXISTS MilestoneEntity (
    id TEXT PRIMARY KEY NOT NULL,
    goalId TEXT NOT NULL,
    title TEXT NOT NULL,
    isCompleted INTEGER NOT NULL DEFAULT 0,
    dueDate TEXT,
    createdAt TEXT NOT NULL,
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT,
    FOREIGN KEY (goalId) REFERENCES GoalEntity(id) ON DELETE CASCADE
);

CREATE TABLE GoalHistoryEntity (
    id TEXT NOT NULL PRIMARY KEY,
    goalId TEXT NOT NULL,
    field TEXT NOT NULL,
    oldValue TEXT,
    newValue TEXT,
    changedAt TEXT NOT NULL,
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT
);

CREATE TABLE UserProgressEntity (
    id INTEGER PRIMARY KEY DEFAULT 1,
    currentStreak INTEGER NOT NULL DEFAULT 0,
    lastCheckInDate TEXT,
    totalXp INTEGER NOT NULL DEFAULT 0,
    currentLevel INTEGER NOT NULL DEFAULT 1,
    goalsCompleted INTEGER NOT NULL DEFAULT 0,
    habitsCompleted INTEGER NOT NULL DEFAULT 0,
    journalEntriesCount INTEGER NOT NULL DEFAULT 0,
    longestStreak INTEGER NOT NULL DEFAULT 0,
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT
);

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
    lastSyncedAt TEXT,
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0
);

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
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT,
    FOREIGN KEY (linkedGoalId) REFERENCES GoalEntity(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS HabitCheckInEntity (
    id TEXT PRIMARY KEY NOT NULL,
    habitId TEXT NOT NULL,
    date TEXT NOT NULL,
    completed INTEGER NOT NULL DEFAULT 1,
    notes TEXT NOT NULL DEFAULT '',
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT,
    FOREIGN KEY (habitId) REFERENCES HabitEntity(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS JournalEntryEntity (
    id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    mood TEXT NOT NULL DEFAULT 'NEUTRAL',
    linkedGoalId TEXT,
    linkedHabitId TEXT,
    promptUsed TEXT,
    tags TEXT NOT NULL DEFAULT '',
    date TEXT NOT NULL,
    createdAt TEXT NOT NULL,
    updatedAt TEXT,
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT,
    FOREIGN KEY (linkedGoalId) REFERENCES GoalEntity(id) ON DELETE SET NULL,
    FOREIGN KEY (linkedHabitId) REFERENCES HabitEntity(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS BadgeEntity (
    id TEXT PRIMARY KEY NOT NULL,
    badgeType TEXT NOT NULL,
    earnedAt TEXT NOT NULL,
    isNew INTEGER NOT NULL DEFAULT 1,
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT
);

CREATE TABLE IF NOT EXISTS ChallengeEntity (
    id TEXT PRIMARY KEY NOT NULL,
    challengeType TEXT NOT NULL,
    startDate TEXT NOT NULL,
    endDate TEXT NOT NULL,
    currentProgress INTEGER NOT NULL DEFAULT 0,
    targetProgress INTEGER NOT NULL,
    isCompleted INTEGER NOT NULL DEFAULT 0,
    completedAt TEXT,
    xpEarned INTEGER NOT NULL DEFAULT 0,
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT
);

CREATE TABLE IF NOT EXISTS GoalDependencyEntity (
    id TEXT PRIMARY KEY NOT NULL,
    sourceGoalId TEXT NOT NULL,
    targetGoalId TEXT NOT NULL,
    dependencyType TEXT NOT NULL,
    createdAt TEXT NOT NULL,
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT,
    FOREIGN KEY (sourceGoalId) REFERENCES GoalEntity(id) ON DELETE CASCADE,
    FOREIGN KEY (targetGoalId) REFERENCES GoalEntity(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ChatSessionEntity (
    id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    createdAt TEXT NOT NULL,
    lastMessageAt TEXT NOT NULL,
    summary TEXT,
    coachId TEXT NOT NULL DEFAULT 'luna_general',
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT
);

CREATE TABLE IF NOT EXISTS ChatMessageEntity (
    id TEXT PRIMARY KEY NOT NULL,
    sessionId TEXT NOT NULL,
    content TEXT NOT NULL,
    role TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    relatedGoalId TEXT,
    metadata TEXT,
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT,
    FOREIGN KEY (sessionId) REFERENCES ChatSessionEntity(id) ON DELETE CASCADE
);

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
    isRead INTEGER NOT NULL DEFAULT 0,
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT
);

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
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT,
    FOREIGN KEY (linkedGoalId) REFERENCES GoalEntity(id) ON DELETE SET NULL,
    FOREIGN KEY (linkedHabitId) REFERENCES HabitEntity(id) ON DELETE SET NULL
);

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
);

CREATE TABLE IF NOT EXISTS UserActivityPatternEntity (
    id TEXT PRIMARY KEY NOT NULL DEFAULT 'default',
    mostActiveHours TEXT NOT NULL DEFAULT '',
    mostActiveDays TEXT NOT NULL DEFAULT '',
    averageResponseTime INTEGER NOT NULL DEFAULT 0,
    bestCheckInTimes TEXT NOT NULL DEFAULT '',
    lastUpdated TEXT NOT NULL
);

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
);

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
    updatedAt TEXT,
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT
);

CREATE TABLE IF NOT EXISTS CoachGroupEntity (
    id TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    icon TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    createdAt TEXT NOT NULL,
    updatedAt TEXT,
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT
);

CREATE TABLE IF NOT EXISTS CoachGroupMemberEntity (
    id TEXT PRIMARY KEY NOT NULL,
    groupId TEXT NOT NULL,
    coachType TEXT NOT NULL,
    coachId TEXT NOT NULL,
    displayOrder INTEGER NOT NULL DEFAULT 0,
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT,
    FOREIGN KEY (groupId) REFERENCES CoachGroupEntity(id) ON DELETE CASCADE
);

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
    mood TEXT,
    ambientSound TEXT,
    focusTheme TEXT,
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT,
    FOREIGN KEY (goalId) REFERENCES GoalEntity(id) ON DELETE CASCADE,
    FOREIGN KEY (milestoneId) REFERENCES MilestoneEntity(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS CoachPersonaOverrideEntity (
    coachId TEXT PRIMARY KEY NOT NULL,
    userPersona TEXT NOT NULL DEFAULT '',
    updatedAt TEXT NOT NULL,
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT
);

CREATE TABLE IF NOT EXISTS BeginnerObjectiveEntity (
    id TEXT PRIMARY KEY NOT NULL,
    objectiveType TEXT NOT NULL,
    isCompleted INTEGER NOT NULL DEFAULT 0,
    completedAt TEXT,
    xpAwarded INTEGER NOT NULL DEFAULT 0,
    createdAt TEXT NOT NULL,
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT
);

CREATE TABLE IF NOT EXISTS HealthMetricEntity (
    id TEXT PRIMARY KEY NOT NULL,
    metricType TEXT NOT NULL,
    value_ REAL NOT NULL,
    unit TEXT NOT NULL,
    date TEXT NOT NULL,
    source TEXT NOT NULL DEFAULT 'PLATFORM',
    recordedAt TEXT NOT NULL,
    createdAt TEXT NOT NULL,
    sync_updated_at TEXT,
    is_deleted INTEGER NOT NULL DEFAULT 0,
    sync_version INTEGER NOT NULL DEFAULT 0,
    last_synced_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_goals_title ON GoalEntity(title);
CREATE INDEX IF NOT EXISTS idx_goals_status ON GoalEntity(status);
CREATE INDEX IF NOT EXISTS idx_goals_category ON GoalEntity(category);
CREATE INDEX IF NOT EXISTS idx_goals_timeline ON GoalEntity(timeline);
CREATE INDEX IF NOT EXISTS idx_goals_duedate ON GoalEntity(dueDate);
CREATE INDEX IF NOT EXISTS idx_goals_archived ON GoalEntity(isArchived);
CREATE INDEX IF NOT EXISTS idx_milestones_goalid ON MilestoneEntity(goalId);
CREATE INDEX IF NOT EXISTS idx_user_firebase_uid ON UserEntity(firebaseUid);
CREATE INDEX IF NOT EXISTS idx_habits_category ON HabitEntity(category);
CREATE INDEX IF NOT EXISTS idx_habits_linked_goal ON HabitEntity(linkedGoalId);
CREATE INDEX IF NOT EXISTS idx_habits_active ON HabitEntity(isActive);
CREATE UNIQUE INDEX IF NOT EXISTS idx_checkins_habit_date ON HabitCheckInEntity(habitId, date);
CREATE INDEX IF NOT EXISTS idx_checkins_habit ON HabitCheckInEntity(habitId);
CREATE INDEX IF NOT EXISTS idx_checkins_date ON HabitCheckInEntity(date);
CREATE INDEX IF NOT EXISTS idx_journal_date ON JournalEntryEntity(date);
CREATE INDEX IF NOT EXISTS idx_journal_mood ON JournalEntryEntity(mood);
CREATE INDEX IF NOT EXISTS idx_journal_goal ON JournalEntryEntity(linkedGoalId);
CREATE INDEX IF NOT EXISTS idx_journal_habit ON JournalEntryEntity(linkedHabitId);
CREATE INDEX IF NOT EXISTS idx_badge_type ON BadgeEntity(badgeType);
CREATE INDEX IF NOT EXISTS idx_badge_new ON BadgeEntity(isNew);
CREATE INDEX IF NOT EXISTS idx_challenge_type ON ChallengeEntity(challengeType);
CREATE INDEX IF NOT EXISTS idx_challenge_completed ON ChallengeEntity(isCompleted);
CREATE INDEX IF NOT EXISTS idx_challenge_end_date ON ChallengeEntity(endDate);
CREATE INDEX IF NOT EXISTS idx_dependency_source ON GoalDependencyEntity(sourceGoalId);
CREATE INDEX IF NOT EXISTS idx_dependency_target ON GoalDependencyEntity(targetGoalId);
CREATE INDEX IF NOT EXISTS idx_dependency_type ON GoalDependencyEntity(dependencyType);
CREATE INDEX IF NOT EXISTS idx_chat_session_last_message ON ChatSessionEntity(lastMessageAt);
CREATE INDEX IF NOT EXISTS idx_chat_message_session ON ChatMessageEntity(sessionId);
CREATE INDEX IF NOT EXISTS idx_chat_message_timestamp ON ChatMessageEntity(timestamp);
CREATE INDEX IF NOT EXISTS idx_review_type ON ReviewReportEntity(type);
CREATE INDEX IF NOT EXISTS idx_review_generated ON ReviewReportEntity(generatedAt);
CREATE INDEX IF NOT EXISTS idx_review_unread ON ReviewReportEntity(isRead);
CREATE INDEX IF NOT EXISTS idx_reminder_type ON ReminderEntity(type);
CREATE INDEX IF NOT EXISTS idx_reminder_enabled ON ReminderEntity(isEnabled);
CREATE INDEX IF NOT EXISTS idx_reminder_goal ON ReminderEntity(linkedGoalId);
CREATE INDEX IF NOT EXISTS idx_reminder_habit ON ReminderEntity(linkedHabitId);
CREATE INDEX IF NOT EXISTS idx_scheduled_reminder ON ScheduledNotificationEntity(reminderId);
CREATE INDEX IF NOT EXISTS idx_scheduled_time ON ScheduledNotificationEntity(scheduledAt);
CREATE INDEX IF NOT EXISTS idx_scheduled_delivered ON ScheduledNotificationEntity(isDelivered);
CREATE INDEX IF NOT EXISTS idx_custom_coach_template ON CustomCoachEntity(templateId);
CREATE INDEX IF NOT EXISTS idx_coach_group_member_group ON CoachGroupMemberEntity(groupId);
CREATE INDEX IF NOT EXISTS idx_coach_group_member_coach ON CoachGroupMemberEntity(coachId);
CREATE INDEX IF NOT EXISTS idx_focus_goal ON FocusSessionEntity(goalId);
CREATE INDEX IF NOT EXISTS idx_focus_milestone ON FocusSessionEntity(milestoneId);
CREATE INDEX IF NOT EXISTS idx_focus_completed ON FocusSessionEntity(wasCompleted);
CREATE INDEX IF NOT EXISTS idx_focus_started ON FocusSessionEntity(startedAt);
CREATE INDEX IF NOT EXISTS idx_goal_sync ON GoalEntity(is_deleted, last_synced_at);
CREATE INDEX IF NOT EXISTS idx_milestone_sync ON MilestoneEntity(is_deleted, last_synced_at);
CREATE INDEX IF NOT EXISTS idx_goal_history_sync ON GoalHistoryEntity(is_deleted, last_synced_at);
CREATE INDEX IF NOT EXISTS idx_user_progress_sync ON UserProgressEntity(is_deleted, last_synced_at);
CREATE INDEX IF NOT EXISTS idx_user_sync ON UserEntity(is_deleted, lastSyncedAt);
CREATE INDEX IF NOT EXISTS idx_habit_sync ON HabitEntity(is_deleted, last_synced_at);
CREATE INDEX IF NOT EXISTS idx_habit_checkin_sync ON HabitCheckInEntity(is_deleted, last_synced_at);
CREATE INDEX IF NOT EXISTS idx_journal_sync ON JournalEntryEntity(is_deleted, last_synced_at);
CREATE INDEX IF NOT EXISTS idx_badge_sync ON BadgeEntity(is_deleted, last_synced_at);
CREATE INDEX IF NOT EXISTS idx_challenge_sync ON ChallengeEntity(is_deleted, last_synced_at);
CREATE INDEX IF NOT EXISTS idx_goal_dependency_sync ON GoalDependencyEntity(is_deleted, last_synced_at);
CREATE INDEX IF NOT EXISTS idx_chat_session_sync ON ChatSessionEntity(is_deleted, last_synced_at);
CREATE INDEX IF NOT EXISTS idx_chat_message_sync ON ChatMessageEntity(is_deleted, last_synced_at);
CREATE INDEX IF NOT EXISTS idx_review_sync ON ReviewReportEntity(is_deleted, last_synced_at);
CREATE INDEX IF NOT EXISTS idx_reminder_sync ON ReminderEntity(is_deleted, last_synced_at);
CREATE INDEX IF NOT EXISTS idx_custom_coach_sync ON CustomCoachEntity(is_deleted, last_synced_at);
CREATE INDEX IF NOT EXISTS idx_coach_group_sync ON CoachGroupEntity(is_deleted, last_synced_at);
CREATE INDEX IF NOT EXISTS idx_coach_group_member_sync ON CoachGroupMemberEntity(is_deleted, last_synced_at);
CREATE INDEX IF NOT EXISTS idx_focus_session_sync ON FocusSessionEntity(is_deleted, last_synced_at);
CREATE INDEX IF NOT EXISTS idx_goals_sync_updated ON GoalEntity(sync_updated_at);
CREATE INDEX IF NOT EXISTS idx_milestones_sync_updated ON MilestoneEntity(sync_updated_at);
CREATE INDEX IF NOT EXISTS idx_habits_sync_updated ON HabitEntity(sync_updated_at);
CREATE INDEX IF NOT EXISTS idx_journal_sync_updated ON JournalEntryEntity(sync_updated_at);
CREATE INDEX IF NOT EXISTS idx_checkins_habit_date ON HabitCheckInEntity(habitId, date);
CREATE INDEX IF NOT EXISTS idx_goals_deleted ON GoalEntity(is_deleted);
CREATE INDEX IF NOT EXISTS idx_milestones_deleted ON MilestoneEntity(is_deleted);
CREATE INDEX IF NOT EXISTS idx_habits_deleted ON HabitEntity(is_deleted);
CREATE INDEX IF NOT EXISTS idx_journal_deleted ON JournalEntryEntity(is_deleted);
CREATE INDEX IF NOT EXISTS idx_focus_date ON FocusSessionEntity(startedAt, wasCompleted);
CREATE INDEX IF NOT EXISTS idx_health_metric_type ON HealthMetricEntity(metricType);
CREATE INDEX IF NOT EXISTS idx_health_metric_date ON HealthMetricEntity(date);
