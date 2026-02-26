package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.data.mapper.createNewBadge
import az.tribe.lifeplanner.data.mapper.createNewChallenge
import az.tribe.lifeplanner.data.mapper.toDomain
import az.tribe.lifeplanner.data.mapper.toEntity
import az.tribe.lifeplanner.domain.enum.BadgeType
import az.tribe.lifeplanner.domain.enum.ChallengeType
import az.tribe.lifeplanner.domain.model.Badge
import az.tribe.lifeplanner.domain.model.Challenge
import az.tribe.lifeplanner.domain.model.UserProgress
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import az.tribe.lifeplanner.widget.WidgetDataSyncService
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class GamificationRepositoryImpl(
    private val database: SharedDatabase,
    private val widgetSyncService: WidgetDataSyncService
) : GamificationRepository {

    private suspend fun notifyWidgets() {
        try {
            widgetSyncService.refreshWidgets()
        } catch (e: Exception) {
            Logger.w("GamificationRepositoryImpl") { "Widget refresh failed: ${e.message}" }
        }
    }

    init {
        CoroutineScope(Dispatchers.IO).launch {
            initializeProgress()
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
                database.getUserProgressEntity()!!
            }
        emit(progress.toDomain())
    }

    override suspend fun updateDailyStreak(): Flow<Int> = flow {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        val progress = database.getUserProgressEntity()
            ?: run {
                initializeProgress()
                database.getUserProgressEntity()!!
            }

        val lastCheckIn = progress.lastCheckInDate?.let { LocalDate.parse(it) }

        val newStreak = when {
            lastCheckIn == null -> 1
            isConsecutiveDay(lastCheckIn, today) -> progress.currentStreak.toInt() + 1
            lastCheckIn == today -> progress.currentStreak.toInt()
            else -> 1
        }

        val longestStreak = maxOf(newStreak.toLong(), progress.longestStreak)

        database.updateUserStreakFull(
            currentStreak = newStreak.toLong(),
            lastCheckInDate = today.toString(),
            longestStreak = longestStreak
        )

        notifyWidgets()
        emit(newStreak)
    }

    override suspend fun addXp(amount: Int): UserProgress {
        val current = database.getUserProgressEntity()
            ?: run {
                initializeProgress()
                database.getUserProgressEntity()!!
            }

        val newTotalXp = current.totalXp + amount
        val newLevel = UserProgress.calculateLevelFromXp(newTotalXp.toInt())

        database.updateUserXp(newTotalXp, newLevel.toLong())

        notifyWidgets()
        return database.getUserProgressEntity()!!.toDomain()
    }

    override suspend fun incrementGoalsCompleted() {
        database.incrementGoalsCompleted()
    }

    override suspend fun incrementHabitsCompleted() {
        database.incrementHabitsCompleted()
    }

    override suspend fun incrementJournalEntries() {
        database.incrementJournalEntries()
    }

    // --- Badge Operations ---

    override suspend fun getAllBadges(): List<Badge> {
        return database.getAllBadges().map { it.toDomain() }
    }

    override suspend fun getNewBadges(): List<Badge> {
        return database.getNewBadges().map { it.toDomain() }
    }

    override suspend fun hasBadge(type: BadgeType): Boolean {
        return database.hasBadge(type.name)
    }

    override suspend fun awardBadge(type: BadgeType): Badge? {
        if (hasBadge(type)) return null

        val badge = createNewBadge(type)
        database.insertBadge(badge.toEntity())
        return badge
    }

    override suspend fun markBadgeAsSeen(badgeId: String) {
        database.markBadgeAsSeen(badgeId)
    }

    override suspend fun markAllBadgesAsSeen() {
        database.markAllBadgesAsSeen()
    }

    override suspend fun getBadgeCount(): Int {
        return database.getBadgeCount().toInt()
    }

    override suspend fun checkAndAwardBadges(): List<Badge> {
        val awardedBadges = mutableListOf<Badge>()
        val progress = database.getUserProgressEntity()?.toDomain() ?: return emptyList()

        // Check streak badges
        val streakBadges = listOf(
            BadgeType.STREAK_3 to 3,
            BadgeType.STREAK_7 to 7,
            BadgeType.STREAK_14 to 14,
            BadgeType.STREAK_30 to 30,
            BadgeType.STREAK_100 to 100
        )

        for ((badgeType, requirement) in streakBadges) {
            if (progress.currentStreak >= requirement && !hasBadge(badgeType)) {
                awardBadge(badgeType)?.let { awardedBadges.add(it) }
            }
        }

        // Check goal completion badges
        val goalBadges = listOf(
            BadgeType.FIRST_STEP to 1,
            BadgeType.GOAL_1 to 1,
            BadgeType.GOAL_5 to 5,
            BadgeType.GOAL_10 to 10,
            BadgeType.GOAL_25 to 25,
            BadgeType.GOAL_50 to 50
        )

        for ((badgeType, requirement) in goalBadges) {
            if (progress.goalsCompleted >= requirement && !hasBadge(badgeType)) {
                awardBadge(badgeType)?.let { awardedBadges.add(it) }
            }
        }

        // Check habit badges
        if (progress.habitsCompleted >= 1 && !hasBadge(BadgeType.HABIT_STARTER)) {
            awardBadge(BadgeType.HABIT_STARTER)?.let { awardedBadges.add(it) }
        }
        if (progress.habitsCompleted >= 5 && !hasBadge(BadgeType.HABIT_5)) {
            awardBadge(BadgeType.HABIT_5)?.let { awardedBadges.add(it) }
        }

        // Check journal badges
        val journalBadges = listOf(
            BadgeType.JOURNAL_FIRST to 1,
            BadgeType.JOURNAL_10 to 10,
            BadgeType.JOURNAL_30 to 30
        )

        for ((badgeType, requirement) in journalBadges) {
            if (progress.journalEntriesCount >= requirement && !hasBadge(badgeType)) {
                awardBadge(badgeType)?.let { awardedBadges.add(it) }
            }
        }

        return awardedBadges
    }

    // --- Challenge Operations ---

    override suspend fun getActiveChallenges(): List<Challenge> {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        return database.getActiveChallenges(today).map { it.toDomain() }
    }

    override suspend fun getCompletedChallenges(): List<Challenge> {
        return database.getCompletedChallenges().map { it.toDomain() }
    }

    override suspend fun startChallenge(type: ChallengeType): Challenge {
        // Check if an active challenge of this type already exists
        val existingChallenge = database.getChallengeByType(type.name)
        if (existingChallenge != null) {
            return existingChallenge.toDomain()
        }

        // Create a new challenge only if one doesn't exist
        val challenge = createNewChallenge(type)
        database.insertChallenge(challenge.toEntity())
        return challenge
    }

    override suspend fun updateChallengeProgress(challengeId: String, progress: Int) {
        database.updateChallengeProgress(challengeId, progress.toLong())
    }

    override suspend fun checkAndCompleteChallenge(challengeId: String): Challenge? {
        val challengeEntity = database.getChallengeById(challengeId) ?: return null
        val challenge = challengeEntity.toDomain()

        if (challenge.currentProgress >= challenge.targetProgress && !challenge.isCompleted) {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val type = ChallengeType.valueOf(challengeEntity.challengeType)

            database.completeChallenge(
                id = challengeId,
                completedAt = now.toString(),
                xpEarned = type.xpReward.toLong()
            )

            // Award XP for completing the challenge
            addXp(type.xpReward)

            return database.getChallengeById(challengeId)?.toDomain()
        }

        return null
    }

    override suspend fun cleanupExpiredChallenges() {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        database.deleteExpiredChallenges(today)
    }

    override suspend fun getAvailableChallenges(): List<ChallengeType> {
        val activeChallenges = getActiveChallenges().map { it.type }
        return ChallengeType.entries.filter { it !in activeChallenges }
    }

    private fun isConsecutiveDay(previous: LocalDate, current: LocalDate): Boolean {
        return (current.toEpochDays() - previous.toEpochDays()).toLong() == 1L
    }

    // --- Activity-based Challenge Updates ---

    override suspend fun onHabitCheckedIn() {
        // Update habit-related challenges
        val activeChallenges = getActiveChallenges()

        for (challenge in activeChallenges) {
            when (challenge.type) {
                ChallengeType.DAILY_HABIT,
                ChallengeType.WEEKLY_HABITS -> {
                    val newProgress = challenge.currentProgress + 1
                    updateChallengeProgress(challenge.id, newProgress)
                    checkAndCompleteChallenge(challenge.id)
                }
                else -> { /* Not a habit challenge */ }
            }
        }
    }

    override suspend fun onJournalEntryCreated() {
        // Update journal-related challenges
        val activeChallenges = getActiveChallenges()

        for (challenge in activeChallenges) {
            when (challenge.type) {
                ChallengeType.DAILY_JOURNAL,
                ChallengeType.WEEKLY_JOURNAL -> {
                    val newProgress = challenge.currentProgress + 1
                    updateChallengeProgress(challenge.id, newProgress)
                    checkAndCompleteChallenge(challenge.id)
                }
                else -> { /* Not a journal challenge */ }
            }
        }
    }

    override suspend fun onGoalCompleted() {
        // Update goal-related challenges
        val activeChallenges = getActiveChallenges()

        for (challenge in activeChallenges) {
            when (challenge.type) {
                ChallengeType.WEEKLY_GOALS,
                ChallengeType.MONTHLY_COMPLETION -> {
                    val newProgress = challenge.currentProgress + 1
                    updateChallengeProgress(challenge.id, newProgress)
                    checkAndCompleteChallenge(challenge.id)
                }
                else -> { /* Not a goal challenge */ }
            }
        }
    }

    override suspend fun onMilestoneCompleted() {
        // Update milestone-related challenges
        val activeChallenges = getActiveChallenges()

        for (challenge in activeChallenges) {
            when (challenge.type) {
                ChallengeType.WEEKLY_MILESTONE -> {
                    val newProgress = challenge.currentProgress + 1
                    updateChallengeProgress(challenge.id, newProgress)
                    checkAndCompleteChallenge(challenge.id)
                }
                else -> { /* Not a milestone challenge */ }
            }
        }
    }
}