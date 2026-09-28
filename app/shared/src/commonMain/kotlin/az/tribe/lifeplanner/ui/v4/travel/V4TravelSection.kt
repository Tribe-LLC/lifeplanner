package az.tribe.lifeplanner.ui.v4.travel

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.model.Trip
import az.tribe.lifeplanner.domain.service.TripPlanner
import az.tribe.lifeplanner.ui.v4.components.OneLine
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4Divider
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.theme.V4
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import org.koin.compose.viewmodel.koinViewModel

/** The Travel area page's own part: the trips, and planning a new one. */
@Composable
fun TravelSection(onOpenTrip: (String) -> Unit, viewModel: V4TravelViewModel = koinViewModel()) {
    val s by viewModel.state.collectAsState()
    val c = V4.colors
    val tint = c.area(PlanArea.TRAVEL)
    var planning by remember { mutableStateOf(false) }

    if (s.upcoming.isEmpty()) {
        V4Card(modifier = Modifier.fillMaxWidth(), color = tint.soft, bordered = false) {
            Text("No trip planned", style = V4.type.headline, color = c.ink)
            Text(
                "Add where and when. You get a budget that lives in Money, a list for before you go, the weather for each day, and a travel mode that keeps your streaks safe.",
                style = V4.type.caption, color = c.ink2,
            )
            V4PillButton("Plan a trip", onClick = { planning = true }, container = tint.color)
        }
    } else {
        TripList("Coming up", s.upcoming, onOpenTrip)
        V4PillButton("Plan another trip", onClick = { planning = true }, filled = false)
    }
    if (s.past.isNotEmpty()) TripList("Been", s.past, onOpenTrip)

    if (planning) {
        PlanTripSheet(
            currency = s.currency,
            onDismiss = { planning = false },
            onSave = { place, start, end, budget ->
                planning = false
                viewModel.create(place, start, end, budget, onOpenTrip)
            },
        )
    }
}

@Composable
private fun TripList(title: String, rows: List<TripRow>, onOpen: (String) -> Unit) {
    val c = V4.colors
    Text(title, style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
    V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
        rows.forEachIndexed { i, r ->
            if (i > 0) V4Divider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button) { onOpen(r.trip.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    OneLine(r.trip.destination, V4.type.bodyStrong, c.ink)
                    OneLine("${r.range}, ${r.countdown}", V4.type.caption, if (r.active) c.area(PlanArea.TRAVEL).ink else c.ink3)
                }
                r.trip.budget?.let { Text(MoneyFormat.format(it, r.trip.currency), style = V4.type.label, color = c.ink2) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanTripSheet(
    currency: String,
    onDismiss: () -> Unit,
    onSave: (place: String, start: LocalDate, end: LocalDate, budget: Double?) -> Unit,
    initial: Trip? = null,
) {
    val c = V4.colors
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var place by remember { mutableStateOf(initial?.destination ?: "") }
    var start by remember { mutableStateOf(initial?.startDate ?: today.plus(DatePeriod(days = 14))) }
    var end by remember { mutableStateOf(initial?.endDate ?: today.plus(DatePeriod(days = 18))) }
    var budget by remember { mutableStateOf(initial?.budget?.let { MoneyFormat.plain(it) } ?: "") }
    var picking by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (initial == null) "Plan a trip" else "Change trip", style = V4.type.title, color = c.ink, modifier = Modifier.semantics { heading() })
            Text("Where", style = V4.type.label, color = c.ink2)
            TravelField(place, { place = it }, "Tokyo, Lisbon, the lake house", "Destination")
            Text("When", style = V4.type.label, color = c.ink2)
            Box(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, c.line, RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { picking = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    TripPlanner.range(Trip(id = "", destination = "", startDate = start, endDate = end)) +
                        ", ${TripPlanner.days(Trip(id = "", destination = "", startDate = start, endDate = end)).size} days",
                    style = V4.type.bodyStrong, color = c.ink,
                )
            }
            Text("Budget, optional", style = V4.type.label, color = c.ink2)
            TravelField(budget, { v -> budget = v.filter { it.isDigit() || it == '.' || it == ',' } }, "${MoneyFormat.symbol(currency)} for the whole trip", "Trip budget", KeyboardType.Decimal)
            V4PrimaryButton(
                if (initial == null) "Plan it" else "Save",
                onClick = { onSave(place, start, end, budget.replace(',', '.').toDoubleOrNull()) },
                enabled = place.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (picking) {
        val zero = LocalDate(1970, 1, 1)
        fun ms(d: LocalDate) = (d.toEpochDays() - zero.toEpochDays()) * 86_400_000L
        val state = rememberDateRangePickerState(initialSelectedStartDateMillis = ms(start), initialSelectedEndDateMillis = ms(end))
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedStartDateMillis?.let { start = LocalDate.fromEpochDays((it / 86_400_000L).toInt()) }
                    end = state.selectedEndDateMillis?.let { LocalDate.fromEpochDays((it / 86_400_000L).toInt()) } ?: start
                    picking = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state = state, modifier = Modifier.heightIn(max = 520.dp), showModeToggle = false)
        }
    }
}

@Composable
internal fun TravelField(value: String, onChange: (String) -> Unit, hint: String, label: String, keyboard: KeyboardType = KeyboardType.Text) {
    val c = V4.colors
    Box(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, c.line, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(hint, style = V4.type.body, color = c.ink3)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = V4.type.bodyStrong.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}
