package az.tribe.lifeplanner.ui.objectives

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.analytics.Analytics
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.model.BeginnerObjective
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.model.ObjectiveType
import az.tribe.lifeplanner.domain.repository.BeginnerObjectiveRepository
import az.tribe.lifeplanner.domain.repository.ChatRepository
import az.tribe.lifeplanner.domain.repository.FocusRepository
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.JournalRepository
import az.tribe.lifeplanner.domain.repository.ReminderRepository
import az.tribe.lifeplanner.domain.repository.UserRepository
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import az.tribe.lifeplanner.domain.enum.BadgeType
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BeginnerObjectiveViewModel(
    private val repository: BeginnerObjectiveRepository,
    private val gamificationRepository: GamificationRepository,
    private val goalRepository: GoalRepository,
    private val habitRepository: HabitRepository,
    private val journalRepository: JournalRepository,
    private val focusRepository: FocusRepository,
    private val chatRepository: ChatRepository,
    private val reminderRepository: ReminderRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val log = Logger.withTag("BeginnerObjectiveVM")

    val objectives: StateFlow<List<BeginnerObjective>> = repository.observeObjectives()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val settings = Settings()
    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded

    private val _isDismissed = MutableStateFlow(settings.getBoolean("objectives_completed", false))
    val isDismissed: StateFlow<Boolean> = _isDismissed

    /** Emitted once when all objectives are completed — UI shows celebration. */
    private val _celebrationEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val celebrationEvent: SharedFlow<Unit> = _celebrationEvent

    init {
        viewModelScope.launch {
            repository.initializeObjectives()
            createGettingStartedGoalIfNeeded()
            detectCompletedObjectives()
        }
        observeGoals()
        observeHabits()
        observeJournal()
        observeFocusSessions()
        syncGettingStartedMilestones()
        observeAllObjectivesComplete()
    }

    fun toggleExpanded() {
        _isExpanded.value = !_isExpanded.value
    }

    fun dismiss() {
        _isDismissed.value = true
        settings.putBoolean("objectives_completed", true)
    }

    fun completeObjective(type: ObjectiveType) {
        viewModelScope.launch {
            try {
                if (!repository.isObjectiveCompleted(type)) {
                    repository.completeObjective(type)
                    Analytics.objectiveCompleted(type.name)
                    // XP is awarded server-side via trg_beginner_objective_completed trigger
                    log.d { "Completed objective ${type.name} (${type.xpReward} XP awarded server-side)" }
                }
            } catch (e: Exception) {
                log.e(e) { "Failed to complete objective: ${type.name}" }
            }
        }
    }

    private fun uncompleteObjective(type: ObjectiveType) {
        viewModelScope.launch {
            try {
                if (repository.isObjectiveCompleted(type)) {
                    repository.uncompleteObjective(type)
                    log.d { "Reverted objective: ${type.name}" }
                }
            } catch (e: Exception) {
                log.e(e) { "Failed to uncomplete objective: ${type.name}" }
            }
        }
    }

    private fun observeGoals() {
        viewModelScope.launch {
            goalRepository.observeAllGoals().collectLatest { goals ->
                if (goals.isNotEmpty()) {
                    completeObjective(ObjectiveType.CREATE_GOAL)
                } else {
                    uncompleteObjective(ObjectiveType.CREATE_GOAL)
                }
                if (goals.any { it.status == GoalStatus.COMPLETED }) {
                    completeObjective(ObjectiveType.COMPLETE_GOAL)
                } else {
                    uncompleteObjective(ObjectiveType.COMPLETE_GOAL)
                }
            }
        }
    }

    private fun observeHabits() {
        viewModelScope.launch {
            habitRepository.observeHabitsWithTodayStatus().collectLatest { habits ->
                if (habits.isNotEmpty()) completeObjective(ObjectiveType.CREATE_HABIT)
                if (habits.any { (_, completedToday) -> completedToday }) completeObjective(ObjectiveType.COMPLETE_HABIT_CHECKIN)
            }
        }
    }

    private fun observeJournal() {
        viewModelScope.launch {
            journalRepository.observeAllEntries().collectLatest { entries ->
                if (entries.isNotEmpty()) completeObjective(ObjectiveType.WRITE_JOURNAL)
            }
        }
    }

    private fun observeFocusSessions() {
        viewModelScope.launch {
            focusRepository.observeAllSessions().collectLatest { sessions ->
                // Mindfulness Minutes: at least 1 minute (60 seconds) to count
                if (sessions.any { it.wasCompleted && it.actualDurationSeconds >= 60 }) {
                    completeObjective(ObjectiveType.START_FOCUS_SESSION)
                }
            }
        }
    }

    // ── Getting Started Goal ──────────────────────────────────────────

    companion object {
        const val GETTING_STARTED_GOAL_ID = "getting_started_goal"
    }

    /**
     * Creates a "Getting Started" goal with beginner objectives as milestones.
     * Only created once — skipped if it already exists.
     */
    private suspend fun createGettingStartedGoalIfNeeded() {
        try {
            if (goalRepository.getGoalById(GETTING_STARTED_GOAL_ID) != null) return

            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val objectiveTypes = ObjectiveType.entries.sortedBy { it.sortOrder }

            val goal = Goal(
                id = GETTING_STARTED_GOAL_ID,
                category = GoalCategory.CAREER,
                title = "Getting Started",
                description = "Complete these steps to explore the app and earn XP along the way.",
                status = GoalStatus.IN_PROGRESS,
                timeline = GoalTimeline.SHORT_TERM,
                dueDate = now.date.plus(DatePeriod(days = 7)),
                progress = 0,
                milestones = objectiveTypes.map { type ->
                    Milestone(
                        id = "gs_milestone_${type.name}",
                        title = type.title,
                        isCompleted = false,
                        dueDate = null
                    )
                },
                notes = "Auto-created to help you explore the app. Complete objectives to earn XP and level up!",
                createdAt = now,
                completionRate = 0f,
                isArchived = false
            )
            goalRepository.insertGoal(goal)
            log.d { "Created Getting Started goal with ${objectiveTypes.size} milestones" }
        } catch (e: Exception) {
            log.e(e) { "Failed to create Getting Started goal" }
        }
    }

    /**
     * Keeps Getting Started goal milestones in sync with completed objectives.
     * When an objective is completed, the matching milestone gets checked off.
     */
    private fun syncGettingStartedMilestones() {
        viewModelScope.launch {
            repository.observeObjectives().collectLatest { allObjectives ->
                try {
                    val goal = goalRepository.getGoalById(GETTING_STARTED_GOAL_ID) ?: return@collectLatest
                    if (goal.status == GoalStatus.COMPLETED) return@collectLatest

                    var changed = false
                    for (objective in allObjectives) {
                        if (!objective.isCompleted) continue
                        val milestoneId = "gs_milestone_${objective.type.name}"
                        val milestone = goal.milestones.find { it.id == milestoneId }
                        if (milestone != null && !milestone.isCompleted) {
                            goalRepository.toggleMilestoneCompletion(milestoneId, true)
                            changed = true
                        }
                    }

                    if (changed) {
                        // Recalculate progress
                        val refreshed = goalRepository.getGoalById(GETTING_STARTED_GOAL_ID)
                        if (refreshed != null && refreshed.milestones.isNotEmpty()) {
                            val completed = refreshed.milestones.count { it.isCompleted }
                            val progress = ((completed.toFloat() / refreshed.milestones.size) * 100).toInt()
                            goalRepository.updateProgress(GETTING_STARTED_GOAL_ID, progress)

                            // Auto-complete when all milestones done
                            if (refreshed.milestones.all { it.isCompleted }) {
                                val completedGoal = refreshed.copy(status = GoalStatus.COMPLETED)
                                goalRepository.updateGoal(completedGoal)
                                log.d { "Getting Started goal completed!" }
                            }
                        }
                    }
                } catch (e: Exception) {
                    log.e(e) { "Failed to sync Getting Started milestones" }
                }
            }
        }
    }

    /**
     * Watch for all objectives completed — award Explorer badge, auto-complete goal,
     * emit celebration event, and permanently dismiss the card.
     */
    private fun observeAllObjectivesComplete() {
        viewModelScope.launch {
            objectives.collectLatest { allObjectives ->
                if (allObjectives.isEmpty()) return@collectLatest
                if (!allObjectives.all { it.isCompleted }) return@collectLatest
                if (settings.getBoolean("objectives_completed", false)) return@collectLatest

                // All done for the first time!
                log.d { "All objectives completed — awarding Explorer badge" }

                // Award badge (idempotent — hasBadge check inside)
                try {
                    if (!gamificationRepository.hasBadge(BadgeType.GETTING_STARTED)) {
                        gamificationRepository.awardBadge(BadgeType.GETTING_STARTED)
                    }
                } catch (e: Exception) {
                    log.e(e) { "Failed to award Explorer badge" }
                }

                // Fire analytics immediately when all objectives are actually done,
                // not gated on the user tapping the dismiss button.
                Analytics.allObjectivesCompleted()

                // Notify UI to show celebration — card stays visible so the user
                // can see the "All objectives complete!" state. The card is
                // dismissed when the user taps the dismiss button.
                _celebrationEvent.tryEmit(Unit)
            }
        }
    }

    /**
     * Scans existing data to auto-complete objectives that have already been achieved.
     * Runs once on init — catches objectives the user completed before this feature existed.
     */
    private suspend fun detectCompletedObjectives() {
        try {
            val goals = goalRepository.getAllGoals()
            if (goals.isNotEmpty()) completeObjective(ObjectiveType.CREATE_GOAL)
            else uncompleteObjective(ObjectiveType.CREATE_GOAL)

            if (goals.any { it.status == GoalStatus.COMPLETED }) completeObjective(ObjectiveType.COMPLETE_GOAL)
            else uncompleteObjective(ObjectiveType.COMPLETE_GOAL)

            val habits = habitRepository.getAllHabits()
            if (habits.isNotEmpty()) completeObjective(ObjectiveType.CREATE_HABIT)
            if (habits.any { it.totalCompletions > 0 }) completeObjective(ObjectiveType.COMPLETE_HABIT_CHECKIN)

            val entries = journalRepository.getAllEntries()
            if (entries.isNotEmpty()) completeObjective(ObjectiveType.WRITE_JOURNAL)

            val focusSessions = focusRepository.getCompletedSessions()
            if (focusSessions.any { it.actualDurationSeconds >= 60 }) {
                completeObjective(ObjectiveType.START_FOCUS_SESSION)
            }

            val reminders = reminderRepository.getAllReminders()
            if (reminders.isNotEmpty()) completeObjective(ObjectiveType.SET_REMINDER)

            val chatSessions = chatRepository.getAllSessions()
            if (chatSessions.any { it.messages.isNotEmpty() }) {
                completeObjective(ObjectiveType.CHAT_WITH_COACH)
            }

            val user = userRepository.getCurrentUser()
            if (user != null && !user.isGuest && user.email != null) {
                completeObjective(ObjectiveType.SECURE_ACCOUNT)
            }
        } catch (e: Exception) {
            log.e(e) { "Failed to detect completed objectives" }
        }
    }

    /**
     * Called from screens that don't have observable data.
     * Screens call this when the user performs the action.
     */
    fun markObjectiveCompleted(type: ObjectiveType) {
        completeObjective(type)
    }
}
