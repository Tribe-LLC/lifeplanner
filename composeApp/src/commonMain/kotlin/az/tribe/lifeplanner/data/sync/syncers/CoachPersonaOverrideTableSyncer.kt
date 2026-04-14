package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.CoachPersonaOverrideSyncDto
import az.tribe.lifeplanner.database.CoachPersonaOverrideEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlin.time.Clock

class CoachPersonaOverrideTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<CoachPersonaOverrideEntity, CoachPersonaOverrideSyncDto>(supabase) {

    override val tableName = "coach_persona_overrides"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<CoachPersonaOverrideSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<CoachPersonaOverrideEntity> {
        return db { it.lifePlannerDBQueries.getUnsyncedCoachPersonaOverrides().executeAsList() }
    }

    override suspend fun getDeletedLocal(): List<CoachPersonaOverrideEntity> {
        return db { it.lifePlannerDBQueries.getDeletedCoachPersonaOverrides().executeAsList() }
    }

    override suspend fun localToRemote(local: CoachPersonaOverrideEntity, userId: String): CoachPersonaOverrideSyncDto {
        return CoachPersonaOverrideSyncDto(
            coachId = local.coachId,
            userId = userId,
            userPersona = local.userPersona,
            updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
            isDeleted = local.is_deleted != 0L,
            syncVersion = local.sync_version
        )
    }

    override suspend fun remoteToLocal(remote: CoachPersonaOverrideSyncDto): CoachPersonaOverrideEntity {
        return CoachPersonaOverrideEntity(
            coachId = remote.coachId,
            userPersona = remote.userPersona,
            updatedAt = remote.updatedAt,
            sync_updated_at = remote.updatedAt,
            is_deleted = if (remote.isDeleted) 1L else 0L,
            sync_version = remote.syncVersion,
            last_synced_at = Clock.System.now().toString()
        )
    }

    override suspend fun upsertLocal(entity: CoachPersonaOverrideEntity) {
        db { it.lifePlannerDBQueries.upsertCoachPersonaOverrideFromSync(
            coachId = entity.coachId,
            userPersona = entity.userPersona,
            updatedAt = entity.updatedAt,
            sync_updated_at = entity.sync_updated_at,
            is_deleted = entity.is_deleted,
            sync_version = entity.sync_version,
            last_synced_at = entity.last_synced_at
        )}
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markCoachPersonaOverrideSynced(now, id) }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedCoachPersonaOverrides() }
    }

    override suspend fun getEntityId(entity: CoachPersonaOverrideEntity) = entity.coachId

    override suspend fun getLastPullTimestamp(): String? {
        return settings.getStringOrNull("sync_pull_coach_persona_overrides")
    }

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_coach_persona_overrides", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()

        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<CoachPersonaOverrideSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<CoachPersonaOverrideSyncDto>()
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
