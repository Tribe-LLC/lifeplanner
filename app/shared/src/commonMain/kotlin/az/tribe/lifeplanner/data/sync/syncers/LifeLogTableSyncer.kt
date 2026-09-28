package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.LifeLogSyncDto
import az.tribe.lifeplanner.database.LifeLogEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlin.time.Clock

class LifeLogTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<LifeLogEntity, LifeLogSyncDto>(supabase) {

    override val tableName = "life_logs"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<LifeLogSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<LifeLogEntity> =
        db { it.lifePlannerDBQueries.getUnsyncedLifeLogs().executeAsList() }

    override suspend fun getDeletedLocal(): List<LifeLogEntity> =
        db { it.lifePlannerDBQueries.getDeletedLifeLogs().executeAsList() }

    override suspend fun localToRemote(local: LifeLogEntity, userId: String) = LifeLogSyncDto(
        id = local.id,
        userId = userId,
        area = local.area,
        kind = local.kind,
        status = local.status,
        title = local.title,
        amount = local.amount,
        currency = local.currency,
        category = local.category,
        quantity = local.quantity,
        unit = local.unit,
        durationMin = local.durationMin,
        occurredAt = local.occurredAt,
        date = local.date,
        source = local.source,
        externalId = local.externalId,
        tripId = local.tripId,
        notes = local.notes,
        createdAt = local.createdAt,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: LifeLogSyncDto): LifeLogEntity = LifeLogEntity(
        id = remote.id,
        area = remote.area,
        kind = remote.kind,
        status = remote.status,
        title = remote.title,
        amount = remote.amount,
        currency = remote.currency,
        category = remote.category,
        quantity = remote.quantity,
        unit = remote.unit,
        durationMin = remote.durationMin,
        occurredAt = remote.occurredAt,
        date = remote.date,
        source = remote.source,
        externalId = remote.externalId,
        tripId = remote.tripId,
        notes = remote.notes,
        createdAt = remote.createdAt,
        sync_updated_at = remote.updatedAt,
        is_deleted = if (remote.isDeleted) 1L else 0L,
        sync_version = remote.syncVersion,
        last_synced_at = Clock.System.now().toString()
    )

    override suspend fun upsertLocal(entity: LifeLogEntity) {
        db { it.lifePlannerDBQueries.upsertLifeLogFromSync(
            entity.id, entity.area, entity.kind, entity.status, entity.title, entity.amount, entity.currency, entity.category, entity.quantity, entity.unit, entity.durationMin, entity.occurredAt, entity.date, entity.source, entity.externalId, entity.tripId, entity.notes, entity.createdAt,
            entity.sync_updated_at, entity.is_deleted, entity.sync_version, entity.last_synced_at
        )}
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markLifeLogSynced(now, id) }
    }

    override suspend fun markSyncedBatch(entities: List<LifeLogEntity>, now: String) {
        if (entities.isEmpty()) return
        db { d -> entities.forEach { d.lifePlannerDBQueries.markLifeLogSynced(now, it.id) } }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedLifeLogs() }
    }

    override suspend fun getEntityId(entity: LifeLogEntity) = entity.id

    override suspend fun getLastPullTimestamp(): String? =
        settings.getStringOrNull("sync_pull_life_logs")

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_life_logs", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()
        Logger.d("SyncEngine") { "Pull $tableName: userId=$userId, lastPull=$lastPull" }

        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<LifeLogSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<LifeLogSyncDto>()
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

    private suspend fun getLocalById(id: String): LifeLogEntity? = try {
        db { it.lifePlannerDBQueries.selectLifeLogById(id).executeAsOneOrNull() }
    } catch (e: Exception) {
        null
    }
}
