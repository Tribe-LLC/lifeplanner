package az.tribe.lifeplanner.ui.v4.areas

import az.tribe.lifeplanner.data.plans.PlanBoard
import az.tribe.lifeplanner.data.plans.PlanState
import az.tribe.lifeplanner.data.plans.PlanView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.habits.HabitRow
import az.tribe.lifeplanner.data.habits.HabitService
import az.tribe.lifeplanner.domain.enum.HealthMetricType
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.HealthRepository
import az.tribe.lifeplanner.ui.v4.today.V4TodayViewModel
import az.tribe.lifeplanner.usecases.habit.AwardHabitCompletionUseCase
import az.tribe.lifeplanner.usecases.habit.CheckInHabitUseCase
import az.tribe.lifeplanner.usecases.habit.UncheckHabitUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Health numbers shown on the Fitness and Sleep pages. Null means no data, not zero. */
data class AreaHealth(
    val stepsToday: Double? = null,
    val stepsTarget: Double = 8_000.0,
    val restingHr: Double? = null,
    val weightKg: Double? = null,
    val sleepNights: List<Pair<kotlinx.datetime.LocalDate, Double>> = emptyList(),
    /** Steps per day for the last 7 days, oldest first; days without data are left out. */
    val stepsWeek: List<Pair<kotlinx.datetime.LocalDate, Double>> = emptyList(),
    /** Weight now minus about a month ago, when both are known. */
    val weightChangeKg: Double? = null,
) {
    val hasAny get() = stepsToday != null || restingHr != null || weightKg != null || sleepNights.isNotEmpty() || stepsWeek.isNotEmpty()
}

data class AreaUiState(
    /** Plans under way or paused, soonest date first. */
    val plans: List<PlanView> = emptyList(),
    /** Finished and let go, behind a "2 done, 1 let go" link. */
    val done: List<PlanView> = emptyList(),
    val letGo: List<PlanView> = emptyList(),
    val routines: List<HabitRow> = emptyList(),
    val health: AreaHealth = AreaHealth(),
)

/**
 * Any area's page, built from the same pieces: plans (goals and their steps) and routines
 * (habits). Areas with their own data (Money, Travel) add sections on top.
 */
class V4AreaViewModel(
    val area: PlanArea,
    habitRepository: HabitRepository,
    private val healthRepository: HealthRepository,
    private val checkInHabit: CheckInHabitUseCase,
    private val uncheckHabit: UncheckHabitUseCase,
    private val awardHabitCompletion: AwardHabitCompletionUseCase,
    habitService: HabitService,
    board: PlanBoard,
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private val health = MutableStateFlow(AreaHealth())

    val state: StateFlow<AreaUiState> = combine(
        board.plans,
        habitService.rows,
        health,
    ) { all, habits, h ->
        val mine = all.filter { it.area == area }
        AreaUiState(
            plans = mine.filter { it.state == PlanState.ACTIVE || it.state == PlanState.PAUSED }
                .sortedWith(compareBy({ it.state == PlanState.PAUSED }, { it.target })),
            done = mine.filter { it.state == PlanState.DONE }.sortedByDescending { it.spec?.finished ?: it.target },
            letGo = mine.filter { it.state == PlanState.LET_GO },
            routines = habits.filter { r -> r.habit.isActive && belongs(r.habit) },
            health = h,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AreaUiState())

    private val habitRepo = habitRepository

    init {
        if (area == PlanArea.FITNESS || area == PlanArea.MIND) viewModelScope.launch { loadHealth() }
    }

    private fun belongs(habit: Habit): Boolean {
        val onToday = V4TodayViewModel.areaOf(habit)
        // Mind keeps only its own kind (sleep, breathing): a "wellbeing" habit like reading lives on Habits.
        return onToday == area || (area != PlanArea.HABITS && area != PlanArea.MIND && PlanArea.forCategory(habit.category) == area)
    }

    private suspend fun loadHealth() {
        val today = Clock.System.todayIn(tz)
        val week = today.minus(DatePeriod(days = 6))
        fun metrics(t: HealthMetricType, from: kotlinx.datetime.LocalDate) =
            suspend { runCatching { healthRepository.getMetricsInRange(t, from, today) }.getOrDefault(emptyList()) }
        val steps = metrics(HealthMetricType.STEPS, today)().sumOf { it.value }.takeIf { it > 0 }
        val stepsTarget = habitRepo.getAllHabits().firstOrNull { it.healthMetricType == HealthMetricType.STEPS }?.healthTarget ?: 8_000.0
        val hr = metrics(HealthMetricType.HEART_RATE, week)().map { it.value }.takeIf { it.isNotEmpty() }?.average()
        val weight = runCatching { healthRepository.getLatestMetric(HealthMetricType.WEIGHT) }.getOrNull()?.value
        val sleep = metrics(HealthMetricType.SLEEP, week.minus(DatePeriod(days = 1)))().sortedByDescending { it.date }.map { it.date to it.value }
        val stepsWeek = metrics(HealthMetricType.STEPS, week)().groupBy { it.date }
            .map { (d, rows) -> d to rows.sumOf { it.value } }.filter { it.second > 0 }.sortedBy { it.first }
        val weights = metrics(HealthMetricType.WEIGHT, today.minus(DatePeriod(days = 35)))().sortedBy { it.date }
        val change = if (weights.size >= 2 && weights.last().date.toEpochDays() - weights.first().date.toEpochDays() >= 14) {
            weights.last().value - weights.first().value
        } else null
        health.value = AreaHealth(steps, stepsTarget, hr, weight, sleep, stepsWeek, change)
    }

    /** After Health is connected from the page, so the numbers show without leaving it. */
    fun refreshHealth() {
        if (area == PlanArea.FITNESS || area == PlanArea.MIND) viewModelScope.launch { loadHealth() }
    }

    fun toggleRoutine(habit: Habit, done: Boolean) {
        viewModelScope.launch {
            runCatching {
                val today = Clock.System.todayIn(tz)
                if (done) uncheckHabit(habit.id, today)
                else {
                    checkInHabit(habit.id, today)
                    awardHabitCompletion(habit.id, today)
                }
            }
        }
    }
}
