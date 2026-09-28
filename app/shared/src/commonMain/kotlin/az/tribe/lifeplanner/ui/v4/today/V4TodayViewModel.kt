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

enum class DayItemType { HABIT, STEP, EVENT }

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
)

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
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)

    private val events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    private val health = MutableStateFlow(HealthToday())
    private val stepsDoneToday = MutableStateFlow(readStepsDone())
    private val dismissed = MutableStateFlow(settings.getStringOrNull(dismissKey()))

    private data class HealthToday(val steps: Double? = null, val sleepHours: Double? = null)

    private val habitsWithCounts = habitRepository.observeHabitsWithTodayStatus().mapLatest { list ->
        val counts = runCatching { habitRepository.getCheckInsByDate(today()) }.getOrDefault(emptyList())
            .associate { it.habitId to it.count }
        list.map { (habit, done) -> Triple(habit, done, counts[habit.id] ?: 0) }
    }

    val state: StateFlow<TodayUiState> = combine(
        combine(habitsWithCounts, goalRepository.observeAllGoals(), events, ::Triple),
        health,
        stepsDoneToday,
        planAreas.enabledAreas,
        dismissed,
    ) { (habits, goals, evts), h, stepsDone, areas, dismissedId ->
        build(habits, goals, evts, h, stepsDone, areas, dismissedId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState(date = today()))

    init {
        refresh()
    }

    /** Re-reads what does not come from the database: the calendar and Health. Called on resume. */
    fun refresh() {
        viewModelScope.launch {
            val prefs = integrationPrefs.state.value
            if (prefs.calendar) loadEvents()
            if (prefs.health) runCatching { syncHealthData() }
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

    fun dismissNudge(nudge: CoachNudge) {
        settings.putString(dismissKey(), nudge.id)
        dismissed.value = nudge.id
        PostHogAnalytics.capture("v4_coach_nudge_dismissed", mapOf("id" to nudge.id))
    }

    // ── Building the day ─────────────────────────────────────────────────────

    private fun build(
        habits: List<Triple<Habit, Boolean, Int>>,
        goals: List<Goal>,
        evts: List<CalendarEvent>,
        h: HealthToday,
        stepsDone: Set<String>,
        areas: Set<PlanArea>,
        dismissedId: String?,
    ): TodayUiState {
        val today = today()
        val items = mutableListOf<DayItem>()

        habits.filter { it.first.isActive }.forEach { (habit, done, count) ->
            items += DayItem(
                key = "h_${habit.id}",
                type = DayItemType.HABIT,
                refId = habit.id,
                time = parseTime(habit.reminderTime),
                title = habit.title,
                area = areaOf(habit),
                meta = habitMeta(habit, done, count),
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
            if (PlanArea.FITNESS in areas) h.steps?.let { add(TodayChip("${formatThousands(it.toLong())} steps", PlanArea.FITNESS)) }
            if (PlanArea.MIND in areas) h.sleepHours?.let { add(TodayChip("Slept ${formatHours(it)}", PlanArea.MIND)) }
        }

        val nudge = pickNudge(checkable, h, habits.map { it.first }).takeIf { it?.id != dismissedId }

        return TodayUiState(date = today, items = sorted, done = done, total = checkable.size, chips = chips, nudge = nudge, loaded = true)
    }

    private fun pickNudge(checkable: List<DayItem>, h: HealthToday, habits: List<Habit>): CoachNudge? {
        val hour = Clock.System.now().toLocalDateTime(tz).hour
        val sleep = h.sleepHours
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
            val streaker = habits.filter { h -> left.any { it.refId == h.id } }.maxByOrNull { it.currentStreak }
            val streakLine = streaker?.takeIf { it.currentStreak >= 2 }?.let { " ${it.title} is on a ${it.currentStreak} day streak." } ?: ""
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

        fun habitMeta(habit: Habit, done: Boolean, count: Int): String = when {
            habit.targetCount > 1 -> "${if (done) habit.targetCount else count} of ${habit.targetCount}${habit.unit?.let { " $it" } ?: ""}"
            habit.currentStreak >= 2 -> "${habit.currentStreak} day streak"
            habit.healthMetricType != null -> "Ticks itself from Health"
            habit.type == HabitType.QUIT -> if (done) "Resisted today" else "Stay strong"
            else -> habit.frequency.displayName
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
    }
}
