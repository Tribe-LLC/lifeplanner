package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.ChatMessageSyncDto
import az.tribe.lifeplanner.database.ChatMessageEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class ChatMessageTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<ChatMessageEntity, ChatMessageSyncDto>(supabase) {

    override val tableName = "chat_messages"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<ChatMessageSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<ChatMessageEntity> =
        db { it.lifePlannerDBQueries.getUnsyncedChatMessages().executeAsList() }

    override suspend fun getDeletedLocal(): List<ChatMessageEntity> =
        db { it.lifePlannerDBQueries.getDeletedChatMessages().executeAsList() }

    override suspend fun localToRemote(local: ChatMessageEntity, userId: String): ChatMessageSyncDto {
        val metadataJson = try {
            local.metadata?.let { Json.parseToJsonElement(it) }
        } catch (_: Exception) { null }

        return ChatMessageSyncDto(
            id = local.id,
            userId = userId,
            sessionId = local.sessionId,
            content = local.content,
            role = local.role,
            timestamp = local.timestamp,
            relatedGoalId = local.relatedGoalId,
            metadata = metadataJson,
            updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
            isDeleted = local.is_deleted != 0L,
            syncVersion = local.sync_version
        )
    }

    override suspend fun remoteToLocal(remote: ChatMessageSyncDto) = ChatMessageEntity(
        id = remote.id,
        sessionId = remote.sessionId,
        content = remote.content,
        role = remote.role,
        timestamp = remote.timestamp,
        relatedGoalId = remote.relatedGoalId,
        metadata = remote.metadata?.toString(),
        sync_updated_at = remote.updatedAt,
        is_deleted = if (remote.isDeleted) 1L else 0L,
        sync_version = remote.syncVersion,
        last_synced_at = Clock.System.now().toString()
    )

    override suspend fun upsertLocal(entity: ChatMessageEntity) {
        db {
            it.lifePlannerDBQueries.upsertChatMessageFromSync(
                id = entity.id,
                sessionId = entity.sessionId,
                content = entity.content,
                role = entity.role,
                timestamp = entity.timestamp,
                relatedGoalId = entity.relatedGoalId,
                metadata = entity.metadata,
                sync_updated_at = entity.sync_updated_at,
                is_deleted = entity.is_deleted,
                sync_version = entity.sync_version,
                last_synced_at = entity.last_synced_at
            )
        }
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markChatMessageSynced(now, id) }
    }

    override suspend fun markSyncedBatch(entities: List<ChatMessageEntity>, now: String) {
        if (entities.isEmpty()) return
        db { d -> entities.forEach { d.lifePlannerDBQueries.markChatMessageSynced(now, it.id) } }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedChatMessages() }
    }

    override suspend fun getEntityId(entity: ChatMessageEntity): String = entity.id

    override suspend fun getLastPullTimestamp(): String? =
        settings.getStringOrNull("sync_pull_chat_messages")

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_chat_messages", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()
        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<ChatMessageSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<ChatMessageSyncDto>()
        }
        remoteItems.forEach { upsertLocal(remoteToLocal(it)) }
        setLastPullTimestamp(now)
        if (remoteItems.isNotEmpty()) Logger.d("SyncEngine") { "Pulled ${remoteItems.size} items from $tableName" }
        return remoteItems.size
    }
}
