package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.data.mapper.createNewDependency
import az.tribe.lifeplanner.data.mapper.parseLocalDateTime
import az.tribe.lifeplanner.data.mapper.toDomain
import az.tribe.lifeplanner.data.mapper.toDomainDependencies
import az.tribe.lifeplanner.data.mapper.toEntity
import az.tribe.lifeplanner.domain.enum.DependencyType
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.model.DependencyGraph
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.GoalDependency
import az.tribe.lifeplanner.domain.model.GoalNode
import az.tribe.lifeplanner.domain.repository.GoalDependencyRepository
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class GoalDependencyRepositoryImpl(
    private val database: SharedDatabase,
    private val syncManager: az.tribe.lifeplanner.data.sync.SyncManager
) : GoalDependencyRepository {

    override fun getAllDependencies(): Flow<List<GoalDependency>> = flow {
        emit(database.getAllDependencies().toDomainDependencies())
    }

    override suspend fun getDependenciesBySourceGoal(goalId: String): List<GoalDependency> {
        return database.getDependenciesBySourceGoal(goalId).toDomainDependencies()
    }

    override suspend fun getDependenciesByTargetGoal(goalId: String): List<GoalDependency> {
        return database.getDependenciesByTargetGoal(goalId).toDomainDependencies()
    }

    override suspend fun getDependenciesForGoal(goalId: String): List<GoalDependency> {
        return database.getDependenciesForGoal(goalId).toDomainDependencies()
    }

    override suspend fun dependencyExists(goalId1: String, goalId2: String): Boolean {
        return database.getDependencyBetweenGoals(goalId1, goalId2) != null
    }

    override suspend fun addDependency(
        sourceGoalId: String,
        targetGoalId: String,
        dependencyType: DependencyType
    ): GoalDependency {
        // Check if dependency already exists
        if (dependencyExists(sourceGoalId, targetGoalId)) {
            throw IllegalStateException("Dependency already exists between these goals")
        }

        // Check for circular dependency for blocking types
        if (dependencyType == DependencyType.BLOCKS || dependencyType == DependencyType.BLOCKED_BY) {
            if (wouldCreateCycle(sourceGoalId, targetGoalId)) {
                throw IllegalStateException("Adding this dependency would create a circular dependency")
            }
        }

        val dependency = createNewDependency(sourceGoalId, targetGoalId, dependencyType)
        database.insertGoalDependency(dependency.toEntity())
        return dependency
    }

    override suspend fun removeDependency(dependencyId: String) {
        database.deleteDependency(dependencyId)
    }

    override suspend fun removeDependenciesForGoal(goalId: String) {
        database.deleteDependenciesByGoal(goalId)
    }

    override suspend fun getBlockingGoals(goalId: String): List<Goal> {
        return database.getBlockingGoals(goalId).map { entity ->
            val milestones = database.getMilestonesByGoalId(entity.id)
            entity.toDomainGoal(milestones.map { it.toDomainMilestone() })
        }
    }

    override suspend fun getBlockedGoals(goalId: String): List<Goal> {
        return database.getBlockedGoals(goalId).map { entity ->
            val milestones = database.getMilestonesByGoalId(entity.id)
            entity.toDomainGoal(milestones.map { it.toDomainMilestone() })
        }
    }

    override suspend fun getChildGoals(goalId: String): List<Goal> {
        return database.getChildGoals(goalId).map { entity ->
            val milestones = database.getMilestonesByGoalId(entity.id)
            entity.toDomainGoal(milestones.map { it.toDomainMilestone() })
        }
    }

    override suspend fun getParentGoals(goalId: String): List<Goal> {
        return database.getParentGoals(goalId).map { entity ->
            val milestones = database.getMilestonesByGoalId(entity.id)
            entity.toDomainGoal(milestones.map { it.toDomainMilestone() })
        }
    }

    override suspend fun getRelatedGoals(goalId: String): List<Goal> {
        return database.getRelatedGoals(goalId).map { entity ->
            val milestones = database.getMilestonesByGoalId(entity.id)
            entity.toDomainGoal(milestones.map { it.toDomainMilestone() })
        }
    }

    override suspend fun buildDependencyGraph(): DependencyGraph {
        val allGoals = database.getAllGoals()
        val allDependencies = database.getAllDependencies().toDomainDependencies()

        val nodes = allGoals.map { goalEntity ->
            val milestones = database.getMilestonesByGoalId(goalEntity.id)
            val goal = goalEntity.toDomainGoal(milestones.map { it.toDomainMilestone() })

            val dependencies = allDependencies.filter { it.sourceGoalId == goal.id }
            val dependents = allDependencies.filter { it.targetGoalId == goal.id }

            GoalNode(
                goal = goal,
                dependencies = dependencies,
                dependents = dependents,
                level = calculateLevel(goal.id, allDependencies, mutableSetOf())
            )
        }

        return DependencyGraph(nodes = nodes, edges = allDependencies)
    }

    override suspend fun buildDependencyGraphForGoal(goalId: String): DependencyGraph {
        val connectedGoalIds = getConnectedGoalIds(goalId, mutableSetOf())
        val allGoals = database.getAllGoals().filter { it.id in connectedGoalIds }
        val allDependencies = database.getAllDependencies()
            .toDomainDependencies()
            .filter { it.sourceGoalId in connectedGoalIds && it.targetGoalId in connectedGoalIds }

        val nodes = allGoals.map { goalEntity ->
            val milestones = database.getMilestonesByGoalId(goalEntity.id)
            val goal = goalEntity.toDomainGoal(milestones.map { it.toDomainMilestone() })

            val dependencies = allDependencies.filter { it.sourceGoalId == goal.id }
            val dependents = allDependencies.filter { it.targetGoalId == goal.id }

            GoalNode(
                goal = goal,
                dependencies = dependencies,
                dependents = dependents,
                level = calculateLevel(goal.id, allDependencies, mutableSetOf())
            )
        }

        return DependencyGraph(nodes = nodes, edges = allDependencies)
    }

    override suspend fun getSuggestedDependencies(goalId: String): List<Pair<Goal, DependencyType>> {
        val currentGoalEntity = database.getAllGoals().find { it.id == goalId } ?: return emptyList()
        val currentGoal = currentGoalEntity.toDomainGoal(emptyList())

        val existingDependencies = getDependenciesForGoal(goalId)
        val connectedGoalIds = existingDependencies.flatMap {
            listOf(it.sourceGoalId, it.targetGoalId)
        }.toSet() + goalId

        val allGoals = database.getAllGoals()
            .filter { it.id !in connectedGoalIds }
            .map { entity ->
                val milestones = database.getMilestonesByGoalId(entity.id)
                entity.toDomainGoal(milestones.map { it.toDomainMilestone() })
            }

        return allGoals.mapNotNull { goal ->
            val suggestedType = suggestDependencyType(currentGoal, goal)
            suggestedType?.let { goal to it }
        }.take(5) // Limit to 5 suggestions
    }

    override suspend fun wouldCreateCycle(sourceGoalId: String, targetGoalId: String): Boolean {
        // Check if targetGoalId can reach sourceGoalId through existing dependencies
        val visited = mutableSetOf<String>()
        return canReach(targetGoalId, sourceGoalId, visited)
    }

    // Helper function to check if there's a path from start to target
    private suspend fun canReach(start: String, target: String, visited: MutableSet<String>): Boolean {
        if (start == target) return true
        if (start in visited) return false

        visited.add(start)

        val dependencies = database.getDependenciesBySourceGoal(start).toDomainDependencies()
        for (dep in dependencies) {
            if (dep.dependencyType == DependencyType.BLOCKS || dep.dependencyType == DependencyType.PARENT_OF) {
                if (canReach(dep.targetGoalId, target, visited)) {
                    return true
                }
            }
        }

        return false
    }

    // Helper function to get all connected goal IDs
    private suspend fun getConnectedGoalIds(goalId: String, visited: MutableSet<String>): Set<String> {
        if (goalId in visited) return visited
        visited.add(goalId)

        val dependencies = database.getDependenciesForGoal(goalId).toDomainDependencies()
        for (dep in dependencies) {
            val connectedId = if (dep.sourceGoalId == goalId) dep.targetGoalId else dep.sourceGoalId
            getConnectedGoalIds(connectedId, visited)
        }

        return visited
    }

    // Helper function to calculate the hierarchical level of a goal
    private fun calculateLevel(
        goalId: String,
        allDependencies: List<GoalDependency>,
        visited: MutableSet<String>
    ): Int {
        if (goalId in visited) return 0
        visited.add(goalId)

        val blockingDependencies = allDependencies.filter {
            it.sourceGoalId == goalId &&
                    (it.dependencyType == DependencyType.BLOCKED_BY || it.dependencyType == DependencyType.CHILD_OF)
        }

        if (blockingDependencies.isEmpty()) return 0

        return blockingDependencies.maxOfOrNull { dep ->
            1 + calculateLevel(dep.targetGoalId, allDependencies, visited)
        } ?: 0
    }

    // Helper function to suggest dependency type based on goal similarities
    private fun suggestDependencyType(currentGoal: Goal, otherGoal: Goal): DependencyType? {
        // Same category goals might be related
        if (currentGoal.category == otherGoal.category) {
            return DependencyType.RELATED
        }

        // Goals with similar timelines in complementary categories might support each other
        if (currentGoal.timeline == otherGoal.timeline) {
            val complementaryPairs = setOf(
                setOf(GoalCategory.PHYSICAL, GoalCategory.EMOTIONAL),
                setOf(GoalCategory.CAREER, GoalCategory.FINANCIAL),
                setOf(GoalCategory.FAMILY, GoalCategory.SOCIAL)
            )

            if (complementaryPairs.any {
                    currentGoal.category in it && otherGoal.category in it
                }) {
                return DependencyType.SUPPORTS
            }
        }

        // If the other goal is due before the current goal, it might be a prerequisite
        if (otherGoal.dueDate < currentGoal.dueDate &&
            otherGoal.category == currentGoal.category) {
            return DependencyType.BLOCKED_BY
        }

        return null
    }
}

// Extension functions to convert entities to domain objects
private fun az.tribe.lifeplanner.database.GoalEntity.toDomainGoal(
    milestones: List<az.tribe.lifeplanner.domain.model.Milestone>
): Goal {
    return Goal(
        id = id,
        category = az.tribe.lifeplanner.domain.enum.GoalCategory.valueOf(category),
        title = title,
        description = description,
        status = az.tribe.lifeplanner.domain.enum.GoalStatus.valueOf(status),
        timeline = az.tribe.lifeplanner.domain.enum.GoalTimeline.valueOf(timeline),
        dueDate = kotlinx.datetime.LocalDate.parse(dueDate),
        progress = progress,
        milestones = milestones,
        notes = notes ?: "",
        createdAt = parseLocalDateTime(createdAt),
        completionRate = completionRate?.toFloat() ?: 0f,
        isArchived = isArchived == 1L
    )
}

private fun az.tribe.lifeplanner.database.MilestoneEntity.toDomainMilestone(): az.tribe.lifeplanner.domain.model.Milestone {
    return az.tribe.lifeplanner.domain.model.Milestone(
        id = id,
        title = title,
        isCompleted = isCompleted == 1L,
        dueDate = dueDate?.let { kotlinx.datetime.LocalDate.parse(it) }
    )
}
