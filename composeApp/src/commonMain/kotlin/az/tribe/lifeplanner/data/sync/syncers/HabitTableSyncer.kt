package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.HabitSyncDto
import az.tribe.lifeplanner.database.HabitEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlin.time.Clock

class HabitTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<HabitEntity, HabitSyncDto>(supabase) {

    override val tableName = "habits"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<HabitSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<HabitEntity> {
        return db { it.lifePlannerDBQueries.getUnsyncedHabits().executeAsList() }
    }

    override suspend fun getDeletedLocal(): List<HabitEntity> {
        return db { it.lifePlannerDBQueries.getDeletedHabits().executeAsList() }
    }

    override suspend fun localToRemote(local: HabitEntity, userId: String) = HabitSyncDto(
        id = local.id,
        userId = userId,
        title = local.title,
        description = local.description,
        category = local.category,
        frequency = local.frequency,
        targetCount = local.targetCount,
        currentStreak = local.currentStreak,
        longestStreak = local.longestStreak,
        totalCompletions = local.totalCompletions,
        lastCompletedDate = local.lastCompletedDate,
        linkedGoalId = local.linkedGoalId?.takeIf { it != "getting_started_goal" },
        correlationScore = local.correlationScore,
        isActive = local.isActive != 0L,
        createdAt = local.createdAt,
        reminderTime = local.reminderTime,
        type = local.type,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: HabitSyncDto): HabitEntity {
        return HabitEntity(
            id = remote.id,
            title = remote.title,
            description = remote.description,
            category = remote.category,
            frequency = remote.frequency,
            targetCount = remote.targetCount,
            currentStreak = remote.currentStreak,
            longestStreak = remote.longestStreak,
            totalCompletions = remote.totalCompletions,
            lastCompletedDate = remote.lastCompletedDate,
            linkedGoalId = remote.linkedGoalId,
            correlationScore = remote.correlationScore,
            isActive = if (remote.isActive) 1L else 0L,
            createdAt = remote.createdAt,
            reminderTime = remote.reminderTime,
            type = remote.type,
            sync_updated_at = remote.updatedAt,
            is_deleted = if (remote.isDeleted) 1L else 0L,
            sync_version = remote.syncVersion,
            last_synced_at = Clock.System.now().toString()
        )
    }

    override suspend fun upsertLocal(entity: HabitEntity) {
        db { it.lifePlannerDBQueries.upsertHabitFromSync(
            id = entity.id,
            title = entity.title,
            description = entity.description,
            category = entity.category,
            frequency = entity.frequency,
            targetCount = entity.targetCount,
            currentStreak = entity.currentStreak,
            longestStreak = entity.longestStreak,
            totalCompletions = entity.totalCompletions,
            lastCompletedDate = entity.lastCompletedDate,
            linkedGoalId = entity.linkedGoalId,
            correlationScore = entity.correlationScore,
            isActive = entity.isActive,
            createdAt = entity.createdAt,
            reminderTime = entity.reminderTime,
            type = entity.type,
            sync_updated_at = entity.sync_updated_at,
            is_deleted = entity.is_deleted,
            sync_version = entity.sync_version,
            last_synced_at = entity.last_synced_at
        )}
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markHabitSynced(now, id) }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedHabits() }
    }

    override suspend fun getEntityId(entity: HabitEntity) = entity.id

    override suspend fun getLastPullTimestamp(): String? {
        return settings.getStringOrNull("sync_pull_habits")
    }

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_habits", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()

        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<HabitSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<HabitSyncDto>()
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
