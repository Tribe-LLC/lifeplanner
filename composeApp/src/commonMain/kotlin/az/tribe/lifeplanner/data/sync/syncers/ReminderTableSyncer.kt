package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.ReminderSyncDto
import az.tribe.lifeplanner.database.ReminderEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlin.time.Clock

class ReminderTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<ReminderEntity, ReminderSyncDto>(supabase) {

    override val tableName = "reminders"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<ReminderSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<ReminderEntity> =
        db { it.lifePlannerDBQueries.getUnsyncedReminders().executeAsList() }

    override suspend fun getDeletedLocal(): List<ReminderEntity> =
        db { it.lifePlannerDBQueries.getDeletedReminders().executeAsList() }

    override suspend fun localToRemote(local: ReminderEntity, userId: String) = ReminderSyncDto(
        id = local.id,
        userId = userId,
        title = local.title,
        message = local.message,
        type = local.type,
        frequency = local.frequency,
        scheduledTime = local.scheduledTime,
        scheduledDays = local.scheduledDays,
        linkedGoalId = local.linkedGoalId,
        linkedHabitId = local.linkedHabitId,
        isEnabled = local.isEnabled != 0L,
        isSmartTiming = local.isSmartTiming != 0L,
        lastTriggeredAt = local.lastTriggeredAt,
        snoozedUntil = local.snoozedUntil,
        createdAt = local.createdAt,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: ReminderSyncDto) = ReminderEntity(
        id = remote.id,
        title = remote.title,
        message = remote.message,
        type = remote.type,
        frequency = remote.frequency,
        scheduledTime = remote.scheduledTime,
        scheduledDays = remote.scheduledDays,
        linkedGoalId = remote.linkedGoalId,
        linkedHabitId = remote.linkedHabitId,
        isEnabled = if (remote.isEnabled) 1L else 0L,
        isSmartTiming = if (remote.isSmartTiming) 1L else 0L,
        lastTriggeredAt = remote.lastTriggeredAt,
        snoozedUntil = remote.snoozedUntil,
        createdAt = remote.createdAt,
        updatedAt = remote.updatedAt,
        sync_updated_at = remote.updatedAt,
        is_deleted = if (remote.isDeleted) 1L else 0L,
        sync_version = remote.syncVersion,
        last_synced_at = Clock.System.now().toString()
    )

    override suspend fun upsertLocal(entity: ReminderEntity) {
        db {
            it.lifePlannerDBQueries.upsertReminderFromSync(
                id = entity.id,
                title = entity.title,
                message = entity.message,
                type = entity.type,
                frequency = entity.frequency,
                scheduledTime = entity.scheduledTime,
                scheduledDays = entity.scheduledDays,
                linkedGoalId = entity.linkedGoalId,
                linkedHabitId = entity.linkedHabitId,
                isEnabled = entity.isEnabled,
                isSmartTiming = entity.isSmartTiming,
                lastTriggeredAt = entity.lastTriggeredAt,
                snoozedUntil = entity.snoozedUntil,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                sync_updated_at = entity.sync_updated_at,
                is_deleted = entity.is_deleted,
                sync_version = entity.sync_version,
                last_synced_at = entity.last_synced_at
            )
        }
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markReminderSynced(now, id) }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedReminders() }
    }

    override suspend fun getEntityId(entity: ReminderEntity): String = entity.id

    override suspend fun getLastPullTimestamp(): String? =
        settings.getStringOrNull("sync_pull_reminders")

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_reminders", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()
        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<ReminderSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<ReminderSyncDto>()
        }
        remoteItems.forEach { upsertLocal(remoteToLocal(it)) }
        setLastPullTimestamp(now)
        if (remoteItems.isNotEmpty()) Logger.d("SyncEngine") { "Pulled ${remoteItems.size} items from $tableName" }
        return remoteItems.size
    }
}
