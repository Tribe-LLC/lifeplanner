package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.CoachGroupSyncDto
import az.tribe.lifeplanner.database.CoachGroupEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlin.time.Clock

class CoachGroupTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<CoachGroupEntity, CoachGroupSyncDto>(supabase) {

    override val tableName = "coach_groups"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<CoachGroupSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<CoachGroupEntity> {
        return db { it.lifePlannerDBQueries.getUnsyncedCoachGroups().executeAsList() }
    }

    override suspend fun getDeletedLocal(): List<CoachGroupEntity> {
        return db { it.lifePlannerDBQueries.getDeletedCoachGroups().executeAsList() }
    }

    override suspend fun localToRemote(local: CoachGroupEntity, userId: String) = CoachGroupSyncDto(
        id = local.id,
        userId = userId,
        name = local.name,
        icon = local.icon,
        description = local.description,
        createdAt = local.createdAt,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: CoachGroupSyncDto): CoachGroupEntity {
        return CoachGroupEntity(
            id = remote.id,
            name = remote.name,
            icon = remote.icon,
            description = remote.description,
            createdAt = remote.createdAt,
            updatedAt = remote.updatedAt,
            sync_updated_at = remote.updatedAt,
            is_deleted = if (remote.isDeleted) 1L else 0L,
            sync_version = remote.syncVersion,
            last_synced_at = Clock.System.now().toString()
        )
    }

    override suspend fun upsertLocal(entity: CoachGroupEntity) {
        db { it.lifePlannerDBQueries.upsertCoachGroupFromSync(
            id = entity.id,
            name = entity.name,
            icon = entity.icon,
            description = entity.description,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            sync_updated_at = entity.sync_updated_at,
            is_deleted = entity.is_deleted,
            sync_version = entity.sync_version,
            last_synced_at = entity.last_synced_at
        )}
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markCoachGroupSynced(now, id) }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedCoachGroups() }
    }

    override suspend fun getEntityId(entity: CoachGroupEntity) = entity.id

    override suspend fun getLastPullTimestamp(): String? {
        return settings.getStringOrNull("sync_pull_coach_groups")
    }

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_coach_groups", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()

        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<CoachGroupSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<CoachGroupSyncDto>()
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
