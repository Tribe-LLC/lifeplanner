package az.tribe.lifeplanner.infrastructure

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import az.tribe.lifeplanner.database.GoalDependencyEntity
import az.tribe.lifeplanner.database.GoalEntity
import az.tribe.lifeplanner.database.MilestoneEntity
import az.tribe.lifeplanner.domain.model.GoalChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

// --- GoalEntity accessors ---

suspend fun SharedDatabase.getAllGoals(): List<GoalEntity> {
    return this { db -> db.lifePlannerDBQueries.selectAll().executeAsList() }
}

suspend fun SharedDatabase.getGoalById(id: String): GoalEntity? {
    return this { db -> db.lifePlannerDBQueries.selectGoalById(id).executeAsOneOrNull() }
}

suspend fun SharedDatabase.deleteAllGoals() {
    this { db ->
        val goals = db.lifePlannerDBQueries.selectAll().executeAsList()
        val now = nowTimestamp()
        goals.forEach { goal -> db.lifePlannerDBQueries.softDeleteGoal(now, goal.id) }
    }
}

suspend fun SharedDatabase.insertGoal(goal: GoalEntity) {
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
            valueId = goal.valueId,
            wheelArea = goal.wheelArea,
            predictedDueDate = goal.predictedDueDate,
            sync_updated_at = nowTimestamp(),
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )
    }
}

suspend fun SharedDatabase.insertGoals(goals: List<GoalEntity>) {
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
                valueId = goal.valueId,
            wheelArea = goal.wheelArea,
                predictedDueDate = goal.predictedDueDate,
                sync_updated_at = nowTimestamp(),
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
            )
        }
    }
}

suspend fun SharedDatabase.getGoalsByTimeline(timeline: String): List<GoalEntity> {
    return this { db ->
        db.lifePlannerDBQueries.selectGoalsByTimeline(timeline).executeAsList()
    }
}

suspend fun SharedDatabase.getGoalsByCategory(category: String): List<GoalEntity> {
    return this { db ->
        db.lifePlannerDBQueries.selectGoalsByCategory(category).executeAsList()
    }
}

suspend fun SharedDatabase.deleteGoalById(id: String) {
    this { db ->
        val now = nowTimestamp()
        // Cascade soft-delete to milestones (FK ON DELETE CASCADE only works for hard deletes)
        db.lifePlannerDBQueries.softDeleteMilestonesByGoalId(now, id)
        db.lifePlannerDBQueries.softDeleteGoal(now, id)
    }
}

suspend fun SharedDatabase.updateGoal(goal: GoalEntity) {
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
            valueId = goal.valueId,
            wheelArea = goal.wheelArea,
            predictedDueDate = goal.predictedDueDate,
            id = goal.id,
            createdAt = goal.createdAt
        )
    }
}

suspend fun SharedDatabase.updateGoalProgress(id: String, progress: Long, completionRate: Double = 0.0) {
    this { db ->
        db.lifePlannerDBQueries.updateGoalProgress(
            progress = progress,
            completionRate = completionRate,
            id = id
        )
    }
}

suspend fun SharedDatabase.updateGoalNotes(id: String, notes: String) {
    this { db ->
        db.lifePlannerDBQueries.updateGoalNotes(notes = notes, id = id)
    }
}

suspend fun SharedDatabase.archiveGoal(id: String) {
    this { db ->
        db.lifePlannerDBQueries.archiveGoal(id)
    }
}

suspend fun SharedDatabase.unarchiveGoal(id: String) {
    this { db ->
        db.lifePlannerDBQueries.unarchiveGoal(id)
    }
}

suspend fun SharedDatabase.searchGoals(query: String): List<GoalEntity> {
    return this { db ->
        db.lifePlannerDBQueries.searchGoals(query, query).executeAsList()
    }
}

suspend fun SharedDatabase.getActiveGoals(): List<GoalEntity> {
    return this { db ->
        db.lifePlannerDBQueries.getActiveGoals().executeAsList()
    }
}

suspend fun SharedDatabase.getCompletedGoals(): List<GoalEntity> {
    return this { db ->
        db.lifePlannerDBQueries.getCompletedGoals().executeAsList()
    }
}

suspend fun SharedDatabase.getUpcomingDeadlines(startDate: String, endDate: String): List<GoalEntity> {
    return this { db ->
        db.lifePlannerDBQueries.getUpcomingDeadlines(startDate, endDate).executeAsList()
    }
}

suspend fun SharedDatabase.getTotalGoalCount(): Long {
    return this { db ->
        db.lifePlannerDBQueries.getTotalGoalCount().executeAsOne()
    }
}

suspend fun SharedDatabase.getActiveGoalCount(): Long {
    return this { db ->
        db.lifePlannerDBQueries.getActiveGoalCount().executeAsOne()
    }
}

suspend fun SharedDatabase.getCompletedGoalCount(): Long {
    return this { db ->
        db.lifePlannerDBQueries.getCompletedGoalCount().executeAsOne()
    }
}

suspend fun SharedDatabase.getOverallCompletionRate(): Double {
    return this { db ->
        db.lifePlannerDBQueries.getOverallCompletionRate().executeAsOneOrNull() ?: 0.0
    }
}

suspend fun SharedDatabase.getGoalCountByCategory(): Map<String, Long> {
    return this { db ->
        db.lifePlannerDBQueries.getGoalCountByCategory().executeAsList()
            .associate { result -> result.category to result.COUNT }
    }
}

suspend fun SharedDatabase.getGoalCountByTimeline(): Map<String, Long> {
    return this { db ->
        db.lifePlannerDBQueries.getGoalCountByTimeline().executeAsList()
            .associate { result -> result.timeline to result.COUNT }
    }
}

suspend fun SharedDatabase.getGoalCountByStatus(): Map<String, Long> {
    return this { db ->
        db.lifePlannerDBQueries.getGoalCountByStatus().executeAsList()
            .associate { result -> result.status to result.COUNT }
    }
}

suspend fun SharedDatabase.getAverageProgressByCategory(): Map<String, Double> {
    return this { db ->
        db.lifePlannerDBQueries.getAverageProgressByCategory().executeAsList()
            .associate { result -> result.category to (result.AVG ?: 0.0) }
    }
}

// --- Milestone operations ---

suspend fun SharedDatabase.insertMilestone(milestone: MilestoneEntity) {
    this { db ->
        db.lifePlannerDBQueries.insertMilestone(
            id = milestone.id,
            goalId = milestone.goalId,
            title = milestone.title,
            isCompleted = milestone.isCompleted,
            dueDate = milestone.dueDate,
            estimatedEffort = milestone.estimatedEffort,
            createdAt = milestone.createdAt,
            sync_updated_at = nowTimestamp(),
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )
        // Bump the parent goal so observeAllGoals re-emits with the new step.
        db.lifePlannerDBQueries.touchGoalByMilestone(id = milestone.id)
    }
}

suspend fun SharedDatabase.getMilestonesByGoalId(goalId: String): List<MilestoneEntity> {
    return this { db ->
        db.lifePlannerDBQueries.getMilestonesByGoalId(goalId).executeAsList()
    }
}

suspend fun SharedDatabase.updateMilestone(milestone: MilestoneEntity) {
    this { db ->
        db.lifePlannerDBQueries.updateMilestone(
            title = milestone.title,
            isCompleted = milestone.isCompleted,
            dueDate = milestone.dueDate,
            estimatedEffort = milestone.estimatedEffort,
            id = milestone.id
        )
        // Without this a moved or renamed step (Today, Tomorrow, Let it go) stayed on screen as it was.
        db.lifePlannerDBQueries.touchGoalByMilestone(id = milestone.id)
    }
}

suspend fun SharedDatabase.getGoalIdForMilestone(milestoneId: String): String? {
    return this { db ->
        db.lifePlannerDBQueries.getGoalIdForMilestone(milestoneId).executeAsOneOrNull()
    }
}

suspend fun SharedDatabase.deleteMilestone(id: String) {
    this { db ->
        db.lifePlannerDBQueries.softDeleteMilestone(nowTimestamp(), id)
        db.lifePlannerDBQueries.touchGoalByMilestone(id = id)
    }
}

suspend fun SharedDatabase.toggleMilestoneCompletion(id: String, isCompleted: Boolean) {
    this { db ->
        db.lifePlannerDBQueries.toggleMilestoneCompletion(
            isCompleted = if (isCompleted) 1L else 0L,
            id = id
        )
        // Bump the parent goal so observeAllGoals re-emits and the toggle actually shows up.
        db.lifePlannerDBQueries.touchGoalByMilestone(id = id)
    }
}

// Batch fetch all milestones in ONE query, then group by goalId in memory
suspend fun SharedDatabase.getMilestonesForGoals(goalIds: List<String>): Map<String, List<MilestoneEntity>> {
    if (goalIds.isEmpty()) return emptyMap()
    return this { db ->
        val goalIdSet = goalIds.toSet()
        db.lifePlannerDBQueries.getAllActiveMilestones().executeAsList()
            .filter { it.goalId in goalIdSet }
            .groupBy { it.goalId }
    }
}

// --- Goal History operations ---

suspend fun SharedDatabase.insertGoalHistory(
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

suspend fun SharedDatabase.getGoalHistory(goalId: String): List<GoalChange> {
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

// --- Goal Dependency operations ---

suspend fun SharedDatabase.getAllDependencies(): List<GoalDependencyEntity> {
    return this { db -> db.lifePlannerDBQueries.getAllDependencies().executeAsList() }
}

suspend fun SharedDatabase.getDependenciesBySourceGoal(sourceGoalId: String): List<GoalDependencyEntity> {
    return this { db ->
        db.lifePlannerDBQueries.getDependenciesBySourceGoal(sourceGoalId).executeAsList()
    }
}

suspend fun SharedDatabase.getDependenciesByTargetGoal(targetGoalId: String): List<GoalDependencyEntity> {
    return this { db ->
        db.lifePlannerDBQueries.getDependenciesByTargetGoal(targetGoalId).executeAsList()
    }
}

suspend fun SharedDatabase.getDependenciesForGoal(goalId: String): List<GoalDependencyEntity> {
    return this { db ->
        db.lifePlannerDBQueries.getDependenciesForGoal(goalId, goalId).executeAsList()
    }
}

suspend fun SharedDatabase.getDependencyById(id: String): GoalDependencyEntity? {
    return this { db -> db.lifePlannerDBQueries.getDependencyById(id).executeAsOneOrNull() }
}

suspend fun SharedDatabase.getDependencyBetweenGoals(goalId1: String, goalId2: String): GoalDependencyEntity? {
    return this { db ->
        db.lifePlannerDBQueries.getDependencyBetweenGoals(goalId1, goalId2, goalId2, goalId1)
            .executeAsOneOrNull()
    }
}

suspend fun SharedDatabase.insertGoalDependency(dependency: GoalDependencyEntity) {
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

suspend fun SharedDatabase.deleteDependency(id: String) {
    this { db -> db.lifePlannerDBQueries.softDeleteGoalDependency(nowTimestamp(), id) }
}

suspend fun SharedDatabase.deleteDependenciesByGoal(goalId: String) {
    // Soft-delete all dependencies involving this goal
    this { db ->
        val deps = db.lifePlannerDBQueries.getDependenciesForGoal(goalId, goalId).executeAsList()
        val now = nowTimestamp()
        deps.forEach { dep ->
            db.lifePlannerDBQueries.softDeleteGoalDependency(now, dep.id)
        }
    }
}

suspend fun SharedDatabase.getDependencyCount(): Long {
    return this { db -> db.lifePlannerDBQueries.getDependencyCount().executeAsOne() }
}

suspend fun SharedDatabase.getBlockingGoals(goalId: String): List<GoalEntity> {
    return this { db -> db.lifePlannerDBQueries.getBlockingGoals(goalId).executeAsList() }
}

suspend fun SharedDatabase.getBlockedGoals(goalId: String): List<GoalEntity> {
    return this { db -> db.lifePlannerDBQueries.getBlockedGoals(goalId).executeAsList() }
}

suspend fun SharedDatabase.getChildGoals(goalId: String): List<GoalEntity> {
    return this { db -> db.lifePlannerDBQueries.getChildGoals(goalId).executeAsList() }
}

suspend fun SharedDatabase.getParentGoals(goalId: String): List<GoalEntity> {
    return this { db -> db.lifePlannerDBQueries.getParentGoals(goalId).executeAsList() }
}

suspend fun SharedDatabase.getRelatedGoals(goalId: String): List<GoalEntity> {
    return this { db -> db.lifePlannerDBQueries.getRelatedGoals(goalId).executeAsList() }
}

// --- Reactive Flow observer ---

fun SharedDatabase.observeAllGoals(): Flow<List<GoalEntity>> = flow {
    initDatabase()
    emitAll(
        database!!.lifePlannerDBQueries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
    )
}
