package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.domain.enum.DependencyType
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/**
 * Represents a dependency relationship between two goals
 */
@Serializable
data class GoalDependency(
    val id: String,
    val sourceGoalId: String,
    val targetGoalId: String,
    val dependencyType: DependencyType,
    val createdAt: LocalDateTime
) {
    /**
     * Get the inverse dependency type for bidirectional relationships
     */
    fun getInverseType(): DependencyType = when (dependencyType) {
        DependencyType.BLOCKS -> DependencyType.BLOCKED_BY
        DependencyType.BLOCKED_BY -> DependencyType.BLOCKS
        DependencyType.PARENT_OF -> DependencyType.CHILD_OF
        DependencyType.CHILD_OF -> DependencyType.PARENT_OF
        DependencyType.RELATED -> DependencyType.RELATED
        DependencyType.SUPPORTS -> DependencyType.SUPPORTS
    }
}

/**
 * Represents a goal with its dependency information for graph visualization
 */
data class GoalNode(
    val goal: Goal,
    val dependencies: List<GoalDependency> = emptyList(),
    val dependents: List<GoalDependency> = emptyList(),
    val level: Int = 0 // For hierarchical layout
) {
    val hasBlockingDependencies: Boolean
        get() = dependencies.any { it.dependencyType == DependencyType.BLOCKED_BY }

    val isBlocking: Boolean
        get() = dependents.any { it.dependencyType == DependencyType.BLOCKS }

    val childGoals: List<String>
        get() = dependents.filter { it.dependencyType == DependencyType.PARENT_OF }
            .map { it.targetGoalId }

    val parentGoals: List<String>
        get() = dependencies.filter { it.dependencyType == DependencyType.CHILD_OF }
            .map { it.targetGoalId }
}

/**
 * Result class for dependency graph data
 */
data class DependencyGraph(
    val nodes: List<GoalNode>,
    val edges: List<GoalDependency>
) {
    val isEmpty: Boolean get() = nodes.isEmpty()

    fun getNodeByGoalId(goalId: String): GoalNode? =
        nodes.find { it.goal.id == goalId }

    fun getRootNodes(): List<GoalNode> =
        nodes.filter { it.dependencies.isEmpty() }

    fun getLeafNodes(): List<GoalNode> =
        nodes.filter { it.dependents.isEmpty() }
}
