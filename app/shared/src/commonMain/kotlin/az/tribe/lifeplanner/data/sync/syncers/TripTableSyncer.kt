package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.TripSyncDto
import az.tribe.lifeplanner.database.TripEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlin.time.Clock

class TripTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<TripEntity, TripSyncDto>(supabase) {

    override val tableName = "trips"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<TripSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<TripEntity> =
        db { it.lifePlannerDBQueries.getUnsyncedTrips().executeAsList() }

    override suspend fun getDeletedLocal(): List<TripEntity> =
        db { it.lifePlannerDBQueries.getDeletedTrips().executeAsList() }

    override suspend fun localToRemote(local: TripEntity, userId: String) = TripSyncDto(
        id = local.id,
        userId = userId,
        destination = local.destination,
        latitude = local.latitude,
        longitude = local.longitude,
        startDate = local.startDate,
        endDate = local.endDate,
        budget = local.budget,
        currency = local.currency,
        travelMode = local.travelMode,
        notes = local.notes,
        createdAt = local.createdAt,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: TripSyncDto): TripEntity = TripEntity(
        id = remote.id,
        destination = remote.destination,
        latitude = remote.latitude,
        longitude = remote.longitude,
        startDate = remote.startDate,
        endDate = remote.endDate,
        budget = remote.budget,
        currency = remote.currency,
        travelMode = remote.travelMode,
        notes = remote.notes,
        createdAt = remote.createdAt,
        sync_updated_at = remote.updatedAt,
        is_deleted = if (remote.isDeleted) 1L else 0L,
        sync_version = remote.syncVersion,
        last_synced_at = Clock.System.now().toString()
    )

    override suspend fun upsertLocal(entity: TripEntity) {
        db { it.lifePlannerDBQueries.upsertTripFromSync(
            entity.id, entity.destination, entity.latitude, entity.longitude, entity.startDate, entity.endDate, entity.budget, entity.currency, entity.travelMode, entity.notes, entity.createdAt,
            entity.sync_updated_at, entity.is_deleted, entity.sync_version, entity.last_synced_at
        )}
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markTripSynced(now, id) }
    }

    override suspend fun markSyncedBatch(entities: List<TripEntity>, now: String) {
        if (entities.isEmpty()) return
        db { d -> entities.forEach { d.lifePlannerDBQueries.markTripSynced(now, it.id) } }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedTrips() }
    }

    override suspend fun getEntityId(entity: TripEntity) = entity.id

    override suspend fun getLastPullTimestamp(): String? =
        settings.getStringOrNull("sync_pull_trips")

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_trips", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()
        Logger.d("SyncEngine") { "Pull $tableName: userId=$userId, lastPull=$lastPull" }

        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<TripSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<TripSyncDto>()
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

    private suspend fun getLocalById(id: String): TripEntity? = try {
        db { it.lifePlannerDBQueries.selectTripById(id).executeAsOneOrNull() }
    } catch (e: Exception) {
        null
    }
}
