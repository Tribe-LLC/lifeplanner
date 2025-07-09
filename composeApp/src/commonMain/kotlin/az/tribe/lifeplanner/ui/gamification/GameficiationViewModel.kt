package az.tribe.lifeplanner.ui.gamification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.model.UserProgress
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

    private val _streakEvents = MutableSharedFlow<StreakEvent>()
    val streakEvents = _streakEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            loadUserProgress()
            checkDailyStreak()
        }
    }

    private suspend fun loadUserProgress() {
        gamificationRepository.getUserProgress().collect {
            _userProgress.value = it
        }
    }

    fun checkDailyStreak() {
        viewModelScope.launch {
            val currentProgress = _userProgress.value ?: return@launch
            val streak = gamificationRepository.updateDailyStreak().first()


            // Refresh progress after potential changes
            loadUserProgress()
        }
    }

}

sealed class StreakEvent {
    data class TimelineUnlocked(val timeline: GoalTimeline) : StreakEvent()
    data class StreakUpdated(val newStreak: Int) : StreakEvent()
}