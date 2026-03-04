package az.tribe.lifeplanner.ui.gamification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.domain.enum.BadgeType
import az.tribe.lifeplanner.domain.enum.ChallengeType
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.model.Badge
import az.tribe.lifeplanner.domain.model.Challenge
import az.tribe.lifeplanner.domain.model.UserProgress
import az.tribe.lifeplanner.domain.model.XpRewards
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GamificationViewModel(
    private val gamificationRepository: GamificationRepository
) : ViewModel() {

    private val _userProgress = MutableStateFlow<UserProgress?>(null)
    val userProgress: StateFlow<UserProgress?> = _userProgress.asStateFlow()

    private val _badges = MutableStateFlow<List<Badge>>(emptyList())
    val badges: StateFlow<List<Badge>> = _badges.asStateFlow()

    private val _newBadges = MutableStateFlow<List<Badge>>(emptyList())
    val newBadges: StateFlow<List<Badge>> = _newBadges.asStateFlow()

    private val _activeChallenges = MutableStateFlow<List<Challenge>>(emptyList())
    val activeChallenges: StateFlow<List<Challenge>> = _activeChallenges.asStateFlow()

    private val _completedChallenges = MutableStateFlow<List<Challenge>>(emptyList())
    val completedChallenges: StateFlow<List<Challenge>> = _completedChallenges.asStateFlow()

    private val _availableChallenges = MutableStateFlow<List<ChallengeType>>(emptyList())
    val availableChallenges: StateFlow<List<ChallengeType>> = _availableChallenges.asStateFlow()

    private val _gamificationEvents = MutableSharedFlow<GamificationEvent>()
    val gamificationEvents = _gamificationEvents.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            loadAll()
        }
    }

    private suspend fun loadAll() {
        _isLoading.value = true
        try {
            loadUserProgress()
            loadBadges()
            loadChallenges()
            checkDailyStreak()
            checkAndAwardBadges()
        } catch (e: Exception) {
            co.touchlab.kermit.Logger.w("GamificationVM") { "loadAll failed: ${e.message}" }
        } finally {
            _isLoading.value = false
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loadAll()
        }
    }

    private suspend fun loadUserProgress() {
        gamificationRepository.getUserProgress().collect {
            _userProgress.value = it
        }
    }

    private suspend fun loadBadges() {
        _badges.value = gamificationRepository.getAllBadges()
        _newBadges.value = gamificationRepository.getNewBadges()
    }

    private suspend fun loadChallenges() {
        gamificationRepository.cleanupExpiredChallenges()
        _activeChallenges.value = gamificationRepository.getActiveChallenges()
        _completedChallenges.value = gamificationRepository.getCompletedChallenges()
        _availableChallenges.value = gamificationRepository.getAvailableChallenges()
    }

    fun checkDailyStreak() {
        viewModelScope.launch {
            val previousStreak = _userProgress.value?.currentStreak ?: 0
            val newStreak = gamificationRepository.updateDailyStreak().first()

            if (newStreak > previousStreak) {
                // Award XP for daily check-in
                gamificationRepository.addXp(XpRewards.DAILY_CHECK_IN)
                _gamificationEvents.emit(GamificationEvent.StreakUpdated(newStreak))
            }

            loadUserProgress()
            checkAndAwardBadges()
        }
    }

    private suspend fun checkAndAwardBadges() {
        val awardedBadges = gamificationRepository.checkAndAwardBadges()
        if (awardedBadges.isNotEmpty()) {
            loadBadges()
            awardedBadges.forEach { badge ->
                _gamificationEvents.emit(GamificationEvent.BadgeEarned(badge))
            }
        }
    }

    fun markBadgeAsSeen(badgeId: String) {
        viewModelScope.launch {
            gamificationRepository.markBadgeAsSeen(badgeId)
            loadBadges()
        }
    }

    fun markAllBadgesAsSeen() {
        viewModelScope.launch {
            gamificationRepository.markAllBadgesAsSeen()
            loadBadges()
        }
    }

    fun startChallenge(type: ChallengeType) {
        viewModelScope.launch {
            val challenge = gamificationRepository.startChallenge(type)
            loadChallenges()
            _gamificationEvents.emit(GamificationEvent.ChallengeStarted(challenge))
        }
    }

    fun updateChallengeProgress(challengeId: String, progress: Int) {
        viewModelScope.launch {
            gamificationRepository.updateChallengeProgress(challengeId, progress)

            // Check if challenge is completed
            val completedChallenge = gamificationRepository.checkAndCompleteChallenge(challengeId)
            if (completedChallenge != null) {
                _gamificationEvents.emit(GamificationEvent.ChallengeCompleted(completedChallenge))
                loadUserProgress()
            }

            loadChallenges()
        }
    }

    /**
     * Add XP and check for level-up. Emits LevelUp event if the level increased.
     */
    private suspend fun addXpAndCheckLevelUp(xpAmount: Int) {
        val previousLevel = _userProgress.value?.currentLevel ?: 1
        gamificationRepository.addXp(xpAmount)
        loadUserProgress()
        val newLevel = _userProgress.value?.currentLevel ?: 1
        if (newLevel > previousLevel) {
            _gamificationEvents.emit(
                GamificationEvent.LevelUp(
                    newLevel = newLevel,
                    title = _userProgress.value?.title ?: "Adventurer"
                )
            )
        }
    }

    // Methods to be called from other ViewModels when actions occur
    fun onGoalCompleted() {
        viewModelScope.launch {
            gamificationRepository.incrementGoalsCompleted()
            addXpAndCheckLevelUp(XpRewards.GOAL_COMPLETED)
            checkAndAwardBadges()
        }
    }

    fun onMilestoneCompleted() {
        viewModelScope.launch {
            addXpAndCheckLevelUp(XpRewards.MILESTONE_COMPLETED)
        }
    }

    fun onHabitCheckIn(streakDays: Int) {
        viewModelScope.launch {
            gamificationRepository.incrementHabitsCompleted()
            val xp = XpRewards.HABIT_CHECK_IN + (XpRewards.HABIT_STREAK_BONUS * streakDays)
            addXpAndCheckLevelUp(xp)
            checkAndAwardBadges()
        }
    }

    fun onJournalEntryCreated() {
        viewModelScope.launch {
            gamificationRepository.incrementJournalEntries()
            addXpAndCheckLevelUp(XpRewards.JOURNAL_ENTRY)
            checkAndAwardBadges()
        }
    }

    fun onGoalCreated() {
        viewModelScope.launch {
            addXpAndCheckLevelUp(XpRewards.GOAL_CREATED)
        }
    }

    fun onFocusSessionCompleted(durationMinutes: Int) {
        viewModelScope.launch {
            val xp = when {
                durationMinutes >= 60 -> XpRewards.FOCUS_SESSION_60
                durationMinutes >= 45 -> XpRewards.FOCUS_SESSION_45
                durationMinutes >= 25 -> XpRewards.FOCUS_SESSION_25
                durationMinutes >= 15 -> XpRewards.FOCUS_SESSION_15
                else -> (durationMinutes * 0.5f).toInt().coerceAtLeast(1)
            }
            addXpAndCheckLevelUp(xp)
            checkAndAwardBadges()
        }
    }

    /**
     * Get all badge types with their earned status
     */
    fun getAllBadgeTypesWithStatus(): List<Pair<BadgeType, Badge?>> {
        val earnedBadges = _badges.value.associateBy { it.type }
        return BadgeType.entries.map { type ->
            type to earnedBadges[type]
        }
    }
}

sealed class GamificationEvent {
    data class StreakUpdated(val newStreak: Int) : GamificationEvent()
    data class BadgeEarned(val badge: Badge) : GamificationEvent()
    data class ChallengeStarted(val challenge: Challenge) : GamificationEvent()
    data class ChallengeCompleted(val challenge: Challenge) : GamificationEvent()
    data class LevelUp(val newLevel: Int, val title: String) : GamificationEvent()
    data class XpEarned(val amount: Int) : GamificationEvent()
}