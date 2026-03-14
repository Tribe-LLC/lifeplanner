package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.JournalEntrySyncDto
import az.tribe.lifeplanner.database.JournalEntryEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class JournalEntryTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<JournalEntryEntity, JournalEntrySyncDto>(supabase) {

    override val tableName = "journal_entries"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<JournalEntrySyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<JournalEntryEntity> =
        db { it.lifePlannerDBQueries.getUnsyncedJournalEntries().executeAsList() }

    override suspend fun getDeletedLocal(): List<JournalEntryEntity> =
        db { it.lifePlannerDBQueries.getDeletedJournalEntries().executeAsList() }

    override suspend fun localToRemote(local: JournalEntryEntity, userId: String): JournalEntrySyncDto {
        // Convert local tags string to JsonElement for JSONB column
        val tagsJson = try {
            if (local.tags.isNotBlank()) Json.parseToJsonElement(local.tags)
            else JsonArray(emptyList())
        } catch (_: Exception) {
            // If tags is a plain comma-separated string, convert to JSON array
            val tagList = local.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            JsonArray(tagList.map { JsonPrimitive(it) })
        }
        return JournalEntrySyncDto(
            id = local.id,
            userId = userId,
            title = local.title,
            content = local.content,
            mood = local.mood,
            linkedGoalId = local.linkedGoalId,
            linkedHabitId = local.linkedHabitId,
            promptUsed = local.promptUsed,
            tags = tagsJson,
            date = local.date,
            createdAt = local.createdAt,
            updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
            isDeleted = local.is_deleted != 0L,
            syncVersion = local.sync_version
        )
    }

    override suspend fun remoteToLocal(remote: JournalEntrySyncDto) = JournalEntryEntity(
        id = remote.id,
        title = remote.title,
        content = remote.content,
        mood = remote.mood,
        linkedGoalId = remote.linkedGoalId,
        linkedHabitId = remote.linkedHabitId,
        promptUsed = remote.promptUsed,
        tags = try {
            remote.tags.jsonArray.joinToString(",") { it.jsonPrimitive.content }
        } catch (_: Exception) {
            remote.tags.toString()
        },
        date = remote.date,
        createdAt = remote.createdAt,
        updatedAt = remote.updatedAt,
        sync_updated_at = remote.updatedAt,
        is_deleted = if (remote.isDeleted) 1L else 0L,
        sync_version = remote.syncVersion,
        last_synced_at = Clock.System.now().toString()
    )

    override suspend fun upsertLocal(entity: JournalEntryEntity) {
        db {
            it.lifePlannerDBQueries.upsertJournalEntryFromSync(
                id = entity.id,
                title = entity.title,
                content = entity.content,
                mood = entity.mood,
                linkedGoalId = entity.linkedGoalId,
                linkedHabitId = entity.linkedHabitId,
                promptUsed = entity.promptUsed,
                tags = entity.tags,
                date = entity.date,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
                sync_updated_at = entity.sync_updated_at,
                is_deleted = entity.is_deleted,
                sync_version = entity.sync_version,
                last_synced_at = entity.last_synced_at
            )
        }
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markJournalEntrySynced(now, id) }
    }

    override suspend fun markSyncedBatch(entities: List<JournalEntryEntity>, now: String) {
        if (entities.isEmpty()) return
        db { d -> entities.forEach { d.lifePlannerDBQueries.markJournalEntrySynced(now, it.id) } }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedJournalEntries() }
    }

    override suspend fun getEntityId(entity: JournalEntryEntity): String = entity.id

    override suspend fun getLastPullTimestamp(): String? =
        settings.getStringOrNull("sync_pull_journal_entries")

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_journal_entries", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()
        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<JournalEntrySyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<JournalEntrySyncDto>()
        }
        remoteItems.forEach { upsertLocal(remoteToLocal(it)) }
        setLastPullTimestamp(now)
        if (remoteItems.isNotEmpty()) Logger.d("SyncEngine") { "Pulled ${remoteItems.size} items from $tableName" }
        return remoteItems.size
    }
}
