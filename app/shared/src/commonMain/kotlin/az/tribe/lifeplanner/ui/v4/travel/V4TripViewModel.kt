package az.tribe.lifeplanner.ui.v4.travel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.calendar.CalendarWriter
import az.tribe.lifeplanner.data.integrations.DataFlow
import az.tribe.lifeplanner.data.integrations.IntegrationPrefs
import az.tribe.lifeplanner.data.money.FxRates
import az.tribe.lifeplanner.data.travel.BookingReader
import az.tribe.lifeplanner.data.travel.TripWeather
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.model.Trip
import az.tribe.lifeplanner.domain.model.TripItem
import az.tribe.lifeplanner.domain.model.TripItemKind
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.repository.TripRepository
import az.tribe.lifeplanner.domain.service.Booking
import az.tribe.lifeplanner.domain.service.Bookings
import az.tribe.lifeplanner.domain.service.DayForecast
import az.tribe.lifeplanner.domain.service.Fx
import az.tribe.lifeplanner.domain.service.FxTable
import az.tribe.lifeplanner.domain.service.TripMeta
import az.tribe.lifeplanner.domain.service.TripPlanner
import az.tribe.lifeplanner.ui.v4.areas.SpendRow
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
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** One line a booking puts on a day of the timeline. */
data class DayBooking(val item: TripItem, val booking: Booking, val text: String)

/** One row of "Your days": the bookings that touch it, then the one-line plan. */
data class TripDay(val date: LocalDate, val plan: TripItem?, val forecast: DayForecast?, val bookings: List<DayBooking> = emptyList())

/** The trip's money while it is under way: "¥8,400 left today", "About €52. €364 left of €1,600 for the next 7 days." */
data class WalletView(val big: String, val word: String, val sub: String)

/** Pasting a booking: reading it, then an editable preview, never a silent save. */
sealed interface BookingDraft {
    data object Idle : BookingDraft
    data object Reading : BookingDraft
    /** [version] changes when the list itself changes (read, or one left out), so the fields start fresh. */
    data class Found(val bookings: List<Booking>, val version: Int = 0) : BookingDraft
    data class Failed(val message: String) : BookingDraft
}

data class TripState(
    val trip: Trip? = null,
    val range: String = "",
    val countdown: String = "",
    val budget: TripPlanner.TripBudget = TripPlanner.TripBudget(0.0, null, emptyList()),
    val spends: List<LifeLog> = emptyList(),
    val spendRows: List<SpendRow> = emptyList(),
    val todos: List<TripItem> = emptyList(),
    val packHint: String? = null,
    val days: List<TripDay> = emptyList(),
    val bookings: List<Pair<TripItem, Booking>> = emptyList(),
    val onCalendar: Boolean = false,
    val canCalendar: Boolean = false,
    val gone: Boolean = false,
    /** The destination's money when it is not the budget's, e.g. JPY on a euro trip. */
    val local: String? = null,
    val wallet: WalletView? = null,
    val unconverted: String? = null,
    val active: Boolean = false,
    val recap: TripPlanner.Recap? = null,
    val draft: BookingDraft = BookingDraft.Idle,
)

/** One trip: its wallet, bookings, what to do before, the days, and travel mode. */
@OptIn(ExperimentalUuidApi::class)
class V4TripViewModel(
    private val tripId: String,
    private val trips: TripRepository,
    private val logs: LifeLogRepository,
    private val weather: TripWeather,
    private val calendar: CalendarWriter,
    private val prefs: IntegrationPrefs,
    private val settings: Settings,
    private val fx: FxRates,
    private val reader: BookingReader,
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)

    private val forecast = MutableStateFlow<List<DayForecast>>(emptyList())
    private val calendarState = MutableStateFlow(settings.hasKey(eventKey()) to false)
    private val draft = MutableStateFlow<BookingDraft>(BookingDraft.Idle)
    private val avgHigh = MutableStateFlow(settings.getIntOrNull(highKey()))

    private val trip = trips.observeAll().map { all -> all.firstOrNull { it.id == tripId } }

    val state: StateFlow<TripState> = combine(
        combine(trip, trips.observeItems(tripId), logs.observeForTrip(tripId), ::Triple),
        combine(forecast, avgHigh, ::Pair),
        calendarState,
        fx.table,
        draft,
    ) { (t, items, spends), (fc, high), (onCal, canCal), rates, d ->
        if (t == null) return@combine TripState(gone = true)
        build(t, items, spends, fc, high, onCal, canCal, rates, d)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripState())

    private fun build(
        t: Trip, items: List<TripItem>, spends: List<LifeLog>, fc: List<DayForecast>, high: Int?,
        onCal: Boolean, canCal: Boolean, rates: FxTable?, d: BookingDraft,
    ): TripState {
        val today = today()
        val money = spends.filter { it.kind == LogKind.EXPENSE }
        val plans = items.filter { it.kind == TripItemKind.DAY && it.date != null }.associateBy { it.date!! }
        val bookings = items.filter { it.kind == TripItemKind.BOOKING }.mapNotNull { i -> Bookings.decode(i.notes)?.let { i to it } }
        val byDate = fc.associateBy { it.date }
        val home = t.currency
        val local = TripMeta.of(t).localCurrency?.takeIf { it != home }
        val budget = TripPlanner.budget(t, money, rates)
        val days = TripPlanner.days(t).map { date ->
            val lines = bookings.flatMap { (i, b) -> Bookings.linesFor(b, date).map { DayBooking(i, b, it) } }
            TripDay(date, plans[date], byDate[date], lines)
        }
        val wallet = TripPlanner.wallet(t, money, today, rates)?.let { w ->
            val inLocal = local?.let { Fx.convert(rates, w.leftToday, home, it) }
            val big = if (inLocal != null) MoneyFormat.format(kotlin.math.abs(inLocal), local) else MoneyFormat.format(kotlin.math.abs(w.leftToday), home)
            val about = if (inLocal != null) "About ${MoneyFormat.format(kotlin.math.abs(w.leftToday), home)}. " else ""
            val rest = if (w.daysLeft <= 1) "Last day of the trip." else "${MoneyFormat.format(w.leftTotal, home)} left of ${MoneyFormat.format(t.budget!!, home)} for the next ${w.daysLeft} days."
            WalletView(big, if (w.leftToday < 0) "over today" else "left today", about + rest)
        }
        val recap = if (TripPlanner.isOver(t, today)) {
            val planned = days.filter { it.plan != null || it.bookings.isNotEmpty() }.map { it.date }.toSet()
            TripPlanner.recap(t, money, planned, high, rates)
        } else null
        return TripState(
            trip = t,
            range = TripPlanner.range(t),
            countdown = TripPlanner.countdown(t, today),
            budget = budget,
            spends = money,
            spendRows = money.map { l ->
                val other = l.currency != null && l.currency != home
                SpendRow(
                    l, MoneyFormat.format(l.amount ?: 0.0, l.currency),
                    if (!other) null else Fx.convert(rates, l.amount ?: 0.0, l.currency, home)?.let { MoneyFormat.format(it, home) } ?: "not converted yet",
                )
            },
            todos = items.filter { it.kind == TripItemKind.TODO },
            packHint = TripPlanner.packHint(fc),
            days = days,
            bookings = bookings,
            onCalendar = onCal,
            canCalendar = canCal,
            local = local,
            wallet = wallet,
            unconverted = Fx.unconvertedLine(az.tribe.lifeplanner.domain.service.MoneyTotal(budget.spent, budget.unconverted)),
            active = TripPlanner.isActive(t, today),
            recap = recap,
            draft = d,
        )
    }

    init {
        viewModelScope.launch {
            val p = prefs.state.value
            val can = p.calendar && p.isOn(DataFlow.EVENTS_OUT) && runCatching { calendar.canWrite() }.getOrDefault(false)
            calendarState.value = calendarState.value.first to can
            // The place is looked up right after the trip is made, so wait for it if it is not in yet.
            val t = trip.filterNotNull().first()
            // Trips made before the wallet have no country yet: one lookup fills it in.
            val needsPlace = t.latitude == null || TripMeta.of(t).countryCode == null
            val place = if (needsPlace) weather.find(t.destination) else null
            if (place != null) {
                val fresh = trips.getById(t.id) ?: t
                trips.save(TripMeta.withCountry(fresh.copy(latitude = fresh.latitude ?: place.latitude, longitude = fresh.longitude ?: place.longitude), place.countryCode))
            }
            val lat = t.latitude ?: place?.latitude
            val lon = t.longitude ?: place?.longitude
            if (lat != null && lon != null) {
                forecast.value = weather.forecast(lat, lon, t.startDate, t.endDate, today())
                // A finished trip remembers its weather for the recap; Open-Meteo keeps about three months.
                if (TripPlanner.isOver(t, today()) && avgHigh.value == null && today().minus(DatePeriod(days = 85)) <= t.endDate) {
                    weather.averageHigh(lat, lon, t.startDate, t.endDate)?.let { settings.putInt(highKey(), it); avgHigh.value = it }
                }
            }
        }
        viewModelScope.launch { fx.refresh() }
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
        viewModelScope.launch {
            trips.deleteItem(item.id)
            // A booking's price goes with it.
            if (item.kind == TripItemKind.BOOKING) state.value.spends.filter { it.externalId == bookingMarker(item.id) }.forEach { logs.delete(it.id) }
        }
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

    fun addSpend(amount: Double, kind: TripPlanner.SpendKind, note: String, currency: String? = null) {
        val t = state.value.trip ?: return
        if (amount <= 0) return
        viewModelScope.launch {
            // Spends before the trip (flights, hotel) are dated today; spends during it on the day.
            logs.save(
                LifeLog(
                    id = Uuid.random().toString(), area = PlanArea.MONEY, kind = LogKind.EXPENSE,
                    title = TripPlanner.SpendKind.titleFor(kind, note), amount = amount, currency = currency ?: t.currency,
                    category = "travel", occurredAt = Clock.System.now().toLocalDateTime(tz), tripId = t.id,
                ),
            )
            PostHogAnalytics.capture("v4_trip_spend", mapOf("kind" to kind.name.lowercase(), "local" to (currency != null && currency != t.currency)))
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

    // ── Paste a booking ──

    fun readBooking(text: String) {
        val t = state.value.trip ?: return
        if (text.isBlank()) return
        draft.value = BookingDraft.Reading
        viewModelScope.launch {
            val found = reader.read(text, t.destination, t.startDate)
            draft.value = when {
                found == null -> BookingDraft.Failed("Could not read that just now. Check the connection and try again.")
                found.isEmpty() -> BookingDraft.Failed("I could not find a flight, hotel or train in that. Try pasting the whole confirmation.")
                else -> BookingDraft.Found(found)
            }
        }
    }

    fun editDraft(index: Int, b: Booking) {
        val d = draft.value as? BookingDraft.Found ?: return
        draft.value = d.copy(bookings = d.bookings.mapIndexed { i, old -> if (i == index) b else old })
    }

    fun dropDraft(index: Int) {
        val d = draft.value as? BookingDraft.Found ?: return
        val left = d.bookings.filterIndexed { i, _ -> i != index }
        draft.value = if (left.isEmpty()) BookingDraft.Idle else BookingDraft.Found(left, d.version + 1)
    }

    fun clearDraft() {
        draft.value = BookingDraft.Idle
    }

    /**
     * Saves what the preview shows: each booking on the days it touches, its price as a trip spend
     * (Flights, Stay, Getting around), and the trip's dates stretched to fit when a booking falls
     * just outside them.
     */
    fun saveDraft() {
        val d = draft.value as? BookingDraft.Found ?: return
        val t = state.value.trip ?: return
        viewModelScope.launch {
            var start = t.startDate
            var end = t.endDate
            val now = Clock.System.now().toLocalDateTime(tz)
            d.bookings.forEachIndexed { i, b ->
                val id = Uuid.random().toString()
                trips.saveItem(
                    TripItem(id, tripId, TripItemKind.BOOKING, "${b.kind.label}: ${b.name}", notes = Bookings.encode(b), date = b.startDate ?: t.startDate, sortOrder = state.value.bookings.size + i),
                )
                b.price?.let { price ->
                    logs.save(
                        LifeLog(
                            id = Uuid.random().toString(), area = PlanArea.MONEY, kind = LogKind.EXPENSE,
                            title = TripPlanner.SpendKind.titleFor(b.kind.spendKind, b.name), amount = price, currency = b.currency ?: t.currency,
                            category = "travel", occurredAt = now, tripId = t.id, externalId = bookingMarker(id),
                        ),
                    )
                }
                b.startDate?.let { s -> if (s < start && start.minus(DatePeriod(days = STRETCH_DAYS)) <= s) start = s }
                b.endDate?.let { e -> if (e > end && end.plus(DatePeriod(days = STRETCH_DAYS)) >= e) end = e }
            }
            if (start != t.startDate || end != t.endDate) trips.getById(t.id)?.let { trips.save(it.copy(startDate = start, endDate = end)) }
            PostHogAnalytics.capture(
                "v4_travel_booking_saved",
                mapOf("count" to d.bookings.size, "kinds" to d.bookings.joinToString(",") { it.kind.key }, "priced" to d.bookings.count { it.price != null }),
            )
            draft.value = BookingDraft.Idle
        }
    }

    fun recapShared() = PostHogAnalytics.capture("v4_travel_recap_shared", mapOf("where" to "trip"))

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
            state.value.bookings.forEach { trips.deleteItem(it.first.id) }
            trips.delete(tripId)
            onDone()
        }
    }

    private fun eventKey() = "v4_cal_trip_$tripId"
    private fun highKey() = "v4_trip_high_$tripId"

    companion object {
        /** How far outside the trip a booking can fall and still stretch its dates. */
        const val STRETCH_DAYS = 3

        fun bookingMarker(itemId: String) = "booking:$itemId"
    }
}
