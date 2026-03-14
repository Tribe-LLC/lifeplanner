package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.HabitCheckInSyncDto
import az.tribe.lifeplanner.database.HabitCheckInEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlinx.datetime.Clock

class HabitCheckInTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<HabitCheckInEntity, HabitCheckInSyncDto>(supabase) {

    override val tableName = "habit_check_ins"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<HabitCheckInSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<HabitCheckInEntity> =
        db { it.lifePlannerDBQueries.getUnsyncedHabitCheckIns().executeAsList() }

    override suspend fun getDeletedLocal(): List<HabitCheckInEntity> =
        db { it.lifePlannerDBQueries.getDeletedHabitCheckIns().executeAsList() }

    override suspend fun localToRemote(local: HabitCheckInEntity, userId: String) = HabitCheckInSyncDto(
        id = local.id,
        userId = userId,
        habitId = local.habitId,
        date = local.date,
        completed = local.completed != 0L,
        notes = local.notes,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: HabitCheckInSyncDto) = HabitCheckInEntity(
        id = remote.id,
        habitId = remote.habitId,
        date = remote.date,
        completed = if (remote.completed) 1L else 0L,
        notes = remote.notes,
        sync_updated_at = remote.updatedAt,
        is_deleted = if (remote.isDeleted) 1L else 0L,
        sync_version = remote.syncVersion,
        last_synced_at = Clock.System.now().toString()
    )

    override suspend fun upsertLocal(entity: HabitCheckInEntity) {
        db {
            it.lifePlannerDBQueries.upsertHabitCheckInFromSync(
                id = entity.id,
                habitId = entity.habitId,
                date = entity.date,
                completed = entity.completed,
                notes = entity.notes,
                sync_updated_at = entity.sync_updated_at,
                is_deleted = entity.is_deleted,
                sync_version = entity.sync_version,
                last_synced_at = entity.last_synced_at
            )
        }
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markHabitCheckInSynced(now, id) }
    }

    override suspend fun markSyncedBatch(entities: List<HabitCheckInEntity>, now: String) {
        if (entities.isEmpty()) return
        db { d -> entities.forEach { d.lifePlannerDBQueries.markHabitCheckInSynced(now, it.id) } }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedHabitCheckIns() }
    }

    override suspend fun getEntityId(entity: HabitCheckInEntity): String = entity.id

    override suspend fun getLastPullTimestamp(): String? =
        settings.getStringOrNull("sync_pull_habit_check_ins")

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_habit_check_ins", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()
        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<HabitCheckInSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<HabitCheckInSyncDto>()
        }
        remoteItems.forEach { upsertLocal(remoteToLocal(it)) }
        setLastPullTimestamp(now)
        if (remoteItems.isNotEmpty()) Logger.d("SyncEngine") { "Pulled ${remoteItems.size} items from $tableName" }
        return remoteItems.size
    }
}
