package az.tribe.lifeplanner.ui.v4.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.model.TripItem
import az.tribe.lifeplanner.domain.model.WeatherCondition
import az.tribe.lifeplanner.domain.service.TripPlanner
import az.tribe.lifeplanner.ui.v4.areas.Choice
import az.tribe.lifeplanner.ui.v4.components.CheckCircleButton
import az.tribe.lifeplanner.ui.v4.components.OneLine
import az.tribe.lifeplanner.ui.v4.components.V4BackLink
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4Divider
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.components.V4ProgressBar
import az.tribe.lifeplanner.ui.v4.components.V4SwitchRow
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.illustration.AreaIllustration
import az.tribe.lifeplanner.ui.v4.theme.V4
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** One trip, per the canvas: budget from Money, before you go, your days with weather, travel mode. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun V4TripScreen(
    tripId: String,
    onBack: () -> Unit,
    viewModel: V4TripViewModel = koinViewModel(key = "trip_$tripId") { parametersOf(tripId) },
) {
    val s by viewModel.state.collectAsState()
    val c = V4.colors
    val tint = c.area(PlanArea.TRAVEL)
    var addingSpend by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf(false) }
    var editingDay by remember { mutableStateOf<LocalDate?>(null) }
    var removingItem by remember { mutableStateOf<TripItem?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var newTodo by remember { mutableStateOf("") }

    LaunchedEffect(s.gone) { if (s.gone) onBack() }
    val trip = s.trip

    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        V4BackLink("Travel", onBack)
        if (trip == null) return@Column
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(trip.destination, style = V4.type.display, color = c.ink, modifier = Modifier.semantics { heading() })
                Text("${s.range}, ${s.countdown}", style = V4.type.label, color = tint.ink)
            }
            AreaIllustration(PlanArea.TRAVEL, size = 76.dp)
        }

        // ── Budget ──
        V4Card {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Trip budget", style = V4.type.bodyStrong, color = c.ink)
                Text("In Money", style = V4.type.label, color = c.area(PlanArea.MONEY).ink)
            }
            val b = s.budget
            if (trip.budget != null && !editingBudget) {
                val left = b.left ?: 0.0
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(MoneyFormat.format(kotlin.math.abs(left), trip.currency), style = V4.type.number, color = c.ink)
                    Text(
                        (if (left < 0) "over " else "left of ") + MoneyFormat.format(trip.budget, trip.currency),
                        style = V4.type.body, color = c.ink2, modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                V4ProgressBar((b.spent / trip.budget).toFloat(), tint.color, height = 10.dp)
            } else if (!editingBudget) {
                Text(
                    if (b.spent > 0) "${MoneyFormat.format(b.spent, trip.currency)} spent so far. Set a budget to see what is left."
                    else "One number for the whole trip. Spends you add here also show in Money.",
                    style = V4.type.caption, color = c.ink2,
                )
            }
            if (editingBudget) {
                var text by remember { mutableStateOf(trip.budget?.let { MoneyFormat.plain(it) } ?: "") }
                TravelField(text, { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' } }, "${MoneyFormat.symbol(trip.currency)} for the whole trip", "Trip budget", KeyboardType.Decimal)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    V4PillButton("Save", onClick = { viewModel.setBudget(text.replace(',', '.').toDoubleOrNull()); editingBudget = false })
                    V4PillButton("Cancel", onClick = { editingBudget = false }, filled = false)
                }
            }
            b.byKind.forEach { (kind, sum) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(kind.label, style = V4.type.body, color = c.ink)
                    Text(MoneyFormat.format(sum, trip.currency), style = V4.type.bodyStrong, color = c.ink)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V4PillButton("Add a spend", onClick = { addingSpend = true }, container = tint.color)
                if (!editingBudget) V4PillButton(if (trip.budget == null) "Set budget" else "Change", onClick = { editingBudget = true }, filled = false)
            }
        }

        // ── Before you go ──
        val left = s.todos.count { !it.isDone }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Before you go", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
            Text(if (left == 0) "All set" else "$left left", style = V4.type.label, color = c.ink2)
        }
        V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            s.todos.forEachIndexed { i, t ->
                if (i > 0) V4Divider()
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { removingItem = t }.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        OneLine(t.title, V4.type.bodyStrong, if (t.isDone) c.ink3 else c.ink)
                        val meta = t.notes ?: if (t.title == "Pack") s.packHint else null
                        meta?.let { Text(it, style = V4.type.caption, color = c.ink3, maxLines = 2) }
                    }
                    CheckCircleButton(t.isDone, (if (t.isDone) "Undo: " else "Mark done: ") + t.title, { viewModel.toggle(t) }, color = tint.color)
                }
            }
            if (s.todos.isNotEmpty()) V4Divider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { TravelField(newTodo, { newTodo = it }, "Add something", "New item for before you go") }
                V4TextButton("Add", onClick = { viewModel.addTodo(newTodo); newTodo = "" })
            }
        }

        // ── Your days ──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Your days", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
            if (s.canCalendar || s.onCalendar) V4TextButton(if (s.onCalendar) "In Calendar" else "Add to Calendar", onClick = viewModel::toggleCalendar)
        }
        V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            s.days.forEachIndexed { i, d ->
                if (i > 0) V4Divider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button) { editingDay = d.date }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.date.dayOfWeek.name.take(3), style = V4.type.micro, color = tint.ink)
                        Text("${d.date.day}", style = V4.type.headline, color = c.ink)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        OneLine(d.plan?.title ?: "Free day", V4.type.bodyStrong, if (d.plan == null) c.ink3 else c.ink)
                        val meta = listOfNotNull(
                            d.forecast?.let { WeatherCondition.fromWmo(it.wmoCode).label },
                            d.forecast?.rainChance?.takeIf { it >= 30 }?.let { "$it% rain" },
                            if (i == 0) "Arrive" else if (i == s.days.lastIndex) "Head home" else null,
                        ).joinToString(", ")
                        if (meta.isNotEmpty()) OneLine(meta, V4.type.caption, c.ink3)
                    }
                    d.forecast?.let { Text("${it.maxC}°", style = V4.type.bodyStrong, color = c.ink) }
                }
            }
        }
        if (s.days.none { it.forecast != null }) {
            Text("Weather shows up here about two weeks before you go.", style = V4.type.caption, color = c.ink3)
        }

        // ── Travel mode ──
        V4Card {
            V4SwitchRow("Travel mode while away", trip.travelMode, viewModel::setTravelMode)
            Text(
                "Pauses home habits on Today during the trip. Keeps walking and water. Streaks stay safe: trip days neither keep nor break them.",
                style = V4.type.caption, color = c.ink2,
            )
        }

        V4TextButton("Delete this trip", onClick = { deleting = true }, color = c.ink2)
    }

    if (addingSpend && trip != null) {
        var amount by remember { mutableStateOf("") }
        var kind by remember { mutableStateOf(TripPlanner.SpendKind.FLIGHTS) }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingSpend = false },
            title = { Text("Add a trip spend") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TravelField(amount, { v -> amount = v.filter { it.isDigit() || it == '.' || it == ',' } }, "${MoneyFormat.symbol(trip.currency)} amount", "Amount", KeyboardType.Decimal)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TripPlanner.SpendKind.entries.forEach { k -> Choice(k.label, kind == k) { kind = k } }
                    }
                    TravelField(note, { note = it }, "What was it? (optional)", "Note")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    amount.replace(',', '.').toDoubleOrNull()?.let { viewModel.addSpend(it, kind, note) }
                    addingSpend = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addingSpend = false }) { Text("Cancel") } },
        )
    }

    editingDay?.let { date ->
        var text by remember(date) { mutableStateOf(s.days.firstOrNull { it.date == date }?.plan?.title ?: "") }
        AlertDialog(
            onDismissRequest = { editingDay = null },
            title = { Text("${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.day}") },
            text = { TravelField(text, { text = it }, "What is the plan?", "Plan for the day") },
            confirmButton = { TextButton(onClick = { viewModel.setDayPlan(date, text); editingDay = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editingDay = null }) { Text("Cancel") } },
        )
    }

    removingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { removingItem = null },
            title = { Text("Remove this?") },
            text = { Text(item.title) },
            confirmButton = { TextButton(onClick = { viewModel.removeItem(item); removingItem = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removingItem = null }) { Text("Keep") } },
        )
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete ${trip?.destination ?: "this trip"}?") },
            text = { Text("Its list and day plans go. Spends stay in Money.") },
            confirmButton = { TextButton(onClick = { deleting = false; viewModel.delete {} }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Keep") } },
        )
    }
}
