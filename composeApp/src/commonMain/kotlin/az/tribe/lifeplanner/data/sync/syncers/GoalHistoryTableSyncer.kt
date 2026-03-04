package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.GoalHistorySyncDto
import az.tribe.lifeplanner.database.GoalHistoryEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlinx.datetime.Clock

class GoalHistoryTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<GoalHistoryEntity, GoalHistorySyncDto>(supabase) {

    override val tableName = "goal_history"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<GoalHistorySyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<GoalHistoryEntity> =
        db { it.lifePlannerDBQueries.getUnsyncedGoalHistory().executeAsList() }

    override suspend fun getDeletedLocal(): List<GoalHistoryEntity> =
        db { it.lifePlannerDBQueries.getDeletedGoalHistory().executeAsList() }

    override suspend fun localToRemote(local: GoalHistoryEntity, userId: String) = GoalHistorySyncDto(
        id = local.id,
        userId = userId,
        goalId = local.goalId,
        field = local.field_,
        oldValue = local.oldValue,
        newValue = local.newValue,
        changedAt = local.changedAt,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: GoalHistorySyncDto) = GoalHistoryEntity(
        id = remote.id,
        goalId = remote.goalId,
        field_ = remote.field,
        oldValue = remote.oldValue,
        newValue = remote.newValue,
        changedAt = remote.changedAt,
        sync_updated_at = remote.updatedAt,
        is_deleted = if (remote.isDeleted) 1L else 0L,
        sync_version = remote.syncVersion,
        last_synced_at = Clock.System.now().toString()
    )

    override suspend fun upsertLocal(entity: GoalHistoryEntity) {
        db {
            it.lifePlannerDBQueries.upsertGoalHistoryFromSync(
                id = entity.id,
                goalId = entity.goalId,
                field_ = entity.field_,
                oldValue = entity.oldValue,
                newValue = entity.newValue,
                changedAt = entity.changedAt,
                sync_updated_at = entity.sync_updated_at,
                is_deleted = entity.is_deleted,
                sync_version = entity.sync_version,
                last_synced_at = entity.last_synced_at
            )
        }
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markGoalHistorySynced(now, id) }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedGoalHistory() }
    }

    override suspend fun getEntityId(entity: GoalHistoryEntity): String = entity.id

    override suspend fun getLastPullTimestamp(): String? =
        settings.getStringOrNull("sync_pull_goal_history")

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_goal_history", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()
        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<GoalHistorySyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<GoalHistorySyncDto>()
        }
        remoteItems.forEach { upsertLocal(remoteToLocal(it)) }
        setLastPullTimestamp(now)
        if (remoteItems.isNotEmpty()) Logger.d("SyncEngine") { "Pulled ${remoteItems.size} items from $tableName" }
        return remoteItems.size
    }
}
