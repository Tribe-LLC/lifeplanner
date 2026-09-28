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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.model.TripItem
import az.tribe.lifeplanner.domain.model.WeatherCondition
import az.tribe.lifeplanner.domain.service.Booking
import az.tribe.lifeplanner.domain.service.BookingKind
import az.tribe.lifeplanner.domain.service.Bookings
import az.tribe.lifeplanner.domain.service.TripPlanner
import az.tribe.lifeplanner.ui.v4.areas.AmountRow
import az.tribe.lifeplanner.ui.v4.areas.Choice
import az.tribe.lifeplanner.ui.v4.areas.MultiLineField
import az.tribe.lifeplanner.ui.v4.areas.toAmount
import az.tribe.lifeplanner.ui.v4.components.CheckCircleButton
import az.tribe.lifeplanner.ui.v4.components.OneLine
import az.tribe.lifeplanner.ui.v4.components.V4BackLink
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4Divider
import az.tribe.lifeplanner.ui.v4.components.V4IconButton
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.components.V4ProgressBar
import az.tribe.lifeplanner.ui.v4.components.V4Switch
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.illustration.AreaIllustration
import az.tribe.lifeplanner.ui.v4.theme.V4
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.DotsThree
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import androidx.compose.runtime.key
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** One trip, per the canvas: recap, wallet, paste a booking, before you go, and the days with their bookings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun V4TripScreen(
    tripId: String,
    onBack: () -> Unit,
    onPlanTrip: () -> Unit = onBack,
    viewModel: V4TripViewModel = koinViewModel(key = "trip_$tripId") { parametersOf(tripId) },
) {
    val s by viewModel.state.collectAsState()
    val c = V4.colors
    val tint = c.area(PlanArea.TRAVEL)
    var addingSpend by remember { mutableStateOf(false) }
    var editingBudget by remember { mutableStateOf(false) }
    var editingDay by remember { mutableStateOf<LocalDate?>(null) }
    var removingItem by remember { mutableStateOf<TripItem?>(null) }
    var openBooking by remember { mutableStateOf<Pair<TripItem, Booking>?>(null) }
    var removingSpend by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var newTodo by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }

    LaunchedEffect(s.gone) { if (s.gone) onBack() }
    val trip = s.trip

    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            V4BackLink("Travel", onBack)
            if (trip != null) {
                Box {
                    V4IconButton(PhosphorIcons.Regular.DotsThree, "More for this trip", { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.surface) {
                        Row(
                            Modifier.width(280.dp).padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Travel mode", style = V4.type.bodyStrong, color = c.ink)
                                Text("Home habits rest while away, streaks stay safe", style = V4.type.caption, color = c.ink3)
                            }
                            V4Switch(trip.travelMode, viewModel::setTravelMode, "Travel mode")
                        }
                        V4Divider()
                        DropdownMenuItem(
                            text = { Text("Delete this trip", style = V4.type.bodyStrong, color = c.ink2) },
                            onClick = { menu = false; deleting = true },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }
        if (trip == null) return@Column
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(trip.destination, style = V4.type.display, color = c.ink, modifier = Modifier.semantics { heading() })
                Text("${s.range}, ${s.countdown}", style = V4.type.label, color = tint.ink)
            }
            AreaIllustration(PlanArea.TRAVEL, size = 76.dp)
        }

        s.recap?.let { r -> TripRecapCard(r, onPlanNext = onPlanTrip, onShared = viewModel::recapShared) }

        // ── Wallet ──
        V4Card {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (s.active) "Trip wallet" else "Trip budget", style = V4.type.bodyStrong, color = c.ink, modifier = Modifier.semantics { heading() })
                Text("In Money", style = V4.type.label, color = c.area(PlanArea.MONEY).ink)
            }
            val b = s.budget
            val w = s.wallet
            if (w != null && !editingBudget) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(w.big, style = V4.type.number, color = c.ink)
                        Text(w.word, style = V4.type.body, color = c.ink2, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    Text(w.sub, style = V4.type.caption, color = c.ink2)
                }
                V4ProgressBar((b.spent / trip.budget!!).toFloat(), tint.color, height = 10.dp)
            } else if (trip.budget != null && !editingBudget) {
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
                    if (b.spent > 0) "${MoneyFormat.format(b.spent, trip.currency)} spent so far. Set a budget to see what is left each day."
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
            if (b.byKind.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    b.byKind.forEach { (kind, sum) -> Text("${kind.label} ${MoneyFormat.format(sum, trip.currency)}", style = V4.type.caption, color = c.ink2) }
                }
            }
            if (s.spendRows.isNotEmpty()) {
                V4Divider()
                s.spendRows.take(6).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClickLabel = "Remove") { removingItem = null; removingSpend = row.log.id },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OneLine(row.log.title, V4.type.body, c.ink, Modifier.weight(1f))
                        Text(row.amount, style = V4.type.bodyStrong, color = c.ink)
                        row.home?.let { Text(it, style = V4.type.caption, color = c.ink3) }
                    }
                }
            }
            s.unconverted?.let { Text(it, style = V4.type.caption, color = c.ink3) }
            if (s.active && s.local != null) {
                Text("While you are in ${trip.destination}, \"ramen 1200\" in the add box means ${s.local}.", style = V4.type.caption, color = c.ink3)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                V4PillButton("Add a spend", onClick = { addingSpend = true }, container = tint.color)
                if (!editingBudget) V4PillButton(if (trip.budget == null) "Set budget" else "Change", onClick = { editingBudget = true }, filled = false)
            }
        }

        // ── Paste a booking ──
        if (s.recap == null) {
            PasteBooking(
                draft = s.draft,
                text = pasted,
                onText = { pasted = it },
                near = trip.startDate,
                homeCurrency = trip.currency,
                onRead = { viewModel.readBooking(pasted) },
                onEdit = viewModel::editDraft,
                onDrop = viewModel::dropDraft,
                onSave = { viewModel.saveDraft(); pasted = "" },
                onCancel = viewModel::clearDraft,
            )
        }

        // ── Before you go ──
        if (s.recap == null) {
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
        }

        // ── Your days, with the bookings on them ──
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Your days", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
            if (s.canCalendar || s.onCalendar) V4TextButton(if (s.onCalendar) "In Calendar" else "Add to Calendar", onClick = viewModel::toggleCalendar)
        }
        V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
            s.days.forEachIndexed { i, d ->
                if (i > 0) V4Divider()
                val isToday = d.date == today
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp)
                        .background(if (isToday) tint.soft else c.surface)
                        .clickable(role = Role.Button, onClickLabel = "Plan the day") { editingDay = d.date }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(d.date.dayOfWeek.name.take(3), style = V4.type.micro, color = tint.ink)
                        Text("${d.date.day}", style = V4.type.headline, color = c.ink)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        d.bookings.forEach { db ->
                            Row(
                                Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClickLabel = "Booking details") { openBooking = db.item to db.booking }
                                    .heightIn(min = 32.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    db.booking.kind.label, style = V4.type.micro, color = tint.ink,
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(tint.soft).padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                                Text(db.text, style = V4.type.label, color = tint.ink, maxLines = 2)
                            }
                        }
                        OneLine(d.plan?.title ?: "Free day", V4.type.bodyStrong, if (d.plan == null) c.ink3 else c.ink)
                        val meta = listOfNotNull(
                            if (isToday) "Today" else null,
                            d.forecast?.let { WeatherCondition.fromWmo(it.wmoCode).label },
                            d.forecast?.rainChance?.takeIf { it >= 30 }?.let { "$it% rain" },
                            if (i == 0 && d.bookings.isEmpty()) "Arrive" else if (i == s.days.lastIndex && d.bookings.isEmpty()) "Head home" else null,
                        ).joinToString(", ")
                        if (meta.isNotEmpty()) OneLine(meta, V4.type.caption, c.ink3)
                    }
                    d.forecast?.let { Text("${it.maxC}°", style = V4.type.bodyStrong, color = c.ink) }
                }
            }
        }
        Text(
            if (s.days.none { it.forecast != null } && s.recap == null) "Weather shows up here about two weeks before you go. Tap a day to write its plan."
            else "Tap a day to write its plan. Tap a booking to see its code.",
            style = V4.type.caption, color = c.ink3,
        )
    }

    if (addingSpend && trip != null) {
        var amount by remember { mutableStateOf("") }
        var kind by remember { mutableStateOf(if (s.active) TripPlanner.SpendKind.FOOD else TripPlanner.SpendKind.FLIGHTS) }
        var note by remember { mutableStateOf("") }
        var currency by remember { mutableStateOf(if (s.active) s.local ?: trip.currency ?: "EUR" else trip.currency ?: "EUR") }
        AlertDialog(
            onDismissRequest = { addingSpend = false },
            title = { Text("Add a trip spend") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AmountRow(amount, { amount = it }, currency, { currency = it }, "Amount")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TripPlanner.SpendKind.entries.forEach { k -> Choice(k.label, kind == k) { kind = k } }
                    }
                    TravelField(note, { note = it }, "What was it? (optional)", "Note")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    amount.toAmount()?.let { viewModel.addSpend(it, kind, note, currency) }
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

    openBooking?.let { (item, b) ->
        AlertDialog(
            onDismissRequest = { openBooking = null },
            title = { Text("${b.kind.label}: ${b.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOfNotNull(
                        listOfNotNull(b.from, b.to).takeIf { it.size == 2 }?.joinToString(" to "),
                        b.start?.let { (if (b.kind == BookingKind.HOTEL) "Check in " else "Leaves ") + Bookings.formatWhen(it) },
                        b.end?.let { (if (b.kind == BookingKind.HOTEL) "Check out " else "Arrives ") + Bookings.formatWhen(it) },
                        b.address,
                        b.code?.let { "Code $it" },
                        b.price?.let { "Paid ${MoneyFormat.format(it, b.currency ?: trip?.currency)}, in the trip budget" },
                    ).forEach { Text(it, style = V4.type.body, color = c.ink) }
                }
            },
            confirmButton = { TextButton(onClick = { openBooking = null }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { openBooking = null; removingItem = item }) { Text("Remove") } },
        )
    }

    removingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { removingItem = null },
            title = { Text("Remove this?") },
            text = { Text(if (item.kind == az.tribe.lifeplanner.domain.model.TripItemKind.BOOKING) "${item.title}. Its price leaves the budget too." else item.title) },
            confirmButton = { TextButton(onClick = { viewModel.removeItem(item); removingItem = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removingItem = null }) { Text("Keep") } },
        )
    }

    removingSpend?.let { id ->
        val row = s.spendRows.firstOrNull { it.log.id == id }
        AlertDialog(
            onDismissRequest = { removingSpend = null },
            title = { Text("Remove this spend?") },
            text = { Text(row?.let { "${it.log.title}, ${it.amount}" } ?: "") },
            confirmButton = { TextButton(onClick = { viewModel.removeSpend(id); removingSpend = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removingSpend = null }) { Text("Keep") } },
        )
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete ${trip?.destination ?: "this trip"}?") },
            text = { Text("Its list, bookings and day plans go. Spends stay in Money.") },
            confirmButton = { TextButton(onClick = { deleting = false; viewModel.delete {} }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Keep") } },
        )
    }
}

/**
 * "Paste a booking": a box for a confirmation, then what was read as an editable preview. Parsing
 * is unreliable, so nothing is saved until the preview is checked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PasteBooking(
    draft: BookingDraft,
    text: String,
    onText: (String) -> Unit,
    near: LocalDate,
    homeCurrency: String?,
    onRead: () -> Unit,
    onEdit: (Int, Booking) -> Unit,
    onDrop: (Int) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = V4.colors
    val tint = c.area(PlanArea.TRAVEL)
    V4Card(color = tint.soft, bordered = false) {
        Text("Paste a booking", style = V4.type.bodyStrong, color = c.ink, modifier = Modifier.semantics { heading() })
        when (draft) {
            is BookingDraft.Found -> {
                Text("I found this. Change anything that is off.", style = V4.type.caption, color = tint.ink)
                draft.bookings.forEachIndexed { i, b -> key(draft.version, i) { BookingPreview(b, near, { onEdit(i, it) }, { onDrop(i) }, homeCurrency) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    V4PillButton(if (draft.bookings.size == 1) "Save booking" else "Save ${draft.bookings.size} bookings", onClick = onSave, container = tint.color)
                    V4PillButton("Not now", onClick = onCancel, filled = false)
                }
            }
            else -> {
                Text(
                    "Copy a confirmation email for a flight, hotel or train and paste it here. You check it before anything is saved.",
                    style = V4.type.caption, color = tint.ink,
                )
                Box(Modifier.clip(RoundedCornerShape(16.dp)).background(c.surface)) {
                    MultiLineField(text, onText, "Paste the confirmation here", "Booking confirmation", minLines = 3)
                }
                if (draft is BookingDraft.Failed) Text(draft.message, style = V4.type.caption, color = c.ink2)
                V4PillButton(
                    if (draft is BookingDraft.Reading) "Reading..." else "Read it",
                    onClick = { if (draft !is BookingDraft.Reading && text.isNotBlank()) onRead() },
                    container = tint.color,
                )
            }
        }
    }
}

/** One booking as editable fields. Dates read back what they show ("10 Oct 15:00"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookingPreview(b: Booking, near: LocalDate, onChange: (Booking) -> Unit, onDrop: () -> Unit, homeCurrency: String?) {
    val c = V4.colors
    var name by remember(b.kind) { mutableStateOf(b.name) }
    var from by remember { mutableStateOf(b.from ?: "") }
    var to by remember { mutableStateOf(b.to ?: "") }
    var start by remember { mutableStateOf(Bookings.formatWhen(b.start)) }
    var end by remember { mutableStateOf(Bookings.formatWhen(b.end)) }
    var code by remember { mutableStateOf(b.code ?: "") }
    var price by remember { mutableStateOf(b.price?.let { MoneyFormat.plain(it) } ?: "") }
    var currency by remember { mutableStateOf(b.currency ?: homeCurrency ?: "EUR") }
    fun push(k: BookingKind = b.kind) = onChange(
        b.copy(
            kind = k, name = name.trim().ifEmpty { b.name }, from = from.trim().ifEmpty { null }, to = to.trim().ifEmpty { null },
            start = Bookings.parseWhen(start, near) ?: b.start.takeIf { start.isNotBlank() },
            end = Bookings.parseWhen(end, near) ?: b.end.takeIf { end.isNotBlank() },
            code = code.trim().ifEmpty { null }, price = price.toAmount(), currency = currency,
        ),
    )
    val hotel = b.kind == BookingKind.HOTEL
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(BookingKind.FLIGHT, BookingKind.HOTEL, BookingKind.TRAIN).forEach { k -> Choice(k.label, b.kind == k) { push(k) } }
        }
        TravelField(name, { name = it; push() }, if (hotel) "Hotel name" else "Flight or train number", "Name")
        if (!hotel) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { TravelField(from, { from = it; push() }, "From", "From") }
                Box(Modifier.weight(1f)) { TravelField(to, { to = it; push() }, "To", "To") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { TravelField(start, { start = it; push() }, if (hotel) "Check in" else "Leaves", if (hotel) "Check in" else "Leaves") }
            Box(Modifier.weight(1f)) { TravelField(end, { end = it; push() }, if (hotel) "Check out" else "Arrives", if (hotel) "Check out" else "Arrives") }
        }
        if ((start.isNotBlank() && Bookings.parseWhen(start, near) == null) || (end.isNotBlank() && Bookings.parseWhen(end, near) == null)) {
            Text("Write dates like 10 Oct 15:00.", style = V4.type.caption, color = c.ink2)
        }
        TravelField(code, { code = it; push() }, "Confirmation code", "Confirmation code")
        AmountRow(price, { price = it; push() }, currency, { currency = it; push() }, "Price")
        Text(
            "Saving puts it on your days" + (price.toAmount()?.let { ", and ${MoneyFormat.format(it, currency)} in ${b.kind.spendKind.label}" } ?: "") + ".",
            style = V4.type.caption, color = c.ink3,
        )
        V4TextButton("Leave this one out", onClick = onDrop, color = c.ink2)
    }
}
