package az.tribe.lifeplanner.infrastructure

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
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
import az.tribe.lifeplanner.database.CustomCoachEntity
import az.tribe.lifeplanner.database.CoachGroupEntity
import az.tribe.lifeplanner.database.BeginnerObjectiveEntity
import az.tribe.lifeplanner.database.CoachGroupMemberEntity
import az.tribe.lifeplanner.database.FocusSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

class SharedDatabase(
    private val driverProvider: DatabaseDriverFactory,
) {
    private var database: LifePlannerDB? = null
    private val dbMutex = Mutex()

    private fun nowTimestamp(): String = Clock.System.now().toString()

    private suspend fun initDatabase() {
        if (database == null) {
            dbMutex.withLock {
                if (database == null) {
                    database = LifePlannerDB.invoke(
                        driver = driverProvider.createDriver()
                    )
                }
            }
        }
    }

    suspend operator fun <R> invoke(block: suspend (LifePlannerDB) -> R): R {
        initDatabase()
        return block(database!!)
    }

    /**
     * Delete all local data from every table. Used on logout.
     */
    suspend fun clearAllLocalData() {
        this { db ->
            val q = db.lifePlannerDBQueries
            // Tier 3 (depends on Tier 2)
            q.deleteAllChatMessages()
            // Tier 2 (depends on Tier 1)
            q.deleteAllMilestones()
            q.deleteAllGoalHistory()
            q.deleteAllGoalDependencies()
            q.deleteAllHabitCheckIns()
            q.deleteAllJournalEntries()
            q.deleteAllChatSessions()
            q.deleteAllReminders()
            q.deleteAllFocusSessions()
            q.deleteAllChallenges()
            q.deleteAllCoachGroupMembers()
            q.deleteAllScheduledNotifications()
            q.deleteAllReviewReports()
            q.deleteAllUserProgress()
            q.deleteAllBeginnerObjectives()
            // Tier 1 (no deps)
            q.deleteAllGoals()
            q.deleteAllHabits()
            q.deleteAllBadges()
            q.deleteAllCustomCoaches()
            q.deleteAllCoachGroups()
            q.deleteAllUsers()
        }
    }

    // --- GoalEntity accessors (Updated) ---

    suspend fun getAllGoals(): List<GoalEntity> {
        return this { db -> db.lifePlannerDBQueries.selectAll().executeAsList() }
    }

    suspend fun getGoalById(id: String): GoalEntity? {
        return this { db -> db.lifePlannerDBQueries.selectGoalById(id).executeAsOneOrNull() }
    }

    suspend fun deleteAllGoals() {
        this { db ->
            val goals = db.lifePlannerDBQueries.selectAll().executeAsList()
            val now = nowTimestamp()
            goals.forEach { goal -> db.lifePlannerDBQueries.softDeleteGoal(now, goal.id) }
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
                isArchived = goal.isArchived,
                aiReasoning = goal.aiReasoning,
                sync_updated_at = nowTimestamp(),
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
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
                    isArchived = goal.isArchived,
                    aiReasoning = goal.aiReasoning,
                    sync_updated_at = nowTimestamp(),
                    is_deleted = 0L,
                    sync_version = 0L,
                    last_synced_at = null
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
            val now = nowTimestamp()
            // Cascade soft-delete to milestones (FK ON DELETE CASCADE only works for hard deletes)
            db.lifePlannerDBQueries.softDeleteMilestonesByGoalId(now, id)
            db.lifePlannerDBQueries.softDeleteGoal(now, id)
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
                aiReasoning = goal.aiReasoning,
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
                createdAt = milestone.createdAt,
                sync_updated_at = nowTimestamp(),
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
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

    suspend fun getGoalIdForMilestone(milestoneId: String): String? {
        return this { db ->
            db.lifePlannerDBQueries.getGoalIdForMilestone(milestoneId).executeAsOneOrNull()
        }
    }

    suspend fun deleteMilestone(id: String) {
        this { db ->
            db.lifePlannerDBQueries.softDeleteMilestone(nowTimestamp(), id)
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

    // Batch fetch all milestones in ONE query, then group by goalId in memory
    suspend fun getMilestonesForGoals(goalIds: List<String>): Map<String, List<MilestoneEntity>> {
        if (goalIds.isEmpty()) return emptyMap()
        return this { db ->
            val goalIdSet = goalIds.toSet()
            db.lifePlannerDBQueries.getAllActiveMilestones().executeAsList()
                .filter { it.goalId in goalIdSet }
                .groupBy { it.goalId }
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
                sync_updated_at = nowTimestamp(),
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
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
                reminderTime = habit.reminderTime,
                sync_updated_at = nowTimestamp(),
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
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
        this { db -> db.lifePlannerDBQueries.softDeleteHabit(nowTimestamp(), id) }
    }

    // --- Habit Check-in operations ---

    suspend fun insertHabitCheckIn(checkIn: HabitCheckInEntity) {
        this { db ->
            db.lifePlannerDBQueries.insertHabitCheckIn(
                id = checkIn.id,
                habitId = checkIn.habitId,
                date = checkIn.date,
                completed = checkIn.completed,
                notes = checkIn.notes,
                sync_updated_at = nowTimestamp(),
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
            )
        }
    }

    suspend fun insertHabitCheckInOrIgnore(checkIn: HabitCheckInEntity) {
        this { db ->
            db.lifePlannerDBQueries.insertHabitCheckInOrIgnore(
                id = checkIn.id,
                habitId = checkIn.habitId,
                date = checkIn.date,
                completed = checkIn.completed,
                notes = checkIn.notes,
                sync_updated_at = nowTimestamp(),
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
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

    // Single query to get all completed check-in dates for streak calculation (eliminates N+1)
    suspend fun getCompletedCheckInDatesDesc(habitId: String): List<String> {
        return this { db ->
            db.lifePlannerDBQueries.getCompletedCheckInDatesDesc(habitId).executeAsList()
        }
    }

    suspend fun deleteDuplicateCheckIns() {
        this { db -> db.lifePlannerDBQueries.deleteDuplicateCheckIns() }
    }

    /**
     * Force SQLDelight to invalidate cached queries on habit-related tables.
     * Needed when external writers (e.g. Glance widget) modify the DB
     * outside the SQLDelight driver.
     */
    suspend fun invalidateHabitCache() {
        this { db -> db.lifePlannerDBQueries.deleteDuplicateCheckIns() }
    }

    suspend fun getCheckInsInRange(habitId: String, startDate: String, endDate: String): List<HabitCheckInEntity> {
        return this { db ->
            db.lifePlannerDBQueries.getCheckInsInRange(habitId, startDate, endDate).executeAsList()
        }
    }

    suspend fun deleteCheckIn(id: String) {
        this { db -> db.lifePlannerDBQueries.softDeleteHabitCheckIn(nowTimestamp(), id) }
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

    suspend fun getJournalEntriesByHabitId(habitId: String): List<JournalEntryEntity> {
        return this { db -> db.lifePlannerDBQueries.getJournalEntriesByHabitId(habitId).executeAsList() }
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
                linkedHabitId = entry.linkedHabitId,
                promptUsed = entry.promptUsed,
                tags = entry.tags,
                date = entry.date,
                createdAt = entry.createdAt,
                updatedAt = entry.updatedAt,
                sync_updated_at = nowTimestamp(),
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
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
                linkedHabitId = entry.linkedHabitId,
                promptUsed = entry.promptUsed,
                tags = entry.tags,
                updatedAt = entry.updatedAt,
                id = entry.id
            )
        }
    }

    suspend fun deleteJournalEntry(id: String) {
        this { db -> db.lifePlannerDBQueries.softDeleteJournalEntry(nowTimestamp(), id) }
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

    // --- Reactive Flow observers (auto-emit on table changes) ---

    fun observeAllGoals(): Flow<List<GoalEntity>> = flow {
        initDatabase()
        emitAll(
            database!!.lifePlannerDBQueries.selectAll()
                .asFlow()
                .mapToList(Dispatchers.IO)
        )
    }

    fun observeAllHabits(): Flow<List<HabitEntity>> = flow {
        initDatabase()
        emitAll(
            database!!.lifePlannerDBQueries.getAllHabits()
                .asFlow()
                .mapToList(Dispatchers.IO)
        )
    }

    fun observeCheckInsByDate(date: String): Flow<List<HabitCheckInEntity>> = flow {
        initDatabase()
        emitAll(
            database!!.lifePlannerDBQueries.getCheckInsByDate(date)
                .asFlow()
                .mapToList(Dispatchers.IO)
        )
    }

    fun observeAllJournalEntries(): Flow<List<JournalEntryEntity>> = flow {
        initDatabase()
        emitAll(
            database!!.lifePlannerDBQueries.getAllJournalEntries()
                .asFlow()
                .mapToList(Dispatchers.IO)
        )
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
                isNew = badge.isNew,
                sync_updated_at = nowTimestamp(),
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
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
                xpEarned = challenge.xpEarned,
                sync_updated_at = nowTimestamp(),
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
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
        this { db -> db.lifePlannerDBQueries.softDeleteChallenge(nowTimestamp(), id) }
    }

    suspend fun deleteExpiredChallenges(today: String) {
        // Expired challenges are soft-deleted; they can still sync
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
                longestStreak = longestStreak,
                sync_updated_at = nowTimestamp(),
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
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

    suspend fun decrementHabitsCompleted() {
        this { db -> db.lifePlannerDBQueries.decrementHabitsCompleted() }
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
                createdAt = dependency.createdAt,
                sync_updated_at = nowTimestamp(),
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
            )
        }
    }

    suspend fun deleteDependency(id: String) {
        this { db -> db.lifePlannerDBQueries.softDeleteGoalDependency(nowTimestamp(), id) }
    }

    suspend fun deleteDependenciesByGoal(goalId: String) {
        // Soft-delete all dependencies involving this goal
        this { db ->
            val deps = db.lifePlannerDBQueries.getDependenciesForGoal(goalId, goalId).executeAsList()
            val now = nowTimestamp()
            deps.forEach { dep ->
                db.lifePlannerDBQueries.softDeleteGoalDependency(now, dep.id)
            }
        }
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
        summary: String?,
        coachId: String = "luna_general"
    ) {
        this { db ->
            db.lifePlannerDBQueries.insertChatSession(
                id, title, createdAt, lastMessageAt, summary, coachId,
                nowTimestamp(), 0L, 0L, null
            )
        }
    }

    suspend fun getChatSessionByCoachId(coachId: String): ChatSessionEntity? {
        return this { db -> db.lifePlannerDBQueries.getChatSessionByCoachId(coachId).executeAsOneOrNull() }
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
        this { db -> db.lifePlannerDBQueries.softDeleteChatSession(nowTimestamp(), id) }
    }

    suspend fun deleteOldChatSessions(beforeDate: String) {
        // Soft-delete old sessions and their messages
        this { db ->
            val oldSessions = db.lifePlannerDBQueries.getAllChatSessions().executeAsList()
                .filter { it.lastMessageAt < beforeDate }
            val now = nowTimestamp()
            oldSessions.forEach { session ->
                val messages = db.lifePlannerDBQueries.getMessagesBySessionId(session.id).executeAsList()
                messages.forEach { msg -> db.lifePlannerDBQueries.softDeleteChatMessage(now, msg.id) }
                db.lifePlannerDBQueries.softDeleteChatSession(now, session.id)
            }
        }
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
                id, sessionId, content, role, timestamp, relatedGoalId, metadata,
                nowTimestamp(), 0L, 0L, null
            )
        }
    }

    suspend fun deleteMessage(id: String) {
        this { db -> db.lifePlannerDBQueries.softDeleteChatMessage(nowTimestamp(), id) }
    }

    suspend fun deleteMessagesBySession(sessionId: String) {
        this { db ->
            val messages = db.lifePlannerDBQueries.getMessagesBySessionId(sessionId).executeAsList()
            val now = nowTimestamp()
            messages.forEach { msg -> db.lifePlannerDBQueries.softDeleteChatMessage(now, msg.id) }
        }
    }

    suspend fun getMessageCountBySession(sessionId: String): Long {
        return this { db -> db.lifePlannerDBQueries.getMessageCountBySession(sessionId).executeAsOne() }
    }

    suspend fun getLastMessageBySession(sessionId: String): ChatMessageEntity? {
        return this { db -> db.lifePlannerDBQueries.getLastMessageBySession(sessionId).executeAsOneOrNull() }
    }

    suspend fun updateChatMessageMetadata(id: String, metadata: String?) {
        this { db -> db.lifePlannerDBQueries.updateChatMessageMetadata(metadata, id) }
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
                feedbackRating, feedbackComment, feedbackAt, isRead,
                nowTimestamp(), 0L, 0L, null
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
        this { db -> db.lifePlannerDBQueries.softDeleteReviewReport(nowTimestamp(), id) }
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
                snoozedUntil, createdAt, updatedAt,
                nowTimestamp(), 0L, 0L, null
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
        this { db -> db.lifePlannerDBQueries.softDeleteReminder(nowTimestamp(), id) }
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
        // ScheduledNotificationEntity has no is_deleted column; dismiss instead
        this { db -> db.lifePlannerDBQueries.dismissNotification(id) }
    }

    suspend fun deleteScheduledNotificationsByReminder(reminderId: String) {
        // Dismiss all notifications for the reminder (no soft-delete column on this table)
        this { db ->
            val notifications = db.lifePlannerDBQueries.getScheduledNotificationsByReminder(reminderId).executeAsList()
            notifications.forEach { n -> db.lifePlannerDBQueries.dismissNotification(n.id) }
        }
    }

    suspend fun deleteDeliveredNotifications(beforeDate: String) {
        this { db -> db.lifePlannerDBQueries.deleteDeliveredNotifications(beforeDate) }
    }

    suspend fun getPendingNotificationCount(): Long {
        return this { db -> db.lifePlannerDBQueries.getPendingNotificationCount().executeAsOne() }
    }

    // ===== Custom Coach Operations =====

    suspend fun insertCustomCoach(
        id: String,
        name: String,
        icon: String,
        iconBackgroundColor: String,
        iconAccentColor: String,
        systemPrompt: String,
        characteristics: String,
        isFromTemplate: Long,
        templateId: String?,
        createdAt: String,
        updatedAt: String?
    ) {
        this { db ->
            db.lifePlannerDBQueries.insertCustomCoach(
                id, name, icon, iconBackgroundColor, iconAccentColor,
                systemPrompt, characteristics, isFromTemplate, templateId,
                createdAt, updatedAt,
                nowTimestamp(), 0L, 0L, null
            )
        }
    }

    suspend fun getAllCustomCoaches(): List<CustomCoachEntity> {
        return this { db -> db.lifePlannerDBQueries.getAllCustomCoaches().executeAsList() }
    }

    suspend fun getCustomCoachById(id: String): CustomCoachEntity? {
        return this { db -> db.lifePlannerDBQueries.getCustomCoachById(id).executeAsOneOrNull() }
    }

    suspend fun updateCustomCoach(
        name: String,
        icon: String,
        iconBackgroundColor: String,
        iconAccentColor: String,
        systemPrompt: String,
        characteristics: String,
        updatedAt: String?,
        id: String
    ) {
        this { db ->
            db.lifePlannerDBQueries.updateCustomCoach(
                name, icon, iconBackgroundColor, iconAccentColor,
                systemPrompt, characteristics, updatedAt, id
            )
        }
    }

    suspend fun deleteCustomCoach(id: String) {
        this { db -> db.lifePlannerDBQueries.softDeleteCustomCoach(nowTimestamp(), id) }
    }

    suspend fun getCustomCoachCount(): Long {
        return this { db -> db.lifePlannerDBQueries.getCustomCoachCount().executeAsOne() }
    }

    // ===== Coach Group Operations =====

    suspend fun insertCoachGroup(
        id: String,
        name: String,
        icon: String,
        description: String,
        createdAt: String,
        updatedAt: String?
    ) {
        this { db ->
            db.lifePlannerDBQueries.insertCoachGroup(id, name, icon, description, createdAt, updatedAt, null, 0L, 0L, null)
        }
    }

    suspend fun getAllCoachGroups(): List<CoachGroupEntity> {
        return this { db -> db.lifePlannerDBQueries.getAllCoachGroups().executeAsList() }
    }

    suspend fun getCoachGroupById(id: String): CoachGroupEntity? {
        return this { db -> db.lifePlannerDBQueries.getCoachGroupById(id).executeAsOneOrNull() }
    }

    suspend fun updateCoachGroup(
        name: String,
        icon: String,
        description: String,
        updatedAt: String?,
        id: String
    ) {
        this { db ->
            db.lifePlannerDBQueries.updateCoachGroup(name, icon, description, updatedAt, id)
        }
    }

    suspend fun deleteCoachGroup(id: String) {
        this { db -> db.lifePlannerDBQueries.softDeleteCoachGroup(nowTimestamp(), id) }
    }

    suspend fun getCoachGroupCount(): Long {
        return this { db -> db.lifePlannerDBQueries.getCoachGroupCount().executeAsOne() }
    }

    // ===== Coach Group Member Operations =====

    suspend fun insertCoachGroupMember(
        id: String,
        groupId: String,
        coachType: String,
        coachId: String,
        displayOrder: Long
    ) {
        this { db ->
            db.lifePlannerDBQueries.insertCoachGroupMember(id, groupId, coachType, coachId, displayOrder, null, 0L, 0L, null)
        }
    }

    suspend fun getCoachGroupMembers(groupId: String): List<CoachGroupMemberEntity> {
        return this { db -> db.lifePlannerDBQueries.getCoachGroupMembers(groupId).executeAsList() }
    }

    // Batch fetch all coach group members in ONE query (eliminates N+1 when loading groups)
    suspend fun getAllActiveCoachGroupMembers(): Map<String, List<CoachGroupMemberEntity>> {
        return this { db ->
            db.lifePlannerDBQueries.getAllActiveCoachGroupMembers().executeAsList()
                .groupBy { it.groupId }
        }
    }

    suspend fun deleteCoachGroupMember(id: String) {
        this { db -> db.lifePlannerDBQueries.softDeleteCoachGroupMember(nowTimestamp(), id) }
    }

    suspend fun deleteCoachGroupMembersByGroup(groupId: String) {
        this { db ->
            val members = db.lifePlannerDBQueries.getCoachGroupMembers(groupId).executeAsList()
            val now = nowTimestamp()
            members.forEach { m -> db.lifePlannerDBQueries.softDeleteCoachGroupMember(now, m.id) }
        }
    }

    suspend fun updateCoachGroupMemberOrder(displayOrder: Long, id: String) {
        this { db -> db.lifePlannerDBQueries.updateCoachGroupMemberOrder(displayOrder, id) }
    }

    // ===== Focus Session Operations =====

    fun observeAllFocusSessions(): Flow<List<FocusSessionEntity>> = flow {
        initDatabase()
        emitAll(
            database!!.lifePlannerDBQueries.selectAllFocusSessions()
                .asFlow()
                .mapToList(Dispatchers.IO)
        )
    }

    suspend fun insertFocusSession(
        id: String,
        goalId: String,
        milestoneId: String,
        plannedDurationMinutes: Long,
        actualDurationSeconds: Long,
        wasCompleted: Long,
        xpEarned: Long,
        startedAt: String,
        completedAt: String?,
        createdAt: String,
        mood: String? = null,
        ambientSound: String? = null,
        focusTheme: String? = null
    ) {
        this { db ->
            db.lifePlannerDBQueries.insertFocusSession(
                id, goalId, milestoneId, plannedDurationMinutes,
                actualDurationSeconds, wasCompleted, xpEarned,
                startedAt, completedAt, createdAt,
                mood, ambientSound, focusTheme,
                null, 0L, 0L, null
            )
        }
    }

    suspend fun updateFocusSession(
        id: String,
        actualDurationSeconds: Long,
        wasCompleted: Long,
        xpEarned: Long,
        completedAt: String?,
        mood: String? = null,
        ambientSound: String? = null,
        focusTheme: String? = null
    ) {
        this { db ->
            db.lifePlannerDBQueries.updateFocusSession(
                actualDurationSeconds, wasCompleted, xpEarned, completedAt,
                mood, ambientSound, focusTheme, id
            )
        }
    }

    suspend fun getFocusSessionById(id: String): FocusSessionEntity? {
        return this { db -> db.lifePlannerDBQueries.getFocusSessionById(id).executeAsOneOrNull() }
    }

    suspend fun getFocusSessionsByGoalId(goalId: String): List<FocusSessionEntity> {
        return this { db -> db.lifePlannerDBQueries.getFocusSessionsByGoalId(goalId).executeAsList() }
    }

    suspend fun getFocusSessionsByMilestoneId(milestoneId: String): List<FocusSessionEntity> {
        return this { db -> db.lifePlannerDBQueries.getFocusSessionsByMilestoneId(milestoneId).executeAsList() }
    }

    suspend fun getCompletedFocusSessions(): List<FocusSessionEntity> {
        return this { db -> db.lifePlannerDBQueries.getCompletedFocusSessions().executeAsList() }
    }

    suspend fun getTotalFocusSeconds(): Long {
        return this { db -> db.lifePlannerDBQueries.getTotalFocusSeconds().executeAsOne() }
    }

    suspend fun getTotalFocusSessionCount(): Long {
        return this { db -> db.lifePlannerDBQueries.getTotalFocusSessionCount().executeAsOne() }
    }

    suspend fun getTodayFocusSessions(todayDate: String): List<FocusSessionEntity> {
        return this { db -> db.lifePlannerDBQueries.getTodayFocusSessions(todayDate).executeAsList() }
    }

    // ===== Retrospective Operations =====

    suspend fun getHabitCheckInsWithHabitForDate(date: String): List<az.tribe.lifeplanner.database.GetHabitCheckInsWithHabitForDate> {
        return this { db -> db.lifePlannerDBQueries.getHabitCheckInsWithHabitForDate(date).executeAsList() }
    }

    suspend fun getFocusSessionsByDate(datePrefix: String): List<FocusSessionEntity> {
        return this { db -> db.lifePlannerDBQueries.getFocusSessionsByDate(datePrefix).executeAsList() }
    }

    suspend fun getGoalChangesOnDate(datePrefix: String): List<az.tribe.lifeplanner.database.GetGoalChangesOnDate> {
        return this { db -> db.lifePlannerDBQueries.getGoalChangesOnDate(datePrefix).executeAsList() }
    }

    suspend fun getBadgesEarnedOnDate(datePrefix: String): List<BadgeEntity> {
        return this { db -> db.lifePlannerDBQueries.getBadgesEarnedOnDate(datePrefix).executeAsList() }
    }

    suspend fun getGoalsExistingOnDate(dateStr: String): List<GoalEntity> {
        return this { db -> db.lifePlannerDBQueries.getGoalsExistingOnDate(dateStr).executeAsList() }
    }

    // ===== Coach Persona Override Operations =====

    suspend fun getCoachPersonaOverride(coachId: String): az.tribe.lifeplanner.database.CoachPersonaOverrideEntity? {
        return this { db -> db.lifePlannerDBQueries.getCoachPersonaOverride(coachId).executeAsOneOrNull() }
    }

    suspend fun upsertCoachPersonaOverride(coachId: String, userPersona: String, updatedAt: String) {
        this { db -> db.lifePlannerDBQueries.upsertCoachPersonaOverride(coachId, userPersona, updatedAt, nowTimestamp()) }
    }

    suspend fun deleteCoachPersonaOverride(coachId: String) {
        this { db -> db.lifePlannerDBQueries.deleteCoachPersonaOverride(coachId) }
    }

    // ===== Beginner Objective Operations =====

    fun observeAllBeginnerObjectives(): Flow<List<BeginnerObjectiveEntity>> = flow {
        initDatabase()
        emitAll(
            database!!.lifePlannerDBQueries.getAllBeginnerObjectives()
                .asFlow()
                .mapToList(Dispatchers.IO)
        )
    }

    suspend fun getAllBeginnerObjectives(): List<BeginnerObjectiveEntity> {
        return this { db -> db.lifePlannerDBQueries.getAllBeginnerObjectives().executeAsList() }
    }

    suspend fun getBeginnerObjectiveByType(objectiveType: String): BeginnerObjectiveEntity? {
        return this { db -> db.lifePlannerDBQueries.getBeginnerObjectiveByType(objectiveType).executeAsOneOrNull() }
    }

    suspend fun getCompletedBeginnerObjectivesCount(): Long {
        return this { db -> db.lifePlannerDBQueries.getCompletedBeginnerObjectivesCount().executeAsOne() }
    }

    suspend fun deduplicateBeginnerObjectives() {
        this { db ->
            db.lifePlannerDBQueries.deduplicateBeginnerObjectives()
        }
    }

    suspend fun insertBeginnerObjective(
        id: String,
        objectiveType: String,
        isCompleted: Long,
        completedAt: String?,
        xpAwarded: Long,
        createdAt: String
    ) {
        this { db ->
            db.lifePlannerDBQueries.insertBeginnerObjective(
                id, objectiveType, isCompleted, completedAt, xpAwarded, createdAt,
                nowTimestamp(), 0L, 0L, null
            )
        }
    }

    suspend fun completeBeginnerObjective(completedAt: String, xpAwarded: Long, objectiveType: String) {
        this { db ->
            db.lifePlannerDBQueries.completeBeginnerObjective(completedAt, xpAwarded, objectiveType)
        }
    }

    suspend fun uncompleteBeginnerObjective(objectiveType: String) {
        this { db ->
            db.lifePlannerDBQueries.uncompleteBeginnerObjective(objectiveType)
        }
    }

    suspend fun getDatesWithActivity(
        checkInStart: String, checkInEnd: String,
        journalStart: String, journalEnd: String,
        focusStart: String, focusEnd: String,
        historyStart: String, historyEnd: String,
        badgeStart: String, badgeEnd: String
    ): List<String> {
        return this { db ->
            db.lifePlannerDBQueries.getDatesWithActivity(
                checkInStart, checkInEnd,
                journalStart, journalEnd,
                focusStart, focusEnd,
                historyStart, historyEnd,
                badgeStart, badgeEnd
            ).executeAsList().mapNotNull { it }
        }
    }
}