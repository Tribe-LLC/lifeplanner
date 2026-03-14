package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.CustomCoachSyncDto
import az.tribe.lifeplanner.database.CustomCoachEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

class CustomCoachTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<CustomCoachEntity, CustomCoachSyncDto>(supabase) {

    override val tableName = "custom_coaches"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<CustomCoachSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<CustomCoachEntity> {
        return db { it.lifePlannerDBQueries.getUnsyncedCustomCoaches().executeAsList() }
    }

    override suspend fun getDeletedLocal(): List<CustomCoachEntity> {
        return db { it.lifePlannerDBQueries.getDeletedCustomCoaches().executeAsList() }
    }

    override suspend fun localToRemote(local: CustomCoachEntity, userId: String): CustomCoachSyncDto {
        val characteristicsJson = try {
            if (local.characteristics.isNotBlank()) Json.parseToJsonElement(local.characteristics)
            else JsonArray(emptyList())
        } catch (_: Exception) { JsonArray(emptyList()) }

        return CustomCoachSyncDto(
            id = local.id,
            userId = userId,
            name = local.name,
            icon = local.icon,
            iconBackgroundColor = local.iconBackgroundColor,
            iconAccentColor = local.iconAccentColor,
            systemPrompt = local.systemPrompt,
            characteristics = characteristicsJson,
            isFromTemplate = local.isFromTemplate != 0L,
            templateId = local.templateId,
            createdAt = local.createdAt,
            updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
            isDeleted = local.is_deleted != 0L,
            syncVersion = local.sync_version
        )
    }

    override suspend fun remoteToLocal(remote: CustomCoachSyncDto): CustomCoachEntity {
        return CustomCoachEntity(
            id = remote.id,
            name = remote.name,
            icon = remote.icon,
            iconBackgroundColor = remote.iconBackgroundColor,
            iconAccentColor = remote.iconAccentColor,
            systemPrompt = remote.systemPrompt,
            characteristics = remote.characteristics.toString(),
            isFromTemplate = if (remote.isFromTemplate) 1L else 0L,
            templateId = remote.templateId,
            createdAt = remote.createdAt,
            updatedAt = remote.updatedAt,
            sync_updated_at = remote.updatedAt,
            is_deleted = if (remote.isDeleted) 1L else 0L,
            sync_version = remote.syncVersion,
            last_synced_at = Clock.System.now().toString()
        )
    }

    override suspend fun upsertLocal(entity: CustomCoachEntity) {
        db { it.lifePlannerDBQueries.upsertCustomCoachFromSync(
            id = entity.id,
            name = entity.name,
            icon = entity.icon,
            iconBackgroundColor = entity.iconBackgroundColor,
            iconAccentColor = entity.iconAccentColor,
            systemPrompt = entity.systemPrompt,
            characteristics = entity.characteristics,
            isFromTemplate = entity.isFromTemplate,
            templateId = entity.templateId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            sync_updated_at = entity.sync_updated_at,
            is_deleted = entity.is_deleted,
            sync_version = entity.sync_version,
            last_synced_at = entity.last_synced_at
        )}
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markCustomCoachSynced(now, id) }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedCustomCoaches() }
    }

    override suspend fun getEntityId(entity: CustomCoachEntity) = entity.id

    override suspend fun getLastPullTimestamp(): String? {
        return settings.getStringOrNull("sync_pull_custom_coaches")
    }

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_custom_coaches", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()

        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<CustomCoachSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<CustomCoachSyncDto>()
        }

        remoteItems.forEach { remote ->
            val local = remoteToLocal(remote)
            upsertLocal(local)
        }

        setLastPullTimestamp(now)
        if (remoteItems.isNotEmpty()) {
            Logger.d("SyncEngine") { "Pulled ${remoteItems.size} items from $tableName" }
        }
        return remoteItems.size
    }
}
