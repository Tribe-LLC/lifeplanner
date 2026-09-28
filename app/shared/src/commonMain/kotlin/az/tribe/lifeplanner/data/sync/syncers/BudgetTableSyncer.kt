package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.BudgetSyncDto
import az.tribe.lifeplanner.database.BudgetEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlin.time.Clock

class BudgetTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<BudgetEntity, BudgetSyncDto>(supabase) {

    override val tableName = "budgets"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<BudgetSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<BudgetEntity> =
        db { it.lifePlannerDBQueries.getUnsyncedBudgets().executeAsList() }

    override suspend fun getDeletedLocal(): List<BudgetEntity> =
        db { it.lifePlannerDBQueries.getDeletedBudgets().executeAsList() }

    override suspend fun localToRemote(local: BudgetEntity, userId: String) = BudgetSyncDto(
        id = local.id,
        userId = userId,
        area = local.area,
        metric = local.metric,
        category = local.category,
        amount = local.amount,
        currency = local.currency,
        period = local.period,
        tripId = local.tripId,
        createdAt = local.createdAt,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: BudgetSyncDto): BudgetEntity = BudgetEntity(
        id = remote.id,
        area = remote.area,
        metric = remote.metric,
        category = remote.category,
        amount = remote.amount,
        currency = remote.currency,
        period = remote.period,
        tripId = remote.tripId,
        createdAt = remote.createdAt,
        sync_updated_at = remote.updatedAt,
        is_deleted = if (remote.isDeleted) 1L else 0L,
        sync_version = remote.syncVersion,
        last_synced_at = Clock.System.now().toString()
    )

    override suspend fun upsertLocal(entity: BudgetEntity) {
        db { it.lifePlannerDBQueries.upsertBudgetFromSync(
            entity.id, entity.area, entity.metric, entity.category, entity.amount, entity.currency, entity.period, entity.tripId, entity.createdAt,
            entity.sync_updated_at, entity.is_deleted, entity.sync_version, entity.last_synced_at
        )}
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markBudgetSynced(now, id) }
    }

    override suspend fun markSyncedBatch(entities: List<BudgetEntity>, now: String) {
        if (entities.isEmpty()) return
        db { d -> entities.forEach { d.lifePlannerDBQueries.markBudgetSynced(now, it.id) } }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedBudgets() }
    }

    override suspend fun getEntityId(entity: BudgetEntity) = entity.id

    override suspend fun getLastPullTimestamp(): String? =
        settings.getStringOrNull("sync_pull_budgets")

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_budgets", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()
        Logger.d("SyncEngine") { "Pull $tableName: userId=$userId, lastPull=$lastPull" }

        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<BudgetSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<BudgetSyncDto>()
        }

        var applied = 0
        remoteItems.forEach { remote ->
            val existingLocal = getLocalById(remote.id)
            if (existingLocal == null || remote.syncVersion >= existingLocal.sync_version) {
                upsertLocal(remoteToLocal(remote))
                applied++
            }
        }
        setLastPullTimestamp(now)
        if (applied > 0) {
            Logger.d("SyncEngine") { "Applied $applied of ${remoteItems.size} pulled items from $tableName" }
        }
        return applied
    }

    private suspend fun getLocalById(id: String): BudgetEntity? = try {
        db { it.lifePlannerDBQueries.selectBudgetById(id).executeAsOneOrNull() }
    } catch (e: Exception) {
        null
    }
}
