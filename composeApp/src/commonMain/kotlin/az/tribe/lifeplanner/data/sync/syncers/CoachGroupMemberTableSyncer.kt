package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.CoachGroupMemberSyncDto
import az.tribe.lifeplanner.database.CoachGroupMemberEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlin.time.Clock

class CoachGroupMemberTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<CoachGroupMemberEntity, CoachGroupMemberSyncDto>(supabase) {

    override val tableName = "coach_group_members"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<CoachGroupMemberSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<CoachGroupMemberEntity> =
        db { it.lifePlannerDBQueries.getUnsyncedCoachGroupMembers().executeAsList() }

    override suspend fun getDeletedLocal(): List<CoachGroupMemberEntity> =
        db { it.lifePlannerDBQueries.getDeletedCoachGroupMembers().executeAsList() }

    override suspend fun localToRemote(local: CoachGroupMemberEntity, userId: String) = CoachGroupMemberSyncDto(
        id = local.id,
        userId = userId,
        groupId = local.groupId,
        coachType = local.coachType,
        coachId = local.coachId,
        displayOrder = local.displayOrder,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: CoachGroupMemberSyncDto) = CoachGroupMemberEntity(
        id = remote.id,
        groupId = remote.groupId,
        coachType = remote.coachType,
        coachId = remote.coachId,
        displayOrder = remote.displayOrder,
        sync_updated_at = remote.updatedAt,
        is_deleted = if (remote.isDeleted) 1L else 0L,
        sync_version = remote.syncVersion,
        last_synced_at = Clock.System.now().toString()
    )

    override suspend fun upsertLocal(entity: CoachGroupMemberEntity) {
        db {
            it.lifePlannerDBQueries.upsertCoachGroupMemberFromSync(
                id = entity.id,
                groupId = entity.groupId,
                coachType = entity.coachType,
                coachId = entity.coachId,
                displayOrder = entity.displayOrder,
                sync_updated_at = entity.sync_updated_at,
                is_deleted = entity.is_deleted,
                sync_version = entity.sync_version,
                last_synced_at = entity.last_synced_at
            )
        }
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markCoachGroupMemberSynced(now, id) }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedCoachGroupMembers() }
    }

    override suspend fun getEntityId(entity: CoachGroupMemberEntity): String = entity.id

    override suspend fun getLastPullTimestamp(): String? =
        settings.getStringOrNull("sync_pull_coach_group_members")

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_coach_group_members", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()
        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<CoachGroupMemberSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<CoachGroupMemberSyncDto>()
        }
        remoteItems.forEach { upsertLocal(remoteToLocal(it)) }
        setLastPullTimestamp(now)
        if (remoteItems.isNotEmpty()) Logger.d("SyncEngine") { "Pulled ${remoteItems.size} items from $tableName" }
        return remoteItems.size
    }
}
