package az.tribe.lifeplanner.ui.v4.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.habits.HabitService
import az.tribe.lifeplanner.data.habits.NudgePrefs
import az.tribe.lifeplanner.data.habits.NudgeService
import az.tribe.lifeplanner.data.integrations.IntegrationPrefs
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.HabitCompletionSource
import az.tribe.lifeplanner.domain.enum.HabitType
import az.tribe.lifeplanner.domain.enum.HealthMetricType
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.PlanAreasRepository
import az.tribe.lifeplanner.domain.service.HabitLearning
import az.tribe.lifeplanner.domain.service.HabitSchedule
import az.tribe.lifeplanner.domain.service.Schedule
import az.tribe.lifeplanner.ui.v4.areas.Starter
import az.tribe.lifeplanner.usecases.habit.AwardHabitCompletionUseCase
import az.tribe.lifeplanner.usecases.habit.CheckInHabitUseCase
import az.tribe.lifeplanner.usecases.habit.UncheckHabitUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** A starter habit on a deck card: what it is, which area it came from, and why it is worth it. */
data class StarterCard(
    val starter: Starter,
    val area: PlanArea,
    val why: String,
    val tint: CardTint,
    val category: GoalCategory = GoalCategory.WELLBEING,
) {
    val meta: String get() = when {
        starter.type == HabitType.QUIT -> "To break"
        starter.target > 1 -> "${starter.target} ${starter.unit ?: "times"} a day"
        else -> HabitSchedule.describe(starter.schedule).replaceFirstChar { it.uppercase() }
    }
    val whenText: String get() = when {
        starter.reminder != null -> "${starter.reminder.hour.toString().padStart(2, '0')}:${starter.reminder.minute.toString().padStart(2, '0')}"
        starter.health != null || starter.target > 1 -> "All day"
        else -> "Any time"
    }
}

object StarterDeck {
    /** How many to start with; small wins first. */
    const val PICK = 3

    private val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

    private fun byArea(health: Boolean): Map<PlanArea, List<StarterCard>> = mapOf(
        PlanArea.HABITS to listOf(
            StarterCard(Starter("Drink water", target = 8, unit = "glasses"), PlanArea.HABITS, "The easiest win of the day.", CardTint.TEAL),
            StarterCard(Starter("Read 10 pages", reminder = LocalTime(21, 30)), PlanArea.HABITS, "Ten pages is about 15 minutes.", CardTint.BLUE),
            StarterCard(Starter("Make the bed"), PlanArea.HABITS, "One thing done before the day starts.", CardTint.GREEN),
        ),
        PlanArea.FITNESS to listOfNotNull(
            if (health) StarterCard(Starter("Walk 8,000 steps", health = HealthMetricType.STEPS, healthTarget = 8_000.0), PlanArea.FITNESS, "Ticks itself from Health, nothing to log.", CardTint.ORANGE, GoalCategory.BODY)
            else StarterCard(Starter("Walk 20 minutes"), PlanArea.FITNESS, "The simplest workout there is.", CardTint.ORANGE, GoalCategory.BODY),
            StarterCard(Starter("Work out", schedule = Schedule.PerWeek(3), source = HabitCompletionSource.WORKOUT), PlanArea.FITNESS, "Three a week. Ticks itself when you finish a workout.", CardTint.ORANGE, GoalCategory.BODY),
            StarterCard(Starter("Stretch, 10 min", reminder = LocalTime(7, 30)), PlanArea.FITNESS, "Wakes you up better than coffee.", CardTint.GREEN, GoalCategory.BODY),
        ),
        PlanArea.MIND to listOf(
            StarterCard(Starter("Breathe, 1 minute", source = HabitCompletionSource.BREATHING), PlanArea.MIND, "Ticks itself when you finish a breathing break.", CardTint.PURPLE),
            StarterCard(Starter("Phone out of the bedroom", type = HabitType.QUIT, reminder = LocalTime(22, 30)), PlanArea.MIND, "Better sleep starts before bed.", CardTint.PINK),
            StarterCard(Starter("Three good things", reminder = LocalTime(21, 30)), PlanArea.MIND, "Write three small good things. It adds up.", CardTint.PURPLE),
        ),
        PlanArea.MONEY to listOf(
            StarterCard(Starter("Log what you spend", reminder = LocalTime(21, 0)), PlanArea.MONEY, "Thirty seconds, and the budget stays true.", CardTint.GREEN, GoalCategory.MONEY),
            StarterCard(Starter("No impulse buys", type = HabitType.QUIT), PlanArea.MONEY, "Wait a day before anything you did not plan.", CardTint.TEAL, GoalCategory.MONEY),
        ),
        PlanArea.MEALS to listOf(
            StarterCard(Starter("Cook at home", schedule = Schedule.PerWeek(3)), PlanArea.MEALS, "Cheaper, and you know what is in it.", CardTint.ORANGE),
            StarterCard(Starter("Vegetables with lunch"), PlanArea.MEALS, "Half the plate, no counting.", CardTint.GREEN),
        ),
        PlanArea.STUDY to listOf(
            StarterCard(Starter("Study 25 minutes", schedule = Schedule.Days(weekdays), target = 25, unit = "min", source = HabitCompletionSource.FOCUS), PlanArea.STUDY, "Your study time counts toward it by itself.", CardTint.BLUE),
            StarterCard(Starter("Review today's notes", reminder = LocalTime(20, 0)), PlanArea.STUDY, "Five minutes today saves an hour before the exam.", CardTint.PURPLE),
        ),
        PlanArea.CAREER to listOf(
            StarterCard(Starter("15 minutes on your career", schedule = Schedule.Days(weekdays)), PlanArea.CAREER, "A skill, an application, a message. It compounds.", CardTint.BLUE, GoalCategory.CAREER),
            StarterCard(Starter("Message someone in your field", schedule = Schedule.PerWeek(1)), PlanArea.CAREER, "Most jobs come from people, not listings.", CardTint.PINK, GoalCategory.CAREER),
        ),
        PlanArea.TRAVEL to listOf(
            StarterCard(Starter("Learn 5 words of a language"), PlanArea.TRAVEL, "Hello, thank you, sorry, how much, where.", CardTint.TEAL),
        ),
    )

    /**
     * Starters from the picked areas, taking turns between areas so the first few cards are
     * varied. Habits come first because every user has them.
     */
    fun forAreas(areas: Set<PlanArea>, health: Boolean, max: Int = 9): List<StarterCard> {
        val all = byArea(health)
        val order = listOf(PlanArea.HABITS) + PlanArea.entries.filter { it != PlanArea.HABITS && it in areas }
        val lists = order.mapNotNull { all[it]?.toMutableList() }.filter { it.isNotEmpty() }
        val out = mutableListOf<StarterCard>()
        var round = 0
        while (out.size < max && lists.any { round < it.size }) {
            lists.forEach { l -> if (round < l.size && out.size < max) out += l[round] }
            round++
        }
        return out
    }
}

data class StartersState(
    val cards: List<StarterCard> = emptyList(),
    val index: Int = 0,
    /** Made so far, in order, with the card each came from. */
    val added: List<Pair<StarterCard, Habit>> = emptyList(),
    /** The deck is over: [StarterDeck.PICK] added, or no cards left. */
    val finished: Boolean = false,
    /** Ticked on the first-tick screen, by habit id: count for count habits, 1 for plain ones. */
    val ticked: Map<String, Int> = emptyMap(),
    val evening: Boolean = true,
    val eveningText: String = "",
    val busy: Boolean = false,
) {
    val current: StarterCard? get() = cards.getOrNull(index)
    val canUndo: Boolean get() = index > 0 && !busy
}

/**
 * The last first-run step: pick up to three starter habits by swiping, then tick any already done
 * today, so a new user reaches their first tick before Today even opens.
 */
class V4StartersViewModel(
    private val service: HabitService,
    private val habits: HabitRepository,
    private val planAreas: PlanAreasRepository,
    private val integrations: IntegrationPrefs,
    private val nudgePrefs: NudgePrefs,
    private val nudges: NudgeService,
    private val checkIn: CheckInHabitUseCase,
    private val uncheck: UncheckHabitUseCase,
    private val award: AwardHabitCompletionUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(
        StartersState(
            cards = StarterDeck.forAreas(planAreas.enabledAreas.value, integrations.state.value.health),
            evening = nudgePrefs.state.value.evening,
            eveningText = eveningText(),
        )
    )
    val state: StateFlow<StartersState> = _state.asStateFlow()

    /** Left or right, from a swipe or a button. */
    fun swipe(dir: Swipe) {
        val s = _state.value
        val card = s.current ?: return
        if (s.busy) return
        if (dir != Swipe.RIGHT) {
            advance(s.added)
            return
        }
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val habit = runCatching {
                val st = card.starter
                service.create(st.title, st.type, st.schedule, st.target, st.unit, st.reminder, st.health, st.healthTarget, st.source, card.category)
            }.getOrNull()
            if (habit != null) {
                // Starter habits show on the Habits page, so it has to be on.
                val areas = planAreas.enabledAreas.value
                if (PlanArea.HABITS !in areas) planAreas.setEnabledAreas(areas + PlanArea.HABITS)
                PostHogAnalytics.capture("v4_starter_added", mapOf("title" to card.starter.title, "area" to card.area.key))
            }
            _state.update { it.copy(busy = false) }
            advance(_state.value.added + listOfNotNull(habit?.let { card to it }))
        }
    }

    private fun advance(added: List<Pair<StarterCard, Habit>>) {
        _state.update {
            val next = it.index + 1
            val done = added.size >= StarterDeck.PICK || next >= it.cards.size
            if (done && !it.finished) PostHogAnalytics.capture("v4_starter_deck_done", mapOf("added" to added.size, "seen" to next))
            it.copy(index = next, added = added, finished = done)
        }
    }

    /** Takes back the last card; a habit it made is removed again. */
    fun undo() {
        val s = _state.value
        if (!s.canUndo) return
        val prev = s.cards[s.index - 1]
        val last = s.added.lastOrNull()?.takeIf { it.first == prev }
        _state.update { it.copy(index = it.index - 1, finished = false, added = if (last != null) it.added.dropLast(1) else it.added) }
        if (last != null) viewModelScope.launch { runCatching { service.remove(last.second) } }
    }

    fun tick(habit: Habit) = viewModelScope.launch {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val s = _state.value
        runCatching {
            if (habit.targetCount > 1) {
                val c = habits.addCount(habit.id, today, 1)
                if (c.completed) award(habit.id, today)
                _state.update { it.copy(ticked = it.ticked + (habit.id to minOf(habit.targetCount, (s.ticked[habit.id] ?: 0) + 1))) }
            } else if (habit.id in s.ticked) {
                uncheck(habit.id, today)
                _state.update { it.copy(ticked = it.ticked - habit.id) }
            } else {
                checkIn(habit.id, today)
                award(habit.id, today)
                _state.update { it.copy(ticked = it.ticked + (habit.id to 1)) }
            }
        }
        if (s.ticked.isEmpty()) PostHogAnalytics.capture("v4_first_tick", emptyMap())
    }

    fun setEvening(on: Boolean) {
        nudgePrefs.setEvening(on)
        nudges.replan()
        _state.update { it.copy(evening = on) }
    }

    private fun eveningText(): String {
        val m = nudgePrefs.state.value.eveningMinute ?: nudges.learned.value ?: HabitLearning.DEFAULT_CHECK_IN
        return "Evening check-in, ${HabitLearning.clock(m)}"
    }
}
