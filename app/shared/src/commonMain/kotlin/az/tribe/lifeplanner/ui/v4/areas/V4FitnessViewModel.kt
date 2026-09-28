package az.tribe.lifeplanner.ui.v4.areas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.calendar.CalendarPreferences
import az.tribe.lifeplanner.data.calendar.CalendarReader
import az.tribe.lifeplanner.data.fitness.ActiveWorkout
import az.tribe.lifeplanner.data.fitness.WorkoutService
import az.tribe.lifeplanner.data.health.WorkoutKind
import az.tribe.lifeplanner.data.integrations.IntegrationPrefs
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.enum.HealthMetricType
import az.tribe.lifeplanner.domain.model.Budget
import az.tribe.lifeplanner.domain.model.BudgetPeriod
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.BudgetRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.HealthRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.repository.PlanAreasRepository
import az.tribe.lifeplanner.domain.service.DayRing
import az.tribe.lifeplanner.domain.service.FitnessStreak
import az.tribe.lifeplanner.domain.service.FitnessStreakState
import az.tribe.lifeplanner.domain.service.FitnessWeek
import az.tribe.lifeplanner.domain.service.WeekSlot
import az.tribe.lifeplanner.domain.service.WorkoutNotes
import az.tribe.lifeplanner.domain.service.WorkoutWeek
import az.tribe.lifeplanner.domain.service.WorkoutWeekPlan
import az.tribe.lifeplanner.data.fitness.WorkoutWeekService
import az.tribe.lifeplanner.ui.v4.today.V4TodayViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A line under "Works with your other areas". */
data class FitnessLink(val area: PlanArea, val label: String, val text: String)

/** One row of "Coming up": a workout planned here, or a workout-looking calendar event. */
data class ComingUp(val key: String, val at: kotlinx.datetime.LocalDateTime, val day: String, val time: String, val title: String, val meta: String, val log: LifeLog?)

data class FitnessState(
    val today: LifeLog? = null,
    /** The last time the hero's workout was done with a note of what was done. */
    val lastTime: LifeLog? = null,
    val week: WorkoutWeek? = null,
    val weekSummary: List<String> = emptyList(),
    val streak: FitnessStreakState? = null,
    val todayDone: Boolean = false,
    val rings: List<DayRing> = emptyList(),
    val doneThisWeek: Int = 0,
    val target: Int = 3,
    val lastWatch: LifeLog? = null,
    val links: List<FitnessLink> = emptyList(),
    val comingUp: List<ComingUp> = emptyList(),
    val canWriteHealth: Boolean = false,
    val canCalendar: Boolean = false,
)

@OptIn(ExperimentalUuidApi::class)
class V4FitnessViewModel(
    private val workouts: WorkoutService,
    private val logs: LifeLogRepository,
    private val budgets: BudgetRepository,
    private val goals: GoalRepository,
    private val healthRepository: HealthRepository,
    private val calendarReader: CalendarReader,
    private val calendarPreferences: CalendarPreferences,
    private val integrationPrefs: IntegrationPrefs,
    private val planAreas: PlanAreasRepository,
    private val weekService: WorkoutWeekService,
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)

    val active: StateFlow<ActiveWorkout?> = workouts.active

    private data class Extras(
        val sleepHours: Double? = null,
        val calendarWorkouts: List<ComingUp> = emptyList(),
        val canWriteHealth: Boolean = false,
        val canCalendar: Boolean = false,
    )

    private val extras = MutableStateFlow(Extras())

    val state: StateFlow<FitnessState> = combine(
        // A year back: the weeks-in-a-row count and "Last time" both look further than a week.
        logs.observeInRange(today().minus(DatePeriod(days = 371)), today().plus(DatePeriod(days = 14))),
        budgets.observeAll(),
        goals.observeAllGoals(),
        planAreas.enabledAreas,
        extras,
    ) { all, bs, gs, _, ex ->
        val today = today()
        val w = all.filter { FitnessWeek.isWorkout(it) }
        val week = WorkoutWeekService.weekOf(bs)
        val todays = w.filter { it.date == today && it.status != LogStatus.SKIPPED && it.source == LifeLog.SOURCE_PLAN }
            .sortedBy { it.occurredAt }
        val hero = todays.firstOrNull { it.status == LogStatus.PLANNED } ?: todays.firstOrNull()
        val target = bs.firstOrNull { it.area == PlanArea.FITNESS && it.metric == Budget.METRIC_WORKOUTS }?.amount?.toInt() ?: 3

        val plannedLogs = w.filter { it.status == LogStatus.PLANNED && it.id != hero?.id && it.date >= today }
        val planned = plannedLogs.map { l ->
                ComingUp(
                    key = l.id,
                    at = l.occurredAt,
                    day = if (l.date == today) "Today" else FitnessWeek.shortDay(l.date.dayOfWeek),
                    time = if (WorkoutService.hasTime(l)) fmt(l.occurredAt.time) else "Any",
                    title = l.title + (l.durationMin?.let { ", $it min" } ?: ""),
                    meta = WorkoutNotes.display(l.notes) ?: if (WorkoutWeekPlan.isGenerated(l)) "From your week" else "Planned here",
                    log = l,
                )
            }
        // Workouts we put on the calendar come back from it too; list those once, as ours.
        val fromCalendar = ex.calendarWorkouts.filter { e -> (plannedLogs + listOfNotNull(hero)).none { it.occurredAt == e.at && it.title == e.title } }
        val comingUp = (planned + fromCalendar).sortedBy { it.at }.take(6)

        val links = buildList {
            ex.sleepHours?.let { h ->
                add(FitnessLink(PlanArea.MIND, "Sleep and mind", "${V4TodayViewModel.formatHours(h)} last night" + if (h < 6) ", so go easy today." else ". Good for a harder session."))
            }
            gs.firstOrNull { !it.isArchived && it.status != GoalStatus.COMPLETED && PlanArea.forCategory(it.category) == PlanArea.FITNESS }?.let { g ->
                add(FitnessLink(PlanArea.HABITS, "Plan", "${g.title}. ${g.progress ?: 0}% there."))
            }
        }

        FitnessState(
            today = hero,
            lastTime = hero?.takeIf { it.status == LogStatus.PLANNED }?.let { WorkoutNotes.lastTime(w, it) },
            week = week,
            weekSummary = WorkoutWeekPlan.summary(week),
            streak = FitnessStreak.of(w, target, today, WorkoutWeekService.breaksOf(bs)),
            todayDone = hero?.status == LogStatus.DONE,
            rings = FitnessWeek.rings(all, today),
            doneThisWeek = FitnessWeek.doneInLastWeek(all, today),
            target = target,
            lastWatch = w.filter { it.source == LifeLog.SOURCE_HEALTH && it.status == LogStatus.DONE }.maxByOrNull { it.occurredAt },
            links = links,
            comingUp = comingUp,
            canWriteHealth = ex.canWriteHealth,
            canCalendar = ex.canCalendar,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FitnessState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            weekService.ensure()
            workouts.importFromHealth()
            val today = today()
            val sleep = runCatching { healthRepository.getMetricsInRange(HealthMetricType.SLEEP, today.minus(DatePeriod(days = 1)), today) }
                .getOrDefault(emptyList()).maxByOrNull { it.date }?.value?.takeIf { it > 0 }
            extras.value = Extras(
                sleepHours = sleep,
                calendarWorkouts = if (integrationPrefs.state.value.calendar) readCalendarWorkouts() else emptyList(),
                canWriteHealth = workouts.canWriteHealth(),
                canCalendar = workouts.canAddToCalendar(),
            )
        }
    }

    private suspend fun readCalendarWorkouts(): List<ComingUp> {
        val start = Clock.System.now()
        val end = today().plus(DatePeriod(days = 8)).atStartOfDayIn(tz)
        return runCatching { calendarReader.readEvents(start.toEpochMilliseconds(), end.toEpochMilliseconds()) }.getOrDefault(emptyList())
            .filter { e -> !e.allDay && (e.calendarId?.let { calendarPreferences.isEnabled(it) } ?: true) }
            // Ours are already listed from the plan; the calendar copy says "Planned in LifePlanner".
            .filter { e -> WorkoutKind.fromTitle(e.title) != WorkoutKind.OTHER || "workout" in e.title.lowercase() }
            .map { e ->
                val at = Instant.fromEpochMilliseconds(e.startEpochMillis).toLocalDateTime(tz)
                ComingUp(
                    key = "cal_${e.id}",
                    at = at,
                    day = if (at.date == today()) "Today" else FitnessWeek.shortDay(at.date.dayOfWeek),
                    time = fmt(at.time),
                    title = e.title,
                    meta = "In your calendar",
                    log = null,
                )
            }
    }

    fun start(kind: WorkoutKind, title: String, plannedId: String? = null) = workouts.start(kind, title, plannedId)

    fun stop(onSaved: (logId: String, toHealth: Boolean) -> Unit) {
        viewModelScope.launch { workouts.stop()?.let { onSaved(it.log.id, it.toHealth) } }
    }

    /** "What did you do?" after a workout. Blank saves nothing. */
    fun setDid(logId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { workouts.setDid(logId, text) }
    }

    fun saveWeek(slots: List<WeekSlot>, toCalendar: Boolean) {
        viewModelScope.launch {
            weekService.save(slots, toCalendar)
            refresh()
        }
    }

    fun startBreak(days: Int) {
        viewModelScope.launch { weekService.startBreak(days) }
    }

    fun endBreak() {
        viewModelScope.launch { weekService.endBreak() }
    }

    fun moveToNextFreeDay(log: LifeLog) {
        viewModelScope.launch { workouts.moveToNextFreeDay(log) }
    }

    fun cancel() = workouts.cancel()

    /** Ticks today's workout, or un-ticks it. [onDone] runs after a tick, to ask what was done. */
    fun toggleToday(log: LifeLog, onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            if (log.status == LogStatus.DONE) workouts.undoDone(log) else { workouts.markDone(log); onDone(log.id) }
        }
    }

    fun plan(title: String, date: LocalDate, time: LocalTime?, minutes: Int, toCalendar: Boolean) {
        viewModelScope.launch {
            workouts.plan(title, date, time, minutes, toCalendar)
            refresh()
        }
    }

    fun moveToToday(log: LifeLog) {
        viewModelScope.launch { workouts.moveTo(log, today(), null) }
    }

    fun remove(log: LifeLog) {
        viewModelScope.launch { workouts.remove(log) }
    }

    fun setTarget(perWeek: Int) {
        viewModelScope.launch {
            val existing = budgets.getAll().firstOrNull { it.area == PlanArea.FITNESS && it.metric == Budget.METRIC_WORKOUTS }
            budgets.save(
                Budget(
                    id = existing?.id ?: Uuid.random().toString(),
                    area = PlanArea.FITNESS,
                    metric = Budget.METRIC_WORKOUTS,
                    amount = perWeek.toDouble(),
                    period = BudgetPeriod.WEEK,
                ),
            )
        }
    }

    companion object {
        fun fmt(t: LocalTime) = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
    }
}
