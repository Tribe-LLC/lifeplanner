package az.tribe.lifeplanner.ui.v4.travel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.core.CurrencyPrefs
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.travel.TripWeather
import az.tribe.lifeplanner.domain.model.Trip
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.repository.PlanAreasRepository
import az.tribe.lifeplanner.domain.repository.TripRepository
import az.tribe.lifeplanner.domain.service.TripPlanner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class TripRow(val trip: Trip, val range: String, val countdown: String, val active: Boolean)

data class TravelState(
    val upcoming: List<TripRow> = emptyList(),
    val past: List<TripRow> = emptyList(),
    val currency: String = "EUR",
    val loaded: Boolean = false,
)

/** The Travel area page: trips coming up and gone, and making a new one. */
@OptIn(ExperimentalUuidApi::class)
class V4TravelViewModel(
    private val trips: TripRepository,
    private val weather: TripWeather,
    private val currencyPrefs: CurrencyPrefs,
    private val planAreas: PlanAreasRepository,
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)

    val state: StateFlow<TravelState> = trips.observeAll().map { all ->
        val today = today()
        fun row(t: Trip) = TripRow(t, TripPlanner.range(t), TripPlanner.countdown(t, today), TripPlanner.isActive(t, today))
        TravelState(
            upcoming = all.filter { !TripPlanner.isOver(it, today) }.sortedBy { it.startDate }.map(::row),
            past = all.filter { TripPlanner.isOver(it, today) }.sortedByDescending { it.startDate }.map(::row),
            currency = currencyPrefs.code,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TravelState(currency = currencyPrefs.code))

    /** Saves the trip at once, then looks the place up in the background for its weather. */
    fun create(destination: String, start: LocalDate, end: LocalDate, budget: Double?, onCreated: (String) -> Unit) {
        val name = TripPlanner.placeName(destination)
        if (name.isBlank()) return
        viewModelScope.launch {
            val trip = Trip(
                id = Uuid.random().toString(),
                destination = name,
                startDate = minOf(start, end),
                endDate = maxOf(start, end),
                budget = budget?.takeIf { it > 0 },
                currency = currencyPrefs.code,
            )
            trips.save(trip)
            TripPlanner.defaultChecklist(trip.id).forEach { trips.saveItem(it) }
            planAreas.setEnabledAreas(planAreas.enabledAreas.value + PlanArea.TRAVEL)
            PostHogAnalytics.capture(
                "v4_trip_created",
                mapOf("days" to TripPlanner.days(trip).size, "in_days" to (trip.startDate.toEpochDays() - today().toEpochDays()), "budget" to (budget != null)),
            )
            onCreated(trip.id)
            weather.find(name)?.let { place ->
                trips.getById(trip.id)?.let { trips.save(it.copy(latitude = place.latitude, longitude = place.longitude)) }
            }
        }
    }
}
