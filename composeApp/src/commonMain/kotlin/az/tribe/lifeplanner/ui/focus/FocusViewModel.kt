package az.tribe.lifeplanner.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.domain.enum.AmbientSound
import az.tribe.lifeplanner.domain.enum.FocusTheme
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.FocusSession
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.model.XpRewards
import az.tribe.lifeplanner.domain.repository.FocusRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

enum class TimerState {
    IDLE, RUNNING, PAUSED, COMPLETED, CANCELLED
}

class FocusViewModel(
    private val focusRepository: FocusRepository,
    private val goalRepository: GoalRepository
) : ViewModel() {

    // Setup state — milestone-first selection
    private val _activeGoals = MutableStateFlow<List<Goal>>(emptyList())
    val activeGoals: StateFlow<List<Goal>> = _activeGoals.asStateFlow()

    // Flat list: all incomplete milestones paired with their parent goal
    private val _milestoneItems = MutableStateFlow<List<MilestoneItem>>(emptyList())
    val milestoneItems: StateFlow<List<MilestoneItem>> = _milestoneItems.asStateFlow()

    private val _selectedGoal = MutableStateFlow<Goal?>(null)
    val selectedGoal: StateFlow<Goal?> = _selectedGoal.asStateFlow()

    private val _selectedMilestone = MutableStateFlow<Milestone?>(null)
    val selectedMilestone: StateFlow<Milestone?> = _selectedMilestone.asStateFlow()

    private val _durationMinutes = MutableStateFlow(25)
    val durationMinutes: StateFlow<Int> = _durationMinutes.asStateFlow()

    // Timer state
    private val _timerState = MutableStateFlow(TimerState.IDLE)
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    // Session result
    private val _lastXpEarned = MutableStateFlow(0)
    val lastXpEarned: StateFlow<Int> = _lastXpEarned.asStateFlow()

    // Today stats
    private val _todaySessionCount = MutableStateFlow(0)
    val todaySessionCount: StateFlow<Int> = _todaySessionCount.asStateFlow()

    private val _todayMinutes = MutableStateFlow(0)
    val todayMinutes: StateFlow<Int> = _todayMinutes.asStateFlow()

    // All-time stats
    private val _allTimeSessionCount = MutableStateFlow(0)
    val allTimeSessionCount: StateFlow<Int> = _allTimeSessionCount.asStateFlow()

    private val _allTimeMinutes = MutableStateFlow(0)
    val allTimeMinutes: StateFlow<Int> = _allTimeMinutes.asStateFlow()

    // Mood, Ambient Sound, Focus Theme
    private val _selectedMood = MutableStateFlow<Mood?>(null)
    val selectedMood: StateFlow<Mood?> = _selectedMood.asStateFlow()

    private val _selectedAmbientSound = MutableStateFlow(AmbientSound.NONE)
    val selectedAmbientSound: StateFlow<AmbientSound> = _selectedAmbientSound.asStateFlow()

    private val _selectedFocusTheme = MutableStateFlow(FocusTheme.DEFAULT)
    val selectedFocusTheme: StateFlow<FocusTheme> = _selectedFocusTheme.asStateFlow()

    // Events
    private val _focusEvents = MutableSharedFlow<FocusEvent>()
    val focusEvents = _focusEvents.asSharedFlow()

    private var timerJob: Job? = null
    private var currentSessionId: String? = null
    private var timerStartInstant: Instant? = null
    private var pausedElapsedSeconds: Int = 0

    init {
        loadActiveGoals()
        loadTodayStats()
        loadAllTimeStats()
        autoSuggestMood()
    }

    private fun loadActiveGoals() {
        viewModelScope.launch {
            try {
                val goals = goalRepository.getActiveGoals()
                val goalsWithMilestones = goals.filter { goal ->
                    goal.milestones.any { !it.isCompleted }
                }
                _activeGoals.value = goalsWithMilestones

                // Build flat milestone list grouped by goal
                _milestoneItems.value = goalsWithMilestones.flatMap { goal ->
                    goal.milestones
                        .filter { !it.isCompleted }
                        .map { milestone -> MilestoneItem(milestone, goal) }
                }
            } catch (e: Exception) { Logger.e("FocusViewModel") { "loadGoalsAndMilestones failed: ${e.message}" } }
        }
    }

    private fun loadTodayStats() {
        viewModelScope.launch {
            try {
                val todaySessions = focusRepository.getTodaySessions()
                _todaySessionCount.value = todaySessions.count { it.wasCompleted }
                _todayMinutes.value = todaySessions
                    .filter { it.wasCompleted }
                    .sumOf { it.actualDurationSeconds } / 60
            } catch (e: Exception) { Logger.e("FocusViewModel") { "loadTodayStats failed: ${e.message}" } }
        }
    }

    private fun loadAllTimeStats() {
        viewModelScope.launch {
            try {
                _allTimeSessionCount.value = focusRepository.getTotalSessionCount()
                _allTimeMinutes.value = focusRepository.getTotalFocusMinutes()
            } catch (e: Exception) { Logger.e("FocusViewModel") { "loadAllTimeStats failed: ${e.message}" } }
        }
    }

    private fun autoSuggestMood() {
        val hour = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).hour
        _selectedMood.value = when (hour) {
            in 5..11 -> Mood.HAPPY
            in 12..17 -> Mood.NEUTRAL
            in 18..21 -> Mood.HAPPY
            else -> Mood.NEUTRAL
        }
    }

    fun setMood(mood: Mood) {
        _selectedMood.value = mood
    }

    fun setAmbientSound(sound: AmbientSound) {
        _selectedAmbientSound.value = sound
    }

    fun setFocusTheme(theme: FocusTheme) {
        _selectedFocusTheme.value = theme
    }

    fun selectMilestoneWithGoal(milestone: Milestone, goal: Goal) {
        _selectedMilestone.value = milestone
        _selectedGoal.value = goal
    }

    fun setDuration(minutes: Int) {
        _durationMinutes.value = minutes
    }

    fun preSelectGoalAndMilestone(goalId: String, milestoneId: String) {
        viewModelScope.launch {
            try {
                val goals = goalRepository.getActiveGoals()
                val goal = goals.find { it.id == goalId }
                if (goal != null) {
                    _selectedGoal.value = goal
                    val milestone = goal.milestones.find { it.id == milestoneId }
                    if (milestone != null) {
                        _selectedMilestone.value = milestone
                    }
                }
            } catch (e: Exception) { Logger.e("FocusViewModel") { "selectGoalAndMilestone failed: ${e.message}" } }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun startTimer() {
        val goal = _selectedGoal.value ?: return
        val milestone = _selectedMilestone.value ?: return
        val duration = _durationMinutes.value

        val totalSeconds = duration * 60
        _remainingSeconds.value = totalSeconds
        _elapsedSeconds.value = 0
        _progress.value = 0f
        pausedElapsedSeconds = 0

        val sessionId = Uuid.random().toString()
        currentSessionId = sessionId
        val now = Clock.System.now()
        timerStartInstant = now

        viewModelScope.launch {
            val session = FocusSession(
                id = sessionId,
                goalId = goal.id,
                milestoneId = milestone.id,
                plannedDurationMinutes = duration,
                actualDurationSeconds = 0,
                wasCompleted = false,
                xpEarned = 0,
                startedAt = now.toLocalDateTime(TimeZone.currentSystemDefault()),
                completedAt = null,
                createdAt = now.toLocalDateTime(TimeZone.currentSystemDefault()),
                mood = _selectedMood.value,
                ambientSound = _selectedAmbientSound.value,
                focusTheme = _selectedFocusTheme.value
            )
            focusRepository.insertSession(session)
        }

        _timerState.value = TimerState.RUNNING
        startTickLoop(totalSeconds)
    }

    fun pauseTimer() {
        if (_timerState.value != TimerState.RUNNING) return
        timerJob?.cancel()
        pausedElapsedSeconds = _elapsedSeconds.value
        _timerState.value = TimerState.PAUSED
    }

    fun resumeTimer() {
        if (_timerState.value != TimerState.PAUSED) return
        val totalSeconds = _durationMinutes.value * 60
        timerStartInstant = Clock.System.now()
        _timerState.value = TimerState.RUNNING
        startTickLoop(totalSeconds)
    }

    fun cancelTimer() {
        timerJob?.cancel()
        val elapsed = _elapsedSeconds.value
        val xp = calculatePartialXp(elapsed)
        _lastXpEarned.value = xp

        viewModelScope.launch {
            currentSessionId?.let { id ->
                val session = focusRepository.getSessionById(id)
                if (session != null) {
                    focusRepository.updateSession(
                        session.copy(
                            actualDurationSeconds = elapsed,
                            wasCompleted = false,
                            xpEarned = xp,
                            completedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                        )
                    )
                }
            }
            if (xp > 0) {
                _focusEvents.emit(FocusEvent.SessionCancelled(xp))
            }
        }
        _timerState.value = TimerState.CANCELLED
    }

    fun addFiveMinutes() {
        _durationMinutes.value += 5
        val totalSeconds = _durationMinutes.value * 60
        _remainingSeconds.value = totalSeconds - _elapsedSeconds.value
    }

    fun resetToSetup() {
        timerJob?.cancel()
        _timerState.value = TimerState.IDLE
        _selectedGoal.value = null
        _selectedMilestone.value = null
        _durationMinutes.value = 25
        _remainingSeconds.value = 0
        _elapsedSeconds.value = 0
        _progress.value = 0f
        _lastXpEarned.value = 0
        _selectedAmbientSound.value = AmbientSound.NONE
        _selectedFocusTheme.value = FocusTheme.DEFAULT
        currentSessionId = null
        timerStartInstant = null
        pausedElapsedSeconds = 0
        loadActiveGoals()
        loadTodayStats()
        loadAllTimeStats()
        autoSuggestMood()
    }

    private fun startTickLoop(totalSeconds: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_timerState.value == TimerState.RUNNING) {
                delay(1000L)
                // Wall-clock sync for accuracy after backgrounding
                val now = Clock.System.now()
                val startInstant = timerStartInstant ?: now
                val wallElapsed = (now - startInstant).inWholeSeconds.toInt()
                val elapsed = (pausedElapsedSeconds + wallElapsed).coerceAtMost(totalSeconds)

                _elapsedSeconds.value = elapsed
                _remainingSeconds.value = (totalSeconds - elapsed).coerceAtLeast(0)
                _progress.value = (elapsed.toFloat() / totalSeconds).coerceIn(0f, 1f)

                if (elapsed >= totalSeconds) {
                    onTimerComplete()
                    break
                }
            }
        }
    }

    private fun onTimerComplete() {
        val duration = _durationMinutes.value
        val xp = calculateXpForDuration(duration)
        _lastXpEarned.value = xp
        _progress.value = 1f
        _remainingSeconds.value = 0
        _elapsedSeconds.value = duration * 60
        _timerState.value = TimerState.COMPLETED

        viewModelScope.launch {
            currentSessionId?.let { id ->
                val session = focusRepository.getSessionById(id)
                if (session != null) {
                    focusRepository.updateSession(
                        session.copy(
                            actualDurationSeconds = duration * 60,
                            wasCompleted = true,
                            xpEarned = xp,
                            completedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                        )
                    )
                }
            }
            _focusEvents.emit(FocusEvent.SessionCompleted(xp, duration))
            loadTodayStats()
            loadAllTimeStats()
        }
    }

    private fun calculateXpForDuration(minutes: Int): Int {
        return when {
            minutes >= 60 -> XpRewards.FOCUS_SESSION_60
            minutes >= 45 -> XpRewards.FOCUS_SESSION_45
            minutes >= 25 -> XpRewards.FOCUS_SESSION_25
            minutes >= 15 -> XpRewards.FOCUS_SESSION_15
            else -> (minutes * 0.5f).toInt().coerceAtLeast(1)
        }
    }

    private fun calculatePartialXp(elapsedSeconds: Int): Int {
        val elapsedMinutes = elapsedSeconds / 60
        return if (elapsedMinutes >= 5) {
            (elapsedMinutes * 0.5f).toInt()
        } else {
            0
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}

sealed class FocusEvent {
    data class SessionCompleted(val xpEarned: Int, val durationMinutes: Int) : FocusEvent()
    data class SessionCancelled(val partialXp: Int) : FocusEvent()
}

data class MilestoneItem(
    val milestone: Milestone,
    val goal: Goal
)
