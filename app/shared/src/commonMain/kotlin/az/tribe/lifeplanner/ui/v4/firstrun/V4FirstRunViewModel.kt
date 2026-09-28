package az.tribe.lifeplanner.ui.v4.firstrun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.integrations.IntegrationPrefs
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.HealthMetricType
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.HealthMetricSource
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.ChatRepository
import az.tribe.lifeplanner.domain.repository.FocusRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.HealthRepository
import az.tribe.lifeplanner.domain.repository.JournalRepository
import az.tribe.lifeplanner.domain.repository.PlanAreasRepository
import az.tribe.lifeplanner.ui.onboarding.CoachOnboardingViewModel
import az.tribe.lifeplanner.ui.onboarding.IntroFlow
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What an existing user already has, for the update screen. Counts follow sync as it lands. */
data class KeptData(
    val goals: Int,
    val steps: Int,
    val habits: Int,
    val journal: Int,
    val chats: Long,
    val suggested: Set<PlanArea>,
) {
    val isEmpty get() = goals == 0 && habits == 0 && journal == 0 && chats == 0L
}

class V4FirstRunViewModel(
    private val planAreas: PlanAreasRepository,
    goalRepository: GoalRepository,
    habitRepository: HabitRepository,
    journalRepository: JournalRepository,
    private val chatRepository: ChatRepository,
    private val healthRepository: HealthRepository,
    private val integrationPrefs: IntegrationPrefs,
    private val settings: Settings,
    focusRepository: FocusRepository,
) : ViewModel() {

    private val chats = MutableStateFlow(0L)

    val kept: StateFlow<KeptData?> = combine(
        goalRepository.observeAllGoals(),
        habitRepository.observeHabitsWithTodayStatus(),
        journalRepository.observeAllEntries(),
        chats,
        focusRepository.observeAllSessions(),
    ) { goals, habits, journal, chatCount, focus ->
        KeptData(
            goals = goals.size,
            steps = goals.sumOf { it.milestones.size },
            habits = habits.size,
            journal = journal.size,
            chats = chatCount,
            suggested = suggestAreas(goals, habits.map { it.first }, journal.size) +
                // The focus timer moved into Study, so its users need Study on to find it.
                (if (focus.isNotEmpty()) setOf(PlanArea.STUDY) else emptySet()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val enabledAreas: StateFlow<Set<PlanArea>> = planAreas.enabledAreas

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    init {
        viewModelScope.launch { chats.value = runCatching { chatRepository.getSessionCount() }.getOrDefault(0L) }
    }

    fun pickAreas(areas: Set<PlanArea>) {
        planAreas.setEnabledAreas(areas)
        PostHogAnalytics.capture("v4_areas_picked", mapOf("areas" to areas.joinToString(",") { it.key }, "count" to areas.size))
    }

    /** The update screen shows areas "set from what you used": apply them once, unless already chosen. */
    fun applySuggestedIfUnset(suggested: Set<PlanArea>) {
        if (!planAreas.hasChosenAreas()) planAreas.setEnabledAreas(suggested)
    }

    /**
     * An existing user who synced Health before keeps it on without being asked again: their
     * platform rows are the proof they granted it.
     */
    fun carryOverHealth() {
        viewModelScope.launch {
            val connected = runCatching {
                listOf(HealthMetricType.STEPS, HealthMetricType.SLEEP).any {
                    healthRepository.getLatestMetric(it)?.source == HealthMetricSource.PLATFORM
                }
            }.getOrDefault(false)
            if (connected) integrationPrefs.setHealth(true)
        }
    }

    /**
     * Ends first run for good. Also marks the v3 gates done, so switching the v4 flag off never
     * sends this user back through an intro they already passed.
     */
    fun finish(path: String) {
        planAreas.markFirstRunDone()
        IntroFlow.markComplete(settings)
        settings.putBoolean(CoachOnboardingViewModel.COACH_ONBOARDING_KEY, true)
        PostHogAnalytics.capture(
            "v4_first_run_completed",
            mapOf("path" to path, "areas" to planAreas.enabledAreas.value.joinToString(",") { it.key }),
        )
        _finished.value = true
    }

    companion object {
        fun suggestAreas(goals: List<Goal>, habits: List<Habit>, journalCount: Int): Set<PlanArea> {
            val s = mutableSetOf(PlanArea.HABITS)
            goals.forEach { s += PlanArea.forCategory(it.category) }
            habits.forEach { h ->
                when (h.healthMetricType) {
                    HealthMetricType.STEPS, HealthMetricType.WEIGHT, HealthMetricType.HEART_RATE -> s += PlanArea.FITNESS
                    HealthMetricType.SLEEP -> s += PlanArea.MIND
                    null -> {}
                }
                when (h.category) {
                    GoalCategory.BODY -> s += PlanArea.FITNESS
                    GoalCategory.MONEY -> s += PlanArea.MONEY
                    GoalCategory.WELLBEING -> s += PlanArea.MIND
                    else -> {}
                }
            }
            if (journalCount > 0) s += PlanArea.MIND
            return s
        }
    }
}
