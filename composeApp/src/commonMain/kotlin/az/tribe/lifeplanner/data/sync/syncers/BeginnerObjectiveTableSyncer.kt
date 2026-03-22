package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.BeginnerObjectiveSyncDto
import az.tribe.lifeplanner.database.BeginnerObjectiveEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlinx.datetime.Clock

class BeginnerObjectiveTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<BeginnerObjectiveEntity, BeginnerObjectiveSyncDto>(supabase) {

    override val tableName = "beginner_objectives"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<BeginnerObjectiveSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<BeginnerObjectiveEntity> {
        return db { it.lifePlannerDBQueries.getUnsyncedBeginnerObjectives().executeAsList() }
    }

    override suspend fun getDeletedLocal(): List<BeginnerObjectiveEntity> {
        return db { it.lifePlannerDBQueries.getDeletedBeginnerObjectives().executeAsList() }
    }

    override suspend fun localToRemote(local: BeginnerObjectiveEntity, userId: String) = BeginnerObjectiveSyncDto(
        id = local.id,
        userId = userId,
        objectiveType = local.objectiveType,
        isCompleted = local.isCompleted != 0L,
        completedAt = local.completedAt,
        xpAwarded = local.xpAwarded,
        createdAt = local.createdAt,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: BeginnerObjectiveSyncDto): BeginnerObjectiveEntity {
        return BeginnerObjectiveEntity(
            id = remote.id,
            objectiveType = remote.objectiveType,
            isCompleted = if (remote.isCompleted) 1L else 0L,
            completedAt = remote.completedAt,
            xpAwarded = remote.xpAwarded,
            createdAt = remote.createdAt,
            sync_updated_at = remote.updatedAt,
            is_deleted = if (remote.isDeleted) 1L else 0L,
            sync_version = remote.syncVersion,
            last_synced_at = Clock.System.now().toString()
        )
    }

    override suspend fun upsertLocal(entity: BeginnerObjectiveEntity) {
        db { it.lifePlannerDBQueries.upsertBeginnerObjectiveFromSync(
            id = entity.id,
            objectiveType = entity.objectiveType,
            isCompleted = entity.isCompleted,
            completedAt = entity.completedAt,
            xpAwarded = entity.xpAwarded,
            createdAt = entity.createdAt,
            sync_updated_at = entity.sync_updated_at,
            is_deleted = entity.is_deleted,
            sync_version = entity.sync_version,
            last_synced_at = entity.last_synced_at
        )}
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markBeginnerObjectiveSynced(now, id) }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedBeginnerObjectives() }
    }

    override suspend fun getEntityId(entity: BeginnerObjectiveEntity) = entity.id

    override suspend fun getLastPullTimestamp(): String? {
        return settings.getStringOrNull("sync_pull_beginner_objectives")
    }

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_beginner_objectives", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()

        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<BeginnerObjectiveSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<BeginnerObjectiveSyncDto>()
        }

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
