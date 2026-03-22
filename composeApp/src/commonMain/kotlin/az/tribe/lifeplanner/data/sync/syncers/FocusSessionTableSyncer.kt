package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.FocusSessionSyncDto
import az.tribe.lifeplanner.database.FocusSessionEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlinx.datetime.Clock

class FocusSessionTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<FocusSessionEntity, FocusSessionSyncDto>(supabase) {

    override val tableName = "focus_sessions"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<FocusSessionSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<FocusSessionEntity> =
        db { it.lifePlannerDBQueries.getUnsyncedFocusSessions().executeAsList() }

    override suspend fun getDeletedLocal(): List<FocusSessionEntity> =
        db { it.lifePlannerDBQueries.getDeletedFocusSessions().executeAsList() }

    override suspend fun localToRemote(local: FocusSessionEntity, userId: String) = FocusSessionSyncDto(
        id = local.id,
        userId = userId,
        goalId = local.goalId.ifEmpty { null }?.takeIf { it != "getting_started_goal" },
        milestoneId = local.milestoneId.ifEmpty { null }?.takeIf { !it.startsWith("gs_milestone_") },
        plannedDurationMinutes = local.plannedDurationMinutes,
        actualDurationSeconds = local.actualDurationSeconds,
        wasCompleted = local.wasCompleted != 0L,
        xpEarned = local.xpEarned,
        startedAt = local.startedAt,
        completedAt = local.completedAt,
        createdAt = local.createdAt,
        mood = local.mood,
        ambientSound = local.ambientSound,
        focusTheme = local.focusTheme,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: FocusSessionSyncDto) = FocusSessionEntity(
        id = remote.id,
        goalId = remote.goalId ?: "",
        milestoneId = remote.milestoneId ?: "",
        plannedDurationMinutes = remote.plannedDurationMinutes,
        actualDurationSeconds = remote.actualDurationSeconds,
        wasCompleted = if (remote.wasCompleted) 1L else 0L,
        xpEarned = remote.xpEarned,
        startedAt = remote.startedAt,
        completedAt = remote.completedAt,
        createdAt = remote.createdAt,
        mood = remote.mood,
        ambientSound = remote.ambientSound,
        focusTheme = remote.focusTheme,
        sync_updated_at = remote.updatedAt,
        is_deleted = if (remote.isDeleted) 1L else 0L,
        sync_version = remote.syncVersion,
        last_synced_at = Clock.System.now().toString()
    )

    override suspend fun upsertLocal(entity: FocusSessionEntity) {
        db {
            it.lifePlannerDBQueries.upsertFocusSessionFromSync(
                id = entity.id,
                goalId = entity.goalId,
                milestoneId = entity.milestoneId,
                plannedDurationMinutes = entity.plannedDurationMinutes,
                actualDurationSeconds = entity.actualDurationSeconds,
                wasCompleted = entity.wasCompleted,
                xpEarned = entity.xpEarned,
                startedAt = entity.startedAt,
                completedAt = entity.completedAt,
                createdAt = entity.createdAt,
                mood = entity.mood,
                ambientSound = entity.ambientSound,
                focusTheme = entity.focusTheme,
                sync_updated_at = entity.sync_updated_at,
                is_deleted = entity.is_deleted,
                sync_version = entity.sync_version,
                last_synced_at = entity.last_synced_at
            )
        }
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markFocusSessionSynced(now, id) }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedFocusSessions() }
    }

    override suspend fun getEntityId(entity: FocusSessionEntity): String = entity.id

    override suspend fun getLastPullTimestamp(): String? =
        settings.getStringOrNull("sync_pull_focus_sessions")

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_focus_sessions", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()
        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<FocusSessionSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<FocusSessionSyncDto>()
        }
        remoteItems.forEach { upsertLocal(remoteToLocal(it)) }
        setLastPullTimestamp(now)
        if (remoteItems.isNotEmpty()) Logger.d("SyncEngine") { "Pulled ${remoteItems.size} items from $tableName" }
        return remoteItems.size
    }
}
