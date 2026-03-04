package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.BadgeSyncDto
import az.tribe.lifeplanner.database.BadgeEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlinx.datetime.Clock

class BadgeTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<BadgeEntity, BadgeSyncDto>(supabase) {

    override val tableName = "badges"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<BadgeSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<BadgeEntity> {
        return db { it.lifePlannerDBQueries.getUnsyncedBadges().executeAsList() }
    }

    override suspend fun getDeletedLocal(): List<BadgeEntity> {
        return db { it.lifePlannerDBQueries.getDeletedBadges().executeAsList() }
    }

    override suspend fun localToRemote(local: BadgeEntity, userId: String) = BadgeSyncDto(
        id = local.id,
        userId = userId,
        badgeType = local.badgeType,
        earnedAt = local.earnedAt,
        isNew = local.isNew != 0L,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: BadgeSyncDto): BadgeEntity {
        return BadgeEntity(
            id = remote.id,
            badgeType = remote.badgeType,
            earnedAt = remote.earnedAt,
            isNew = if (remote.isNew) 1L else 0L,
            sync_updated_at = remote.updatedAt,
            is_deleted = if (remote.isDeleted) 1L else 0L,
            sync_version = remote.syncVersion,
            last_synced_at = Clock.System.now().toString()
        )
    }

    override suspend fun upsertLocal(entity: BadgeEntity) {
        db { it.lifePlannerDBQueries.upsertBadgeFromSync(
            id = entity.id,
            badgeType = entity.badgeType,
            earnedAt = entity.earnedAt,
            isNew = entity.isNew,
            sync_updated_at = entity.sync_updated_at,
            is_deleted = entity.is_deleted,
            sync_version = entity.sync_version,
            last_synced_at = entity.last_synced_at
        )}
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markBadgeSynced(now, id) }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedBadges() }
    }

    override suspend fun getEntityId(entity: BadgeEntity) = entity.id

    override suspend fun getLastPullTimestamp(): String? {
        return settings.getStringOrNull("sync_pull_badges")
    }

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_badges", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()

        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<BadgeSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<BadgeSyncDto>()
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
