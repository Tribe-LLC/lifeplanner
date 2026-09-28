package az.tribe.lifeplanner.ui.v4.areas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.habits.HabitRow
import az.tribe.lifeplanner.data.habits.HabitService
import az.tribe.lifeplanner.domain.enum.HabitCompletionSource
import az.tribe.lifeplanner.domain.enum.HabitType
import az.tribe.lifeplanner.domain.enum.HealthMetricType
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.service.FitnessWeek
import az.tribe.lifeplanner.domain.service.HabitSchedule
import az.tribe.lifeplanner.domain.service.Schedule
import az.tribe.lifeplanner.usecases.habit.AwardHabitCompletionUseCase
import az.tribe.lifeplanner.usecases.habit.CheckInHabitUseCase
import az.tribe.lifeplanner.usecases.habit.UncheckHabitUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** A day in a history grid: null for days not yet here. */
data class HeatDay(val date: LocalDate, val level: Float?)

data class HabitsState(
    val today: List<Pair<HabitSchedule.Slot, List<HabitRow>>> = emptyList(),
    val doneToday: Int = 0,
    val dueToday: Int = 0,
    val notToday: List<Pair<HabitRow, String>> = emptyList(),
    val slipped: HabitRow? = null,
    val heat: List<HeatDay> = emptyList(),
    val keptShare: Float? = null,
    val all: List<HabitRow> = emptyList(),
    val loaded: Boolean = false,
)

/** A ready-made habit to start from, so the first one takes one tap. */
data class Starter(
    val title: String,
    val type: HabitType = HabitType.BUILD,
    val schedule: Schedule = Schedule.Daily,
    val target: Int = 1,
    val unit: String? = null,
    val reminder: LocalTime? = null,
    val health: HealthMetricType? = null,
    val healthTarget: Double? = null,
    val source: HabitCompletionSource = HabitCompletionSource.MANUAL,
)

class V4HabitsViewModel(
    private val service: HabitService,
    private val habits: HabitRepository,
    private val checkIn: CheckInHabitUseCase,
    private val uncheck: UncheckHabitUseCase,
    private val award: AwardHabitCompletionUseCase,
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)

    val state: StateFlow<HabitsState> = service.rows.map { rows -> build(rows, today()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsState())

    fun toggle(row: HabitRow) = viewModelScope.launch {
        runCatching {
            val today = today()
            if (row.doneToday) uncheck(row.habit.id, today)
            else {
                checkIn(row.habit.id, today)
                award(row.habit.id, today)
            }
            PostHogAnalytics.capture("v4_habit_ticked", mapOf("done" to !row.doneToday))
        }
    }

    fun plusOne(row: HabitRow) = viewModelScope.launch {
        runCatching {
            val c = habits.addCount(row.habit.id, today(), 1)
            if (c.completed && !row.doneToday) award(row.habit.id, today())
        }
    }

    fun setDay(row: HabitRow, date: LocalDate, done: Boolean) = viewModelScope.launch { runCatching { service.setDone(row.habit, date, done) } }
    fun toggleSkip(row: HabitRow) = viewModelScope.launch { runCatching { service.toggleSkip(row.habit) } }
    fun takeBreak(row: HabitRow, days: Int) = viewModelScope.launch { runCatching { service.takeBreak(row.habit, days) } }
    fun endBreak(row: HabitRow) = viewModelScope.launch { runCatching { service.endBreak(row.habit) } }
    fun addNote(row: HabitRow, text: String) = viewModelScope.launch { runCatching { service.addNote(row.habit, text) } }
    fun stop(row: HabitRow) = viewModelScope.launch { runCatching { service.stop(row.habit) } }

    fun save(row: HabitRow, title: String, schedule: Schedule, target: Int, unit: String?, reminder: LocalTime?) = viewModelScope.launch {
        runCatching { service.update(row.habit, title, schedule, target, unit, reminder) }
    }

    fun create(s: Starter) = viewModelScope.launch {
        runCatching {
            service.create(s.title, s.type, s.schedule, s.target, s.unit, s.reminder, s.health, s.healthTarget, s.source)
            PostHogAnalytics.capture("v4_habit_created", mapOf("type" to s.type.name, "schedule" to HabitSchedule.describe(s.schedule)))
        }
    }

    companion object {
        val STARTERS = listOf(
            Starter("Drink water", target = 8, unit = "glasses"),
            Starter("Read 10 pages", reminder = LocalTime(21, 30)),
            Starter("Stretch, 10 min", reminder = LocalTime(7, 30)),
            Starter("Walk 8,000 steps", health = HealthMetricType.STEPS, healthTarget = 8_000.0),
            Starter("Breathe, 1 min", source = HabitCompletionSource.BREATHING),
            Starter("Phone out of the bedroom", type = HabitType.QUIT, reminder = LocalTime(22, 30)),
            Starter("Plan the week", schedule = Schedule.Days(setOf(kotlinx.datetime.DayOfWeek.SUNDAY)), reminder = LocalTime(19, 0)),
        )

        /** Weeks shown in the history grids. */
        const val WEEKS = 12

        fun build(rows: List<HabitRow>, today: LocalDate): HabitsState {
            val due = rows.filter { it.stats.dueToday }
            val grouped = due.groupBy { HabitSchedule.slot(it.habit.reminderTime) }
                .toList().sortedBy { it.first.ordinal }
                .map { (slot, list) -> slot to list.sortedWith(compareBy({ it.doneToday }, { it.habit.reminderTime ?: "99" }, { it.habit.title })) }
            val notToday = rows.filter { !it.stats.dueToday }.map { r -> r to whyNotToday(r, today) }
            val yesterday = today.minus(DatePeriod(days = 1))
            val slipped = rows.firstOrNull { r ->
                r.schedule !is Schedule.PerWeek && HabitSchedule.isScheduled(HabitSchedule.normal(r.schedule), yesterday) &&
                    yesterday !in r.done && yesterday !in r.skipped && r.habit.createdAt.date < yesterday &&
                    r.habit.type == HabitType.BUILD && r.habit.healthMetricType == null
            }
            val (heat, share) = heat(rows, today)
            return HabitsState(
                today = grouped,
                doneToday = due.count { it.doneToday },
                dueToday = due.size,
                notToday = notToday,
                slipped = slipped,
                heat = heat,
                keptShare = share,
                all = rows,
                loaded = true,
            )
        }

        private fun whyNotToday(r: HabitRow, today: LocalDate): String {
            val s = HabitSchedule.normal(r.schedule)
            return when {
                r.stats.skippedToday -> {
                    val back = (1..60).map { today.plus(DatePeriod(days = it)) }.firstOrNull { it !in r.skipped }
                    if (back != null && back > today.plus(DatePeriod(days = 1))) "On a break until ${FitnessWeek.shortDay(back.dayOfWeek)} ${back.day}" else "Skipped today"
                }
                s is Schedule.PerWeek -> "${r.stats.doneThisWeek} of ${s.times} this week, done"
                else -> HabitSchedule.describe(s) + (r.stats.next?.let { ". Next on ${nextDay(it, today)}" } ?: "")
            }
        }

        private fun nextDay(d: LocalDate, today: LocalDate): String =
            if (d == today.plus(DatePeriod(days = 1))) "tomorrow" else FitnessWeek.dayName(d.dayOfWeek)

        /** The last [WEEKS] weeks, Monday first: share of due habits kept each day. */
        fun heat(rows: List<HabitRow>, today: LocalDate): Pair<List<HeatDay>, Float?> {
            val start = HabitSchedule.weekStart(today).minus(DatePeriod(days = 7 * (WEEKS - 1)))
            var due = 0
            var kept = 0
            val days = (0 until WEEKS * 7).map { i ->
                val d = start.plus(DatePeriod(days = i))
                if (d > today) return@map HeatDay(d, null)
                var dayDue = 0
                var dayKept = 0
                rows.forEach { r ->
                    if (d < r.habit.createdAt.date || d in r.skipped) return@forEach
                    val s = HabitSchedule.normal(r.schedule)
                    val isDue = s !is Schedule.PerWeek && HabitSchedule.isScheduled(s, d) && !(d == today && d !in r.done)
                    if (isDue || d in r.done) { dayDue++; if (d in r.done) dayKept++ }
                }
                due += dayDue
                kept += dayKept
                HeatDay(d, if (dayDue == 0) -1f else dayKept.toFloat() / dayDue)
            }
            return days to (if (due == 0) null else kept.toFloat() / due)
        }

        /** One habit's grid: 1 done, 0.5 skipped, 0 missed, -1 off its schedule. */
        fun habitHeat(r: HabitRow, today: LocalDate): List<HeatDay> {
            val start = HabitSchedule.weekStart(today).minus(DatePeriod(days = 7 * (WEEKS - 1)))
            val s = HabitSchedule.normal(r.schedule)
            return (0 until WEEKS * 7).map { i ->
                val d = start.plus(DatePeriod(days = i))
                HeatDay(
                    d,
                    when {
                        d > today -> null
                        d in r.done -> 1f
                        d in r.skipped -> 0.5f
                        d < r.habit.createdAt.date || !HabitSchedule.isScheduled(s, d) || s is Schedule.PerWeek -> -1f
                        else -> 0f
                    },
                )
            }
        }
    }
}
