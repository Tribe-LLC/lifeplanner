package az.tribe.lifeplanner.ui.v4.life

import az.tribe.lifeplanner.domain.repository.TripRepository
import az.tribe.lifeplanner.domain.service.TripPlanner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.domain.enum.HealthMetricType
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.HabitCheckIn
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.FocusRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.HealthRepository
import az.tribe.lifeplanner.domain.repository.JournalRepository
import az.tribe.lifeplanner.domain.repository.PlanAreasRepository
import az.tribe.lifeplanner.domain.repository.BudgetRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.data.habits.HabitRow
import az.tribe.lifeplanner.data.habits.HabitService
import az.tribe.lifeplanner.domain.service.CareerKind
import az.tribe.lifeplanner.domain.service.CareerPlanner
import az.tribe.lifeplanner.domain.service.HabitSchedule
import az.tribe.lifeplanner.domain.service.MindCheckIns
import az.tribe.lifeplanner.domain.service.Schedule
import az.tribe.lifeplanner.domain.service.Stage
import kotlinx.coroutines.flow.first
import az.tribe.lifeplanner.domain.service.MoneySummary
import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.ui.v4.today.V4TodayViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.math.roundToInt
import kotlin.time.Clock

enum class LifeRange { WEEK, MONTH }

data class LifeBar(val label: String, val fraction: Float?, val current: Boolean)

data class AreaSummary(
    val area: PlanArea,
    val stat: String,
    val caption: String,
    val trend: List<Float>,
)

data class RecentItem(val area: PlanArea, val title: String, val meta: String, val whenLabel: String, val sortKey: LocalDateTime)

data class LifeUiState(
    val range: LifeRange = LifeRange.WEEK,
    val score: String = "",
    val scoreCaption: String = "",
    val delta: String? = null,
    val deltaUp: Boolean = true,
    val bars: List<LifeBar> = emptyList(),
    val areas: List<AreaSummary> = emptyList(),
    val recent: List<RecentItem> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * The Life tab: one score for "how much of what you planned got done", then one card per area.
 * Everything is computed from what is already stored; nothing here writes.
 */
class V4LifeViewModel(
    private val habitRepository: HabitRepository,
    private val goalRepository: GoalRepository,
    private val healthRepository: HealthRepository,
    private val journalRepository: JournalRepository,
    private val focusRepository: FocusRepository,
    private val planAreas: PlanAreasRepository,
    private val lifeLogs: LifeLogRepository,
    private val budgets: BudgetRepository,
    private val trips: TripRepository,
    private val habitService: HabitService,
    private val fx: az.tribe.lifeplanner.data.money.FxRates,
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private val range = MutableStateFlow(LifeRange.WEEK)
    private val _state = MutableStateFlow(LifeUiState())
    val state: StateFlow<LifeUiState> = _state.asStateFlow()

    init {
        combine(
            range,
            planAreas.enabledAreas,
            habitRepository.observeHabitsWithTodayStatus(),
            goalRepository.observeAllGoals(),
            journalRepository.observeAllEntries(),
        ) { r, areas, _, goals, _ -> Triple(r, areas, goals) }
            .onEach { (r, areas, goals) -> _state.value = compute(r, areas, goals) }
            .launchIn(viewModelScope)
    }

    fun setRange(r: LifeRange) { range.value = r }

    fun refresh() {
        viewModelScope.launch { _state.value = compute(range.value, planAreas.enabledAreas.value, goalRepository.getAllGoals()) }
    }

    private suspend fun compute(r: LifeRange, areas: Set<PlanArea>, goals: List<Goal>): LifeUiState {
        val today = Clock.System.todayIn(tz)
        val habits = runCatching { habitRepository.getAllHabits() }.getOrDefault(emptyList()).filter { it.isActive }
        val from = today.minus(DatePeriod(days = 69))
        val checkIns = runCatching { habitRepository.getAllCheckInsInRange(from, today) }.getOrDefault(emptyList())
            .filter { it.completed }
        val daily = DailyHabits(runCatching { habitService.rows.first() }.getOrDefault(emptyList()), today)

        val weekStart = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
        val (score, caption, delta, bars) = when (r) {
            LifeRange.WEEK -> {
                val thisWeek = daily.rate(weekStart, today)
                val lastWeek = daily.rate(weekStart.minus(DatePeriod(days = 7)), weekStart.minus(DatePeriod(days = 1)))
                val labels = listOf("M", "T", "W", "T", "F", "S", "S")
                val bars = (0 until 7).map { i ->
                    val d = weekStart.plus(DatePeriod(days = i))
                    LifeBar(labels[i], if (d > today) null else daily.rate(d, d) ?: 0f, d == today)
                }
                Quad(thisWeek, "of your habits done this week", deltaText(thisWeek, lastWeek, "last week"), bars)
            }
            LifeRange.MONTH -> {
                val start = today.minus(DatePeriod(days = 29))
                val cur = daily.rate(start, today)
                val prev = daily.rate(start.minus(DatePeriod(days = 30)), start.minus(DatePeriod(days = 1)))
                val bars = (6 downTo 0).map { back ->
                    val s = weekStart.minus(DatePeriod(days = 7 * back))
                    val e = minOf(s.plus(DatePeriod(days = 6)), today)
                    LifeBar(if (back == 0) "Now" else "W${7 - back}", daily.rate(s, e) ?: 0f, back == 0)
                }
                Quad(cur, "of your habits done in the last 30 days", deltaText(cur, prev, "the 30 before"), bars)
            }
        }

        val summaries = PlanArea.entries.filter { it in areas }.map { area -> summarize(area, today, daily, goals) }
        val recent = recent(today, habits, checkIns)

        // Early on a Monday nothing is over yet: show today's count instead of a 0% that is not earned.
        val fresh = score == null && daily.dueToday > 0
        return LifeUiState(
            range = r,
            score = if (fresh) "${daily.doneToday} of ${daily.dueToday}" else "${((score ?: 0f) * 100).roundToInt()}%",
            scoreCaption = when {
                fresh -> "habits done today. The rest of the week fills in as the days end"
                score == null -> "Add a habit from Today and your week fills in here"
                else -> caption
            },
            delta = delta?.first,
            deltaUp = delta?.second ?: true,
            bars = bars,
            areas = summaries,
            recent = recent,
            loaded = true,
        )
    }

    private data class Quad(val score: Float?, val caption: String, val delta: Pair<String, Boolean>?, val bars: List<LifeBar>)

    private fun deltaText(now: Float?, before: Float?, vs: String): Pair<String, Boolean>? {
        if (now == null || before == null) return null
        val d = ((now - before) * 100).roundToInt()
        return (if (d >= 0) "+$d vs $vs" else "$d vs $vs") to (d >= 0)
    }

    private suspend fun summarize(area: PlanArea, today: LocalDate, daily: DailyHabits, goals: List<Goal>): AreaSummary {
        val week = (6 downTo 0).map { today.minus(DatePeriod(days = it)) }
        val areaGoals = goals.filter { !it.isArchived && PlanArea.forCategory(it.category) == area }
        val openGoals = areaGoals.filter { it.status != az.tribe.lifeplanner.domain.enum.GoalStatus.COMPLETED }
        fun plansFallback(empty: String, emptyCaption: String): AreaSummary {
            if (openGoals.isEmpty()) return AreaSummary(area, empty, emptyCaption, emptyList())
            val next = openGoals.flatMap { g -> g.milestones.filter { !it.isCompleted } }.firstOrNull()
            return AreaSummary(
                area,
                "${openGoals.size} ${if (openGoals.size == 1) "plan" else "plans"}",
                next?.let { "Next: ${it.title}" } ?: "All steps done",
                openGoals.map { (it.progress ?: 0L).toFloat() },
            )
        }
        return when (area) {
            PlanArea.HABITS -> {
                val rate = daily.rate(week.first(), today)
                AreaSummary(
                    area,
                    rate?.let { "${(it * 100).roundToInt()}%" } ?: if (daily.dueToday > 0) "${daily.doneToday} of ${daily.dueToday}" else "None yet",
                    when {
                        rate != null -> "of habit days kept"
                        daily.dueToday > 0 -> "done today"
                        else -> "Add your first from Today"
                    },
                    week.map { daily.rate(it, it) ?: 0f },
                )
            }
            PlanArea.FITNESS -> {
                val steps = runCatching { healthRepository.getMetricsInRange(HealthMetricType.STEPS, week.first(), today) }.getOrDefault(emptyList())
                val workouts = runCatching { lifeLogs.getInRange(week.first(), today) }.getOrDefault(emptyList())
                    .filter { it.kind == LogKind.WORKOUT && it.status == LogStatus.DONE }
                if (steps.isEmpty() && workouts.isNotEmpty()) {
                    val minutes = workouts.sumOf { it.durationMin ?: 0 }
                    AreaSummary(
                        area,
                        "${workouts.size} ${if (workouts.size == 1) "workout" else "workouts"}",
                        "this week" + (if (minutes > 0) ", $minutes min" else ""),
                        week.map { d -> workouts.count { it.date == d }.toFloat() },
                    )
                } else if (steps.isEmpty()) plansFallback("Connect Health", "Steps and workouts show up here")
                else {
                    val byDay = steps.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.value } }
                    AreaSummary(
                        area,
                        V4TodayViewModel.formatThousands(byDay.values.sum().toLong()),
                        "steps this week" + (openGoals.firstOrNull()?.let { ", ${it.title}" } ?: ""),
                        week.map { (byDay[it] ?: 0.0).toFloat() },
                    )
                }
            }
            PlanArea.MIND -> {
                val sleep = runCatching { healthRepository.getMetricsInRange(HealthMetricType.SLEEP, week.first().minus(DatePeriod(days = 1)), today) }.getOrDefault(emptyList())
                val checkIns = runCatching { lifeLogs.getInRange(week.first(), today) }.getOrDefault(emptyList()).filter { MindCheckIns.isCheckIn(it) }
                val moods = runCatching { journalRepository.getEntriesInRange(week.first(), today) }.getOrDefault(emptyList()).map { it.mood.score } +
                    checkIns.mapNotNull { MindCheckIns.score(it) }
                val moodText = when {
                    moods.isEmpty() -> null
                    moods.average() >= 3.8 -> "mood mostly good"
                    moods.average() >= 2.8 -> "mood steady"
                    else -> "mood low lately"
                }
                if (sleep.isEmpty()) AreaSummary(
                    area,
                    if (moods.isEmpty()) "Check in" else "${moods.size} ${if (moods.size == 1) "check-in" else "check-ins"}",
                    moodText?.let { "this week, $it" } ?: "How you feel, sleep from Health",
                    moods.map { it.toFloat() },
                ) else AreaSummary(
                    area,
                    V4TodayViewModel.formatHours(sleep.map { it.value }.average()),
                    "average sleep" + (moodText?.let { ", $it" } ?: ""),
                    sleep.sortedBy { it.date }.map { it.value.toFloat() },
                )
            }
            PlanArea.STUDY -> {
                val focus = runCatching { focusRepository.getCompletedSessions() }.getOrDefault(emptyList())
                    .mapNotNull { s -> s.completedAt?.let { az.tribe.lifeplanner.domain.service.StudyTime(it.date, s.actualMinutes, null) } }
                val rows = runCatching { lifeLogs.getInRange(week.first(), today.plus(DatePeriod(days = 60))) }.getOrDefault(emptyList())
                val times = az.tribe.lifeplanner.domain.service.StudyPlanner.times(rows, focus).filter { it.date >= week.first() && it.date <= today }
                val next = az.tribe.lifeplanner.domain.service.StudyPlanner.upcoming(rows, today).firstOrNull { it.status != az.tribe.lifeplanner.domain.model.LogStatus.DONE }
                val nextText = next?.let { e ->
                    "${az.tribe.lifeplanner.domain.service.StudyPlanner.dueLine(e)} ${az.tribe.lifeplanner.domain.service.StudyPlanner.countdown(e.date, today)}"
                }
                if (times.isEmpty()) plansFallback(if (nextText != null) "Study for it" else "Plan study", nextText ?: "Exams, deadlines and study time")
                else {
                    val minutes = times.sumOf { it.minutes }
                    AreaSummary(
                        area,
                        az.tribe.lifeplanner.domain.service.StudyPlanner.formatMinutes(minutes),
                        "studied this week" + (nextText?.let { ", $it" } ?: ""),
                        week.map { d -> times.filter { it.date == d }.sumOf { it.minutes }.toFloat() },
                    )
                }
            }
            PlanArea.MONEY -> {
                val logs = runCatching { lifeLogs.getInRange(today.minus(DatePeriod(days = 40)), today) }.getOrDefault(emptyList())
                val spends = logs.filter { MoneySummary.isSpend(it) }
                val budget = MoneySummary.primary(runCatching { budgets.getAll() }.getOrDefault(emptyList()))
                val rates = fx.table.value
                val home = budget?.currency ?: spends.firstOrNull()?.currency
                fun sum(l: List<az.tribe.lifeplanner.domain.model.LifeLog>) = az.tribe.lifeplanner.domain.service.Fx.total(l, home, rates).amount
                val trend = run {
                    var running = 0.0
                    week.map { d -> running += sum(spends.filter { it.date == d }); running.toFloat() }
                }
                when {
                    budget != null -> {
                        val st = MoneySummary.status(budget, logs, today, rates)
                        AreaSummary(
                            area,
                            MoneyFormat.format(st.spent, budget.currency),
                            "of ${MoneyFormat.format(budget.amount, budget.currency)}${budget.category?.let { " $it" } ?: ""} budget, ${st.daysLeft} ${if (st.daysLeft == 1) "day" else "days"} left",
                            trend,
                        )
                    }
                    spends.isNotEmpty() -> AreaSummary(
                        area,
                        MoneyFormat.format(sum(spends.filter { it.date >= week.first() }), home),
                        "spent this week. Set a budget to see what is left",
                        trend,
                    )
                    else -> plansFallback("Set a budget", "Know what is left to spend this week")
                }
            }
            PlanArea.TRAVEL -> {
                val t = TripPlanner.current(runCatching { trips.getAll() }.getOrDefault(emptyList()), today)
                if (t == null) plansFallback("Plan a trip", "Budget, packing and days in one place")
                else {
                    val spent = runCatching { lifeLogs.getInRange(today.minus(DatePeriod(days = 180)), t.endDate) }.getOrDefault(emptyList())
                        .filter { it.tripId == t.id && MoneySummary.isSpend(it) }
                        .let { az.tribe.lifeplanner.domain.service.Fx.total(it, t.currency, fx.table.value).amount }
                    AreaSummary(
                        area,
                        t.destination,
                        TripPlanner.countdown(t, today).replaceFirstChar { it.uppercase() } +
                            (t.budget?.let { ", ${MoneyFormat.format(it - spent, t.currency)} left" } ?: ""),
                        emptyList(),
                    )
                }
            }
            PlanArea.MEALS -> {
                val meals = runCatching { lifeLogs.getInRange(week.first(), today) }.getOrDefault(emptyList())
                    .filter { it.kind == LogKind.MEAL && it.status == az.tribe.lifeplanner.domain.model.LogStatus.DONE }
                if (meals.isEmpty()) plansFallback("Plan meals", "A week of dinners, a shopping list, water")
                else AreaSummary(
                    area,
                    "${meals.size} ${if (meals.size == 1) "meal" else "meals"}",
                    "logged this week",
                    week.map { d -> meals.count { it.date == d }.toFloat() },
                )
            }
            PlanArea.CAREER -> {
                val rows = runCatching { lifeLogs.getInRange(today.minus(DatePeriod(days = 400)), today.plus(DatePeriod(days = 400))) }.getOrDefault(emptyList())
                    .filter { CareerKind.of(it) != null }
                val active = rows.filter { CareerPlanner.isActive(it) }
                val wins = CareerPlanner.winsInQuarter(rows, today)
                val due = CareerPlanner.actions(rows, today, horizonDays = 0).count { it.due <= today }
                when {
                    active.isNotEmpty() -> AreaSummary(
                        area,
                        "${active.size} ${if (active.size == 1) "application" else "applications"}",
                        listOfNotNull(
                            active.count { CareerPlanner.stage(it) == Stage.INTERVIEW }.takeIf { it > 0 }?.let { "$it at interview" },
                            due.takeIf { it > 0 }?.let { "$it to do today" },
                        ).joinToString(", ").ifEmpty { "nothing due today" },
                        week.map { d -> rows.count { it.date == d && CareerKind.of(it) == CareerKind.WIN }.toFloat() },
                    )
                    wins.isNotEmpty() -> AreaSummary(
                        area,
                        "${wins.size} ${if (wins.size == 1) "win" else "wins"}",
                        "this quarter" + (openGoals.firstOrNull()?.let { ", ${it.title}" } ?: ""),
                        week.map { d -> wins.count { it.date == d }.toFloat() },
                    )
                    else -> plansFallback("Log a win", "Wins, skills and applications")
                }
            }
        }
    }

    private suspend fun recent(today: LocalDate, habits: List<Habit>, checkIns: List<HabitCheckIn>): List<RecentItem> {
        val yesterday = today.minus(DatePeriod(days = 1))
        fun label(d: LocalDate) = when (d) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> "${d.day}/${d.month.ordinal + 1}"
        }
        val out = mutableListOf<RecentItem>()
        val byId = habits.associateBy { it.id }
        checkIns.filter { it.date >= yesterday }.forEach { ci ->
            val h = byId[ci.habitId] ?: return@forEach
            out += RecentItem(V4TodayViewModel.areaOf(h), h.title, "Done", label(ci.date), LocalDateTime(ci.date.year, ci.date.month, ci.date.day, 12, 0))
        }
        runCatching { journalRepository.getRecentEntries(5) }.getOrDefault(emptyList()).forEach { e ->
            out += RecentItem(PlanArea.MIND, e.title.ifBlank { "Journal entry" }, "Journal, feeling ${e.mood.displayName.lowercase()}", label(e.date), e.createdAt)
        }
        runCatching { focusRepository.getCompletedSessions() }.getOrDefault(emptyList())
            .sortedByDescending { it.completedAt }.take(3).forEach { s ->
                val at = s.completedAt ?: return@forEach
                out += RecentItem(PlanArea.STUDY, "Focus, ${s.actualMinutes} min", "Focus session", label(at.date), at)
            }
        runCatching { lifeLogs.getInRange(yesterday, today) }.getOrDefault(emptyList())
            .filter { it.status == az.tribe.lifeplanner.domain.model.LogStatus.DONE }
            .forEach { l ->
                val meta = when {
                    l.amount != null -> MoneyFormat.format(l.amount, l.currency) + (l.category?.let { ", $it" } ?: "")
                    l.durationMin != null -> "${l.durationMin} min"
                    else -> az.tribe.lifeplanner.ui.v4.components.areaName(l.area)
                }
                out += RecentItem(l.area, l.title, meta, "${l.occurredAt.hour.toString().padStart(2, '0')}:${l.occurredAt.minute.toString().padStart(2, '0')}".let { t -> if (l.date == today) t else label(l.date) }, l.occurredAt)
            }
        runCatching { healthRepository.getLatestMetric(HealthMetricType.SLEEP) }.getOrNull()?.takeIf { it.date >= yesterday }?.let { m ->
            out += RecentItem(PlanArea.MIND, "Slept ${V4TodayViewModel.formatHours(m.value)}", "From Health", "Last night", m.recordedAt)
        }
        return out.sortedByDescending { it.sortKey }.take(6)
    }

    /** Habit completion by day: done check-ins over habits that existed that day. */
    /** Habit days kept: on each day, the habits that were due then (schedule, skips and breaks applied). */
    /** Habit days kept. Today only counts once it is done: a day still in progress is not a miss. */
    private class DailyHabits(private val rows: List<HabitRow>, private val today: LocalDate) {
        val dueToday = rows.count { it.stats.dueToday || it.doneToday }
        val doneToday = rows.count { it.doneToday }

        fun rate(start: LocalDate, end: LocalDate): Float? {
            var done = 0
            var possible = 0
            var d = start
            while (d <= end) {
                rows.forEach { r ->
                    if (d < r.habit.createdAt.date || d in r.skipped) return@forEach
                    val s = HabitSchedule.normal(r.schedule)
                    val kept = d in r.done
                    if (kept || (d != today && s !is Schedule.PerWeek && HabitSchedule.isScheduled(s, d))) {
                        possible++
                        if (kept) done++
                    }
                }
                d = d.plus(DatePeriod(days = 1))
            }
            return if (possible == 0) null else done.toFloat() / possible
        }
    }
}

