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
import az.tribe.lifeplanner.data.meals.MealService
import az.tribe.lifeplanner.domain.repository.PlanAreasRepository
import az.tribe.lifeplanner.domain.service.ParsedEntry
import az.tribe.lifeplanner.domain.service.ParsedInput
import az.tribe.lifeplanner.domain.service.QuickAddParser
import az.tribe.lifeplanner.data.plans.PlanBoard
import az.tribe.lifeplanner.data.plans.PlanView
import az.tribe.lifeplanner.domain.service.PlanLineParser
import az.tribe.lifeplanner.domain.service.PlanProgress
import az.tribe.lifeplanner.ui.v4.components.areaName
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.data.money.FxRates
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.Trip
import az.tribe.lifeplanner.domain.service.Fx
import az.tribe.lifeplanner.domain.service.TripMeta
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
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
    /** A spend in another currency, in the home one: "About €7.40". */
    val approx: String? = null,
    /** Set while a trip is under way: its place, since plain amounts are then in its money. */
    val tripPlace: String? = null,
    /** The line reads as a plan: a card offers to make it one, unless the user chose to log it. */
    val plan: PlanOffer? = null,
    /** What each entry does for a plan, by position: "Counts toward Run a 5K". */
    val planNotes: List<String?> = emptyList(),
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
    private val trips: TripRepository,
    private val meals: MealService,
    private val fx: FxRates,
    board: PlanBoard,
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private val _state = MutableStateFlow(QuickAddState())
    val state: StateFlow<QuickAddState> = _state.asStateFlow()

    /** Plans as they stand, to say what a run or money put aside counts toward. */
    private var plans: List<PlanView> = emptyList()
    /** "Log a run instead" was tapped for this line. */
    private var logInstead = false

    /** The trip under way, if any: while it lasts, "ramen 1200" is in its local money and belongs to it. */
    private var activeTrip: Trip? = null

    init {
        viewModelScope.launch {
            runCatching {
                val today = Clock.System.now().toLocalDateTime(tz).date
                activeTrip = trips.getAll().firstOrNull { TripPlanner.isActive(it, today) }
                if (_state.value.text.isNotEmpty()) onText(_state.value.text)
            }
            runCatching { fx.refresh() }
        }
        viewModelScope.launch {
            board.plans.collect { list ->
                plans = list
                if (_state.value.text.isNotEmpty() && _state.value.savedMessage == null) onText(_state.value.text)
            }
        }
    }

    private fun defaultCurrency(): String = activeTrip?.let { TripMeta.of(it).localCurrency } ?: currency.code

    fun onText(text: String) {
        if (text != _state.value.text) logInstead = false
        val parsed = QuickAddParser.parse(text, Clock.System.now().toLocalDateTime(tz), defaultCurrency())
        val home = currency.code
        val approx = parsed.entries.firstOrNull { it.area == PlanArea.MONEY && it.amount != null && it.currency != null && it.currency != home }?.let { e ->
            Fx.convert(fx.table.value, e.amount!!, e.currency, home)?.let { "About ${MoneyFormat.format(it, home)}" }
        }
        val place = activeTrip?.takeIf { TripMeta.of(it).localCurrency?.let { c -> c != home } == true }?.destination
        val offer = if (parsed.looksLikePlan && !logInstead) QuickAddPlans.offer(text, parsed.occurredAt.date, currency.code) else null
        val subject = PlanLineParser.putAside(text, defaultCurrency())?.subject
        _state.value = QuickAddState(
            text = text, parsed = parsed, approx = approx, tripPlace = place, plan = offer,
            planNotes = parsed.entries.map { QuickAddPlans.note(it, plans, subject) },
        )
    }

    /** Keeps the line as the log it also reads as, not a plan. */
    fun logItInstead() {
        logInstead = true
        onText(_state.value.text)
        PostHogAnalytics.capture("v4_plan_offer_declined")
    }

    fun reset() {
        logInstead = false
        _state.value = QuickAddState()
    }

    fun save() {
        val input = _state.value.parsed ?: return
        if (input.entries.isEmpty() || _state.value.saving || _state.value.plan != null) return
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch {
            runCatching {
                val group = Uuid.random().toString()
                // Travel spends go to the trip under way or coming up in the next two months.
                val today = input.occurredAt.date
                val trip = TripPlanner.current(trips.getAll(), today)?.takeIf { it.startDate.toEpochDays() - today.toEpochDays() <= 60 }
                // While a trip is under way every spend is part of it; before it, only travel ones.
                val onTrip = trip != null && TripPlanner.isActive(trip, today)
                val saveFor = QuickAddPlans.savingsPlan(plans, PlanLineParser.putAside(_state.value.text, defaultCurrency())?.subject)
                val logRows = input.entries.filter { !it.isRoutine }.map { e ->
                    e.toLog(input, group).let { l ->
                        when {
                            e.isBill -> l
                            // Money put aside is tied to its plan, and is never part of a trip's spending.
                            l.category == PlanProgress.SAVINGS -> l.copy(externalId = saveFor?.id)
                            trip != null && l.kind == LogKind.EXPENSE && (onTrip || l.category == "travel") -> l.copy(tripId = trip.id)
                            else -> l
                        }
                    }
                }
                logs.saveAll(logRows)
                input.entries.filter { it.isRoutine }.forEach { addRoutine(it) }
                // Water also goes to Health and ticks the water habit, as it does from the Meals page.
                input.entries.firstOrNull { it.kind == LogKind.WATER }?.let { meals.addWater((it.quantity ?: 1.0).toInt().coerceAtLeast(1), fromQuickAdd = true) }

                // Logging into an area turns it on: the entry should be visible somewhere.
                val areas = input.entries.map { it.area }.toSet()
                val enabled = planAreas.enabledAreas.value
                if (!enabled.containsAll(areas)) planAreas.setEnabledAreas(enabled + areas)

                input.entries.filter { it.isBill }.forEach { b ->
                    PostHogAnalytics.capture("v4_money_bill_added", mapOf("repeat" to (b.bill?.repeat?.key ?: ""), "source" to "quick_add"))
                }
                if (onTrip && logRows.any { it.tripId != null && it.currency != currency.code }) PostHogAnalytics.capture("v4_travel_local_spend")
                if (logRows.any { it.category == PlanProgress.SAVINGS }) {
                    PostHogAnalytics.capture("v4_plan_put_aside", mapOf("source" to "quick_add", "linked" to (saveFor != null)))
                }
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
        // A bill waits as planned on its next due date, with how it repeats in the notes.
        status = if (bill != null) LogStatus.PLANNED else LogStatus.DONE,
        title = title,
        amount = amount,
        currency = currency,
        category = category,
        quantity = quantity,
        unit = unit,
        durationMin = durationMin,
        occurredAt = firstDue?.let { LocalDateTime(it, LocalTime(9, 0)) } ?: input.occurredAt,
        source = if (bill != null) LifeLog.SOURCE_PLAN else LifeLog.SOURCE_QUICK_ADD,
        externalId = if (bill != null) null else group,
        notes = bill?.encode() ?: notes,
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

    companion object {
        fun joinNames(names: List<String>): String = when (names.size) {
            0 -> ""
            1 -> names[0]
            else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
        }
    }
}
