package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.GoalDependencySyncDto
import az.tribe.lifeplanner.database.GoalDependencyEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlin.time.Clock

class GoalDependencyTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<GoalDependencyEntity, GoalDependencySyncDto>(supabase) {

    override val tableName = "goal_dependencies"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<GoalDependencySyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<GoalDependencyEntity> =
        db { it.lifePlannerDBQueries.getUnsyncedGoalDependencies().executeAsList() }

    override suspend fun getDeletedLocal(): List<GoalDependencyEntity> =
        db { it.lifePlannerDBQueries.getDeletedGoalDependencies().executeAsList() }

    override suspend fun localToRemote(local: GoalDependencyEntity, userId: String) = GoalDependencySyncDto(
        id = local.id,
        userId = userId,
        sourceGoalId = local.sourceGoalId,
        targetGoalId = local.targetGoalId,
        dependencyType = local.dependencyType,
        createdAt = local.createdAt,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: GoalDependencySyncDto) = GoalDependencyEntity(
        id = remote.id,
        sourceGoalId = remote.sourceGoalId,
        targetGoalId = remote.targetGoalId,
        dependencyType = remote.dependencyType,
        createdAt = remote.createdAt,
        sync_updated_at = remote.updatedAt,
        is_deleted = if (remote.isDeleted) 1L else 0L,
        sync_version = remote.syncVersion,
        last_synced_at = Clock.System.now().toString()
    )

    override suspend fun upsertLocal(entity: GoalDependencyEntity) {
        db {
            it.lifePlannerDBQueries.upsertGoalDependencyFromSync(
                id = entity.id,
                sourceGoalId = entity.sourceGoalId,
                targetGoalId = entity.targetGoalId,
                dependencyType = entity.dependencyType,
                createdAt = entity.createdAt,
                sync_updated_at = entity.sync_updated_at,
                is_deleted = entity.is_deleted,
                sync_version = entity.sync_version,
                last_synced_at = entity.last_synced_at
            )
        }
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markGoalDependencySynced(now, id) }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedGoalDependencies() }
    }

    override suspend fun getEntityId(entity: GoalDependencyEntity): String = entity.id

    override suspend fun getLastPullTimestamp(): String? =
        settings.getStringOrNull("sync_pull_goal_dependencies")

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_goal_dependencies", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()
        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<GoalDependencySyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<GoalDependencySyncDto>()
        }
        remoteItems.forEach { upsertLocal(remoteToLocal(it)) }
        setLastPullTimestamp(now)
        if (remoteItems.isNotEmpty()) Logger.d("SyncEngine") { "Pulled ${remoteItems.size} items from $tableName" }
        return remoteItems.size
    }
}
