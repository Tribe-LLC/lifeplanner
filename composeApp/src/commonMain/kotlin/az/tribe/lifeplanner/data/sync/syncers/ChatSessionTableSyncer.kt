package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.ChatSessionSyncDto
import az.tribe.lifeplanner.database.ChatSessionEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlinx.datetime.Clock

class ChatSessionTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<ChatSessionEntity, ChatSessionSyncDto>(supabase) {

    override val tableName = "chat_sessions"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<ChatSessionSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<ChatSessionEntity> =
        db { it.lifePlannerDBQueries.getUnsyncedChatSessions().executeAsList() }

    override suspend fun getDeletedLocal(): List<ChatSessionEntity> =
        db { it.lifePlannerDBQueries.getDeletedChatSessions().executeAsList() }

    override suspend fun localToRemote(local: ChatSessionEntity, userId: String) = ChatSessionSyncDto(
        id = local.id,
        userId = userId,
        title = local.title,
        createdAt = local.createdAt,
        lastMessageAt = local.lastMessageAt,
        summary = local.summary,
        coachId = local.coachId,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: ChatSessionSyncDto) = ChatSessionEntity(
        id = remote.id,
        title = remote.title,
        createdAt = remote.createdAt,
        lastMessageAt = remote.lastMessageAt,
        summary = remote.summary,
        coachId = remote.coachId,
        sync_updated_at = remote.updatedAt,
        is_deleted = if (remote.isDeleted) 1L else 0L,
        sync_version = remote.syncVersion,
        last_synced_at = Clock.System.now().toString()
    )

    override suspend fun upsertLocal(entity: ChatSessionEntity) {
        db {
            it.lifePlannerDBQueries.upsertChatSessionFromSync(
                id = entity.id,
                title = entity.title,
                createdAt = entity.createdAt,
                lastMessageAt = entity.lastMessageAt,
                summary = entity.summary,
                coachId = entity.coachId,
                sync_updated_at = entity.sync_updated_at,
                is_deleted = entity.is_deleted,
                sync_version = entity.sync_version,
                last_synced_at = entity.last_synced_at
            )
        }
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markChatSessionSynced(now, id) }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedChatSessions() }
    }

    override suspend fun getEntityId(entity: ChatSessionEntity): String = entity.id

    override suspend fun getLastPullTimestamp(): String? =
        settings.getStringOrNull("sync_pull_chat_sessions")

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_chat_sessions", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()
        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<ChatSessionSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<ChatSessionSyncDto>()
        }
        remoteItems.forEach { upsertLocal(remoteToLocal(it)) }
        setLastPullTimestamp(now)
        if (remoteItems.isNotEmpty()) Logger.d("SyncEngine") { "Pulled ${remoteItems.size} items from $tableName" }
        return remoteItems.size
    }
}
