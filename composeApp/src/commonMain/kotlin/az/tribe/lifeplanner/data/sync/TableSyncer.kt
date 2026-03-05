package az.tribe.lifeplanner.data.sync

import co.touchlab.kermit.Logger
import io.github.jan.supabase.SupabaseClient
import kotlinx.datetime.Clock

/**
 * Abstract syncer for a single table. Handles push (local → remote) and pull (remote → local)
 * for one entity type using last-write-wins conflict resolution.
 */
abstract class TableSyncer<LocalEntity, RemoteDto>(
    protected val supabase: SupabaseClient
) {
    abstract val tableName: String

    // Local DB operations
    abstract suspend fun getUnsyncedLocal(): List<LocalEntity>
    abstract suspend fun getDeletedLocal(): List<LocalEntity>
    abstract suspend fun localToRemote(local: LocalEntity, userId: String): RemoteDto
    abstract suspend fun remoteToLocal(remote: RemoteDto): LocalEntity
    abstract suspend fun upsertLocal(entity: LocalEntity)
    abstract suspend fun markSynced(id: String, now: String)
    abstract suspend fun purgeDeleted()
    abstract suspend fun getEntityId(entity: LocalEntity): String

    // Pull timestamp tracking (per-table)
    abstract suspend fun getLastPullTimestamp(): String?
    abstract suspend fun setLastPullTimestamp(timestamp: String)

    /**
     * Upsert DTOs to the remote Supabase table.
     * Must be overridden by each concrete syncer to provide the concrete type
     * (avoids type erasure issues with reified inline serialization).
     */
    abstract suspend fun upsertRemote(dtos: List<RemoteDto>)

    /**
     * Push local changes to Supabase. Returns number of items pushed.
     */
    open suspend fun pushLocalChanges(userId: String): Int {
        val now = Clock.System.now().toString()
        var pushed = 0

        try {
            // Push unsynced (new/updated) items
            val unsynced = getUnsyncedLocal()
            if (unsynced.isNotEmpty()) {
                val dtos = unsynced.map { localToRemote(it, userId) }
                upsertRemote(dtos)
                unsynced.forEach { markSynced(getEntityId(it), now) }
                pushed += unsynced.size
                Logger.d("SyncEngine") { "Pushed ${unsynced.size} items to $tableName" }
            }

            // Push deletes (soft-deleted items)
            val deleted = getDeletedLocal()
            if (deleted.isNotEmpty()) {
                val deleteDtos = deleted.map { localToRemote(it, userId) }
                upsertRemote(deleteDtos)
                deleted.forEach { markSynced(getEntityId(it), now) }
                pushed += deleted.size
                Logger.d("SyncEngine") { "Pushed ${deleted.size} deletes to $tableName" }
            }

            // Purge locally deleted items that have been synced
            purgeDeleted()

        } catch (e: Exception) {
            Logger.e("SyncEngine") { "Push failed for $tableName: ${e.message}" }
            throw e
        }

        return pushed
    }

    /**
     * Pull remote changes from Supabase. Returns number of items pulled.
     * Fetches all rows updated after the last pull timestamp.
     */
    abstract suspend fun pullRemoteChanges(userId: String): Int

    /**
     * Count pending (unsynced) local changes.
     */
    suspend fun countPending(): Int {
        return try {
            getUnsyncedLocal().size + getDeletedLocal().size
        } catch (e: Exception) {
            0
        }
    }
}
