package az.tribe.lifeplanner.ui.v4.quickadd

import az.tribe.lifeplanner.domain.repository.TripRepository
import az.tribe.lifeplanner.domain.service.TripPlanner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.core.CurrencyPrefs
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.mapper.createNewHabit
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.HabitFrequency
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.repository.PlanAreasRepository
import az.tribe.lifeplanner.domain.service.ParsedEntry
import az.tribe.lifeplanner.domain.service.ParsedInput
import az.tribe.lifeplanner.domain.service.QuickAddParser
import az.tribe.lifeplanner.ui.v4.components.areaName
import az.tribe.lifeplanner.usecases.habit.AwardHabitCompletionUseCase
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class QuickAddState(
    val text: String = "",
    val parsed: ParsedInput? = null,
    val saving: Boolean = false,
    /** Set after a save: what to tell the user, e.g. "Saved to Meals and Money". */
    val savedMessage: String? = null,
)

/**
 * "Add anything". Parses as the user types and saves one row per area the words belong to, so a
 * restaurant lunch lands in Meals and in Money without being typed twice.
 */
@OptIn(ExperimentalUuidApi::class)
class QuickAddViewModel(
    private val logs: LifeLogRepository,
    private val habits: HabitRepository,
    private val planAreas: PlanAreasRepository,
    private val currency: CurrencyPrefs,
    private val awardHabitCompletion: AwardHabitCompletionUseCase,
    private val trips: TripRepository,
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private val _state = MutableStateFlow(QuickAddState())
    val state: StateFlow<QuickAddState> = _state.asStateFlow()

    fun onText(text: String) {
        val parsed = QuickAddParser.parse(text, Clock.System.now().toLocalDateTime(tz), currency.code)
        _state.value = QuickAddState(text = text, parsed = parsed)
    }

    fun reset() {
        _state.value = QuickAddState()
    }

    fun save() {
        val input = _state.value.parsed ?: return
        if (input.entries.isEmpty() || _state.value.saving) return
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch {
            runCatching {
                val group = Uuid.random().toString()
                // Travel spends go to the trip under way or coming up in the next two months.
                val today = input.occurredAt.date
                val trip = TripPlanner.current(trips.getAll(), today)?.takeIf { it.startDate.toEpochDays() - today.toEpochDays() <= 60 }
                val logRows = input.entries.filter { !it.isRoutine }.map { e ->
                    e.toLog(input, group).let { l -> if (trip != null && l.category == "travel") l.copy(tripId = trip.id) else l }
                }
                logs.saveAll(logRows)
                input.entries.filter { it.isRoutine }.forEach { addRoutine(it) }
                input.entries.firstOrNull { it.kind == LogKind.WATER }?.let { tickWaterHabit(it) }

                // Logging into an area turns it on: the entry should be visible somewhere.
                val areas = input.entries.map { it.area }.toSet()
                val enabled = planAreas.enabledAreas.value
                if (!enabled.containsAll(areas)) planAreas.setEnabledAreas(enabled + areas)

                PostHogAnalytics.capture(
                    "v4_quick_add_saved",
                    mapOf("areas" to areas.joinToString(",") { it.key }, "count" to input.entries.size),
                )
                val names = PlanArea.entries.filter { it in areas }.map { areaName(it) }
                _state.value = _state.value.copy(saving = false, savedMessage = "Saved to ${joinNames(names)}")
            }.onFailure {
                Logger.w("QuickAdd") { "Save failed: ${it.message}" }
                _state.value = _state.value.copy(saving = false, savedMessage = "Could not save that. Try again.")
            }
        }
    }

    private fun ParsedEntry.toLog(input: ParsedInput, group: String) = LifeLog(
        id = Uuid.random().toString(),
        area = area,
        kind = kind ?: LogKind.NOTE,
        title = title,
        amount = amount,
        currency = currency,
        category = category,
        quantity = quantity,
        unit = unit,
        durationMin = durationMin,
        occurredAt = input.occurredAt,
        source = LifeLog.SOURCE_QUICK_ADD,
        externalId = group,
        notes = notes,
    )

    private suspend fun addRoutine(e: ParsedEntry) {
        val category = when (e.area) {
            PlanArea.FITNESS -> GoalCategory.BODY
            PlanArea.MONEY -> GoalCategory.MONEY
            PlanArea.MIND -> GoalCategory.WELLBEING
            PlanArea.STUDY, PlanArea.CAREER -> GoalCategory.CAREER
            else -> GoalCategory.WELLBEING
        }
        habits.insertHabit(createNewHabit(title = e.title, category = category, frequency = HabitFrequency.DAILY))
    }

    /** "2 glasses of water" also ticks a water habit, if there is one. */
    private suspend fun tickWaterHabit(e: ParsedEntry) {
        val today = Clock.System.now().toLocalDateTime(tz).date
        val habit = habits.getAllHabits().firstOrNull { it.isActive && "water" in it.title.lowercase() } ?: return
        if (habits.getCheckInByHabitAndDate(habit.id, today)?.completed == true) return
        val n = (e.quantity ?: 1.0).toInt().coerceAtLeast(1)
        val checkIn = if (habit.targetCount > 1) habits.addCount(habit.id, today, n) else habits.checkIn(habit.id, today)
        if (checkIn.completed) awardHabitCompletion(habit.id, today)
    }

    companion object {
        fun joinNames(names: List<String>): String = when (names.size) {
            0 -> ""
            1 -> names[0]
            else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
        }
    }
}
