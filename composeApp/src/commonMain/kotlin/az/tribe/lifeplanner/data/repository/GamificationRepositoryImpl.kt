package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.data.mapper.createNewChallenge
import az.tribe.lifeplanner.data.mapper.toDomain
import az.tribe.lifeplanner.data.mapper.toEntity
import az.tribe.lifeplanner.domain.enum.BadgeType
import az.tribe.lifeplanner.domain.enum.ChallengeType
import az.tribe.lifeplanner.domain.model.Badge
import az.tribe.lifeplanner.domain.model.Challenge
import az.tribe.lifeplanner.domain.model.UserProgress
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import az.tribe.lifeplanner.widget.WidgetDataSyncService
import co.touchlab.kermit.Logger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GamificationRepositoryImpl(
    private val database: SharedDatabase,
    private val widgetSyncService: WidgetDataSyncService,
    private val syncManager: SyncManager,
    private val supabaseClient: SupabaseClient
) : GamificationRepository {

    private suspend fun notifyWidgets() {
        try {
            widgetSyncService.refreshWidgets()
        } catch (e: Exception) {
            Logger.w("GamificationRepositoryImpl") { "Widget refresh failed: ${e.message}" }
        }
    }

    private suspend fun initializeProgress() {
        val existing = database.getUserProgressEntity()
        if (existing == null) {
            database.insertUserProgressFull(
                currentStreak = 0,
                lastCheckInDate = null,
                totalXp = 0,
                currentLevel = 1,
                goalsCompleted = 0,
                habitsCompleted = 0,
                journalEntriesCount = 0,
                longestStreak = 0
            )
        }
    }

    override suspend fun getUserProgress(): Flow<UserProgress> = flow {
        val progress = database.getUserProgressEntity()
            ?: run {
                initializeProgress()
                database.getUserProgressEntity()
            }
        if (progress != null) {
            // Recalculate level if out of sync with XP
            val correctLevel = UserProgress.calculateLevelFromXp(progress.totalXp.toInt())
            if (correctLevel != progress.currentLevel.toInt()) {
                database.updateUserXp(progress.totalXp, correctLevel.toLong())
                val updated = database.getUserProgressEntity()
                emit(updated?.toDomain() ?: progress.toDomain())
            } else {
                emit(progress.toDomain())
            }
        } else {
            emit(UserProgress.default())
        }
    }

    // --- Badge Operations (read-only) ---

    override suspend fun getAllBadges(): List<Badge> {
        return database.getAllBadges().mapNotNull { it.toDomain() }
    }

    override suspend fun getNewBadges(): List<Badge> {
        return database.getNewBadges().mapNotNull { it.toDomain() }
    }

    override suspend fun hasBadge(type: BadgeType): Boolean {
        return database.hasBadge(type.name)
    }

    override suspend fun markBadgeAsSeen(badgeId: String) {
        database.markBadgeAsSeen(badgeId)
        syncManager.requestSync()
    }

    override suspend fun markAllBadgesAsSeen() {
        database.markAllBadgesAsSeen()
        syncManager.requestSync()
    }

    override suspend fun getBadgeCount(): Int {
        return database.getBadgeCount().toInt()
    }

    // --- Challenge Operations ---

    override suspend fun getActiveChallenges(): List<Challenge> {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        return database.getActiveChallenges(today).mapNotNull { it.toDomain() }
    }

    override suspend fun getCompletedChallenges(): List<Challenge> {
        return database.getCompletedChallenges().mapNotNull { it.toDomain() }
    }

    override suspend fun startChallenge(type: ChallengeType): Challenge {
        // Check if an active challenge of this type already exists
        val existingChallenge = database.getChallengeByType(type.name)
        if (existingChallenge != null) {
            existingChallenge.toDomain()?.let { return it }
        }

        // Create a new challenge only if one doesn't exist
        val challenge = createNewChallenge(type)
        database.insertChallenge(challenge.toEntity())
        syncManager.requestSync()
        return challenge
    }

    override suspend fun updateChallengeProgress(challengeId: String, progress: Int) {
        database.updateChallengeProgress(challengeId, progress.toLong())
        syncManager.requestSync()
    }

    override suspend fun checkAndCompleteChallenge(challengeId: String): Challenge? {
        val challengeEntity = database.getChallengeById(challengeId) ?: return null
        val challenge = challengeEntity.toDomain() ?: return null

        if (challenge.currentProgress >= challenge.targetProgress && !challenge.isCompleted) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

            database.completeChallenge(
                id = challengeId,
                completedAt = now.toString(),
                xpEarned = challenge.type.xpReward.toLong()
            )
            syncManager.requestSync()

            return database.getChallengeById(challengeId)?.toDomain()
        }

        return null
    }

    override suspend fun cleanupExpiredChallenges() {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        database.deleteExpiredChallenges(today)
        syncManager.requestSync()
    }

    override suspend fun getAvailableChallenges(): List<ChallengeType> {
        val activeChallenges = getActiveChallenges().map { it.type }
        return ChallengeType.entries.filter { it !in activeChallenges }
    }

    // --- Daily Streak via Server RPC ---

    @Serializable
    private data class StreakResult(val new_streak: Int, val xp_awarded: Int = 0)

    override suspend fun updateDailyStreakRemote(): Pair<Int, Int> {
        return try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
                ?: throw IllegalStateException("Not authenticated")
            val params = buildJsonObject { put("p_user_id", userId) }
            val results = supabaseClient.postgrest.rpc("update_daily_streak", params)
                .decodeList<StreakResult>()
            val result = results.firstOrNull() ?: StreakResult(0, 0)

            // Trigger sync to pull updated user_progress from server
            syncManager.requestSync()
            notifyWidgets()

            Pair(result.new_streak, result.xp_awarded)
        } catch (e: Exception) {
            Logger.w("GamificationRepositoryImpl") { "updateDailyStreakRemote failed: ${e.message}" }
            Pair(0, 0)
        }
    }

    override suspend fun awardXp(amount: Long) {
        initializeProgress()
        database.addXp(amount)
        // Recalculate level from updated total XP
        val progress = database.getUserProgressEntity()
        if (progress != null) {
            val newLevel = UserProgress.calculateLevelFromXp(progress.totalXp.toInt())
            if (newLevel != progress.currentLevel.toInt()) {
                database.updateUserXp(progress.totalXp, newLevel.toLong())
            }
        }
        syncManager.requestSync()
        notifyWidgets()
    }

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    override suspend fun awardBadge(type: BadgeType) {
        if (hasBadge(type)) return
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
        database.insertBadge(
            az.tribe.lifeplanner.database.BadgeEntity(
                id = kotlin.uuid.Uuid.random().toString(),
                badgeType = type.name,
                earnedAt = now,
                isNew = 1L,
                sync_updated_at = now,
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
            )
        )
        syncManager.requestSync()
    }
}
