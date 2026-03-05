package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.enum.BadgeType
import az.tribe.lifeplanner.domain.enum.ChallengeType
import az.tribe.lifeplanner.domain.model.Badge
import az.tribe.lifeplanner.domain.model.Challenge
import az.tribe.lifeplanner.domain.model.UserProgress
import kotlinx.coroutines.flow.Flow


interface GamificationRepository {
    // User Progress (read-only — server handles XP/counters via triggers)
    suspend fun getUserProgress(): Flow<UserProgress>

    // Badges (read-only — server awards via triggers)
    suspend fun getAllBadges(): List<Badge>
    suspend fun getNewBadges(): List<Badge>
    suspend fun hasBadge(type: BadgeType): Boolean
    suspend fun markBadgeAsSeen(badgeId: String)
    suspend fun markAllBadgesAsSeen()
    suspend fun getBadgeCount(): Int

    // Challenges (bidirectional — client creates, server updates progress/completion)
    suspend fun getActiveChallenges(): List<Challenge>
    suspend fun getCompletedChallenges(): List<Challenge>
    suspend fun startChallenge(type: ChallengeType): Challenge
    suspend fun updateChallengeProgress(challengeId: String, progress: Int)
    suspend fun checkAndCompleteChallenge(challengeId: String): Challenge?
    suspend fun cleanupExpiredChallenges()
    suspend fun getAvailableChallenges(): List<ChallengeType>

    // Daily streak — calls server RPC, returns (new_streak, xp_awarded)
    suspend fun updateDailyStreakRemote(): Pair<Int, Int>
}
