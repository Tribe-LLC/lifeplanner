package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.GoalAnalytics
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.model.Milestone
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    fun observeAllGoals(): Flow<List<Goal>>
    suspend fun getAllGoals(): List<Goal>
    suspend fun insertGoal(goal: Goal)
    suspend fun insertGoals(goals: List<Goal>)
    suspend fun updateGoal(goal: Goal)
    suspend fun deleteGoalById(id: String)
    suspend fun deleteAllGoals()
    suspend fun getGoalsByTimeline(timeline: GoalTimeline): List<Goal>
    suspend fun getGoalsByCategory(category: GoalCategory): List<Goal>
    suspend fun updateProgress(id: String, progress: Int)
    suspend fun updateGoalNotes(id: String, notes: String)
    suspend fun archiveGoal(id: String)
    suspend fun unarchiveGoal(id: String)

    // Search and filter methods
    suspend fun searchGoals(query: String): List<Goal>
    suspend fun getActiveGoals(): List<Goal>
    suspend fun getCompletedGoals(): List<Goal>
    suspend fun getUpcomingDeadlines(days: Int = 7): List<Goal>

    // Analytics
    suspend fun getAnalytics(): GoalAnalytics

    // Milestone operations
    suspend fun addMilestone(goalId: String, milestone: Milestone)
    suspend fun updateMilestone(milestone: Milestone)
    suspend fun deleteMilestone(milestoneId: String)
    suspend fun toggleMilestoneCompletion(milestoneId: String, isCompleted: Boolean)
}