package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.GoalSyncDto
import az.tribe.lifeplanner.database.GoalEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import co.touchlab.kermit.Logger
import kotlin.time.Clock

class GoalTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<GoalEntity, GoalSyncDto>(supabase) {

    override val tableName = "goals"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<GoalSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<GoalEntity> {
        return db { it.lifePlannerDBQueries.getUnsyncedGoals().executeAsList()
            .filter { g -> g.id != GETTING_STARTED_GOAL_ID }
        }
    }

    override suspend fun getDeletedLocal(): List<GoalEntity> {
        return db { it.lifePlannerDBQueries.getDeletedGoals().executeAsList()
            .filter { g -> g.id != GETTING_STARTED_GOAL_ID }
        }
    }

    companion object {
        private const val GETTING_STARTED_GOAL_ID = "getting_started_goal"
    }

    override suspend fun localToRemote(local: GoalEntity, userId: String) = GoalSyncDto(
        id = local.id,
        userId = userId,
        category = local.category,
        title = local.title,
        description = local.description,
        status = local.status,
        timeline = local.timeline,
        dueDate = local.dueDate,
        progress = local.progress,
        notes = local.notes ?: "",
        createdAt = local.createdAt,
        completionRate = local.completionRate ?: 0.0,
        isArchived = local.isArchived != 0L,
        aiReasoning = local.aiReasoning,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: GoalSyncDto): GoalEntity {
        return GoalEntity(
            id = remote.id,
            category = remote.category,
            title = remote.title,
            description = remote.description,
            status = remote.status,
            timeline = remote.timeline,
            dueDate = remote.dueDate,
            progress = remote.progress,
            notes = remote.notes,
            createdAt = remote.createdAt,
            completionRate = remote.completionRate,
            isArchived = if (remote.isArchived) 1L else 0L,
            aiReasoning = remote.aiReasoning,
            sync_updated_at = remote.updatedAt,
            is_deleted = if (remote.isDeleted) 1L else 0L,
            sync_version = remote.syncVersion,
            last_synced_at = Clock.System.now().toString()
        )
    }

    override suspend fun upsertLocal(entity: GoalEntity) {
        db { it.lifePlannerDBQueries.upsertGoalFromSync(
            id = entity.id,
            category = entity.category,
            title = entity.title,
            description = entity.description,
            status = entity.status,
            timeline = entity.timeline,
            dueDate = entity.dueDate,
            progress = entity.progress,
            notes = entity.notes ?: "",
            createdAt = entity.createdAt,
            completionRate = entity.completionRate ?: 0.0,
            isArchived = entity.isArchived,
            aiReasoning = entity.aiReasoning,
            sync_updated_at = entity.sync_updated_at,
            is_deleted = entity.is_deleted,
            sync_version = entity.sync_version,
            last_synced_at = entity.last_synced_at
        )}
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markGoalSynced(now, id) }
    }

    override suspend fun markSyncedBatch(entities: List<GoalEntity>, now: String) {
        if (entities.isEmpty()) return
        db { d -> entities.forEach { d.lifePlannerDBQueries.markGoalSynced(now, it.id) } }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedGoals() }
    }

    override suspend fun getEntityId(entity: GoalEntity) = entity.id

    override suspend fun getLastPullTimestamp(): String? {
        return settings.getStringOrNull("sync_pull_goals")
    }

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_goals", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()
        Logger.d("SyncEngine") { "Pull $tableName: userId=$userId, lastPull=$lastPull" }

        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<GoalSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<GoalSyncDto>()
        }

        // Filter out Getting Started goal — local-only system data
        val filteredItems = remoteItems.filter { it.id != GETTING_STARTED_GOAL_ID }
        Logger.d("SyncEngine") { "Pull $tableName: got ${remoteItems.size} items (${remoteItems.size - filteredItems.size} getting_started skipped)" }
        var applied = 0
        filteredItems.forEach { remote ->
            val localEntity = remoteToLocal(remote)
            // Last-write-wins: only apply remote if it has a newer or equal sync_version
            val existingLocal = getLocalById(remote.id)
            if (existingLocal == null || remote.syncVersion >= existingLocal.sync_version) {
                upsertLocal(localEntity)
                applied++
            } else {
                Logger.d("SyncEngine") { "Skipping $tableName ${remote.id}: local sync_version=${existingLocal.sync_version} > remote=${remote.syncVersion}" }
            }
        }

        setLastPullTimestamp(now)
        if (applied > 0) {
            Logger.d("SyncEngine") { "Applied $applied of ${remoteItems.size} pulled items from $tableName" }
        }
        return applied
    }

    /** Look up a local goal by ID for conflict comparison. Returns null if not found. */
    private suspend fun getLocalById(id: String): GoalEntity? {
        return try {
            db { it.lifePlannerDBQueries.selectGoalById(id).executeAsOneOrNull() }
        } catch (e: Exception) {
            null
        }
    }
}
