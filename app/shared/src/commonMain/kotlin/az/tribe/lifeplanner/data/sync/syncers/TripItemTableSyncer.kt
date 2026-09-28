package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.TripItemSyncDto
import az.tribe.lifeplanner.database.TripItemEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlin.time.Clock

class TripItemTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<TripItemEntity, TripItemSyncDto>(supabase) {

    override val tableName = "trip_items"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<TripItemSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<TripItemEntity> =
        db { it.lifePlannerDBQueries.getUnsyncedTripItems().executeAsList() }

    override suspend fun getDeletedLocal(): List<TripItemEntity> =
        db { it.lifePlannerDBQueries.getDeletedTripItems().executeAsList() }

    override suspend fun localToRemote(local: TripItemEntity, userId: String) = TripItemSyncDto(
        id = local.id,
        userId = userId,
        tripId = local.tripId,
        kind = local.kind,
        title = local.title,
        notes = local.notes,
        date = local.date,
        isDone = local.isDone != 0L,
        sortOrder = local.sortOrder,
        createdAt = local.createdAt,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: TripItemSyncDto): TripItemEntity = TripItemEntity(
        id = remote.id,
        tripId = remote.tripId,
        kind = remote.kind,
        title = remote.title,
        notes = remote.notes,
        date = remote.date,
        isDone = if (remote.isDone) 1L else 0L,
        sortOrder = remote.sortOrder,
        createdAt = remote.createdAt,
        sync_updated_at = remote.updatedAt,
        is_deleted = if (remote.isDeleted) 1L else 0L,
        sync_version = remote.syncVersion,
        last_synced_at = Clock.System.now().toString()
    )

    override suspend fun upsertLocal(entity: TripItemEntity) {
        db { it.lifePlannerDBQueries.upsertTripItemFromSync(
            entity.id, entity.tripId, entity.kind, entity.title, entity.notes, entity.date, entity.isDone, entity.sortOrder, entity.createdAt,
            entity.sync_updated_at, entity.is_deleted, entity.sync_version, entity.last_synced_at
        )}
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markTripItemSynced(now, id) }
    }

    override suspend fun markSyncedBatch(entities: List<TripItemEntity>, now: String) {
        if (entities.isEmpty()) return
        db { d -> entities.forEach { d.lifePlannerDBQueries.markTripItemSynced(now, it.id) } }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedTripItems() }
    }

    override suspend fun getEntityId(entity: TripItemEntity) = entity.id

    override suspend fun getLastPullTimestamp(): String? =
        settings.getStringOrNull("sync_pull_trip_items")

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_trip_items", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()
        Logger.d("SyncEngine") { "Pull $tableName: userId=$userId, lastPull=$lastPull" }

        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<TripItemSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<TripItemSyncDto>()
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

    private suspend fun getLocalById(id: String): TripItemEntity? = try {
        db { it.lifePlannerDBQueries.selectTripItemById(id).executeAsOneOrNull() }
    } catch (e: Exception) {
        null
    }
}
