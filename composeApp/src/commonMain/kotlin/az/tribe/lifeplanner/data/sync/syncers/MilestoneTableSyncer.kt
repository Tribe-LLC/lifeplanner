package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.MilestoneSyncDto
import az.tribe.lifeplanner.database.MilestoneEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlin.time.Clock

private const val GETTING_STARTED_GOAL_ID = "getting_started_goal"

class MilestoneTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<MilestoneEntity, MilestoneSyncDto>(supabase) {

    override val tableName = "milestones"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<MilestoneSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<MilestoneEntity> {
        // Single DB access: fetch milestones and validate goal FKs in one block
        // Skip "Getting Started" goal milestones — local-only system data
        return db { d ->
            val milestones = d.lifePlannerDBQueries.getUnsyncedMilestones().executeAsList()
            if (milestones.isEmpty()) return@db emptyList()
            val goalIds = d.lifePlannerDBQueries.selectAll().executeAsList().map { it.id }.toSet()
            milestones.filter { it.goalId in goalIds && it.goalId != GETTING_STARTED_GOAL_ID }
        }
    }

    override suspend fun getDeletedLocal(): List<MilestoneEntity> =
        db { it.lifePlannerDBQueries.getDeletedMilestones().executeAsList()
            .filter { m -> m.goalId != GETTING_STARTED_GOAL_ID }
        }

    override suspend fun localToRemote(local: MilestoneEntity, userId: String) = MilestoneSyncDto(
        id = local.id,
        userId = userId,
        goalId = local.goalId,
        title = local.title,
        isCompleted = local.isCompleted != 0L,
        dueDate = local.dueDate,
        createdAt = local.createdAt,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: MilestoneSyncDto) = MilestoneEntity(
        id = remote.id,
        goalId = remote.goalId,
        title = remote.title,
        isCompleted = if (remote.isCompleted) 1L else 0L,
        dueDate = remote.dueDate,
        createdAt = remote.createdAt,
        sync_updated_at = remote.updatedAt,
        is_deleted = if (remote.isDeleted) 1L else 0L,
        sync_version = remote.syncVersion,
        last_synced_at = Clock.System.now().toString()
    )

    override suspend fun upsertLocal(entity: MilestoneEntity) {
        db {
            it.lifePlannerDBQueries.upsertMilestoneFromSync(
                id = entity.id,
                goalId = entity.goalId,
                title = entity.title,
                isCompleted = entity.isCompleted,
                dueDate = entity.dueDate,
                createdAt = entity.createdAt,
                sync_updated_at = entity.sync_updated_at,
                is_deleted = entity.is_deleted,
                sync_version = entity.sync_version,
                last_synced_at = entity.last_synced_at
            )
        }
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markMilestoneSynced(now, id) }
    }

    override suspend fun markSyncedBatch(entities: List<MilestoneEntity>, now: String) {
        if (entities.isEmpty()) return
        db { d -> entities.forEach { d.lifePlannerDBQueries.markMilestoneSynced(now, it.id) } }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedMilestones() }
    }

    override suspend fun getEntityId(entity: MilestoneEntity): String = entity.id

    override suspend fun getLastPullTimestamp(): String? =
        settings.getStringOrNull("sync_pull_milestones")

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_milestones", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()
        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<MilestoneSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<MilestoneSyncDto>()
        }
        // Skip Getting Started goal milestones — local-only system data
        val filtered = remoteItems.filter { it.goalId != GETTING_STARTED_GOAL_ID }
        filtered.forEach { upsertLocal(remoteToLocal(it)) }
        setLastPullTimestamp(now)
        if (filtered.isNotEmpty()) Logger.d("SyncEngine") { "Pulled ${filtered.size} items from $tableName (skipped ${remoteItems.size - filtered.size} getting_started)" }
        return filtered.size
    }
}
