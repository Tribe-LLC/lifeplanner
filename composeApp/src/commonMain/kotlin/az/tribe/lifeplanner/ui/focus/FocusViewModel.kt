package az.tribe.lifeplanner.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.domain.enum.AmbientSound
import az.tribe.lifeplanner.domain.enum.FocusTheme
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.model.FocusSession
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.model.XpRewards
import az.tribe.lifeplanner.domain.repository.FocusRepository
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.usecases.GetGoalByIdUseCase
import az.tribe.lifeplanner.usecases.ToggleMilestoneCompletionUseCase
import az.tribe.lifeplanner.usecases.UpdateGoalProgressUseCase
import az.tribe.lifeplanner.usecases.UpdateGoalStatusUseCase
import kotlinx.coroutines.flow.firstOrNull
import az.tribe.lifeplanner.data.analytics.Analytics
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
    private val goalRepository: GoalRepository,
    private val gamificationRepository: GamificationRepository,
    private val toggleMilestoneCompletionUseCase: ToggleMilestoneCompletionUseCase,
    private val getGoalByIdUseCase: GetGoalByIdUseCase,
    private val updateGoalProgressUseCase: UpdateGoalProgressUseCase,
    private val updateGoalStatusUseCase: UpdateGoalStatusUseCase
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

    private val _isFreeFlow = MutableStateFlow(false)
    val isFreeFlow: StateFlow<Boolean> = _isFreeFlow.asStateFlow()

    private val _isCustomDuration = MutableStateFlow(false)
    val isCustomDuration: StateFlow<Boolean> = _isCustomDuration.asStateFlow()

    private val _customDurationMinutes = MutableStateFlow(25)
    val customDurationMinutes: StateFlow<Int> = _customDurationMinutes.asStateFlow()

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

    private val _todaySeconds = MutableStateFlow(0)
    val todaySeconds: StateFlow<Int> = _todaySeconds.asStateFlow()

    // All-time stats
    private val _allTimeSessionCount = MutableStateFlow(0)
    val allTimeSessionCount: StateFlow<Int> = _allTimeSessionCount.asStateFlow()

    private val _allTimeSeconds = MutableStateFlow(0)
    val allTimeSeconds: StateFlow<Int> = _allTimeSeconds.asStateFlow()

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

    // Milestone completion prompt state
    private val _showMilestonePrompt = MutableStateFlow(false)
    val showMilestonePrompt: StateFlow<Boolean> = _showMilestonePrompt.asStateFlow()

    private val _milestoneMarkedComplete = MutableStateFlow(false)
    val milestoneMarkedComplete: StateFlow<Boolean> = _milestoneMarkedComplete.asStateFlow()

    private val _canCompleteMilestone = MutableStateFlow(true)
    val canCompleteMilestone: StateFlow<Boolean> = _canCompleteMilestone.asStateFlow()

    private val _milestoneFocusMinutes = MutableStateFlow(0)
    val milestoneFocusMinutes: StateFlow<Int> = _milestoneFocusMinutes.asStateFlow()

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
                _todaySeconds.value = todaySessions
                    .filter { it.wasCompleted }
                    .sumOf { it.actualDurationSeconds }
            } catch (e: Exception) { Logger.e("FocusViewModel") { "loadTodayStats failed: ${e.message}" } }
        }
    }

    private fun loadAllTimeStats() {
        viewModelScope.launch {
            try {
                _allTimeSessionCount.value = focusRepository.getTotalSessionCount()
                _allTimeSeconds.value = focusRepository.getTotalFocusSeconds().toInt()
            } catch (e: Exception) { Logger.e("FocusViewModel") { "loadAllTimeStats failed: ${e.message}" } }
        }
    }

    private fun autoSuggestMood() {
        _selectedMood.value = Mood.HAPPY
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
        loadMilestoneFocusMinutes(milestone.id)
    }

    /** Free flow: pick a goal without selecting a specific milestone */
    fun selectGoalOnly(goal: Goal?) {
        if (_selectedGoal.value?.id == goal?.id) {
            // Deselect
            _selectedGoal.value = null
            _selectedMilestone.value = null
        } else {
            _selectedGoal.value = goal
            _selectedMilestone.value = null
        }
    }

    fun setDuration(minutes: Int) {
        _durationMinutes.value = minutes
        _isCustomDuration.value = false
    }

    fun setTimerMode(freeFlow: Boolean) {
        _isFreeFlow.value = freeFlow
        if (freeFlow) {
            _isCustomDuration.value = false
        }
    }

    fun toggleCustomDuration() {
        val newValue = !_isCustomDuration.value
        _isCustomDuration.value = newValue
        if (newValue) {
            _durationMinutes.value = _customDurationMinutes.value
        }
    }

    fun setCustomDuration(minutes: Int) {
        val clamped = minutes.coerceIn(5, 120)
        _customDurationMinutes.value = clamped
        _durationMinutes.value = clamped
    }

    fun incrementCustomDuration() {
        setCustomDuration(_customDurationMinutes.value + 5)
    }

    fun decrementCustomDuration() {
        setCustomDuration(_customDurationMinutes.value - 5)
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
                        loadMilestoneFocusMinutes(milestoneId)
                    }
                }
            } catch (e: Exception) { Logger.e("FocusViewModel") { "selectGoalAndMilestone failed: ${e.message}" } }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun startTimer() {
        val freeFlow = _isFreeFlow.value
        // In free flow mode, milestone is optional; in timed mode, it's required
        if (!freeFlow) {
            if (_selectedGoal.value == null || _selectedMilestone.value == null) return
        }
        val goalId = _selectedGoal.value?.id ?: ""
        val milestoneId = _selectedMilestone.value?.id ?: ""
        val duration = if (freeFlow) 0 else _durationMinutes.value

        val totalSeconds = if (freeFlow) Int.MAX_VALUE / 2 else duration * 60
        _remainingSeconds.value = if (freeFlow) 0 else totalSeconds
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
                goalId = goalId,
                milestoneId = milestoneId,
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

        Analytics.focusSessionStarted(
            mode = if (freeFlow) "free_flow" else "timed",
            theme = _selectedFocusTheme.value.name,
            hasMilestone = milestoneId.isNotEmpty(),
            durationMinutes = duration
        )
    }

    fun pauseTimer() {
        if (_timerState.value != TimerState.RUNNING) return
        timerJob?.cancel()
        pausedElapsedSeconds = _elapsedSeconds.value
        _timerState.value = TimerState.PAUSED
    }

    fun resumeTimer() {
        if (_timerState.value != TimerState.PAUSED) return
        val totalSeconds = if (_isFreeFlow.value) Int.MAX_VALUE / 2 else _durationMinutes.value * 60
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
        Analytics.focusSessionCancelled(elapsed / 60)

        // Show milestone prompt if focused >= 5 min
        if (elapsed >= 300 && _selectedMilestone.value != null) {
            _showMilestonePrompt.value = true
            checkCompletionEligibility()
        }
    }

    fun addFiveMinutes() {
        if (_isFreeFlow.value) return
        _durationMinutes.value += 5
        val totalSeconds = _durationMinutes.value * 60
        _remainingSeconds.value = totalSeconds - _elapsedSeconds.value
    }

    fun completeFreeFlowSession() {
        timerJob?.cancel()
        val elapsed = _elapsedSeconds.value

        // Mindfulness Minutes: need at least 60 seconds for session to count
        if (elapsed < 60) {
            // Still save session as incomplete so we don't leave orphaned records
            viewModelScope.launch {
                currentSessionId?.let { id ->
                    val session = focusRepository.getSessionById(id)
                    if (session != null) {
                        focusRepository.updateSession(
                            session.copy(
                                actualDurationSeconds = elapsed,
                                wasCompleted = false,
                                xpEarned = 0,
                                completedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                            )
                        )
                    }
                }
            }
            _timerState.value = TimerState.CANCELLED
            return
        }

        val elapsedMinutes = elapsed / 60
        val xp = calculateXpForDuration(elapsedMinutes)
        _lastXpEarned.value = xp
        _progress.value = 1f
        _timerState.value = TimerState.COMPLETED

        viewModelScope.launch {
            currentSessionId?.let { id ->
                val session = focusRepository.getSessionById(id)
                if (session != null) {
                    focusRepository.updateSession(
                        session.copy(
                            actualDurationSeconds = elapsed,
                            wasCompleted = true,
                            xpEarned = xp,
                            completedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                        )
                    )
                }
            }
            _focusEvents.emit(FocusEvent.SessionCompleted(xp, elapsedMinutes))
            loadTodayStats()
            loadAllTimeStats()
        }

        Analytics.focusSessionCompleted("free_flow", elapsedMinutes, xp)

        if (_selectedMilestone.value != null) {
            _showMilestonePrompt.value = true
            checkCompletionEligibility()
        }
    }

    fun resetToSetup() {
        timerJob?.cancel()
        _timerState.value = TimerState.IDLE
        _selectedGoal.value = null
        _selectedMilestone.value = null
        _durationMinutes.value = 25
        _isFreeFlow.value = false
        _isCustomDuration.value = false
        _customDurationMinutes.value = 25
        _remainingSeconds.value = 0
        _elapsedSeconds.value = 0
        _progress.value = 0f
        _lastXpEarned.value = 0
        _selectedAmbientSound.value = AmbientSound.NONE
        _selectedFocusTheme.value = FocusTheme.DEFAULT
        _showMilestonePrompt.value = false
        _milestoneMarkedComplete.value = false
        _canCompleteMilestone.value = true
        _milestoneFocusMinutes.value = 0
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
        val freeFlow = _isFreeFlow.value
        timerJob = viewModelScope.launch {
            while (_timerState.value == TimerState.RUNNING) {
                delay(1000L)
                // Wall-clock sync for accuracy after backgrounding
                val now = Clock.System.now()
                val startInstant = timerStartInstant ?: now
                val wallElapsed = (now - startInstant).inWholeSeconds.toInt()

                if (freeFlow) {
                    val elapsed = pausedElapsedSeconds + wallElapsed
                    _elapsedSeconds.value = elapsed
                    _remainingSeconds.value = 0
                    _progress.value = 0f
                } else {
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

        Analytics.focusSessionCompleted("timed", duration, xp)

        // Show milestone completion prompt
        if (_selectedMilestone.value != null) {
            _showMilestonePrompt.value = true
            checkCompletionEligibility()
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

    private fun checkCompletionEligibility() {
        val milestoneId = _selectedMilestone.value?.id ?: return
        viewModelScope.launch {
            try {
                val sessions = focusRepository.getSessionsByMilestoneId(milestoneId)
                val completedSessions = sessions.filter { it.wasCompleted }
                val userProgress = gamificationRepository.getUserProgress().firstOrNull()
                val level = userProgress?.currentLevel ?: 1
                // Level 5+ can always complete; below level 5 need at least 1 completed session
                _canCompleteMilestone.value = level >= 5 || completedSessions.isNotEmpty()
            } catch (e: Exception) {
                Logger.e("FocusViewModel") { "checkCompletionEligibility failed: ${e.message}" }
                _canCompleteMilestone.value = true
            }
        }
    }

    fun markMilestoneComplete() {
        val goalId = _selectedGoal.value?.id ?: return
        val milestoneId = _selectedMilestone.value?.id ?: return
        viewModelScope.launch {
            try {
                val result = toggleMilestoneCompletionUseCase(milestoneId, true)
                if (result.isSuccess) {
                    _milestoneMarkedComplete.value = true
                    // Recalculate goal progress
                    val updatedGoal = getGoalByIdUseCase(goalId)
                    updatedGoal?.let { g ->
                        val completed = g.milestones.count { it.isCompleted }
                        val total = g.milestones.size
                        if (total > 0) updateGoalProgressUseCase(goalId, ((completed.toFloat() / total) * 100).toInt())
                    }
                    // Auto-transition NOT_STARTED → IN_PROGRESS
                    val goal = getGoalByIdUseCase(goalId)
                    if (goal?.status == GoalStatus.NOT_STARTED) {
                        updateGoalStatusUseCase(goalId, GoalStatus.IN_PROGRESS)
                    }
                }
            } catch (e: Exception) {
                Logger.e("FocusViewModel") { "markMilestoneComplete failed: ${e.message}" }
            }
            _showMilestonePrompt.value = false
        }
    }

    fun dismissMilestonePrompt() {
        _showMilestonePrompt.value = false
    }

    private fun loadMilestoneFocusMinutes(milestoneId: String) {
        viewModelScope.launch {
            try {
                val sessions = focusRepository.getSessionsByMilestoneId(milestoneId)
                _milestoneFocusMinutes.value = sessions.sumOf { it.actualDurationSeconds } / 60
            } catch (e: Exception) {
                Logger.e("FocusViewModel") { "loadMilestoneFocusMinutes failed: ${e.message}" }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // If timer is still running or paused, save the partial session before cleanup
        if (_timerState.value == TimerState.RUNNING || _timerState.value == TimerState.PAUSED) {
            timerJob?.cancel()
            val elapsed = _elapsedSeconds.value
            val xp = calculatePartialXp(elapsed)
            // Use a non-cancellable context so the DB write completes even during ViewModel teardown
            kotlinx.coroutines.runBlocking {
                try {
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
                        gamificationRepository.awardXp(xp.toLong())
                    }
                } catch (e: Exception) {
                    Logger.e("FocusViewModel") { "Failed to save interrupted session: ${e.message}" }
                }
            }
        } else {
            timerJob?.cancel()
        }
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
