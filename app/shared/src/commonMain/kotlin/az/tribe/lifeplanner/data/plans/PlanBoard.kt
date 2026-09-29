package az.tribe.lifeplanner.data.plans

import az.tribe.lifeplanner.data.habits.HabitService
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.enum.HealthMetricType
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.FocusRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.HealthRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.service.CatchUp
import az.tribe.lifeplanner.domain.service.Pace
import az.tribe.lifeplanner.domain.service.PlanInputs
import az.tribe.lifeplanner.domain.service.PlanProgress
import az.tribe.lifeplanner.domain.service.PlanProgressResult
import az.tribe.lifeplanner.domain.service.PlanScheduler
import az.tribe.lifeplanner.domain.service.PlanSpec
import az.tribe.lifeplanner.domain.service.PlanTrack
import az.tribe.lifeplanner.domain.service.RoutineKind
import az.tribe.lifeplanner.domain.service.StudyTime
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

enum class PlanState { ACTIVE, PAUSED, DONE, LET_GO }

/** One plan as every v4 screen shows it: its steps in date order, progress, pace and what to offer. */
data class PlanView(
    val goal: Goal,
    val spec: PlanSpec?,
    val area: PlanArea,
    val state: PlanState,
    val start: LocalDate,
    /** By date; steps with no day last. */
    val steps: List<Milestone>,
    val progress: PlanProgressResult,
    val pace: Pace,
    /** The first step not done yet. */
    val next: Milestone?,
    val catchUp: CatchUp?,
    /** For a finished plan: "You ran a 5K." and what it took. */
    val recap: Pair<String, String>? = null,
) {
    val id: String get() = goal.id
    val title: String get() = goal.title
    val target: LocalDate get() = goal.dueDate
    val track: PlanTrack get() = spec?.track ?: PlanTrack.CHECKLIST

    companion object {
        /** Everything a plan's screens need, from its goal, settings and the data it reads. */
        fun of(goal: Goal, spec: PlanSpec?, inputs: PlanInputs, today: LocalDate, unticked: Map<String, LocalDate>): PlanView {
            val state = when {
                goal.isArchived -> PlanState.LET_GO
                goal.status == GoalStatus.COMPLETED -> PlanState.DONE
                spec?.isPaused(today) == true -> PlanState.PAUSED
                else -> PlanState.ACTIVE
            }
            val steps = goal.milestones.sortedWith(compareBy({ it.dueDate == null }, { it.dueDate }))
            val progress = PlanProgress.of(goal, spec, inputs, today, unticked)
            val start = spec?.start ?: goal.createdAt.date
            val overdue = steps.filter { !it.isCompleted && it.dueDate != null && it.dueDate < today }.minOfOrNull { it.dueDate!! }
            val pace = PlanScheduler.pace(
                start, goal.dueDate, today, progress.fraction, overdue,
                paused = state == PlanState.PAUSED, done = state == PlanState.DONE, keptOn = spec?.keptOn,
                lastDayLabel = if (spec?.track == PlanTrack.RUN) "Race day" else "Last day",
            )
            return PlanView(
                goal, spec, PlanSpec.areaOf(goal, spec?.let { mapOf(goal.id to it) } ?: emptyMap()), state, start, steps, progress, pace,
                next = steps.firstOrNull { !it.isCompleted },
                catchUp = if (state == PlanState.ACTIVE) PlanScheduler.catchUp(pace, goal.dueDate, today, spec?.asked) else null,
                recap = if (state == PlanState.DONE) PlanProgress.recap(goal, spec, inputs, spec?.finished ?: today) else null,
            )
        }
    }
}

/**
 * Every plan with its progress, read once for all of v4: area pages, a plan's page, Today, Life and
 * the auto ticks. Reads the logs a year either side, study time, the routine habits' days and,
 * when asked, weight from Health.
 */
class PlanBoard(
    private val goals: GoalRepository,
    private val specs: PlanSpecs,
    private val logs: LifeLogRepository,
    private val health: HealthRepository,
    private val focus: FocusRepository,
    private val habits: HabitService,
    private val settings: Settings,
) {
    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)

    private val weights = MutableStateFlow<List<Pair<LocalDate, Double>>>(emptyList())
    private val unticked = MutableStateFlow(readUnticked())

    private val studyFocus: Flow<List<StudyTime>> = combine(focus.observeAllSessions(), specs.all) { sessions, sp ->
        sessions.filter { it.wasCompleted || it.actualMinutes > 0 }.mapNotNull { s ->
            s.completedAt?.let { StudyTime(it.date, s.actualMinutes, sp[s.goalId]?.subject) }
        }
    }

    private val extras = combine(studyFocus, habits.rows, weights, unticked) { f, rows, w, u -> Extras(f, rows.associate { it.habit.id to it.done }, w, u) }

    private data class Extras(val focus: List<StudyTime>, val habitDays: Map<String, Set<LocalDate>>, val weights: List<Pair<LocalDate, Double>>, val unticked: Map<String, LocalDate>)

    val plans: Flow<List<PlanView>> = today().let { t ->
        combine(
            goals.observeAllGoals(),
            specs.all,
            logs.observeInRange(t.minus(DatePeriod(days = 400)), t.plus(DatePeriod(days = 400))),
            extras,
        ) { gs, sp, ls, x ->
            val today = today()
            gs.map { g ->
                val spec = sp[g.id]
                val days = spec?.takeIf { it.routineKind == RoutineKind.HABIT }?.routineId?.let { x.habitDays[it] }.orEmpty()
                PlanView.of(g, spec, PlanInputs(ls, x.focus, x.weights, days), today, x.unticked)
            }
        }
    }.onStart { refreshHealth() }

    fun plan(goalId: String): Flow<PlanView?> = plans.map { list -> list.firstOrNull { it.id == goalId } }

    /** Weight from Health for the last year. Called on start and when a screen resumes. */
    suspend fun refreshHealth() {
        val today = today()
        weights.value = runCatching { health.getMetricsInRange(HealthMetricType.WEIGHT, today.minus(DatePeriod(days = 400)), today) }
            .getOrDefault(emptyList()).map { it.date to it.value }.sortedBy { it.first }
    }

    // ── Unticks ──────────────────────────────────────────────────────────────
    // A step unticked by hand only ticks itself again from data after that day. Kept per device.

    fun untick(milestoneId: String) = writeUnticked(unticked.value + (milestoneId to today()))

    fun clearUntick(milestoneId: String) {
        if (milestoneId in unticked.value) writeUnticked(unticked.value - milestoneId)
    }

    private fun readUnticked(): Map<String, LocalDate> = settings.getString(KEY_UNTICKED, "").lines().mapNotNull { line ->
        val (id, day) = line.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        runCatching { id to LocalDate.parse(day) }.getOrNull()
    }.toMap()

    private fun writeUnticked(map: Map<String, LocalDate>) {
        // Only the last few months matter: older unticks have long been overtaken by new data.
        val cutoff = today().minus(DatePeriod(days = 120))
        val keep = map.filterValues { it >= cutoff }
        settings.putString(KEY_UNTICKED, keep.entries.joinToString("\n") { "${it.key}=${it.value}" })
        unticked.value = keep
    }

    private companion object {
        const val KEY_UNTICKED = "v4_plan_unticked"
    }
}
