package az.tribe.lifeplanner.ui.gamification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.domain.enum.BadgeType
import az.tribe.lifeplanner.domain.enum.ChallengeType
import az.tribe.lifeplanner.domain.model.Badge
import az.tribe.lifeplanner.domain.model.Challenge
import az.tribe.lifeplanner.domain.model.UserProgress
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.data.sync.SyncState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class GamificationViewModel(
    private val gamificationRepository: GamificationRepository,
    private val syncManager: SyncManager
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
        viewModelScope.launch {
            syncManager.syncStatus
                .map { it.state }
                .distinctUntilChanged()
                .filter { it == SyncState.SYNCED }
                .collect { loadAll() }
        }
    }

    private suspend fun loadAll() {
        _isLoading.value = true
        try {
            val previousProgress = _userProgress.value
            val previousBadges = _badges.value

            loadUserProgress()
            loadBadges()
            loadChallenges()
            checkDailyStreak()

            // Detect level-up from server-side changes
            val newProgress = _userProgress.value
            if (previousProgress != null && newProgress != null &&
                newProgress.currentLevel > previousProgress.currentLevel) {
                _gamificationEvents.emit(
                    GamificationEvent.LevelUp(
                        newLevel = newProgress.currentLevel,
                        title = newProgress.title
                    )
                )
            }

            // Detect newly earned badges from server-side changes
            val newBadgeTypes = _badges.value.map { it.type }.toSet()
            val previousBadgeTypes = previousBadges.map { it.type }.toSet()
            val earnedTypes = newBadgeTypes - previousBadgeTypes
            if (earnedTypes.isNotEmpty()) {
                _badges.value.filter { it.type in earnedTypes }.forEach { badge ->
                    _gamificationEvents.emit(GamificationEvent.BadgeEarned(badge))
                }
            }
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

    /**
     * Reset all cached state (called on sign-out to clear stale data).
     */
    fun resetState() {
        _userProgress.value = null
        _badges.value = emptyList()
        _newBadges.value = emptyList()
        _activeChallenges.value = emptyList()
        _completedChallenges.value = emptyList()
        _availableChallenges.value = emptyList()
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
            val (newStreak, xpAwarded) = gamificationRepository.updateDailyStreakRemote()
            if (xpAwarded > 0) {
                _gamificationEvents.emit(GamificationEvent.StreakUpdated(newStreak))
            }
            // Reload progress after RPC updates server state
            loadUserProgress()
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
