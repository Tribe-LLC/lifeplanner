package az.tribe.lifeplanner.infrastructure

import az.tribe.lifeplanner.domain.model.GoalChange
import az.tribe.lifeplanner.database.LifePlannerDB
import az.tribe.lifeplanner.di.DatabaseDriverFactory
import az.tribe.lifeplanner.database.GoalEntity
import az.tribe.lifeplanner.database.MilestoneEntity

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
}