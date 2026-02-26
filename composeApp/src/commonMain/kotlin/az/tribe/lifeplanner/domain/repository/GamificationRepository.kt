package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.enum.BadgeType
import az.tribe.lifeplanner.domain.enum.ChallengeType
import az.tribe.lifeplanner.domain.model.Badge
import az.tribe.lifeplanner.domain.model.Challenge
import az.tribe.lifeplanner.domain.model.UserProgress
import kotlinx.coroutines.flow.Flow


interface GamificationRepository {
    // User Progress
    suspend fun getUserProgress(): Flow<UserProgress>
    suspend fun updateDailyStreak(): Flow<Int>
    suspend fun addXp(amount: Int): UserProgress
    suspend fun incrementGoalsCompleted()
    suspend fun incrementHabitsCompleted()
    suspend fun incrementJournalEntries()

    // Badges
    suspend fun getAllBadges(): List<Badge>
    suspend fun getNewBadges(): List<Badge>
    suspend fun hasBadge(type: BadgeType): Boolean
    suspend fun awardBadge(type: BadgeType): Badge?
    suspend fun markBadgeAsSeen(badgeId: String)
    suspend fun markAllBadgesAsSeen()
    suspend fun getBadgeCount(): Int
    suspend fun checkAndAwardBadges(): List<Badge>

    // Challenges
    suspend fun getActiveChallenges(): List<Challenge>
    suspend fun getCompletedChallenges(): List<Challenge>
    suspend fun startChallenge(type: ChallengeType): Challenge
    suspend fun updateChallengeProgress(challengeId: String, progress: Int)
    suspend fun checkAndCompleteChallenge(challengeId: String): Challenge?
    suspend fun cleanupExpiredChallenges()
    suspend fun getAvailableChallenges(): List<ChallengeType>

    // Activity-based challenge updates
    suspend fun onHabitCheckedIn()
    suspend fun onJournalEntryCreated()
    suspend fun onGoalCompleted()
    suspend fun onMilestoneCompleted()
}