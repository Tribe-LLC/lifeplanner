package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.UserProgressSyncDto
import az.tribe.lifeplanner.database.UserProgressEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlin.time.Clock

class UserProgressTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<UserProgressEntity, UserProgressSyncDto>(supabase) {

    override val tableName = "user_progress"
    private val settings = Settings()

    // Pull-only: server is authoritative for user_progress (XP, counters, level)
    override suspend fun pushLocalChanges(userId: String): Int = 0

    override suspend fun upsertRemote(dtos: List<UserProgressSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos) {
            onConflict = "user_id"
        }
    }

    override suspend fun getUnsyncedLocal(): List<UserProgressEntity> =
        db { it.lifePlannerDBQueries.getUnsyncedUserProgress().executeAsList() }

    override suspend fun getDeletedLocal(): List<UserProgressEntity> = emptyList()

    override suspend fun localToRemote(local: UserProgressEntity, userId: String) = UserProgressSyncDto(
        userId = userId,
        currentStreak = local.currentStreak,
        lastCheckInDate = local.lastCheckInDate,
        totalXp = local.totalXp,
        currentLevel = local.currentLevel,
        goalsCompleted = local.goalsCompleted,
        habitsCompleted = local.habitsCompleted,
        journalEntriesCount = local.journalEntriesCount,
        longestStreak = local.longestStreak,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: UserProgressSyncDto) = UserProgressEntity(
        id = 1, // Local singleton row
        currentStreak = remote.currentStreak,
        lastCheckInDate = remote.lastCheckInDate,
        totalXp = remote.totalXp,
        currentLevel = remote.currentLevel,
        goalsCompleted = remote.goalsCompleted,
        habitsCompleted = remote.habitsCompleted,
        journalEntriesCount = remote.journalEntriesCount,
        longestStreak = remote.longestStreak,
        sync_updated_at = remote.updatedAt,
        is_deleted = if (remote.isDeleted) 1L else 0L,
        sync_version = remote.syncVersion,
        last_synced_at = Clock.System.now().toString()
    )

    override suspend fun upsertLocal(entity: UserProgressEntity) {
        db {
            it.lifePlannerDBQueries.upsertUserProgressFromSync(
                id = entity.id,
                currentStreak = entity.currentStreak,
                lastCheckInDate = entity.lastCheckInDate,
                totalXp = entity.totalXp,
                currentLevel = entity.currentLevel,
                goalsCompleted = entity.goalsCompleted,
                habitsCompleted = entity.habitsCompleted,
                journalEntriesCount = entity.journalEntriesCount,
                longestStreak = entity.longestStreak,
                sync_updated_at = entity.sync_updated_at,
                is_deleted = entity.is_deleted,
                sync_version = entity.sync_version,
                last_synced_at = entity.last_synced_at
            )
        }
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markUserProgressSynced(now, id.toLong()) }
    }

    override suspend fun purgeDeleted() {
        // No-op: UserProgress is never deleted
    }

    override suspend fun getEntityId(entity: UserProgressEntity): String = entity.id.toString()

    override suspend fun getLastPullTimestamp(): String? =
        settings.getStringOrNull("sync_pull_user_progress")

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_user_progress", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()
        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<UserProgressSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<UserProgressSyncDto>()
        }
        remoteItems.forEach { upsertLocal(remoteToLocal(it)) }
        setLastPullTimestamp(now)
        if (remoteItems.isNotEmpty()) Logger.d("SyncEngine") { "Pulled ${remoteItems.size} items from $tableName" }
        return remoteItems.size
    }
}
