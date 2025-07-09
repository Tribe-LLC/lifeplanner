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
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class GoalRepositoryImpl(
    private val localGoalStore: SharedDatabase
) : GoalRepository {

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
    }

    override suspend fun insertGoals(goals: List<Goal>) {
        localGoalStore.insertGoals(goals.toEntities())

        // Insert all milestones
        goals.forEach { goal ->
            goal.milestones.forEach { milestone ->
                localGoalStore.insertMilestone(milestone.toEntity(goal.id))
            }
        }
    }

    override suspend fun updateGoal(goal: Goal) {
        localGoalStore.updateGoal(goal.toEntity())

        // Update milestones (simplified approach - you might want to be more sophisticated)
        goal.milestones.forEach { milestone ->
            localGoalStore.updateMilestone(milestone.toEntity(goal.id))
        }
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
        // Calculate completion rate based on progress
        val completionRate = progress.toDouble()
        localGoalStore.updateGoalProgress(id, progress.toLong(), completionRate)
    }

    override suspend fun updateGoalNotes(id: String, notes: String) {
        localGoalStore.updateGoalNotes(id, notes)
    }

    override suspend fun archiveGoal(id: String) {
        localGoalStore.archiveGoal(id)
    }

    override suspend fun unarchiveGoal(id: String) {
        localGoalStore.unarchiveGoal(id)
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
        val endDate = today.toString() // You might want to add days to this
        val startDate = today.toString()

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

        return GoalAnalytics(
            totalGoals = totalGoals,
            activeGoals = activeGoals,
            completedGoals = completedGoals,
            completionRate = completionRate,
            upcomingDeadlines = upcomingDeadlines,
            goalsByCategory = goalsByCategory,
            goalsByTimeline = goalsByTimeline,
            averageProgressPerCategory = averageProgressPerCategory,
            goalsCompletedThisWeek = completedGoals, // Placeholder - implement proper date filtering
            goalsCompletedThisMonth = completedGoals // Placeholder - implement proper date filtering
        )
    }

    override suspend fun addMilestone(goalId: String, milestone: Milestone) {
        localGoalStore.insertMilestone(milestone.toEntity(goalId))
    }

    override suspend fun updateMilestone(milestone: Milestone) {
        // Note: You'll need to track goalId in Milestone or find another way to get it
        localGoalStore.updateMilestone(milestone.toEntity("")) // Placeholder for goalId
    }

    override suspend fun deleteMilestone(milestoneId: String) {
        localGoalStore.deleteMilestone(milestoneId)
    }

    override suspend fun toggleMilestoneCompletion(milestoneId: String, isCompleted: Boolean) {
        localGoalStore.toggleMilestoneCompletion(milestoneId, isCompleted)
    }

    override suspend fun deleteGoalById(id: String) {
        localGoalStore.deleteGoalById(id)
    }

    override suspend fun deleteAllGoals() {
        localGoalStore.deleteAllGoals()
    }
}