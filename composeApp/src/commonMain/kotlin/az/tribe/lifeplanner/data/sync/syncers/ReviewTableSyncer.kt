package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.ReviewReportSyncDto
import az.tribe.lifeplanner.database.ReviewReportEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class ReviewTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<ReviewReportEntity, ReviewReportSyncDto>(supabase) {

    override val tableName = "review_reports"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<ReviewReportSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<ReviewReportEntity> {
        return db { it.lifePlannerDBQueries.getUnsyncedReviewReports().executeAsList() }
    }

    override suspend fun getDeletedLocal(): List<ReviewReportEntity> {
        return db { it.lifePlannerDBQueries.getDeletedReviewReports().executeAsList() }
    }

    override suspend fun localToRemote(local: ReviewReportEntity, userId: String): ReviewReportSyncDto {
        fun parseJson(s: String) = try { Json.parseToJsonElement(s) } catch (_: Exception) { Json.parseToJsonElement("{}") }
        return ReviewReportSyncDto(
            id = local.id,
            userId = userId,
            type = local.type,
            periodStart = local.periodStart,
            periodEnd = local.periodEnd,
            generatedAt = local.generatedAt,
            summary = local.summary,
            highlightsJson = parseJson(local.highlightsJson),
            insightsJson = parseJson(local.insightsJson),
            recommendationsJson = parseJson(local.recommendationsJson),
            statsJson = parseJson(local.statsJson),
            feedbackRating = local.feedbackRating,
            feedbackComment = local.feedbackComment,
            feedbackAt = local.feedbackAt,
            isRead = local.isRead != 0L,
            updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
            isDeleted = local.is_deleted != 0L,
            syncVersion = local.sync_version
        )
    }

    override suspend fun remoteToLocal(remote: ReviewReportSyncDto): ReviewReportEntity {
        return ReviewReportEntity(
            id = remote.id,
            type = remote.type,
            periodStart = remote.periodStart,
            periodEnd = remote.periodEnd,
            generatedAt = remote.generatedAt,
            summary = remote.summary,
            highlightsJson = remote.highlightsJson.toString(),
            insightsJson = remote.insightsJson.toString(),
            recommendationsJson = remote.recommendationsJson.toString(),
            statsJson = remote.statsJson.toString(),
            feedbackRating = remote.feedbackRating,
            feedbackComment = remote.feedbackComment,
            feedbackAt = remote.feedbackAt,
            isRead = if (remote.isRead) 1L else 0L,
            sync_updated_at = remote.updatedAt,
            is_deleted = if (remote.isDeleted) 1L else 0L,
            sync_version = remote.syncVersion,
            last_synced_at = Clock.System.now().toString()
        )
    }

    override suspend fun upsertLocal(entity: ReviewReportEntity) {
        db { it.lifePlannerDBQueries.upsertReviewFromSync(
            id = entity.id,
            type = entity.type,
            periodStart = entity.periodStart,
            periodEnd = entity.periodEnd,
            generatedAt = entity.generatedAt,
            summary = entity.summary,
            highlightsJson = entity.highlightsJson,
            insightsJson = entity.insightsJson,
            recommendationsJson = entity.recommendationsJson,
            statsJson = entity.statsJson,
            feedbackRating = entity.feedbackRating,
            feedbackComment = entity.feedbackComment,
            feedbackAt = entity.feedbackAt,
            isRead = entity.isRead,
            sync_updated_at = entity.sync_updated_at,
            is_deleted = entity.is_deleted,
            sync_version = entity.sync_version,
            last_synced_at = entity.last_synced_at
        )}
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markReviewReportSynced(now, id) }
    }

    override suspend fun purgeDeleted() {
        db { it.lifePlannerDBQueries.purgeDeletedReviewReports() }
    }

    override suspend fun getEntityId(entity: ReviewReportEntity) = entity.id

    override suspend fun getLastPullTimestamp(): String? {
        return settings.getStringOrNull("sync_pull_reviews")
    }

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_reviews", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()

        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<ReviewReportSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<ReviewReportSyncDto>()
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
