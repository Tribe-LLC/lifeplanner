package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.ChallengeSyncDto
import az.tribe.lifeplanner.database.ChallengeEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlin.time.Clock

class ChallengeTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<ChallengeEntity, ChallengeSyncDto>(supabase) {

    override val tableName = "challenges"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<ChallengeSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<ChallengeEntity> =
        db { it.lifePlannerDBQueries.getUnsyncedChallenges().executeAsList() }

    override suspend fun getDeletedLocal(): List<ChallengeEntity> =
        db { it.lifePlannerDBQueries.getDeletedChallenges().executeAsList() }

    override suspend fun localToRemote(local: ChallengeEntity, userId: String) = ChallengeSyncDto(
        id = local.id,
        userId = userId,
        challengeType = local.challengeType,
        startDate = local.startDate,
        endDate = local.endDate,
        currentProgress = local.currentProgress,
        targetProgress = local.targetProgress,
        isCompleted = local.isCompleted != 0L,
        completedAt = local.completedAt,
        xpEarned = local.xpEarned,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: ChallengeSyncDto) = ChallengeEntity(
        id = remote.id,
        challengeType = remote.challengeType,
        startDate = remote.startDate,
        endDate = remote.endDate,
        currentProgress = remote.currentProgress,
        targetProgress = remote.targetProgress,
        isCompleted = if (remote.isCompleted) 1L else 0L,
        completedAt = remote.completedAt,
        xpEarned = remote.xpEarned,
        sync_updated_at = remote.updatedAt,
        is_deleted = if (remote.isDeleted) 1L else 0L,
        sync_version = remote.syncVersion,
        last_synced_at = Clock.System.now().toString()
    )

    override suspend fun upsertLocal(entity: ChallengeEntity) {
        db {
            it.lifePlannerDBQueries.upsertChallengeFromSync(
                id = entity.id,
                challengeType = entity.challengeType,
                startDate = entity.startDate,
                endDate = entity.endDate,
                currentProgress = entity.currentProgress,
                targetProgress = entity.targetProgress,
                isCompleted = entity.isCompleted,
                completedAt = entity.completedAt,
                xpEarned = entity.xpEarned,
                sync_updated_at = entity.sync_updated_at,
                is_deleted = entity.is_deleted,
                sync_version = entity.sync_version,
                last_synced_at = entity.last_synced_at
            )
        }
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markChallengeSynced(now, id) }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedChallenges() }
    }

    override suspend fun getEntityId(entity: ChallengeEntity): String = entity.id

    override suspend fun getLastPullTimestamp(): String? =
        settings.getStringOrNull("sync_pull_challenges")

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_challenges", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()
        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<ChallengeSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<ChallengeSyncDto>()
        }
        remoteItems.forEach { upsertLocal(remoteToLocal(it)) }
        setLastPullTimestamp(now)
        if (remoteItems.isNotEmpty()) Logger.d("SyncEngine") { "Pulled ${remoteItems.size} items from $tableName" }
        return remoteItems.size
    }
}
