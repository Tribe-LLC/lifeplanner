package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.GoalSyncDto
import az.tribe.lifeplanner.database.GoalEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import co.touchlab.kermit.Logger
import kotlinx.datetime.Clock

class GoalTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<GoalEntity, GoalSyncDto>(supabase) {

    override val tableName = "goals"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<GoalSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<GoalEntity> {
        return db { it.lifePlannerDBQueries.getUnsyncedGoals().executeAsList() }
    }

    override suspend fun getDeletedLocal(): List<GoalEntity> {
        return db { it.lifePlannerDBQueries.getDeletedGoals().executeAsList() }
    }

    override suspend fun localToRemote(local: GoalEntity, userId: String) = GoalSyncDto(
        id = local.id,
        userId = userId,
        category = local.category,
        title = local.title,
        description = local.description,
        status = local.status,
        timeline = local.timeline,
        dueDate = local.dueDate,
        progress = local.progress,
        notes = local.notes ?: "",
        createdAt = local.createdAt,
        completionRate = local.completionRate ?: 0.0,
        isArchived = local.isArchived != 0L,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: GoalSyncDto): GoalEntity {
        return GoalEntity(
            id = remote.id,
            category = remote.category,
            title = remote.title,
            description = remote.description,
            status = remote.status,
            timeline = remote.timeline,
            dueDate = remote.dueDate,
            progress = remote.progress,
            notes = remote.notes,
            createdAt = remote.createdAt,
            completionRate = remote.completionRate,
            isArchived = if (remote.isArchived) 1L else 0L,
            sync_updated_at = remote.updatedAt,
            is_deleted = if (remote.isDeleted) 1L else 0L,
            sync_version = remote.syncVersion,
            last_synced_at = Clock.System.now().toString()
        )
    }

    override suspend fun upsertLocal(entity: GoalEntity) {
        db { it.lifePlannerDBQueries.upsertGoalFromSync(
            id = entity.id,
            category = entity.category,
            title = entity.title,
            description = entity.description,
            status = entity.status,
            timeline = entity.timeline,
            dueDate = entity.dueDate,
            progress = entity.progress,
            notes = entity.notes ?: "",
            createdAt = entity.createdAt,
            completionRate = entity.completionRate ?: 0.0,
            isArchived = entity.isArchived,
            sync_updated_at = entity.sync_updated_at,
            is_deleted = entity.is_deleted,
            sync_version = entity.sync_version,
            last_synced_at = entity.last_synced_at
        )}
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markGoalSynced(now, id) }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedGoals() }
    }

    override suspend fun getEntityId(entity: GoalEntity) = entity.id

    override suspend fun getLastPullTimestamp(): String? {
        return settings.getStringOrNull("sync_pull_goals")
    }

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_goals", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()
        Logger.d("SyncEngine") { "Pull $tableName: userId=$userId, lastPull=$lastPull" }

        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<GoalSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<GoalSyncDto>()
        }

        Logger.d("SyncEngine") { "Pull $tableName: got ${remoteItems.size} items" }
        remoteItems.forEach { remote ->
            val local = remoteToLocal(remote)
            upsertLocal(local)
        }

        setLastPullTimestamp(now)
        if (remoteItems.isNotEmpty()) {
            Logger.d("SyncEngine") { "Pulled ${remoteItems.size} items from $tableName" }
        }
        return remoteItems.size
    }
}
