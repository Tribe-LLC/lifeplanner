package az.tribe.lifeplanner.ui.v4.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.analytics.Analytics
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.calendar.CalendarPreferences
import az.tribe.lifeplanner.data.calendar.CalendarReader
import az.tribe.lifeplanner.data.integrations.IntegrationPrefs
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.enum.HabitCompletionSource
import az.tribe.lifeplanner.domain.enum.HabitType
import az.tribe.lifeplanner.domain.enum.HealthMetricType
import az.tribe.lifeplanner.domain.model.CalendarEvent
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.model.XpRewards
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.HealthRepository
import az.tribe.lifeplanner.domain.repository.PlanAreasRepository
import az.tribe.lifeplanner.domain.repository.BudgetRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.data.meals.MealService
import az.tribe.lifeplanner.data.plans.PlanService
import az.tribe.lifeplanner.data.habits.HabitRow
import az.tribe.lifeplanner.data.habits.HabitService
import az.tribe.lifeplanner.data.career.CareerService
import az.tribe.lifeplanner.domain.service.CareerKind
import az.tribe.lifeplanner.domain.service.CareerPlanner
import az.tribe.lifeplanner.domain.service.MealNotes
import az.tribe.lifeplanner.domain.service.MealPlanner
import az.tribe.lifeplanner.domain.service.StudyKind
import az.tribe.lifeplanner.domain.service.StudyPlanner
import az.tribe.lifeplanner.domain.service.MoneySummary
import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.data.fitness.WorkoutService
import az.tribe.lifeplanner.data.health.WorkoutKind
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.service.FitnessWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import az.tribe.lifeplanner.domain.model.Trip
import az.tribe.lifeplanner.domain.model.TripItem
import az.tribe.lifeplanner.domain.model.TripItemKind
import az.tribe.lifeplanner.domain.repository.TripRepository
import az.tribe.lifeplanner.domain.service.TravelMode
import az.tribe.lifeplanner.domain.service.TripPlanner
import kotlinx.coroutines.flow.map
import az.tribe.lifeplanner.usecases.habit.AwardHabitCompletionUseCase
import az.tribe.lifeplanner.usecases.habit.CheckInHabitUseCase
import az.tribe.lifeplanner.usecases.habit.UncheckHabitUseCase
import az.tribe.lifeplanner.usecases.health.SyncHealthDataUseCase
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
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

enum class DayItemType { HABIT, STEP, EVENT, WORKOUT, TRIP, MEAL, STUDY, CAREER }

/** One row of "Your day". Habits and plan steps can be ticked; calendar events are context. */
data class DayItem(
    val key: String,
    val type: DayItemType,
    val refId: String,
    val time: LocalTime?,
    val allDay: Boolean = false,
    val title: String,
    val area: PlanArea?,
    val meta: String,
    val done: Boolean,
    val checkable: Boolean,
    val goalId: String? = null,
)

/** A chip in the row under the title. [area] picks its tint; null is the dark "done" chip. */
data class TodayChip(val text: String, val area: PlanArea?)

/** The coach's one line for today, with what its buttons do. */
data class CoachNudge(
    val id: String,
    val text: String,
    val primary: String?,
    val secondary: String?,
    /** What to say to the coach when the primary button is tapped; null means just dismiss. */
    val coachPrompt: String? = null,
    /** Something the primary button does right here instead, like swapping today's workout. */
    val action: NudgeAction? = null,
)

enum class NudgeAction { SWAP_WORKOUT, UNDO_SWAP }

data class TodayUiState(
    val date: LocalDate,
    val items: List<DayItem> = emptyList(),
    val done: Int = 0,
    val total: Int = 0,
    val chips: List<TodayChip> = emptyList(),
    val nudge: CoachNudge? = null,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class V4TodayViewModel(
    private val habitRepository: HabitRepository,
    private val goalRepository: GoalRepository,
    private val healthRepository: HealthRepository,
    private val calendarReader: CalendarReader,
    private val calendarPreferences: CalendarPreferences,
    private val checkInHabit: CheckInHabitUseCase,
    private val uncheckHabit: UncheckHabitUseCase,
    private val awardHabitCompletion: AwardHabitCompletionUseCase,
    private val gamificationRepository: GamificationRepository,
    private val syncHealthData: SyncHealthDataUseCase,
    private val integrationPrefs: IntegrationPrefs,
    private val planAreas: PlanAreasRepository,
    private val settings: Settings,
    private val lifeLogs: LifeLogRepository,
    budgets: BudgetRepository,
    private val workouts: WorkoutService,
    private val trips: TripRepository,
    private val plans: PlanService,
    private val meals: MealService,
    habitService: HabitService,
    private val career: CareerService,
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)

    private val events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    private val health = MutableStateFlow(HealthToday())
    private val stepsDoneToday = MutableStateFlow(readStepsDone())
    private val dismissed = MutableStateFlow(settings.getStringOrNull(dismissKey()))
    /** Bumped when a swap is made or undone, since that lives in settings, not the database. */
    private val swapTick = MutableStateFlow(0)

    /** Workouts from today through next week: today's go on the day, the rest pick a swap day. */
    /** The trip under way or next, with its list. Drives the trip chip, travel mode and "before you go". */
    private val currentTrip = trips.observeAll().flatMapLatest { all ->
        val t = TripPlanner.current(all, today())
        if (t == null) flowOf(null) else trips.observeItems(t.id).map { items -> t to items }
    }

    private val weekWorkouts = lifeLogs.observeInRange(today(), today().plus(DatePeriod(days = 7)))
        .map { list -> list.filter { FitnessWeek.isWorkout(it) } }

    private data class HealthToday(val steps: Double? = null, val sleepHours: Double? = null)

    /** Planned meals and study, and exams and deadlines, from today through the next two weeks. */
    private val upcomingPlans = lifeLogs.observeInRange(today(), today().plus(DatePeriod(days = 14)))
        .map { list -> list.filter { MealPlanner.isMeal(it) || StudyPlanner.isStudy(it) } }

    /** Applications, interviews and people, whose next step can be overdue as well as today. */
    private val careerRows = lifeLogs.observeInRange(today().minus(DatePeriod(days = 365)), today().plus(DatePeriod(days = 1)))
        .map { list -> list.filter { CareerKind.of(it).let { k -> k == CareerKind.APPLICATION || k == CareerKind.INTERVIEW || k == CareerKind.CONTACT } } }

    /** Habits with their schedule and skips applied, so Today only shows the ones due. */
    private val habitsWithCounts = habitService.rows

    /** "€88 left for food this week", from the leading budget. Null without one. */
    private val moneyChip = combine(
        budgets.observeAll(),
        lifeLogs.observeInRange(today().minus(DatePeriod(days = 40)), today()),
    ) { bs, logs ->
        MoneySummary.primary(bs)?.let { b ->
            val st = MoneySummary.status(b, logs, today())
            val what = b.category?.let { " for $it" } ?: ""
            if (st.left >= 0) "${MoneyFormat.format(st.left, b.currency)} left$what ${MoneySummary.periodWord(b.period)}"
            else "${MoneyFormat.format(-st.left, b.currency)} over$what ${MoneySummary.periodWord(b.period)}"
        }
    }

    val state: StateFlow<TodayUiState> = combine(
        combine(habitsWithCounts, goalRepository.observeAllGoals(), events, ::Triple),
        combine(health, moneyChip, combine(weekWorkouts, upcomingPlans, careerRows, ::Triple), swapTick, currentTrip) { h, m, (w, p, cr), _, t -> Extras(h, m, w, t, p, cr) },
        stepsDoneToday,
        planAreas.enabledAreas,
        dismissed,
    ) { (habits, goals, evts), x, stepsDone, areas, dismissedId ->
        build(habits, goals, evts, x.health, stepsDone, areas, dismissedId, x.money, x.week, x.trip, x.plans, x.career)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState(date = today()))

    private data class Extras(val health: HealthToday, val money: String?, val week: List<LifeLog>, val trip: Pair<Trip, List<TripItem>>?, val plans: List<LifeLog>, val career: List<LifeLog>)

    init {
        refresh()
    }

    /** Re-reads what does not come from the database: the calendar and Health. Called on resume. */
    fun refresh() {
        viewModelScope.launch {
            val prefs = integrationPrefs.state.value
            if (prefs.calendar) loadEvents()
            if (prefs.health) {
                runCatching { syncHealthData() }
                runCatching { workouts.importFromHealth() }
            }
            loadHealth()
        }
    }

    private suspend fun loadEvents() {
        val start = today().atStartOfDayIn(tz)
        val end = today().plus(DatePeriod(days = 1)).atStartOfDayIn(tz)
        events.value = runCatching { calendarReader.readEvents(start.toEpochMilliseconds(), end.toEpochMilliseconds()) }
            .getOrDefault(emptyList())
            .filter { e -> e.calendarId?.let { calendarPreferences.isEnabled(it) } ?: true }
    }

    private suspend fun loadHealth() {
        val today = today()
        val steps = runCatching { healthRepository.getMetricsInRange(HealthMetricType.STEPS, today, today) }
            .getOrDefault(emptyList()).sumOf { it.value }.takeIf { it > 0 }
        // Sleep is filed under the night it started, so last night is yesterday's row (or today's
        // for someone who fell asleep after midnight).
        val sleep = runCatching { healthRepository.getMetricsInRange(HealthMetricType.SLEEP, today.minus(DatePeriod(days = 1)), today) }
            .getOrDefault(emptyList()).maxByOrNull { it.date }?.value?.takeIf { it > 0 }
        health.value = HealthToday(steps, sleep)
    }

    fun toggle(item: DayItem) {
        when (item.type) {
            DayItemType.HABIT -> toggleHabit(item)
            DayItemType.STEP -> toggleStep(item)
            DayItemType.WORKOUT -> toggleWorkout(item)
            DayItemType.TRIP -> viewModelScope.launch {
                runCatching {
                    val t = currentTrip.first() ?: return@launch
                    t.second.firstOrNull { it.id == item.refId }?.let { trips.saveItem(it.copy(isDone = !it.isDone)) }
                }
            }
            DayItemType.MEAL, DayItemType.STUDY -> viewModelScope.launch {
                runCatching {
                    val log = lifeLogs.getById(item.refId) ?: return@launch
                    plans.setDone(log, !item.done)
                    if (!item.done && item.type == DayItemType.MEAL) meals.sendToHealth(log.copy(status = LogStatus.DONE))
                    PostHogAnalytics.capture("v4_today_ticked", mapOf("type" to item.type.name.lowercase(), "done" to !item.done))
                }.onFailure { Logger.w("V4Today") { "Plan toggle failed: ${it.message}" } }
            }
            DayItemType.CAREER -> viewModelScope.launch {
                runCatching {
                    val log = lifeLogs.getById(item.refId) ?: return@launch
                    career.complete(log)
                    PostHogAnalytics.capture("v4_today_ticked", mapOf("type" to "career", "done" to !item.done))
                }
            }
            DayItemType.EVENT -> {}
        }
    }

    private fun toggleHabit(item: DayItem) {
        viewModelScope.launch {
            runCatching {
                val today = today()
                val habit = habitRepository.getHabitById(item.refId) ?: return@launch
                if (item.done) {
                    uncheckHabit(habit.id, today)
                } else if (habit.targetCount > 1) {
                    val c = habitRepository.addCount(habit.id, today, 1)
                    if (c.completed) awardHabit(habit.id)
                } else {
                    checkInHabit(habit.id, today)
                    awardHabit(habit.id)
                }
                PostHogAnalytics.capture("v4_today_ticked", mapOf("type" to "habit", "done" to !item.done))
            }.onFailure { Logger.w("V4Today") { "Habit toggle failed: ${it.message}" } }
        }
    }

    private suspend fun awardHabit(habitId: String) {
        awardHabitCompletion(habitId, today())
        Analytics.habitCheckedIn(habitId, habitRepository.getHabitById(habitId)?.currentStreak ?: 0)
    }

    private fun toggleStep(item: DayItem) {
        val goalId = item.goalId ?: return
        viewModelScope.launch {
            runCatching {
                val completing = !item.done
                goalRepository.toggleMilestoneCompletion(item.refId, completing)
                if (completing) {
                    gamificationRepository.awardXp(XpRewards.MILESTONE_COMPLETED.toLong())
                    Analytics.milestoneCompleted(goalId, item.refId)
                    writeStepsDone(stepsDoneToday.value + item.refId)
                } else {
                    writeStepsDone(stepsDoneToday.value - item.refId)
                }
                // Same bookkeeping as the goal screen: progress follows the steps, and the first
                // finished step starts the goal.
                goalRepository.getGoalById(goalId)?.let { goal ->
                    val total = goal.milestones.size
                    if (total > 0) goalRepository.updateProgress(goalId, goal.milestones.count { it.isCompleted } * 100 / total)
                    if (completing && goal.status == GoalStatus.NOT_STARTED) goalRepository.updateGoal(goal.copy(status = GoalStatus.IN_PROGRESS))
                }
                PostHogAnalytics.capture("v4_today_ticked", mapOf("type" to "step", "done" to completing))
            }.onFailure { Logger.w("V4Today") { "Step toggle failed: ${it.message}" } }
        }
    }

    private fun toggleWorkout(item: DayItem) {
        viewModelScope.launch {
            runCatching {
                val log = lifeLogs.getById(item.refId) ?: return@launch
                if (item.done) workouts.undoDone(log) else workouts.markDone(log)
                PostHogAnalytics.capture("v4_today_ticked", mapOf("type" to "workout", "done" to !item.done))
            }.onFailure { Logger.w("V4Today") { "Workout toggle failed: ${it.message}" } }
        }
    }

    /** The coach card's main button: a swap happens here, anything else goes to the coach. */
    fun onNudgePrimary(nudge: CoachNudge, askCoach: (String) -> Unit) {
        when (nudge.action) {
            NudgeAction.SWAP_WORKOUT -> viewModelScope.launch {
                val w = hardWorkoutToday(weekWorkoutsNow()) ?: return@launch
                workouts.swapForWalk(w, health.value.sleepHours?.let { formatHours(it) } ?: "badly")
                swapTick.value++
            }
            NudgeAction.UNDO_SWAP -> viewModelScope.launch {
                workouts.swapToday()?.let { workouts.undoSwap(it) }
                swapTick.value++
            }
            null -> {
                nudge.coachPrompt?.let(askCoach)
                dismissNudge(nudge)
            }
        }
    }

    fun onNudgeSecondary(nudge: CoachNudge) {
        if (nudge.action == NudgeAction.SWAP_WORKOUT) {
            workouts.keepToday()
            swapTick.value++
        }
        dismissNudge(nudge)
    }

    private suspend fun weekWorkoutsNow(): List<LifeLog> = weekWorkouts.first()

    fun dismissNudge(nudge: CoachNudge) {
        settings.putString(dismissKey(), nudge.id)
        dismissed.value = nudge.id
        PostHogAnalytics.capture("v4_coach_nudge_dismissed", mapOf("id" to nudge.id))
    }

    // ── Building the day ─────────────────────────────────────────────────────

    private fun build(
        habits: List<HabitRow>,
        goals: List<Goal>,
        evts: List<CalendarEvent>,
        h: HealthToday,
        stepsDone: Set<String>,
        areas: Set<PlanArea>,
        dismissedId: String?,
        money: String?,
        week: List<LifeLog>,
        trip: Pair<Trip, List<TripItem>>?,
        planned: List<LifeLog>,
        careerRows: List<LifeLog>,
    ): TodayUiState {
        val today = today()
        val items = mutableListOf<DayItem>()
        val away = trip?.first?.takeIf { it.travelMode && TripPlanner.isActive(it, today) }

        habits.filter { r -> (r.stats.dueToday || r.doneToday) && (away == null || TravelMode.keeps(r.habit)) }.forEach { r ->
            val habit = r.habit
            val done = r.doneToday
            items += DayItem(
                key = "h_${habit.id}",
                type = DayItemType.HABIT,
                refId = habit.id,
                time = parseTime(habit.reminderTime),
                title = habit.title,
                area = areaOf(habit),
                meta = r.meta,
                done = done,
                checkable = true,
            )
        }

        goals.filter { it.status != GoalStatus.COMPLETED && !it.isArchived }.forEach { goal ->
            goal.milestones.forEach { m ->
                val due = m.dueDate
                val show = (!m.isCompleted && due != null && due <= today) || (m.isCompleted && m.id in stepsDone)
                if (show) {
                    val overdue = due != null && due < today && !m.isCompleted
                    items += DayItem(
                        key = "s_${m.id}",
                        type = DayItemType.STEP,
                        refId = m.id,
                        time = null,
                        title = m.title,
                        area = PlanArea.forCategory(goal.category),
                        meta = goal.title + if (overdue) ", overdue" else "",
                        done = m.isCompleted,
                        checkable = true,
                        goalId = goal.id,
                    )
                }
            }
        }

        week.filter { it.date == today && it.source == LifeLog.SOURCE_PLAN && it.status != LogStatus.SKIPPED }.forEach { w ->
            items += DayItem(
                key = "w_${w.id}",
                type = DayItemType.WORKOUT,
                refId = w.id,
                time = if (WorkoutService.hasTime(w)) w.occurredAt.time else null,
                title = w.title,
                area = PlanArea.FITNESS,
                meta = listOfNotNull(w.durationMin?.let { "$it min" }, w.notes).joinToString(", ").ifEmpty { "Workout" },
                done = w.status == LogStatus.DONE,
                checkable = true,
            )
        }

        // Meals planned for today, at their usual time so the day reads in order.
        planned.filter { MealPlanner.isMeal(it) && it.date == today && it.source == LifeLog.SOURCE_PLAN && it.status != LogStatus.SKIPPED }.forEach { m ->
            val slot = MealPlanner.slotOf(m)
            items += DayItem(
                key = "m_${m.id}",
                type = DayItemType.MEAL,
                refId = m.id,
                time = slot.usualTime,
                title = MealPlanner.dishName(m),
                area = PlanArea.MEALS,
                meta = if (MealNotes.decode(m.notes).leftoverOf != null) "${slot.label}, leftovers" else slot.label,
                done = m.status == LogStatus.DONE,
                checkable = true,
            )
        }

        // Study blocks for today, and exams and deadlines that fall on it.
        val dated = planned.filter { StudyPlanner.isDated(it) }.associateBy { it.id }
        planned.filter { StudyPlanner.isStudy(it) && it.date == today && it.source == LifeLog.SOURCE_PLAN && it.status != LogStatus.SKIPPED }.forEach { b ->
            val kind = StudyPlanner.kindOf(b)
            items += DayItem(
                key = "s_${b.id}",
                type = DayItemType.STUDY,
                refId = b.id,
                time = if (WorkoutService.hasTime(b)) b.occurredAt.time else null,
                title = when (kind) {
                    StudyKind.EXAM -> StudyPlanner.dueName(b)
                    StudyKind.DEADLINE -> "Hand in ${b.title}"
                    else -> b.title
                },
                area = PlanArea.STUDY,
                meta = when (kind) {
                    StudyKind.EXAM -> "Exam today"
                    StudyKind.DEADLINE -> "Due today"
                    else -> listOfNotNull(b.durationMin?.let { "$it min" }, b.notes?.let { dated[it] }?.let { "for ${StudyPlanner.dueName(it)}" }).joinToString(", ").ifEmpty { "Study" }
                },
                done = b.status == LogStatus.DONE,
                checkable = true,
            )
        }

        // Career next steps that are due: apply, follow up, interviews today, people to catch up with.
        if (PlanArea.CAREER in areas) CareerPlanner.actions(careerRows, today, horizonDays = 0).filter { it.due <= today }.forEach { a ->
            items += DayItem(
                key = "c_${a.log.id}",
                type = DayItemType.CAREER,
                refId = a.log.id,
                time = if (a.timed) a.log.occurredAt.time else null,
                title = a.title,
                area = PlanArea.CAREER,
                meta = a.meta,
                done = a.log.status == LogStatus.DONE,
                checkable = true,
            )
        }

        // The week before a trip, what is left to do for it shows up here too.
        trip?.let { (t, list) ->
            val until = t.startDate.toEpochDays() - today.toEpochDays()
            if (until in 0..7) list.filter { it.kind == TripItemKind.TODO }.forEach { todo ->
                items += DayItem(
                    key = "t_${todo.id}",
                    type = DayItemType.TRIP,
                    refId = todo.id,
                    time = null,
                    title = todo.title,
                    area = PlanArea.TRAVEL,
                    meta = "Before ${t.destination}",
                    done = todo.isDone,
                    checkable = true,
                )
            }
        }

        evts.forEach { e ->
            val start = Instant.fromEpochMilliseconds(e.startEpochMillis).toLocalDateTime(tz)
            items += DayItem(
                key = "e_${e.id}",
                type = DayItemType.EVENT,
                refId = e.id,
                time = if (e.allDay) null else start.time,
                allDay = e.allDay,
                title = e.title.ifBlank { "Busy" },
                area = null,
                meta = listOfNotNull(e.location?.takeIf { it.isNotBlank() }, e.calendarName ?: "From your calendar").joinToString(", "),
                done = false,
                checkable = false,
            )
        }

        // Things for "any time" first, then the timed day in order.
        val sorted = items.sortedWith(compareBy<DayItem>({ it.time != null }, { it.time }, { it.type.ordinal }))
        val checkable = sorted.filter { it.checkable }
        val done = checkable.count { it.done }

        val chips = buildList {
            if (checkable.isNotEmpty()) add(TodayChip("$done of ${checkable.size} done", null))
            trip?.first?.let { t ->
                val until = (t.startDate.toEpochDays() - today.toEpochDays()).toInt()
                when {
                    TripPlanner.isActive(t, today) -> add(TodayChip("${TripPlanner.countdown(t, today).replaceFirstChar { it.uppercase() }} in ${t.destination}", PlanArea.TRAVEL))
                    until <= 30 -> add(TodayChip("${t.destination} ${TripPlanner.countdown(t, today)}", PlanArea.TRAVEL))
                }
            }
            if (PlanArea.STUDY in areas) {
                StudyPlanner.upcoming(planned, today).firstOrNull { it.status != LogStatus.DONE && it.date > today && it.date <= today.plus(DatePeriod(days = 7)) }?.let { e ->
                    add(TodayChip("${StudyPlanner.dueLine(e)} ${StudyPlanner.countdown(e.date, today)}", PlanArea.STUDY))
                }
            }
            if (PlanArea.MONEY in areas) money?.let { add(TodayChip(it, PlanArea.MONEY)) }
            if (PlanArea.FITNESS in areas) h.steps?.let { add(TodayChip("${formatThousands(it.toLong())} steps", PlanArea.FITNESS)) }
            if (PlanArea.MIND in areas) h.sleepHours?.let { add(TodayChip("Slept ${formatHours(it)}", PlanArea.MIND)) }
        }

        val nudge = pickNudge(checkable, h, habits, week).takeIf { it?.id != dismissedId }

        return TodayUiState(date = today, items = sorted, done = done, total = checkable.size, chips = chips, nudge = nudge, loaded = true)
    }

    /** Today's planned workout that is too much after a short night: anything but a walk or yoga. */
    private fun hardWorkoutToday(week: List<LifeLog>): LifeLog? = week.firstOrNull {
        it.date == today() && it.status == LogStatus.PLANNED && it.source == LifeLog.SOURCE_PLAN &&
            WorkoutKind.fromTitle(it.title) !in setOf(WorkoutKind.WALK, WorkoutKind.YOGA)
    }

    private fun pickNudge(checkable: List<DayItem>, h: HealthToday, habits: List<HabitRow>, week: List<LifeLog>): CoachNudge? {
        val hour = Clock.System.now().toLocalDateTime(tz).hour
        val sleep = h.sleepHours
        workouts.swapToday()?.let { swap ->
            val moved = week.firstOrNull { it.id == swap.originalId }
            return CoachNudge(
                id = "swapped",
                text = "Done. ${moved?.title ?: "Your workout"} is on ${FitnessWeek.dayName(swap.movedTo.dayOfWeek)}, and today is a 20 minute walk.",
                primary = "Put it back",
                secondary = "Thanks",
                action = NudgeAction.UNDO_SWAP,
            )
        }
        val hard = hardWorkoutToday(week)
        if (sleep != null && sleep < 6.0 && hard != null && !workouts.swapDecidedToday()) {
            val to = FitnessWeek.nextFreeDay(week, today())
            return CoachNudge(
                id = "swap",
                text = "You slept ${formatHours(sleep)}. Swap ${hard.title} for a 20 minute walk today? ${hard.title} moves to ${FitnessWeek.dayName(to.dayOfWeek)}.",
                primary = "Swap it",
                secondary = "Keep it",
                action = NudgeAction.SWAP_WORKOUT,
            )
        }
        if (sleep != null && sleep < 6.0) {
            return CoachNudge(
                id = "low_sleep",
                text = "You slept ${formatHours(sleep)}. Keep today light: a 20 minute walk still counts, and the hard workout can wait a day.",
                primary = "Plan a lighter day",
                secondary = "I'm fine",
                coachPrompt = "I only slept ${formatHours(sleep)} last night. Help me make today lighter without losing my streaks.",
            )
        }
        if (habits.isEmpty() && checkable.isEmpty()) {
            return CoachNudge(
                id = "empty",
                text = "Nothing planned yet. Type anything in the box below, like \"drink water every day\" or \"ran 5k\", and I will file it for you.",
                primary = null,
                secondary = "Got it",
            )
        }
        val left = checkable.filter { !it.done }
        if (left.isEmpty() && checkable.isNotEmpty()) {
            return CoachNudge(id = "all_done", text = "Everything for today is done. That is a good day.", primary = null, secondary = "Thanks")
        }
        if (hour >= 18 && left.isNotEmpty()) {
            // Live streaks from HabitService: the stored currentStreak only moves on a check-in, so it goes stale.
            val streaker = habits.filter { r -> left.any { it.refId == r.habit.id } }.maxByOrNull { it.stats.streak }
            val streakLine = streaker?.takeIf { it.stats.streak >= 2 }?.let { r ->
                " ${r.habit.title} is on a ${r.stats.streak} ${if (r.stats.streakInWeeks) "week" else "day"} streak."
            } ?: ""
            return CoachNudge(
                id = "evening",
                text = "${left.size} left for today.$streakLine Pick the easiest one and do it now.",
                primary = null,
                secondary = "On it",
            )
        }
        val first = left.firstOrNull() ?: return null
        val greeting = if (hour < 12) "Morning." else "Afternoon."
        return CoachNudge(
            id = "start",
            text = "$greeting You have ${checkable.size} ${if (checkable.size == 1) "thing" else "things"} today. Start with \"${first.title}\".",
            primary = null,
            secondary = "Okay",
        )
    }

    private fun readStepsDone(): Set<String> =
        settings.getString(stepsKey(), "").split(',').filter { it.isNotBlank() }.toSet()

    private fun writeStepsDone(ids: Set<String>) {
        settings.putString(stepsKey(), ids.joinToString(","))
        stepsDoneToday.value = ids
    }

    private fun stepsKey() = "v4_steps_done_${today()}"
    private fun dismissKey() = "v4_nudge_dismissed_${today()}"

    companion object {
        /** Which area a habit belongs to on Today. Plain habits stay under Habits. */
        fun areaOf(habit: Habit): PlanArea = when {
            habit.healthMetricType == HealthMetricType.SLEEP -> PlanArea.MIND
            habit.healthMetricType != null -> PlanArea.FITNESS
            habit.completionSource == HabitCompletionSource.BREATHING -> PlanArea.MIND
            else -> PlanArea.HABITS
        }

        fun parseTime(raw: String?): LocalTime? = raw?.trim()?.takeIf { it.isNotEmpty() }?.let { s ->
            runCatching {
                val parts = s.split(':')
                LocalTime(parts[0].toInt(), parts.getOrNull(1)?.take(2)?.toInt() ?: 0)
            }.getOrNull()
        }

        fun formatHours(hours: Double): String {
            val totalMin = (hours * 60).toInt()
            val h = totalMin / 60
            val m = totalMin % 60
            return if (m == 0) "${h}h" else "${h}h ${m.toString().padStart(2, '0')}m"
        }

        fun formatThousands(n: Long): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

        fun hhmm(t: kotlinx.datetime.LocalDateTime) = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"

        /** "Today", "Yesterday", or "Mon 21 Sep". */
        fun dayLabel(d: LocalDate): String {
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            return when (d) {
                today -> "Today"
                today.minus(DatePeriod(days = 1)) -> "Yesterday"
                else -> "${FitnessWeek.shortDay(d.dayOfWeek)} ${d.day} ${d.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}"
            }
        }
    }
}
