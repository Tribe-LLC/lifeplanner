package az.tribe.lifeplanner.ui.v4.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.money.FxRates
import az.tribe.lifeplanner.data.travel.TripWeather
import az.tribe.lifeplanner.di.createFileSharer
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.Trip
import az.tribe.lifeplanner.domain.model.TripItemKind
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.repository.TripRepository
import az.tribe.lifeplanner.domain.service.Bookings
import az.tribe.lifeplanner.domain.service.TripPlanner
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.theme.V4
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.X
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * The card a finished trip leaves: "Tokyo, 9 days", what it cost and mostly on what, the weather,
 * how many days had a plan, with Share (as text) and "Plan the next one". Dark on light, light on
 * dark, from the inverse tokens.
 */
@Composable
fun TripRecapCard(
    recap: TripPlanner.Recap,
    onPlanNext: () -> Unit,
    onShared: () -> Unit,
    modifier: Modifier = Modifier,
    onHide: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
) {
    val c = V4.colors
    val sharer = remember { createFileSharer() }
    var shared by remember { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.inverse)
            .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClickLabel = "Open the trip", onClick = onOpen) else Modifier)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Welcome back", style = V4.type.label, color = c.onInverse.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
            if (onHide != null) {
                IconButton(onClick = onHide, modifier = Modifier.size(44.dp)) {
                    Icon(PhosphorIcons.Regular.X, contentDescription = "Hide the recap", tint = c.onInverse.copy(alpha = 0.75f), modifier = Modifier.size(20.dp))
                }
            }
        }
        Text(
            "${recap.destination}, ${recap.days} ${if (recap.days == 1) "day" else "days"}",
            style = V4.type.title, color = c.onInverse, modifier = Modifier.semantics { heading() },
        )
        val tiles = listOfNotNull(
            recap.spent.takeIf { it > 0 }?.let { MoneyFormat.format(it, recap.currency) to (recap.mostly?.let { k -> "spent, mostly ${k.label}" } ?: "spent") },
            recap.avgHigh?.let { "$it°C" to "average high" },
            "${recap.plannedDays} of ${recap.days}" to "days planned",
            recap.underBudget?.let { MoneyFormat.format(it, recap.currency) to "under budget" },
        )
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (big, small) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(c.onInverse.copy(alpha = 0.1f)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(big, style = V4.type.headline, color = c.onInverse)
                        Text(small, style = V4.type.caption, color = c.onInverse.copy(alpha = 0.8f))
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            V4PillButton(
                if (shared) "Shared" else "Share",
                onClick = { sharer.shareFile(recap.shareText, "${recap.destination.lowercase().replace(' ', '-')}-trip.txt", "text/plain"); shared = true; onShared() },
                container = c.onInverse, contentColor = c.inverse,
            )
            V4PillButton("Plan the next one", onClick = onPlanNext, filled = false, contentColor = c.onInverse)
        }
    }
}

/** Finds a trip that ended in the last three days and has not been hidden from Today. */
class V4TripRecapViewModel(
    private val trips: TripRepository,
    private val logs: LifeLogRepository,
    private val weather: TripWeather,
    private val settings: Settings,
    private val fx: FxRates,
) : ViewModel() {

    private val _recap = MutableStateFlow<Pair<Trip, TripPlanner.Recap>?>(null)
    val recap: StateFlow<Pair<Trip, TripPlanner.Recap>?> = _recap.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
                val t = trips.getAll().filter { TripPlanner.recapOnToday(it, today) && !settings.getBoolean(hiddenKey(it.id), false) }
                    .maxByOrNull { it.endDate } ?: return@launch
                val spends = logs.getInRange(t.startDate.minus(DatePeriod(days = 365)), t.endDate.plus1())
                    .filter { it.tripId == t.id && it.kind == LogKind.EXPENSE }
                val items = trips.observeItems(t.id).first()
                val planned = TripPlanner.days(t).filter { d ->
                    items.any { it.kind == TripItemKind.DAY && it.date == d } ||
                        items.any { it.kind == TripItemKind.BOOKING && Bookings.decode(it.notes)?.let { b -> Bookings.linesFor(b, d).isNotEmpty() } == true }
                }.toSet()
                var high = settings.getIntOrNull("v4_trip_high_${t.id}")
                if (high == null && t.latitude != null && t.longitude != null) {
                    high = weather.averageHigh(t.latitude, t.longitude, t.startDate, t.endDate)?.also { settings.putInt("v4_trip_high_${t.id}", it) }
                }
                runCatching { fx.refresh() }
                _recap.value = t to TripPlanner.recap(t, spends, planned, high, fx.table.value)
            }
        }
    }

    fun hide() {
        _recap.value?.first?.let { settings.putBoolean(hiddenKey(it.id), true) }
        _recap.value = null
        PostHogAnalytics.capture("v4_travel_recap_hidden")
    }

    fun shared() = PostHogAnalytics.capture("v4_travel_recap_shared", mapOf("where" to "today"))
    fun planNext() = PostHogAnalytics.capture("v4_travel_recap_plan_next", mapOf("where" to "today"))

    private fun hiddenKey(id: String) = "v4_trip_recap_hidden_$id"
    private fun kotlinx.datetime.LocalDate.plus1() = kotlinx.datetime.LocalDate.fromEpochDays(toEpochDays() + 1)
}

/** The recap on Today, for three days after a trip; Today adds it only while [V4TripRecapViewModel.recap] has one. */
@Composable
fun TripRecapOnToday(
    r: Pair<Trip, TripPlanner.Recap>,
    viewModel: V4TripRecapViewModel,
    onOpenTrip: (String) -> Unit,
    onPlanTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (trip, recap) = r
    TripRecapCard(
        recap = recap,
        onPlanNext = { viewModel.planNext(); onPlanTrip() },
        onShared = viewModel::shared,
        onHide = viewModel::hide,
        onOpen = { onOpenTrip(trip.id) },
        modifier = modifier,
    )
}
