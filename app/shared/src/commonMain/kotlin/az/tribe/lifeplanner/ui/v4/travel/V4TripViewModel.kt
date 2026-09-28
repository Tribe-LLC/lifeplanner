package az.tribe.lifeplanner.ui.v4.travel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.calendar.CalendarWriter
import az.tribe.lifeplanner.data.integrations.DataFlow
import az.tribe.lifeplanner.data.integrations.IntegrationPrefs
import az.tribe.lifeplanner.data.travel.TripWeather
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.model.Trip
import az.tribe.lifeplanner.domain.model.TripItem
import az.tribe.lifeplanner.domain.model.TripItemKind
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.repository.TripRepository
import az.tribe.lifeplanner.domain.service.DayForecast
import az.tribe.lifeplanner.domain.service.TripPlanner
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** One row of "Your days". */
data class TripDay(val date: LocalDate, val plan: TripItem?, val forecast: DayForecast?)

data class TripState(
    val trip: Trip? = null,
    val range: String = "",
    val countdown: String = "",
    val budget: TripPlanner.TripBudget = TripPlanner.TripBudget(0.0, null, emptyList()),
    val spends: List<LifeLog> = emptyList(),
    val todos: List<TripItem> = emptyList(),
    val packHint: String? = null,
    val days: List<TripDay> = emptyList(),
    val onCalendar: Boolean = false,
    val canCalendar: Boolean = false,
    val gone: Boolean = false,
)

/** One trip: its money, what to do before, the days, and travel mode. */
@OptIn(ExperimentalUuidApi::class)
class V4TripViewModel(
    private val tripId: String,
    private val trips: TripRepository,
    private val logs: LifeLogRepository,
    private val weather: TripWeather,
    private val calendar: CalendarWriter,
    private val prefs: IntegrationPrefs,
    private val settings: Settings,
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)

    private val forecast = MutableStateFlow<List<DayForecast>>(emptyList())
    private val calendarState = MutableStateFlow(settings.hasKey(eventKey()) to false)

    private val trip = trips.observeAll().map { all -> all.firstOrNull { it.id == tripId } }

    val state: StateFlow<TripState> = combine(
        trip,
        trips.observeItems(tripId),
        logs.observeForTrip(tripId),
        forecast,
        calendarState,
    ) { t, items, spends, fc, (onCal, canCal) ->
        if (t == null) return@combine TripState(gone = true)
        val money = spends.filter { it.kind == LogKind.EXPENSE }
        val plans = items.filter { it.kind == TripItemKind.DAY && it.date != null }.associateBy { it.date!! }
        val byDate = fc.associateBy { it.date }
        TripState(
            trip = t,
            range = TripPlanner.range(t),
            countdown = TripPlanner.countdown(t, today()),
            budget = TripPlanner.budget(t, money),
            spends = money,
            todos = items.filter { it.kind == TripItemKind.TODO },
            packHint = TripPlanner.packHint(fc),
            days = TripPlanner.days(t).map { d -> TripDay(d, plans[d], byDate[d]) },
            onCalendar = onCal,
            canCalendar = canCal,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripState())

    init {
        viewModelScope.launch {
            val p = prefs.state.value
            val can = p.calendar && p.isOn(DataFlow.EVENTS_OUT) && runCatching { calendar.canWrite() }.getOrDefault(false)
            calendarState.value = calendarState.value.first to can
            // The place is looked up right after the trip is made, so wait for it if it is not in yet.
            val t = trip.filterNotNull().first()
            val located = if (t.latitude == null) (weather.find(t.destination)?.also { place ->
                trips.save(t.copy(latitude = place.latitude, longitude = place.longitude))
            }?.let { it.latitude to it.longitude }) else t.latitude to t.longitude!!
            located?.let { (lat, lon) -> forecast.value = weather.forecast(lat, lon, t.startDate, t.endDate, today()) }
        }
    }

    fun toggle(item: TripItem) {
        viewModelScope.launch { trips.saveItem(item.copy(isDone = !item.isDone)) }
    }

    fun addTodo(title: String) {
        val t = title.trim().takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            trips.saveItem(TripItem(Uuid.random().toString(), tripId, TripItemKind.TODO, t, sortOrder = state.value.todos.size))
        }
    }

    fun removeItem(item: TripItem) {
        viewModelScope.launch { trips.deleteItem(item.id) }
    }

    /** Sets what a day is for. Blank clears it back to a free day. */
    fun setDayPlan(date: LocalDate, text: String) {
        viewModelScope.launch {
            val existing = state.value.days.firstOrNull { it.date == date }?.plan
            val t = text.trim()
            when {
                t.isEmpty() && existing != null -> trips.deleteItem(existing.id)
                t.isEmpty() -> {}
                else -> trips.saveItem(existing?.copy(title = t) ?: TripItem(Uuid.random().toString(), tripId, TripItemKind.DAY, t, date = date))
            }
        }
    }

    fun addSpend(amount: Double, kind: TripPlanner.SpendKind, note: String) {
        val t = state.value.trip ?: return
        if (amount <= 0) return
        viewModelScope.launch {
            // Spends before the trip (flights, hotel) are dated today; spends during it on the day.
            logs.save(
                LifeLog(
                    id = Uuid.random().toString(), area = PlanArea.MONEY, kind = LogKind.EXPENSE,
                    title = TripPlanner.SpendKind.titleFor(kind, note), amount = amount, currency = t.currency,
                    category = "travel", occurredAt = Clock.System.now().toLocalDateTime(tz), tripId = t.id,
                ),
            )
            PostHogAnalytics.capture("v4_trip_spend", mapOf("kind" to kind.name.lowercase()))
        }
    }

    fun removeSpend(id: String) {
        viewModelScope.launch { logs.delete(id) }
    }

    fun setBudget(amount: Double?) {
        val t = state.value.trip ?: return
        viewModelScope.launch { trips.save(t.copy(budget = amount?.takeIf { it > 0 })) }
    }

    fun setTravelMode(on: Boolean) {
        val t = state.value.trip ?: return
        viewModelScope.launch {
            trips.save(t.copy(travelMode = on))
            PostHogAnalytics.capture("v4_travel_mode", mapOf("on" to on))
        }
    }

    fun toggleCalendar() {
        val t = state.value.trip ?: return
        viewModelScope.launch {
            val existing = settings.getStringOrNull(eventKey())
            if (existing != null) {
                calendar.deleteEvent(existing)
                settings.remove(eventKey())
            } else {
                val start = t.startDate.atStartOfDayIn(tz).toEpochMilliseconds()
                val end = t.endDate.plus(DatePeriod(days = 1)).atStartOfDayIn(tz).toEpochMilliseconds()
                calendar.addEvent("Trip to ${t.destination}", start, end, "Planned in LifePlanner")?.let { settings.putString(eventKey(), it) }
            }
            calendarState.value = settings.hasKey(eventKey()) to calendarState.value.second
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.getStringOrNull(eventKey())?.let { calendar.deleteEvent(it); settings.remove(eventKey()) }
            state.value.todos.forEach { trips.deleteItem(it.id) }
            state.value.days.mapNotNull { it.plan }.forEach { trips.deleteItem(it.id) }
            trips.delete(tripId)
            onDone()
        }
    }

    private fun eventKey() = "v4_cal_trip_$tripId"
}
