package az.tribe.lifeplanner.ui.decision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.domain.model.ChoicePoint
import az.tribe.lifeplanner.domain.model.Decision
import az.tribe.lifeplanner.domain.model.DecisionStatus
import az.tribe.lifeplanner.domain.repository.DecisionProfileRepository
import az.tribe.lifeplanner.domain.repository.DecisionRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.service.ChoicePointDetector
import az.tribe.lifeplanner.domain.service.DecisionScorecard
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Pillar 3, the user's response to a [ChoicePoint]: a deliberate re-choice. */
enum class ChoicePointAction(val label: String) {
    KEEP("Keep"),
    RESCHEDULE("Reschedule"),
    SHRINK("Shrink"),
    DROP("Drop")
}

/**
 * Pillar 3, drives the Decision Journal: the logged [Decision]s plus the currently
 * pending [ChoicePoint]s, and resolves a choice point into a recorded [Decision].
 */
class DecisionViewModel(
    private val decisionRepository: DecisionRepository,
    private val goalRepository: GoalRepository,
    private val habitRepository: HabitRepository,
    private val detector: ChoicePointDetector,
    private val decisionProfileRepository: DecisionProfileRepository,
) : ViewModel() {

    private val allDecisions: StateFlow<List<Decision>> =
        decisionRepository.observeAllDecisions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The confirmed decision log (choice-point resolutions, confirmed journal decisions, etc.). */
    val decisions: StateFlow<List<Decision>> =
        allDecisions
            .map { list -> list.filter { it.status == DecisionStatus.CONFIRMED } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The track record behind the log: how many calls were made, how many were reviewed, the
     * process hit rate, and the gap between stated confidence and what actually happened.
     */
    val scorecard: StateFlow<DecisionScorecard> =
        decisions
            .map { DecisionScorecard.from(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DecisionScorecard())

    /** AI-detected journal decisions awaiting the user's confirmation, most recent first. */
    val pendingDecisions: StateFlow<List<Decision>> =
        allDecisions
            .map { list -> list.filter { it.status == DecisionStatus.PENDING }.sortedByDescending { it.decidedAt } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _choicePoints = MutableStateFlow<List<ChoicePoint>>(emptyList())
    val choicePoints: StateFlow<List<ChoicePoint>> = _choicePoints.asStateFlow()

    init { refreshChoicePoints() }

    fun refreshChoicePoints() {
        viewModelScope.launch {
            _choicePoints.value = try {
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                val goals = goalRepository.getActiveGoals()
                val habits = habitRepository.observeHabitsWithTodayStatus().firstOrNull().orEmpty()
                val profile = decisionProfileRepository.getProfile() // Pillar 7: tune prompts to the user's wiring
                detector.detect(today, goals, habits, profile)
            } catch (e: Exception) {
                Logger.w("DecisionViewModel") { "Choice-point detection failed: ${e.message}" }
                emptyList()
            }
        }
    }

    suspend fun getDecision(id: String): Decision? = decisionRepository.getDecisionById(id)

    /**
     * Confirm a PENDING journal decision, optionally with the user's edits (they may correct the
     * chosen option or reasoning the AI inferred). Confirmed decisions enter the log and, from
     * Phase 4 on, become the only journal signal allowed to move the wiring dials.
     */
    fun confirm(decision: Decision, chosenOption: String? = null, reasoning: String? = null) {
        viewModelScope.launch {
            try {
                decisionRepository.updateDecision(
                    decision.copy(
                        chosenOption = chosenOption?.takeIf { it.isNotBlank() } ?: decision.chosenOption,
                        reasoning = reasoning ?: decision.reasoning,
                        status = DecisionStatus.CONFIRMED,
                    )
                )
            } catch (e: Exception) {
                Logger.w("DecisionViewModel") { "Confirming decision failed: ${e.message}" }
            }
        }
    }

    /** Dismiss a PENDING journal decision, the user says it wasn't really a decision. */
    fun dismiss(decision: Decision) {
        viewModelScope.launch {
            try {
                decisionRepository.updateDecision(decision.copy(status = DecisionStatus.DISMISSED))
            } catch (e: Exception) {
                Logger.w("DecisionViewModel") { "Dismissing decision failed: ${e.message}" }
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun resolve(choicePoint: ChoicePoint, action: ChoicePointAction, reasoning: String) {
        viewModelScope.launch {
            try {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                decisionRepository.insertDecision(
                    Decision(
                        id = Uuid.random().toString(),
                        question = choicePoint.title,
                        optionsConsidered = ChoicePointAction.entries.map { it.label },
                        chosenOption = action.label,
                        reasoning = reasoning,
                        relatedGoalId = choicePoint.relatedGoalId,
                        expectedOutcome = "",
                        confidence = 50,
                        decidedAt = now
                    )
                )
                applyAction(choicePoint, action)
                refreshChoicePoints()
            } catch (e: Exception) {
                Logger.w("DecisionViewModel") { "Resolving choice point failed: ${e.message}" }
            }
        }
    }

    private suspend fun applyAction(choicePoint: ChoicePoint, action: ChoicePointAction) {
        val goalId = choicePoint.relatedGoalId ?: return
        when (action) {
            ChoicePointAction.DROP -> goalRepository.archiveGoal(goalId)
            ChoicePointAction.RESCHEDULE -> {
                val goal = goalRepository.getGoalById(goalId) ?: return
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                goalRepository.updateGoal(goal.copy(dueDate = today.plus(DatePeriod(days = 14))))
            }
            // KEEP / SHRINK: the deliberate decision is recorded; no structural change here.
            ChoicePointAction.KEEP, ChoicePointAction.SHRINK -> Unit
        }
    }
}
