package az.tribe.lifeplanner.ui.v4.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.habits.HabitRow
import az.tribe.lifeplanner.data.habits.HabitService
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.service.HabitLearning
import az.tribe.lifeplanner.domain.service.HabitSchedule
import az.tribe.lifeplanner.domain.service.Schedule
import az.tribe.lifeplanner.usecases.habit.AwardHabitCompletionUseCase
import az.tribe.lifeplanner.usecases.habit.CheckInHabitUseCase
import az.tribe.lifeplanner.usecases.habit.UncheckHabitUseCase
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Which way a card left the deck. */
enum class Swipe { RIGHT, LEFT, UP }

// ── Quick check-in ──────────────────────────────────────────────────────────

data class CheckInState(
    val cards: List<HabitRow> = emptyList(),
    val index: Int = 0,
    /** Counts for count habits, changed on the card before it is swiped. */
    val counts: Map<String, Int> = emptyMap(),
    val done: Int = 0,
    val notToday: Int = 0,
    val later: Int = 0,
    val canUndo: Boolean = false,
    val loaded: Boolean = false,
) {
    val current: HabitRow? get() = cards.getOrNull(index)
    val finished: Boolean get() = loaded && index >= cards.size
}

/**
 * One card per habit still open today, in the order the day runs. Right is done, left is
 * "not today" (a skip, so the streak waits), up is "later" (the card goes to the back once).
 * The list is taken once when the deck opens, so ticking never reshuffles it.
 */
class V4CheckInViewModel(
    private val service: HabitService,
    private val habits: HabitRepository,
    private val checkIn: CheckInHabitUseCase,
    private val uncheck: UncheckHabitUseCase,
    private val award: AwardHabitCompletionUseCase,
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)

    private val _state = MutableStateFlow(CheckInState())
    val state: StateFlow<CheckInState> = _state.asStateFlow()

    private data class Step(val row: HabitRow, val swipe: Swipe, val fromIndex: Int)
    private val history = ArrayDeque<Step>()
    private val laterOnce = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            val rows = runCatching { service.rows.first() }.getOrDefault(emptyList())
            val minute = Clock.System.now().toLocalDateTime(tz).let { it.hour * 60 + it.minute }
            val cards = deckOrder(rows.filter { it.stats.dueToday && !it.doneToday && !it.stats.skippedToday && it.habit.healthMetricType == null }, minute)
            _state.value = CheckInState(cards = cards, counts = cards.associate { it.habit.id to it.countToday }, loaded = true)
            PostHogAnalytics.capture("v4_checkin_opened", mapOf("cards" to cards.size))
        }
    }

    fun swipe(dir: Swipe) {
        val s = _state.value
        val row = s.current ?: return
        history.addLast(Step(row, dir, s.index))
        when (dir) {
            Swipe.RIGHT -> {
                _state.value = s.copy(index = s.index + 1, done = s.done + 1, canUndo = true)
                launch { markDone(row) }
            }
            Swipe.LEFT -> {
                _state.value = s.copy(index = s.index + 1, notToday = s.notToday + 1, canUndo = true)
                launch { service.toggleSkip(row.habit) }
            }
            Swipe.UP -> {
                // Later goes to the back once; the second time it simply stays open on Today.
                if (laterOnce.add(row.habit.id)) {
                    val cards = s.cards.toMutableList().apply { removeAt(s.index); add(row) }
                    _state.value = s.copy(cards = cards, canUndo = true)
                } else {
                    _state.value = s.copy(index = s.index + 1, later = s.later + 1, canUndo = true)
                }
            }
        }
        if (_state.value.finished) {
            val f = _state.value
            PostHogAnalytics.capture("v4_checkin_finished", mapOf("done" to f.done, "not_today" to f.notToday, "later" to f.later))
        }
    }

    fun undo() {
        val step = history.removeLastOrNull() ?: return
        val s = _state.value
        when (step.swipe) {
            Swipe.RIGHT -> {
                _state.value = s.copy(index = step.fromIndex, done = s.done - 1, canUndo = history.isNotEmpty())
                launch { uncheck(step.row.habit.id, today()) }
            }
            Swipe.LEFT -> {
                _state.value = s.copy(index = step.fromIndex, notToday = s.notToday - 1, canUndo = history.isNotEmpty())
                launch { service.toggleSkip(step.row.habit) }
            }
            Swipe.UP -> {
                if (s.index > step.fromIndex) {
                    _state.value = s.copy(index = step.fromIndex, later = s.later - 1, canUndo = history.isNotEmpty())
                } else {
                    laterOnce.remove(step.row.habit.id)
                    val cards = s.cards.toMutableList().apply { remove(step.row); add(step.fromIndex, step.row) }
                    _state.value = s.copy(cards = cards, canUndo = history.isNotEmpty())
                }
            }
        }
    }

    /** The + and − on a count card. Reaching the target counts as done, the swipe still moves on. */
    fun count(delta: Int) {
        val s = _state.value
        val row = s.current ?: return
        val now = ((s.counts[row.habit.id] ?: 0) + delta).coerceIn(0, row.habit.targetCount)
        if (now == s.counts[row.habit.id]) return
        _state.value = s.copy(counts = s.counts + (row.habit.id to now))
        launch {
            val c = habits.addCount(row.habit.id, today(), delta)
            if (c.completed && delta > 0) award(row.habit.id, today())
        }
    }

    private suspend fun markDone(row: HabitRow) {
        val today = today()
        if (row.habit.targetCount > 1) {
            val have = habits.getCheckInByHabitAndDate(row.habit.id, today)?.count ?: 0
            val c = habits.addCount(row.habit.id, today, (row.habit.targetCount - have).coerceAtLeast(0))
            if (c.completed) award(row.habit.id, today)
        } else {
            checkIn(row.habit.id, today)
            award(row.habit.id, today)
        }
    }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { Logger.w("V4CheckIn") { "Deck write failed: ${it.message}" } }
    }

    companion object {
        /**
         * What is due around now comes first (by the time it usually happens), then the rest of the
         * day in order, then the ones with no time at all.
         */
        fun deckOrder(rows: List<HabitRow>, nowMinute: Int): List<HabitRow> {
            val nowSlot = HabitLearning.slotOf(nowMinute)
            return rows.sortedWith(
                compareBy<HabitRow>(
                    { r -> if (r.slot == HabitSchedule.Slot.ANYTIME) 2 else if (r.slot.ordinal <= nowSlot.ordinal) 0 else 1 },
                    { it.minute },
                    { it.habit.title },
                )
            )
        }
    }
}

// ── Review what slipped ─────────────────────────────────────────────────────

data class ReviewState(
    val cards: List<HabitRow> = emptyList(),
    val options: Map<String, List<HabitService.Easier>> = emptyMap(),
    val index: Int = 0,
    val kept: Int = 0,
    val easier: Int = 0,
    val letGo: Int = 0,
    val remaining: Int = 0,
    val canUndo: Boolean = false,
    val started: Boolean = false,
    val loaded: Boolean = false,
) {
    val current: HabitRow? get() = cards.getOrNull(index)
    val finished: Boolean get() = started && loaded && index >= cards.size
}

/**
 * Habits that quietly stopped, one card each: right keeps it (it is left alone for two weeks),
 * left lets it go (stopped, history kept), up offers a lighter version.
 */
class V4ReviewViewModel(
    private val service: HabitService,
    private val habits: HabitRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state.asStateFlow()

    private sealed interface Step { val row: HabitRow; val fromIndex: Int }
    private data class Kept(override val row: HabitRow, override val fromIndex: Int) : Step
    private data class LetGo(override val row: HabitRow, override val fromIndex: Int) : Step
    private data class MadeEasier(override val row: HabitRow, override val fromIndex: Int, val how: HabitService.Easier) : Step
    private val history = ArrayDeque<Step>()
    private var total = 0

    init {
        viewModelScope.launch {
            val rows = runCatching { service.rows.first() }.getOrDefault(emptyList())
            total = rows.size
            val cards = rows.filter { it.slip != null }.sortedByDescending { it.slip!!.missedInRow }
            _state.value = ReviewState(cards = cards, options = cards.associate { it.habit.id to service.easierOptions(it) }, remaining = total, loaded = true)
        }
    }

    fun start() {
        _state.value = _state.value.copy(started = true)
        PostHogAnalytics.capture("v4_review_started", mapOf("cards" to _state.value.cards.size))
    }

    fun keep() = decide { s, row ->
        history.addLast(Kept(row, s.index))
        launch { service.keep(row.habit) }
        s.copy(kept = s.kept + 1)
    }

    fun letGo() = decide { s, row ->
        history.addLast(LetGo(row, s.index))
        launch { service.letGo(row.habit) }
        s.copy(letGo = s.letGo + 1, remaining = s.remaining - 1)
    }

    fun easier(how: HabitService.Easier) = decide { s, row ->
        history.addLast(MadeEasier(row, s.index, how))
        launch { service.makeEasier(row, how) }
        s.copy(easier = s.easier + 1)
    }

    /** Takes the last card back. Letting go and pausing are undone for real; the rest just return the card. */
    fun undo() {
        val step = history.removeLastOrNull() ?: return
        val s = _state.value
        _state.value = when (step) {
            is Kept -> s.copy(index = step.fromIndex, kept = s.kept - 1)
            is LetGo -> {
                launch { habits.getHabitById(step.row.habit.id)?.let { habits.updateHabit(it.copy(isActive = true)) } }
                s.copy(index = step.fromIndex, letGo = s.letGo - 1, remaining = s.remaining + 1)
            }
            is MadeEasier -> {
                if (step.how is HabitService.Easier.Pause) launch { service.endBreak(step.row.habit) }
                s.copy(index = step.fromIndex, easier = s.easier - 1)
            }
        }.copy(canUndo = history.isNotEmpty())
    }

    private fun decide(block: (ReviewState, HabitRow) -> ReviewState) {
        val s = _state.value
        val row = s.current ?: return
        val next = block(s, row).let { it.copy(index = it.index + 1, canUndo = true) }
        _state.value = next
        if (next.finished) PostHogAnalytics.capture("v4_review_finished", mapOf("kept" to next.kept, "easier" to next.easier, "let_go" to next.letGo))
    }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { Logger.w("V4Review") { "Review write failed: ${it.message}" } }
    }

    companion object {
        /** One honest line about the pattern, from the history alone. */
        fun noticed(r: HabitRow): String {
            val slip = r.slip
            if (slip?.neverDone == true) return "It has not happened since you added it. A smaller first step is often easier to start."
            r.learnedDays?.let { days ->
                return "It happens on ${HabitSchedule.describe(Schedule.Days(days))}, and almost never on other days."
            }
            if (HabitLearning.reminderIsOff(r.reminderMinute, r.usualMinute)) {
                return "The reminder is at ${r.habit.reminderTime}, but you usually do it around ${HabitLearning.roughly(r.usualMinute!!)}."
            }
            if (slip != null && slip.skippedRecently >= HabitLearning.SKIPS) return "You skip this one more than you miss it. It may not fit your days right now."
            if (slip != null && slip.dueLast30 > 0 && slip.keptLast30 * 2 >= slip.dueLast30) return "This was going well until recently. Probably just a busy stretch."
            if (slip != null && slip.keptLast30 == 0) return "It has not happened in a month. Smaller or less often tends to stick better."
            return "Smaller or less often tends to stick better than all at once."
        }

        /** Last 14 days for the strip on a review card: 1 done, 0.5 skipped, 0 missed, -1 not due. */
        fun strip(r: HabitRow, today: LocalDate): List<Float> = (13 downTo 0).map { back ->
            val d = today.minus(DatePeriod(days = back + 1))
            when {
                d < r.habit.createdAt.date -> -1f
                d in r.done -> 1f
                d in r.skipped -> 0.5f
                r.schedule !is Schedule.PerWeek && !HabitSchedule.isScheduled(HabitSchedule.normal(r.schedule), d) -> -1f
                else -> 0f
            }
        }

        fun back(today: LocalDate, days: Int): LocalDate = today.plus(DatePeriod(days = days))
    }
}
