package az.tribe.lifeplanner.infrastructure

import az.tribe.lifeplanner.domain.model.GoalChange
import az.tribe.lifeplanner.database.LifePlannerDB
import az.tribe.lifeplanner.di.DatabaseDriverFactory
import az.tribe.lifeplanner.database.GoalEntity
import az.tribe.lifeplanner.database.MilestoneEntity
import az.tribe.lifeplanner.database.HabitEntity
import az.tribe.lifeplanner.database.HabitCheckInEntity
import az.tribe.lifeplanner.database.JournalEntryEntity
import az.tribe.lifeplanner.database.BadgeEntity
import az.tribe.lifeplanner.database.ChallengeEntity
import az.tribe.lifeplanner.database.UserProgressEntity
import az.tribe.lifeplanner.database.ChatMessageEntity
import az.tribe.lifeplanner.database.ChatSessionEntity
import az.tribe.lifeplanner.database.GoalDependencyEntity
import az.tribe.lifeplanner.database.ReviewReportEntity
import az.tribe.lifeplanner.database.ReminderEntity
import az.tribe.lifeplanner.database.ReminderSettingsEntity
import az.tribe.lifeplanner.database.ScheduledNotificationEntity
import az.tribe.lifeplanner.database.UserActivityPatternEntity

class SharedDatabase(
    private val driverProvider: DatabaseDriverFactory,
) {
    private var database: LifePlannerDB? = null

    private suspend fun initDatabase() {
        if (database == null) {
            database = LifePlannerDB.invoke(
                driver = driverProvider.createDriver()
            )
        }
    }

    suspend operator fun <R> invoke(block: suspend (LifePlannerDB) -> R): R {
        initDatabase()
        return block(database!!)
    }

    // --- GoalEntity accessors (Updated) ---

    suspend fun getAllGoals(): List<GoalEntity> {
        return this { db -> db.lifePlannerDBQueries.selectAll().executeAsList() }
    }

    suspend fun deleteAllGoals() {
        this { db ->
            db.lifePlannerDBQueries.deleteAllGoals()
        }
    }

    // Updated insertGoal method to handle new fields
    suspend fun insertGoal(goal: GoalEntity) {
        this { db ->
            db.lifePlannerDBQueries.insertGoal(
                id = goal.id,
                category = goal.category,
                title = goal.title,
                description = goal.description,
                status = goal.status,
                timeline = goal.timeline,
                dueDate = goal.dueDate,
                progress = goal.progress,
                notes = goal.notes ?: "",
                createdAt = goal.createdAt,
                completionRate = goal.completionRate ?: 0.0,
                isArchived = goal.isArchived
            )
        }
    }

    suspend fun insertGoals(goals: List<GoalEntity>) {
        this { db ->
            goals.forEach { goal ->
                db.lifePlannerDBQueries.insertGoal(
                    id = goal.id,
                    category = goal.category,
                    title = goal.title,
                    description = goal.description,
                    status = goal.status,
                    timeline = goal.timeline,
                    dueDate = goal.dueDate,
                    progress = goal.progress,
                    notes = goal.notes,
                    createdAt = goal.createdAt,
                    completionRate = goal.completionRate,
                    isArchived = goal.isArchived
                )
            }
        }
    }

    suspend fun getGoalsByTimeline(timeline: String): List<GoalEntity> {
        return this { db ->
            db.lifePlannerDBQueries.selectGoalsByTimeline(timeline).executeAsList()
        }
    }

    suspend fun getGoalsByCategory(category: String): List<GoalEntity> {
        return this { db ->
            db.lifePlannerDBQueries.selectGoalsByCategory(category).executeAsList()
        }
    }

    suspend fun deleteGoalById(id: String) {
        this { db ->
            db.lifePlannerDBQueries.deleteGoalById(id)
        }
    }

    // Updated updateGoal method
    suspend fun updateGoal(goal: GoalEntity) {
        this { db ->
            db.lifePlannerDBQueries.updateGoal(
                category = goal.category,
                title = goal.title,
                description = goal.description,
                status = goal.status,
                timeline = goal.timeline,
                dueDate = goal.dueDate,
                progress = goal.progress,
                notes = goal.notes ?: "",
                completionRate = goal.completionRate,
                isArchived = goal.isArchived,
                id = goal.id,
                createdAt = goal.createdAt
            )
        }
    }

    // Updated updateGoalProgress method
    suspend fun updateGoalProgress(id: String, progress: Long, completionRate: Double = 0.0) {
        this { db ->
            db.lifePlannerDBQueries.updateGoalProgress(
                progress = progress,
                completionRate = completionRate,
                id = id
            )
        }
    }

    // New methods for enhanced functionality
    suspend fun updateGoalNotes(id: String, notes: String) {
        this { db ->
            db.lifePlannerDBQueries.updateGoalNotes(notes = notes, id = id)
        }
    }

    suspend fun archiveGoal(id: String) {
        this { db ->
            db.lifePlannerDBQueries.archiveGoal(id)
        }
    }

    suspend fun unarchiveGoal(id: String) {
        this { db ->
            db.lifePlannerDBQueries.unarchiveGoal(id)
        }
    }

    // Search methods
    suspend fun searchGoals(query: String): List<GoalEntity> {
        return this { db ->
            db.lifePlannerDBQueries.searchGoals(query, query).executeAsList()
        }
    }

    suspend fun getActiveGoals(): List<GoalEntity> {
        return this { db ->
            db.lifePlannerDBQueries.getActiveGoals().executeAsList()
        }
    }

    suspend fun getCompletedGoals(): List<GoalEntity> {
        return this { db ->
            db.lifePlannerDBQueries.getCompletedGoals().executeAsList()
        }
    }

    suspend fun getUpcomingDeadlines(startDate: String, endDate: String): List<GoalEntity> {
        return this { db ->
            db.lifePlannerDBQueries.getUpcomingDeadlines(startDate, endDate).executeAsList()
        }
    }

    // Analytics methods (using simplified approach)
    suspend fun getTotalGoalCount(): Long {
        return this { db ->
            db.lifePlannerDBQueries.getTotalGoalCount().executeAsOne()
        }
    }

    suspend fun getActiveGoalCount(): Long {
        return this { db ->
            db.lifePlannerDBQueries.getActiveGoalCount().executeAsOne()
        }
    }

    suspend fun getCompletedGoalCount(): Long {
        return this { db ->
            db.lifePlannerDBQueries.getCompletedGoalCount().executeAsOne()
        }
    }

    suspend fun getOverallCompletionRate(): Double {
        return this { db ->
            db.lifePlannerDBQueries.getOverallCompletionRate().executeAsOneOrNull() ?: 0.0
        }
    }

    suspend fun getGoalCountByCategory(): Map<String, Long> {
        return this { db ->
            db.lifePlannerDBQueries.getGoalCountByCategory().executeAsList()
                .associate { result -> result.category to result.COUNT }
        }
    }

    suspend fun getGoalCountByTimeline(): Map<String, Long> {
        return this { db ->
            db.lifePlannerDBQueries.getGoalCountByTimeline().executeAsList()
                .associate { result -> result.timeline to result.COUNT }
        }
    }

    suspend fun getGoalCountByStatus(): Map<String, Long> {
        return this { db ->
            db.lifePlannerDBQueries.getGoalCountByStatus().executeAsList()
                .associate { result -> result.status to result.COUNT }
        }
    }

    suspend fun getAverageProgressByCategory(): Map<String, Double> {
        return this { db ->
            db.lifePlannerDBQueries.getAverageProgressByCategory().executeAsList()
                .associate { result -> result.category to (result.AVG ?: 0.0) }
        }
    }

    // Milestone operations
    suspend fun insertMilestone(milestone: MilestoneEntity) {
        this { db ->
            db.lifePlannerDBQueries.insertMilestone(
                id = milestone.id,
                goalId = milestone.goalId,
                title = milestone.title,
                isCompleted = milestone.isCompleted,
                dueDate = milestone.dueDate,
                createdAt = milestone.createdAt
            )
        }
    }

    suspend fun getMilestonesByGoalId(goalId: String): List<MilestoneEntity> {
        return this { db ->
            db.lifePlannerDBQueries.getMilestonesByGoalId(goalId).executeAsList()
        }
    }

    suspend fun updateMilestone(milestone: MilestoneEntity) {
        this { db ->
            db.lifePlannerDBQueries.updateMilestone(
                title = milestone.title,
                isCompleted = milestone.isCompleted,
                dueDate = milestone.dueDate,
                id = milestone.id
            )
        }
    }

    suspend fun deleteMilestone(id: String) {
        this { db ->
            db.lifePlannerDBQueries.deleteMilestone(id)
        }
    }

    suspend fun toggleMilestoneCompletion(id: String, isCompleted: Boolean) {
        this { db ->
            db.lifePlannerDBQueries.toggleMilestoneCompletion(
                isCompleted = if (isCompleted) 1L else 0L,
                id = id
            )
        }
    }

    // Helper method to get goals with their milestones
    suspend fun getMilestonesForGoals(goalIds: List<String>): Map<String, List<MilestoneEntity>> {
        return this { db ->
            goalIds.associateWith { goalId ->
                db.lifePlannerDBQueries.getMilestonesByGoalId(goalId).executeAsList()
            }
        }
    }

    // --- Existing methods remain the same ---

    suspend fun insertGoalHistory(
        id: String,
        goalId: String,
        field: String,
        oldValue: String?,
        newValue: String,
        changedAt: String
    ) {
        this { db ->
            db.lifePlannerDBQueries.insertGoalHistory(
                id = id,
                goalId = goalId,
                field_ = field,
                oldValue = oldValue,
                newValue = newValue,
                changedAt = changedAt,
            )
        }
    }

    suspend fun getGoalHistory(goalId: String): List<GoalChange> {
        return this { db ->
            db.lifePlannerDBQueries.getGoalHistory(goalId).executeAsList().map {
                GoalChange(
                    id = it.id,
                    goalId = it.goalId,
                    field = it.field_,
                    oldValue = it.oldValue,
                    newValue = it.newValue ?: "unknown",
                    changedAt = it.changedAt
                )
            }
        }
    }

    // --- Habit operations ---

    suspend fun getAllHabits(): List<HabitEntity> {
        return this { db -> db.lifePlannerDBQueries.getAllHabits().executeAsList() }
    }

    suspend fun getHabitById(id: String): HabitEntity? {
        return this { db -> db.lifePlannerDBQueries.getHabitById(id).executeAsOneOrNull() }
    }

    suspend fun getHabitsByCategory(category: String): List<HabitEntity> {
        return this { db -> db.lifePlannerDBQueries.getHabitsByCategory(category).executeAsList() }
    }

    suspend fun getHabitsByGoalId(goalId: String): List<HabitEntity> {
        return this { db -> db.lifePlannerDBQueries.getHabitsByGoalId(goalId).executeAsList() }
    }

    suspend fun insertHabit(habit: HabitEntity) {
        this { db ->
            db.lifePlannerDBQueries.insertHabit(
                id = habit.id,
                title = habit.title,
                description = habit.description,
                category = habit.category,
                frequency = habit.frequency,
                targetCount = habit.targetCount,
                currentStreak = habit.currentStreak,
                longestStreak = habit.longestStreak,
                totalCompletions = habit.totalCompletions,
                lastCompletedDate = habit.lastCompletedDate,
                linkedGoalId = habit.linkedGoalId,
                correlationScore = habit.correlationScore,
                isActive = habit.isActive,
                createdAt = habit.createdAt,
                reminderTime = habit.reminderTime
            )
        }
    }

    suspend fun updateHabit(
        id: String,
        title: String,
        description: String,
        category: String,
        frequency: String,
        targetCount: Long,
        linkedGoalId: String?,
        reminderTime: String?
    ) {
        this { db ->
            db.lifePlannerDBQueries.updateHabit(
                title = title,
                description = description,
                category = category,
                frequency = frequency,
                targetCount = targetCount,
                linkedGoalId = linkedGoalId,
                reminderTime = reminderTime,
                id = id
            )
        }
    }

    suspend fun updateHabitStreak(
        id: String,
        currentStreak: Long,
        longestStreak: Long,
        totalCompletions: Long,
        lastCompletedDate: String?
    ) {
        this { db ->
            db.lifePlannerDBQueries.updateHabitStreak(
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                totalCompletions = totalCompletions,
                lastCompletedDate = lastCompletedDate,
                id = id
            )
        }
    }

    suspend fun updateHabitCorrelation(id: String, correlationScore: Double) {
        this { db ->
            db.lifePlannerDBQueries.updateHabitCorrelation(
                correlationScore = correlationScore,
                id = id
            )
        }
    }

    suspend fun deactivateHabit(id: String) {
        this { db -> db.lifePlannerDBQueries.deactivateHabit(id) }
    }

    suspend fun deleteHabit(id: String) {
        this { db -> db.lifePlannerDBQueries.deleteHabit(id) }
    }

    // --- Habit Check-in operations ---

    suspend fun insertHabitCheckIn(checkIn: HabitCheckInEntity) {
        this { db ->
            db.lifePlannerDBQueries.insertHabitCheckIn(
                id = checkIn.id,
                habitId = checkIn.habitId,
                date = checkIn.date,
                completed = checkIn.completed,
                notes = checkIn.notes
            )
        }
    }

    suspend fun getCheckInsByHabitId(habitId: String): List<HabitCheckInEntity> {
        return this { db -> db.lifePlannerDBQueries.getCheckInsByHabitId(habitId).executeAsList() }
    }

    suspend fun getCheckInsByDate(date: String): List<HabitCheckInEntity> {
        return this { db -> db.lifePlannerDBQueries.getCheckInsByDate(date).executeAsList() }
    }

    suspend fun getCheckInByHabitAndDate(habitId: String, date: String): HabitCheckInEntity? {
        return this { db ->
            db.lifePlannerDBQueries.getCheckInByHabitAndDate(habitId, date).executeAsOneOrNull()
        }
    }

    suspend fun deleteDuplicateCheckIns() {
        this { db -> db.lifePlannerDBQueries.deleteDuplicateCheckIns() }
    }

    suspend fun getCheckInsInRange(habitId: String, startDate: String, endDate: String): List<HabitCheckInEntity> {
        return this { db ->
            db.lifePlannerDBQueries.getCheckInsInRange(habitId, startDate, endDate).executeAsList()
        }
    }

    suspend fun deleteCheckIn(id: String) {
        this { db -> db.lifePlannerDBQueries.deleteCheckIn(id) }
    }

    // --- Journal Entry operations ---

    suspend fun getAllJournalEntries(): List<JournalEntryEntity> {
        return this { db -> db.lifePlannerDBQueries.getAllJournalEntries().executeAsList() }
    }

    suspend fun getJournalEntryById(id: String): JournalEntryEntity? {
        return this { db -> db.lifePlannerDBQueries.getJournalEntryById(id).executeAsOneOrNull() }
    }

    suspend fun getJournalEntriesByDate(date: String): List<JournalEntryEntity> {
        return this { db -> db.lifePlannerDBQueries.getJournalEntriesByDate(date).executeAsList() }
    }

    suspend fun getJournalEntriesByGoalId(goalId: String): List<JournalEntryEntity> {
        return this { db -> db.lifePlannerDBQueries.getJournalEntriesByGoalId(goalId).executeAsList() }
    }

    suspend fun getJournalEntriesByMood(mood: String): List<JournalEntryEntity> {
        return this { db -> db.lifePlannerDBQueries.getJournalEntriesByMood(mood).executeAsList() }
    }

    suspend fun getJournalEntriesInRange(startDate: String, endDate: String): List<JournalEntryEntity> {
        return this { db ->
            db.lifePlannerDBQueries.getJournalEntriesInRange(startDate, endDate).executeAsList()
        }
    }

    suspend fun getRecentJournalEntries(limit: Long): List<JournalEntryEntity> {
        return this { db -> db.lifePlannerDBQueries.getRecentJournalEntries(limit).executeAsList() }
    }

    suspend fun insertJournalEntry(entry: JournalEntryEntity) {
        this { db ->
            db.lifePlannerDBQueries.insertJournalEntry(
                id = entry.id,
                title = entry.title,
                content = entry.content,
                mood = entry.mood,
                linkedGoalId = entry.linkedGoalId,
                promptUsed = entry.promptUsed,
                tags = entry.tags,
                date = entry.date,
                createdAt = entry.createdAt,
                updatedAt = entry.updatedAt
            )
        }
    }

    suspend fun updateJournalEntry(entry: JournalEntryEntity) {
        this { db ->
            db.lifePlannerDBQueries.updateJournalEntry(
                title = entry.title,
                content = entry.content,
                mood = entry.mood,
                linkedGoalId = entry.linkedGoalId,
                promptUsed = entry.promptUsed,
                tags = entry.tags,
                updatedAt = entry.updatedAt,
                id = entry.id
            )
        }
    }

    suspend fun deleteJournalEntry(id: String) {
        this { db -> db.lifePlannerDBQueries.deleteJournalEntry(id) }
    }

    suspend fun searchJournalEntries(query: String): List<JournalEntryEntity> {
        return this { db ->
            db.lifePlannerDBQueries.searchJournalEntries(query, query).executeAsList()
        }
    }

    suspend fun getMoodCountInRange(startDate: String, endDate: String): Map<String, Long> {
        return this { db ->
            db.lifePlannerDBQueries.getMoodCountInRange(startDate, endDate).executeAsList()
                .associate { result -> result.mood to result.COUNT }
        }
    }

    // --- Badge operations ---

    suspend fun getAllBadges(): List<BadgeEntity> {
        return this { db -> db.lifePlannerDBQueries.getAllBadges().executeAsList() }
    }

    suspend fun getBadgeByType(badgeType: String): BadgeEntity? {
        return this { db -> db.lifePlannerDBQueries.getBadgeByType(badgeType).executeAsOneOrNull() }
    }

    suspend fun hasBadge(badgeType: String): Boolean {
        return this { db -> db.lifePlannerDBQueries.hasBadge(badgeType).executeAsOne() > 0 }
    }

    suspend fun insertBadge(badge: BadgeEntity) {
        this { db ->
            db.lifePlannerDBQueries.insertBadge(
                id = badge.id,
                badgeType = badge.badgeType,
                earnedAt = badge.earnedAt,
                isNew = badge.isNew
            )
        }
    }

    suspend fun markBadgeAsSeen(id: String) {
        this { db -> db.lifePlannerDBQueries.markBadgeAsSeen(id) }
    }

    suspend fun markAllBadgesAsSeen() {
        this { db -> db.lifePlannerDBQueries.markAllBadgesAsSeen() }
    }

    suspend fun getNewBadges(): List<BadgeEntity> {
        return this { db -> db.lifePlannerDBQueries.getNewBadges().executeAsList() }
    }

    suspend fun getBadgeCount(): Long {
        return this { db -> db.lifePlannerDBQueries.getBadgeCount().executeAsOne() }
    }

    // --- Challenge operations ---

    suspend fun getAllChallenges(): List<ChallengeEntity> {
        return this { db -> db.lifePlannerDBQueries.getAllChallenges().executeAsList() }
    }

    suspend fun getActiveChallenges(today: String): List<ChallengeEntity> {
        return this { db -> db.lifePlannerDBQueries.getActiveChallenges(today).executeAsList() }
    }

    suspend fun getCompletedChallenges(): List<ChallengeEntity> {
        return this { db -> db.lifePlannerDBQueries.getCompletedChallenges().executeAsList() }
    }

    suspend fun getChallengeById(id: String): ChallengeEntity? {
        return this { db -> db.lifePlannerDBQueries.getChallengeById(id).executeAsOneOrNull() }
    }

    suspend fun getChallengeByType(challengeType: String): ChallengeEntity? {
        return this { db ->
            db.lifePlannerDBQueries.getChallengeByType(challengeType).executeAsOneOrNull()
        }
    }

    suspend fun insertChallenge(challenge: ChallengeEntity) {
        this { db ->
            db.lifePlannerDBQueries.insertChallenge(
                id = challenge.id,
                challengeType = challenge.challengeType,
                startDate = challenge.startDate,
                endDate = challenge.endDate,
                currentProgress = challenge.currentProgress,
                targetProgress = challenge.targetProgress,
                isCompleted = challenge.isCompleted,
                completedAt = challenge.completedAt,
                xpEarned = challenge.xpEarned
            )
        }
    }

    suspend fun updateChallengeProgress(id: String, progress: Long) {
        this { db -> db.lifePlannerDBQueries.updateChallengeProgress(progress, id) }
    }

    suspend fun completeChallenge(id: String, completedAt: String, xpEarned: Long) {
        this { db -> db.lifePlannerDBQueries.completeChallenge(completedAt, xpEarned, id) }
    }

    suspend fun deleteChallenge(id: String) {
        this { db -> db.lifePlannerDBQueries.deleteChallenge(id) }
    }

    suspend fun deleteExpiredChallenges(today: String) {
        this { db -> db.lifePlannerDBQueries.deleteExpiredChallenges(today) }
    }

    // --- Extended User Progress operations ---

    suspend fun getUserProgressEntity(): UserProgressEntity? {
        return this { db -> db.lifePlannerDBQueries.getUserProgress().executeAsOneOrNull() }
    }

    suspend fun insertUserProgressFull(
        currentStreak: Long,
        lastCheckInDate: String?,
        totalXp: Long,
        currentLevel: Long,
        goalsCompleted: Long,
        habitsCompleted: Long,
        journalEntriesCount: Long,
        longestStreak: Long
    ) {
        this { db ->
            db.lifePlannerDBQueries.insertUserProgress(
                currentStreak = currentStreak,
                lastCheckInDate = lastCheckInDate,
                totalXp = totalXp,
                currentLevel = currentLevel,
                goalsCompleted = goalsCompleted,
                habitsCompleted = habitsCompleted,
                journalEntriesCount = journalEntriesCount,
                longestStreak = longestStreak
            )
        }
    }

    suspend fun updateUserStreakFull(currentStreak: Long, lastCheckInDate: String, longestStreak: Long) {
        this { db ->
            db.lifePlannerDBQueries.updateUserStreak(currentStreak, lastCheckInDate, longestStreak)
        }
    }

    suspend fun updateUserXp(totalXp: Long, currentLevel: Long) {
        this { db -> db.lifePlannerDBQueries.updateUserXp(totalXp, currentLevel) }
    }

    suspend fun addXp(xpAmount: Long) {
        this { db -> db.lifePlannerDBQueries.addXp(xpAmount) }
    }

    suspend fun incrementGoalsCompleted() {
        this { db -> db.lifePlannerDBQueries.incrementGoalsCompleted() }
    }

    suspend fun incrementHabitsCompleted() {
        this { db -> db.lifePlannerDBQueries.incrementHabitsCompleted() }
    }

    suspend fun incrementJournalEntries() {
        this { db -> db.lifePlannerDBQueries.incrementJournalEntries() }
    }

    // --- Goal Dependency operations ---

    suspend fun getAllDependencies(): List<GoalDependencyEntity> {
        return this { db -> db.lifePlannerDBQueries.getAllDependencies().executeAsList() }
    }

    suspend fun getDependenciesBySourceGoal(sourceGoalId: String): List<GoalDependencyEntity> {
        return this { db ->
            db.lifePlannerDBQueries.getDependenciesBySourceGoal(sourceGoalId).executeAsList()
        }
    }

    suspend fun getDependenciesByTargetGoal(targetGoalId: String): List<GoalDependencyEntity> {
        return this { db ->
            db.lifePlannerDBQueries.getDependenciesByTargetGoal(targetGoalId).executeAsList()
        }
    }

    suspend fun getDependenciesForGoal(goalId: String): List<GoalDependencyEntity> {
        return this { db ->
            db.lifePlannerDBQueries.getDependenciesForGoal(goalId, goalId).executeAsList()
        }
    }

    suspend fun getDependencyById(id: String): GoalDependencyEntity? {
        return this { db -> db.lifePlannerDBQueries.getDependencyById(id).executeAsOneOrNull() }
    }

    suspend fun getDependencyBetweenGoals(goalId1: String, goalId2: String): GoalDependencyEntity? {
        return this { db ->
            db.lifePlannerDBQueries.getDependencyBetweenGoals(goalId1, goalId2, goalId2, goalId1)
                .executeAsOneOrNull()
        }
    }

    suspend fun insertGoalDependency(dependency: GoalDependencyEntity) {
        this { db ->
            db.lifePlannerDBQueries.insertGoalDependency(
                id = dependency.id,
                sourceGoalId = dependency.sourceGoalId,
                targetGoalId = dependency.targetGoalId,
                dependencyType = dependency.dependencyType,
                createdAt = dependency.createdAt
            )
        }
    }

    suspend fun deleteDependency(id: String) {
        this { db -> db.lifePlannerDBQueries.deleteDependency(id) }
    }

    suspend fun deleteDependenciesByGoal(goalId: String) {
        this { db -> db.lifePlannerDBQueries.deleteDependenciesByGoal(goalId, goalId) }
    }

    suspend fun getDependencyCount(): Long {
        return this { db -> db.lifePlannerDBQueries.getDependencyCount().executeAsOne() }
    }

    suspend fun getBlockingGoals(goalId: String): List<GoalEntity> {
        return this { db -> db.lifePlannerDBQueries.getBlockingGoals(goalId).executeAsList() }
    }

    suspend fun getBlockedGoals(goalId: String): List<GoalEntity> {
        return this { db -> db.lifePlannerDBQueries.getBlockedGoals(goalId).executeAsList() }
    }

    suspend fun getChildGoals(goalId: String): List<GoalEntity> {
        return this { db -> db.lifePlannerDBQueries.getChildGoals(goalId).executeAsList() }
    }

    suspend fun getParentGoals(goalId: String): List<GoalEntity> {
        return this { db -> db.lifePlannerDBQueries.getParentGoals(goalId).executeAsList() }
    }

    suspend fun getRelatedGoals(goalId: String): List<GoalEntity> {
        return this { db -> db.lifePlannerDBQueries.getRelatedGoals(goalId).executeAsList() }
    }

    // --- Chat Session operations ---

    suspend fun getAllChatSessions(): List<ChatSessionEntity> {
        return this { db -> db.lifePlannerDBQueries.getAllChatSessions().executeAsList() }
    }

    suspend fun getChatSessionById(id: String): ChatSessionEntity? {
        return this { db -> db.lifePlannerDBQueries.getChatSessionById(id).executeAsOneOrNull() }
    }

    suspend fun insertChatSession(
        id: String,
        title: String,
        createdAt: String,
        lastMessageAt: String,
        summary: String?
    ) {
        this { db ->
            db.lifePlannerDBQueries.insertChatSession(id, title, createdAt, lastMessageAt, summary)
        }
    }

    suspend fun updateChatSessionLastMessage(id: String, lastMessageAt: String, title: String) {
        this { db ->
            db.lifePlannerDBQueries.updateChatSessionLastMessage(lastMessageAt, title, id)
        }
    }

    suspend fun updateChatSessionSummary(id: String, summary: String) {
        this { db ->
            db.lifePlannerDBQueries.updateChatSessionSummary(summary, id)
        }
    }

    suspend fun deleteChatSession(id: String) {
        this { db -> db.lifePlannerDBQueries.deleteChatSession(id) }
    }

    suspend fun deleteOldChatSessions(beforeDate: String) {
        this { db -> db.lifePlannerDBQueries.deleteOldChatSessions(beforeDate) }
    }

    suspend fun getChatSessionCount(): Long {
        return this { db -> db.lifePlannerDBQueries.getChatSessionCount().executeAsOne() }
    }

    // --- Chat Message operations ---

    suspend fun getMessagesBySessionId(sessionId: String): List<ChatMessageEntity> {
        return this { db -> db.lifePlannerDBQueries.getMessagesBySessionId(sessionId).executeAsList() }
    }

    suspend fun getRecentMessages(sessionId: String, limit: Long): List<ChatMessageEntity> {
        return this { db -> db.lifePlannerDBQueries.getRecentMessages(sessionId, limit).executeAsList() }
    }

    suspend fun getMessageById(id: String): ChatMessageEntity? {
        return this { db -> db.lifePlannerDBQueries.getMessageById(id).executeAsOneOrNull() }
    }

    suspend fun insertChatMessage(
        id: String,
        sessionId: String,
        content: String,
        role: String,
        timestamp: String,
        relatedGoalId: String?,
        metadata: String?
    ) {
        this { db ->
            db.lifePlannerDBQueries.insertChatMessage(
                id, sessionId, content, role, timestamp, relatedGoalId, metadata
            )
        }
    }

    suspend fun deleteMessage(id: String) {
        this { db -> db.lifePlannerDBQueries.deleteMessage(id) }
    }

    suspend fun deleteMessagesBySession(sessionId: String) {
        this { db -> db.lifePlannerDBQueries.deleteMessagesBySession(sessionId) }
    }

    suspend fun getMessageCountBySession(sessionId: String): Long {
        return this { db -> db.lifePlannerDBQueries.getMessageCountBySession(sessionId).executeAsOne() }
    }

    suspend fun getLastMessageBySession(sessionId: String): ChatMessageEntity? {
        return this { db -> db.lifePlannerDBQueries.getLastMessageBySession(sessionId).executeAsOneOrNull() }
    }

    // --- Review operations ---

    suspend fun getAllReviews(): List<ReviewReportEntity> {
        return this { db -> db.lifePlannerDBQueries.getAllReviews().executeAsList() }
    }

    suspend fun getReviewById(id: String): ReviewReportEntity? {
        return this { db -> db.lifePlannerDBQueries.getReviewById(id).executeAsOneOrNull() }
    }

    suspend fun getReviewsByType(type: String): List<ReviewReportEntity> {
        return this { db -> db.lifePlannerDBQueries.getReviewsByType(type).executeAsList() }
    }

    suspend fun getLatestReviewByType(type: String): ReviewReportEntity? {
        return this { db -> db.lifePlannerDBQueries.getLatestReviewByType(type).executeAsOneOrNull() }
    }

    suspend fun getUnreadReviews(): List<ReviewReportEntity> {
        return this { db -> db.lifePlannerDBQueries.getUnreadReviews().executeAsList() }
    }

    suspend fun insertReview(
        id: String,
        type: String,
        periodStart: String,
        periodEnd: String,
        generatedAt: String,
        summary: String,
        highlightsJson: String,
        insightsJson: String,
        recommendationsJson: String,
        statsJson: String,
        feedbackRating: String?,
        feedbackComment: String?,
        feedbackAt: String?,
        isRead: Long
    ) {
        this { db ->
            db.lifePlannerDBQueries.insertReview(
                id, type, periodStart, periodEnd, generatedAt, summary,
                highlightsJson, insightsJson, recommendationsJson, statsJson,
                feedbackRating, feedbackComment, feedbackAt, isRead
            )
        }
    }

    suspend fun markReviewAsRead(id: String) {
        this { db -> db.lifePlannerDBQueries.markReviewAsRead(id) }
    }

    suspend fun updateReviewFeedback(id: String, rating: String, comment: String?, feedbackAt: String) {
        this { db -> db.lifePlannerDBQueries.updateReviewFeedback(rating, comment, feedbackAt, id) }
    }

    suspend fun deleteReview(id: String) {
        this { db -> db.lifePlannerDBQueries.deleteReview(id) }
    }

    suspend fun getUnreadReviewCount(): Long {
        return this { db -> db.lifePlannerDBQueries.getUnreadReviewCount().executeAsOne() }
    }

    // --- Reminder operations ---

    suspend fun getAllReminders(): List<ReminderEntity> {
        return this { db -> db.lifePlannerDBQueries.getAllReminders().executeAsList() }
    }

    suspend fun getEnabledReminders(): List<ReminderEntity> {
        return this { db -> db.lifePlannerDBQueries.getEnabledReminders().executeAsList() }
    }

    suspend fun getReminderById(id: String): ReminderEntity? {
        return this { db -> db.lifePlannerDBQueries.getReminderById(id).executeAsOneOrNull() }
    }

    suspend fun getRemindersByGoalId(goalId: String): List<ReminderEntity> {
        return this { db -> db.lifePlannerDBQueries.getRemindersByGoalId(goalId).executeAsList() }
    }

    suspend fun getRemindersByHabitId(habitId: String): List<ReminderEntity> {
        return this { db -> db.lifePlannerDBQueries.getRemindersByHabitId(habitId).executeAsList() }
    }

    suspend fun getRemindersByType(type: String): List<ReminderEntity> {
        return this { db -> db.lifePlannerDBQueries.getRemindersByType(type).executeAsList() }
    }

    suspend fun insertReminder(
        id: String,
        title: String,
        message: String,
        type: String,
        frequency: String,
        scheduledTime: String,
        scheduledDays: String,
        linkedGoalId: String?,
        linkedHabitId: String?,
        isEnabled: Long,
        isSmartTiming: Long,
        lastTriggeredAt: String?,
        snoozedUntil: String?,
        createdAt: String,
        updatedAt: String?
    ) {
        this { db ->
            db.lifePlannerDBQueries.insertReminder(
                id, title, message, type, frequency, scheduledTime, scheduledDays,
                linkedGoalId, linkedHabitId, isEnabled, isSmartTiming, lastTriggeredAt,
                snoozedUntil, createdAt, updatedAt
            )
        }
    }

    suspend fun updateReminder(
        id: String,
        title: String,
        message: String,
        type: String,
        frequency: String,
        scheduledTime: String,
        scheduledDays: String,
        linkedGoalId: String?,
        linkedHabitId: String?,
        isEnabled: Long,
        isSmartTiming: Long,
        updatedAt: String?
    ) {
        this { db ->
            db.lifePlannerDBQueries.updateReminder(
                title, message, type, frequency, scheduledTime, scheduledDays,
                linkedGoalId, linkedHabitId, isEnabled, isSmartTiming, updatedAt, id
            )
        }
    }

    suspend fun updateReminderLastTriggered(id: String, lastTriggeredAt: String) {
        this { db -> db.lifePlannerDBQueries.updateReminderLastTriggered(lastTriggeredAt, id) }
    }

    suspend fun snoozeReminder(id: String, snoozedUntil: String) {
        this { db -> db.lifePlannerDBQueries.snoozeReminder(snoozedUntil, id) }
    }

    suspend fun enableReminder(id: String) {
        this { db -> db.lifePlannerDBQueries.enableReminder(id) }
    }

    suspend fun disableReminder(id: String) {
        this { db -> db.lifePlannerDBQueries.disableReminder(id) }
    }

    suspend fun enableAllReminders() {
        this { db -> db.lifePlannerDBQueries.enableAllReminders() }
    }

    suspend fun disableAllReminders() {
        this { db -> db.lifePlannerDBQueries.disableAllReminders() }
    }

    suspend fun deleteReminder(id: String) {
        this { db -> db.lifePlannerDBQueries.deleteReminder(id) }
    }

    suspend fun getReminderCount(): Long {
        return this { db -> db.lifePlannerDBQueries.getReminderCount().executeAsOne() }
    }

    // --- Reminder Settings operations ---

    suspend fun getReminderSettings(): ReminderSettingsEntity? {
        return this { db -> db.lifePlannerDBQueries.getReminderSettings().executeAsOneOrNull() }
    }

    suspend fun insertReminderSettings(
        id: String,
        isEnabled: Long,
        quietHoursStart: String,
        quietHoursEnd: String,
        preferredMorningTime: String,
        preferredEveningTime: String,
        smartTimingEnabled: Long,
        maxRemindersPerDay: Long,
        weeklyReviewDay: String,
        weeklyReviewTime: String
    ) {
        this { db ->
            db.lifePlannerDBQueries.insertReminderSettings(
                id, isEnabled, quietHoursStart, quietHoursEnd, preferredMorningTime,
                preferredEveningTime, smartTimingEnabled, maxRemindersPerDay,
                weeklyReviewDay, weeklyReviewTime
            )
        }
    }

    suspend fun updateReminderSettings(
        isEnabled: Long,
        quietHoursStart: String,
        quietHoursEnd: String,
        preferredMorningTime: String,
        preferredEveningTime: String,
        smartTimingEnabled: Long,
        maxRemindersPerDay: Long,
        weeklyReviewDay: String,
        weeklyReviewTime: String
    ) {
        this { db ->
            db.lifePlannerDBQueries.updateReminderSettings(
                isEnabled, quietHoursStart, quietHoursEnd, preferredMorningTime,
                preferredEveningTime, smartTimingEnabled, maxRemindersPerDay,
                weeklyReviewDay, weeklyReviewTime
            )
        }
    }

    // --- User Activity Pattern operations ---

    suspend fun getUserActivityPattern(): UserActivityPatternEntity? {
        return this { db -> db.lifePlannerDBQueries.getUserActivityPattern().executeAsOneOrNull() }
    }

    suspend fun insertUserActivityPattern(
        id: String,
        mostActiveHours: String,
        mostActiveDays: String,
        averageResponseTime: Long,
        bestCheckInTimes: String,
        lastUpdated: String
    ) {
        this { db ->
            db.lifePlannerDBQueries.insertUserActivityPattern(
                id, mostActiveHours, mostActiveDays, averageResponseTime, bestCheckInTimes, lastUpdated
            )
        }
    }

    suspend fun updateUserActivityPattern(
        mostActiveHours: String,
        mostActiveDays: String,
        averageResponseTime: Long,
        bestCheckInTimes: String,
        lastUpdated: String
    ) {
        this { db ->
            db.lifePlannerDBQueries.updateUserActivityPattern(
                mostActiveHours, mostActiveDays, averageResponseTime, bestCheckInTimes, lastUpdated
            )
        }
    }

    // --- Scheduled Notification operations ---

    suspend fun getScheduledNotifications(): List<ScheduledNotificationEntity> {
        return this { db -> db.lifePlannerDBQueries.getScheduledNotifications().executeAsList() }
    }

    suspend fun getScheduledNotificationById(id: String): ScheduledNotificationEntity? {
        return this { db -> db.lifePlannerDBQueries.getScheduledNotificationById(id).executeAsOneOrNull() }
    }

    suspend fun getScheduledNotificationsByReminder(reminderId: String): List<ScheduledNotificationEntity> {
        return this { db -> db.lifePlannerDBQueries.getScheduledNotificationsByReminder(reminderId).executeAsList() }
    }

    suspend fun insertScheduledNotification(
        id: String,
        reminderId: String,
        title: String,
        message: String,
        scheduledAt: String,
        isDelivered: Long,
        deliveredAt: String?,
        isSnoozed: Long,
        isDismissed: Long
    ) {
        this { db ->
            db.lifePlannerDBQueries.insertScheduledNotification(
                id, reminderId, title, message, scheduledAt, isDelivered, deliveredAt, isSnoozed, isDismissed
            )
        }
    }

    suspend fun markNotificationDelivered(id: String, deliveredAt: String) {
        this { db -> db.lifePlannerDBQueries.markNotificationDelivered(deliveredAt, id) }
    }

    suspend fun markNotificationSnoozed(id: String) {
        this { db -> db.lifePlannerDBQueries.markNotificationSnoozed(id) }
    }

    suspend fun dismissNotification(id: String) {
        this { db -> db.lifePlannerDBQueries.dismissNotification(id) }
    }

    suspend fun deleteScheduledNotification(id: String) {
        this { db -> db.lifePlannerDBQueries.deleteScheduledNotification(id) }
    }

    suspend fun deleteScheduledNotificationsByReminder(reminderId: String) {
        this { db -> db.lifePlannerDBQueries.deleteScheduledNotificationsByReminder(reminderId) }
    }

    suspend fun deleteDeliveredNotifications(beforeDate: String) {
        this { db -> db.lifePlannerDBQueries.deleteDeliveredNotifications(beforeDate) }
    }

    suspend fun getPendingNotificationCount(): Long {
        return this { db -> db.lifePlannerDBQueries.getPendingNotificationCount().executeAsOne() }
    }
}