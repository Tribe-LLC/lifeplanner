package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.enum.DependencyType
import az.tribe.lifeplanner.domain.model.DependencyGraph
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.GoalDependency
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing goal dependencies
 */
interface GoalDependencyRepository {
    /**
     * Get all dependencies as a flow
     */
    fun getAllDependencies(): Flow<List<GoalDependency>>

    /**
     * Get dependencies where the goal is the source
     */
    suspend fun getDependenciesBySourceGoal(goalId: String): List<GoalDependency>

    /**
     * Get dependencies where the goal is the target
     */
    suspend fun getDependenciesByTargetGoal(goalId: String): List<GoalDependency>

    /**
     * Get all dependencies related to a goal (both source and target)
     */
    suspend fun getDependenciesForGoal(goalId: String): List<GoalDependency>

    /**
     * Check if a dependency exists between two goals
     */
    suspend fun dependencyExists(goalId1: String, goalId2: String): Boolean

    /**
     * Add a new dependency between goals
     */
    suspend fun addDependency(
        sourceGoalId: String,
        targetGoalId: String,
        dependencyType: DependencyType
    ): GoalDependency

    /**
     * Remove a dependency
     */
    suspend fun removeDependency(dependencyId: String)

    /**
     * Remove all dependencies for a goal
     */
    suspend fun removeDependenciesForGoal(goalId: String)

    /**
     * Get blocking goals for a specific goal
     */
    suspend fun getBlockingGoals(goalId: String): List<Goal>

    /**
     * Get goals that this goal blocks
     */
    suspend fun getBlockedGoals(goalId: String): List<Goal>

    /**
     * Get child goals (sub-goals)
     */
    suspend fun getChildGoals(goalId: String): List<Goal>

    /**
     * Get parent goals
     */
    suspend fun getParentGoals(goalId: String): List<Goal>

    /**
     * Get related goals
     */
    suspend fun getRelatedGoals(goalId: String): List<Goal>

    /**
     * Build the dependency graph for all goals
     */
    suspend fun buildDependencyGraph(): DependencyGraph

    /**
     * Build the dependency graph for a specific goal and its connections
     */
    suspend fun buildDependencyGraphForGoal(goalId: String): DependencyGraph

    /**
     * Get suggested dependencies based on goal similarity
     */
    suspend fun getSuggestedDependencies(goalId: String): List<Pair<Goal, DependencyType>>

    /**
     * Check if adding a dependency would create a circular dependency
     */
    suspend fun wouldCreateCycle(sourceGoalId: String, targetGoalId: String): Boolean
}
