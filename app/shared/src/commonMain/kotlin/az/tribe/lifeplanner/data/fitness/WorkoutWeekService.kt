package az.tribe.lifeplanner.data.fitness

import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.domain.model.Budget
import az.tribe.lifeplanner.domain.model.BudgetPeriod
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.BudgetRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.service.FitnessBreak
import az.tribe.lifeplanner.domain.service.WeekSlot
import az.tribe.lifeplanner.domain.service.WorkoutNotes
import az.tribe.lifeplanner.domain.service.WorkoutWeek
import az.tribe.lifeplanner.domain.service.WorkoutWeekPlan
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The week that repeats and the breaks that pause it. Both live in budget rows (synced, no new
 * table): the week in one row whose category holds the days, each break in a row of its own.
 * [ensure] keeps the next seven days planned from the week; Today and the Fitness page both call
 * it, so the plan is there whichever opens first.
 */
@OptIn(ExperimentalUuidApi::class)
class WorkoutWeekService(
    private val logs: LifeLogRepository,
    private val budgets: BudgetRepository,
    private val workouts: WorkoutService,
    private val settings: Settings,
) {
    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)
    private val lock = Mutex()

    suspend fun currentWeek(): WorkoutWeek? = weekOf(budgets.getAll())

    /** Saves the week (an empty one switches it off), sets the weekly goal to match, and plans ahead. */
    suspend fun save(slots: List<WeekSlot>, toCalendar: Boolean) {
        val all = budgets.getAll()
        val old = weekOf(all)
        val next = WorkoutWeek(slots.distinctBy { it.day }.sortedBy { it.day.ordinal }, (old?.gen ?: 0) + 1, toCalendar)
        budgets.save(Budget(id = WEEK_ID, area = PlanArea.FITNESS, metric = METRIC_WEEK, category = next.encode(), amount = next.slots.size.toDouble(), period = BudgetPeriod.WEEK))
        if (next.slots.isNotEmpty()) {
            val target = all.firstOrNull { it.area == PlanArea.FITNESS && it.metric == Budget.METRIC_WORKOUTS }
            budgets.save(
                Budget(
                    id = target?.id ?: Uuid.random().toString(), area = PlanArea.FITNESS, metric = Budget.METRIC_WORKOUTS,
                    amount = next.slots.size.toDouble(), period = BudgetPeriod.WEEK,
                ),
            )
        }
        PostHogAnalytics.capture("v4_fitness_week_saved", mapOf("days" to next.slots.size, "calendar" to toCalendar, "first" to (old == null)))
        ensure()
    }

    /**
     * Makes the next seven days match the week: plans what is missing, clears untouched rows from an
     * older week, and sets aside (or brings back) planned days that fall in a break. Safe to call
     * often; the fixed row ids mean nothing is ever planned twice.
     */
    suspend fun ensure() = lock.withLock {
        runCatching {
            val all = budgets.getAll()
            val week = weekOf(all)
            val breaks = breaksOf(all)
            val today = today()
            val rows = logs.getInRange(today, today.plus(DatePeriod(days = 14)))

            // Deleted outright (not set aside): their week is gone, so nothing would plan them again.
            WorkoutWeekPlan.stale(week, rows, today).forEach { workouts.remove(it.copy(externalId = null)) }

            // Planned days inside a break wait; days outside one come back.
            rows.filter { WorkoutWeekPlan.isGenerated(it) && it.date >= today }.forEach { l ->
                val inBreak = breaks.any { l.date in it }
                if (inBreak && l.status == LogStatus.PLANNED) {
                    logs.save(l.copy(status = LogStatus.SKIPPED, notes = WorkoutNotes.withPaused(l.notes, true)))
                } else if (!inBreak && l.status == LogStatus.SKIPPED && WorkoutNotes.isPaused(l.notes)) {
                    logs.save(l.copy(status = LogStatus.PLANNED, notes = WorkoutNotes.withPaused(l.notes, false)))
                }
            }

            if (week == null || week.slots.isEmpty()) return@runCatching
            val now = Clock.System.now().toLocalDateTime(tz).time
            val made = madeIds()
            val fresh = WorkoutWeekPlan.wanted(week, today, now)
                .filter { w -> breaks.none { w.date in it } }
                .filter { w -> w.externalId !in made && !logs.hasExternalId(w.externalId!!) }
            if (fresh.isEmpty()) return@runCatching
            workouts.savePlanned(fresh, week.toCalendar)
            remember(fresh.mapNotNull { it.externalId }, today)
            PostHogAnalytics.capture("v4_fitness_week_planned", mapOf("rows" to fresh.size))
        }.onFailure { Logger.w("WorkoutWeekService") { "Planning the week failed: ${it.message}" } }
        Unit
    }

    // ── Breaks ───────────────────────────────────────────────────────────────

    /** Starts a break of [days] days from today, or changes the length of the one running. */
    suspend fun startBreak(days: Int) {
        val today = today()
        val row = budgets.getAll().firstOrNull { it.area == PlanArea.FITNESS && it.metric == METRIC_BREAK && FitnessBreak.decode(it.category)?.contains(today) == true }
        val current = row?.let { FitnessBreak.decode(it.category) }
        val b = FitnessBreak(current?.from ?: today, today.plus(DatePeriod(days = (days - 1).coerceAtLeast(0))))
        val id = row?.id ?: Uuid.random().toString()
        budgets.save(Budget(id = id, area = PlanArea.FITNESS, metric = METRIC_BREAK, category = b.encode(), amount = days.toDouble(), period = BudgetPeriod.WEEK))
        PostHogAnalytics.capture("v4_fitness_break", mapOf("action" to if (current == null) "start" else "change", "days" to days))
        ensure()
    }

    /** Ends the running break: it now ended yesterday, or goes entirely if it began today. */
    suspend fun endBreak() {
        val today = today()
        budgets.getAll().filter { it.area == PlanArea.FITNESS && it.metric == METRIC_BREAK }.forEach { row ->
            val b = FitnessBreak.decode(row.category) ?: return@forEach
            if (today !in b) return@forEach
            if (b.from >= today) budgets.delete(row.id)
            else budgets.save(row.copy(category = FitnessBreak(b.from, today.minus(DatePeriod(days = 1))).encode()))
        }
        PostHogAnalytics.capture("v4_fitness_break", mapOf("action" to "end"))
        ensure()
    }

    // ── Reading the rows ─────────────────────────────────────────────────────

    // Ids planned on this device, so a day the user deleted is not planned again even after the
    // deleted row is cleaned out locally. Kept for three weeks.
    private fun madeIds(): Set<String> = settings.getString(KEY_MADE, "").split('\n').filter { it.isNotBlank() }.toSet()

    private fun remember(ids: List<String>, today: LocalDate) {
        val cutoff = today.minus(DatePeriod(days = 21)).toString()
        val keep = (madeIds() + ids).filter { id -> id.removePrefix(WorkoutWeekPlan.EXT_PREFIX).split(':').getOrNull(1)?.let { it >= cutoff } ?: false }
        settings.putString(KEY_MADE, keep.joinToString("\n"))
    }

    companion object {
        fun weekOf(all: List<Budget>): WorkoutWeek? =
            all.firstOrNull { it.area == PlanArea.FITNESS && it.metric == METRIC_WEEK }?.let { WorkoutWeek.decode(it.category) }

        fun breaksOf(all: List<Budget>): List<FitnessBreak> =
            all.filter { it.area == PlanArea.FITNESS && it.metric == METRIC_BREAK }.mapNotNull { FitnessBreak.decode(it.category) }.sortedBy { it.from }

        const val METRIC_WEEK = "workout_week"
        const val METRIC_BREAK = "workout_break"
        private const val WEEK_ID = "fitness-week"
        private const val KEY_MADE = "v4_fitweek_made"
    }
}

/**
 * Today's hook: keeps the next seven days planned from the repeating week, so Today has its
 * workout even when the Fitness page was never opened. Does nothing before Koin is up.
 */
suspend fun planWorkoutWeekAhead() {
    runCatching { org.koin.mp.KoinPlatform.getKoinOrNull()?.getOrNull<WorkoutWeekService>()?.ensure() }
}
