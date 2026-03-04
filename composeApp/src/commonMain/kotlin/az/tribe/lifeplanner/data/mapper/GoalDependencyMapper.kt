package az.tribe.lifeplanner.data.mapper

import az.tribe.lifeplanner.database.GoalDependencyEntity
import az.tribe.lifeplanner.domain.enum.DependencyType
import az.tribe.lifeplanner.domain.model.GoalDependency
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * GoalDependencyEntity to Domain mapper
 */
fun GoalDependencyEntity.toDomain(): GoalDependency = GoalDependency(
    id = id,
    sourceGoalId = sourceGoalId,
    targetGoalId = targetGoalId,
    dependencyType = DependencyType.valueOf(dependencyType),
    createdAt = parseLocalDateTime(createdAt)
)

/**
 * GoalDependency Domain to Entity mapper
 */
fun GoalDependency.toEntity(): GoalDependencyEntity = GoalDependencyEntity(
    id = id,
    sourceGoalId = sourceGoalId,
    targetGoalId = targetGoalId,
    dependencyType = dependencyType.name,
    createdAt = createdAt.toString(),
    sync_updated_at = Clock.System.now().toString(),
    is_deleted = 0L,
    sync_version = 0L,
    last_synced_at = null
)

/**
 * List mapper for entities to domain
 */
fun List<GoalDependencyEntity>.toDomainDependencies(): List<GoalDependency> =
    map { it.toDomain() }

/**
 * Create a new GoalDependency
 */
@OptIn(ExperimentalUuidApi::class)
fun createNewDependency(
    sourceGoalId: String,
    targetGoalId: String,
    dependencyType: DependencyType
): GoalDependency = GoalDependency(
    id = Uuid.random().toString(),
    sourceGoalId = sourceGoalId,
    targetGoalId = targetGoalId,
    dependencyType = dependencyType,
    createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
)
