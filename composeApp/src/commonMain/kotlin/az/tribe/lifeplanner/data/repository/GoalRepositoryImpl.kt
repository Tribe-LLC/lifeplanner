package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.data.mapper.toDomainGoals
import az.tribe.lifeplanner.data.mapper.toDomainMilestones
import az.tribe.lifeplanner.data.mapper.toEntities
import az.tribe.lifeplanner.data.mapper.toEntity
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.GoalAnalytics
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import az.tribe.lifeplanner.widget.WidgetDataSyncService
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class GoalRepositoryImpl(
    private val localGoalStore: SharedDatabase,
    private val widgetSyncService: WidgetDataSyncService,
    private val syncManager: SyncManager
) : GoalRepository {

    private suspend fun notifyWidgets() {
        try {
            widgetSyncService.refreshWidgets()
        } catch (e: Exception) {
            Logger.w("GoalRepositoryImpl") { "Widget refresh failed: ${e.message}" }
        }
    }

    override fun observeAllGoals(): Flow<List<Goal>> {
        return localGoalStore.observeAllGoals().map { goalEntities ->
            val goalIds = goalEntities.map { it.id }
            val milestonesMap = localGoalStore.getMilestonesForGoals(goalIds)
                .mapValues { (_, milestones) -> milestones.toDomainMilestones() }
            goalEntities.toDomainGoals(milestonesMap)
        }
    }

    override suspend fun getAllGoals(): List<Goal> {
        val goalEntities = localGoalStore.getAllGoals()
        val goalIds = goalEntities.map { it.id }
        val milestonesMap = localGoalStore.getMilestonesForGoals(goalIds)
            .mapValues { (_, milestones) -> milestones.toDomainMilestones() }

        return goalEntities.toDomainGoals(milestonesMap)
    }

    override suspend fun insertGoal(goal: Goal) {
        localGoalStore.insertGoal(goal.toEntity())

        // Insert milestones if any
        goal.milestones.forEach { milestone ->
            localGoalStore.insertMilestone(milestone.toEntity(goal.id))
        }
        notifyWidgets()
        syncManager.requestSync()
    }

    override suspend fun insertGoals(goals: List<Goal>) {
        localGoalStore.insertGoals(goals.toEntities())

        // Insert all milestones
        goals.forEach { goal ->
            goal.milestones.forEach { milestone ->
                localGoalStore.insertMilestone(milestone.toEntity(goal.id))
            }
        }
        syncManager.requestSync()
    }

    override suspend fun updateGoal(goal: Goal) {
        localGoalStore.updateGoal(goal.toEntity())

        // Update milestones (simplified approach - you might want to be more sophisticated)
        goal.milestones.forEach { milestone ->
            localGoalStore.updateMilestone(milestone.toEntity(goal.id))
        }
        notifyWidgets()
        syncManager.requestSync()
    }

    override suspend fun getGoalsByTimeline(timeline: az.tribe.lifeplanner.domain.enum.GoalTimeline): List<Goal> {
        val goalEntities = localGoalStore.getGoalsByTimeline(timeline.name)
        val goalIds = goalEntities.map { it.id }
        val milestonesMap = localGoalStore.getMilestonesForGoals(goalIds)
            .mapValues { (_, milestones) -> milestones.toDomainMilestones() }

        return goalEntities.toDomainGoals(milestonesMap)
    }

    override suspend fun getGoalsByCategory(category: GoalCategory): List<Goal> {
        val goalEntities = localGoalStore.getGoalsByCategory(category.name)
        val goalIds = goalEntities.map { it.id }
        val milestonesMap = localGoalStore.getMilestonesForGoals(goalIds)
            .mapValues { (_, milestones) -> milestones.toDomainMilestones() }

        return goalEntities.toDomainGoals(milestonesMap)
    }

    override suspend fun updateProgress(id: String, progress: Int) {
        // completionRate stored as 0-100 scale (same as progress)
        val completionRate = progress.toDouble()
        localGoalStore.updateGoalProgress(id, progress.toLong(), completionRate)
        syncManager.requestSync()
    }

    override suspend fun updateGoalNotes(id: String, notes: String) {
        localGoalStore.updateGoalNotes(id, notes)
        syncManager.requestSync()
    }

    override suspend fun archiveGoal(id: String) {
        localGoalStore.archiveGoal(id)
        syncManager.requestSync()
    }

    override suspend fun unarchiveGoal(id: String) {
        localGoalStore.unarchiveGoal(id)
        syncManager.requestSync()
    }

    override suspend fun searchGoals(query: String): List<Goal> {
        val goalEntities = localGoalStore.searchGoals(query)
        val goalIds = goalEntities.map { it.id }
        val milestonesMap = localGoalStore.getMilestonesForGoals(goalIds)
            .mapValues { (_, milestones) -> milestones.toDomainMilestones() }

        return goalEntities.toDomainGoals(milestonesMap)
    }

    override suspend fun getActiveGoals(): List<Goal> {
        val goalEntities = localGoalStore.getActiveGoals()
        val goalIds = goalEntities.map { it.id }
        val milestonesMap = localGoalStore.getMilestonesForGoals(goalIds)
            .mapValues { (_, milestones) -> milestones.toDomainMilestones() }

        return goalEntities.toDomainGoals(milestonesMap)
    }

    override suspend fun getCompletedGoals(): List<Goal> {
        val goalEntities = localGoalStore.getCompletedGoals()
        val goalIds = goalEntities.map { it.id }
        val milestonesMap = localGoalStore.getMilestonesForGoals(goalIds)
            .mapValues { (_, milestones) -> milestones.toDomainMilestones() }

        return goalEntities.toDomainGoals(milestonesMap)
    }

    override suspend fun getUpcomingDeadlines(days: Int): List<Goal> {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val startDate = today.toString()
        val endDate = today.plus(DatePeriod(days = days)).toString()

        val goalEntities = localGoalStore.getUpcomingDeadlines(startDate, endDate)
        val goalIds = goalEntities.map { it.id }
        val milestonesMap = localGoalStore.getMilestonesForGoals(goalIds)
            .mapValues { (_, milestones) -> milestones.toDomainMilestones() }

        return goalEntities.toDomainGoals(milestonesMap)
    }

    override suspend fun getAnalytics(): GoalAnalytics {
        val totalGoals = localGoalStore.getTotalGoalCount().toInt()
        val activeGoals = localGoalStore.getActiveGoalCount().toInt()
        val completedGoals = localGoalStore.getCompletedGoalCount().toInt()
        val completionRate = (localGoalStore.getOverallCompletionRate() / 100.0).toFloat()

        // Calculate upcoming deadlines (simplified)
        val upcomingDeadlines = getUpcomingDeadlines().size

        val goalsByCategory = localGoalStore.getGoalCountByCategory()
            .mapKeys { GoalCategory.valueOf(it.key) }
            .mapValues { it.value.toInt() }

        val goalsByTimeline = localGoalStore.getGoalCountByTimeline()
            .mapKeys { az.tribe.lifeplanner.domain.enum.GoalTimeline.valueOf(it.key) }
            .mapValues { it.value.toInt() }

        val averageProgressPerCategory = localGoalStore.getAverageProgressByCategory()
            .mapKeys { GoalCategory.valueOf(it.key) }
            .mapValues { it.value.toFloat() }

        // Calculate actual this-week and this-month completions from goal history
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val weekStart = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
        val monthStart = LocalDate(today.year, today.monthNumber, 1)
        val allGoals = localGoalStore.getAllGoals()
        val weekCompletedCount = allGoals.count { goal ->
            goal.status == "COMPLETED" && goal.createdAt >= weekStart.toString()
        }
        val monthCompletedCount = allGoals.count { goal ->
            goal.status == "COMPLETED" && goal.createdAt >= monthStart.toString()
        }

        return GoalAnalytics(
            totalGoals = totalGoals,
            activeGoals = activeGoals,
            completedGoals = completedGoals,
            completionRate = completionRate,
            upcomingDeadlines = upcomingDeadlines,
            goalsByCategory = goalsByCategory,
            goalsByTimeline = goalsByTimeline,
            averageProgressPerCategory = averageProgressPerCategory,
            goalsCompletedThisWeek = weekCompletedCount,
            goalsCompletedThisMonth = monthCompletedCount
        )
    }

    override suspend fun addMilestone(goalId: String, milestone: Milestone) {
        localGoalStore.insertMilestone(milestone.toEntity(goalId))
        syncManager.requestSync()
    }

    override suspend fun updateMilestone(milestone: Milestone) {
        val goalId = localGoalStore.getGoalIdForMilestone(milestone.id)
        if (goalId != null) {
            localGoalStore.updateMilestone(milestone.toEntity(goalId))
            syncManager.requestSync()
        }
    }

    override suspend fun deleteMilestone(milestoneId: String) {
        localGoalStore.deleteMilestone(milestoneId)
        syncManager.requestSync()
    }

    override suspend fun toggleMilestoneCompletion(milestoneId: String, isCompleted: Boolean) {
        localGoalStore.toggleMilestoneCompletion(milestoneId, isCompleted)
        syncManager.requestSync()
    }

    override suspend fun deleteGoalById(id: String) {
        localGoalStore.deleteGoalById(id)
        notifyWidgets()
        syncManager.requestSync()
    }

    override suspend fun deleteAllGoals() {
        localGoalStore.deleteAllGoals()
        syncManager.requestSync()
    }
}