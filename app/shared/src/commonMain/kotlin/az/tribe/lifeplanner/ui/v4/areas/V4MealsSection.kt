package az.tribe.lifeplanner.ui.v4.areas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.di.createFileSharer
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.MealSlot
import az.tribe.lifeplanner.ui.health.rememberHealthPermissionLauncher
import az.tribe.lifeplanner.ui.v4.components.CheckCircleButton
import az.tribe.lifeplanner.ui.v4.components.OneLine
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4Divider
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.components.V4ProgressBar
import az.tribe.lifeplanner.ui.v4.components.V4SwitchRow
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.theme.V4
import az.tribe.lifeplanner.ui.v4.travel.TravelField
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Meals page's own part: today by meal, water, the week's plan, the shopping list it feeds,
 * and how eating lines up with the food budget. Built so one thing leads to the next: a planned
 * dinner puts its ingredients on the list, the list's receipt goes to Money, and the meal, once
 * eaten, goes to Health.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MealsSection(onAskCoach: (String) -> Unit, viewModel: V4MealsViewModel = koinViewModel()) {
    val s by viewModel.state.collectAsState()
    val c = V4.colors
    val tint = c.area(PlanArea.MEALS)
    var logging by remember { mutableStateOf<MealSlot?>(null) }
    var planning by remember { mutableStateOf<MealDraft?>(null) }
    var acting by remember { mutableStateOf<MealRow?>(null) }
    var finishing by remember { mutableStateOf(false) }
    var editingWater by remember { mutableStateOf(false) }
    var budgetFor by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val sharer = remember { createFileSharer() }
    val askHealth = rememberHealthPermissionLauncher { viewModel.onHealthGranted() }

    // ── Today ──
    V4Card(modifier = Modifier.fillMaxWidth(), color = tint.soft, bordered = false, verticalSpacing = 10.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Today", style = V4.type.label, color = tint.ink)
            s.foodBudget?.let { Text(it, style = V4.type.label, color = c.ink2) }
        }
        MealSlot.entries.forEach { slot ->
            val rows = s.today[slot].orEmpty()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(slot.label, style = V4.type.label, color = c.ink2, modifier = Modifier.width(78.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (rows.isEmpty()) {
                        Text("Nothing yet", style = V4.type.body, color = c.ink3)
                    }
                    rows.forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable(role = Role.Button) { acting = r }.padding(vertical = 4.dp)) {
                                OneLine(r.dish, V4.type.bodyStrong, if (r.planned) c.ink2 else c.ink)
                                r.meta?.let { OneLine(it, V4.type.micro, c.ink3) }
                            }
                            if (r.planned) CheckCircleButton(false, "Ate it: ${r.dish}", { viewModel.toggleEaten(r) }, color = tint.color)
                        }
                    }
                }
                V4TextButton("Add", onClick = { logging = slot }, modifier = Modifier.semantics { contentDescription = "Add ${slot.label.lowercase()}" })
            }
        }
    }

    // ── Water ──
    V4Card {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Water", style = V4.type.label, color = c.ink2)
                Text("${s.water} of ${s.waterTarget} glasses", style = V4.type.headline, color = c.ink)
            }
            V4PillButton("−", onClick = viewModel::removeWater, filled = false, modifier = Modifier.semantics { contentDescription = "Take back a glass" })
            V4PillButton("+ Glass", onClick = viewModel::addWater, container = tint.color)
        }
        V4ProgressBar(s.water.toFloat() / s.waterTarget, tint.color)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (s.healthOn && !s.needsHealthGrant) "Each glass is 250 ml and goes to Health." else "Each glass is 250 ml.",
                style = V4.type.caption, color = c.ink3,
            )
            V4TextButton("Goal", onClick = { editingWater = !editingWater })
        }
        if (editingWater) CountChoices(listOf(6, 8, 10, 12), s.waterTarget, { "$it" }) { viewModel.setWaterTarget(it); editingWater = false }
    }

    if (s.needsHealthGrant) {
        V4Card {
            Text("Save meals and water to Health", style = V4.type.bodyStrong, color = c.ink)
            Text("Health is connected, but it has not been allowed to take meals and water yet.", style = V4.type.caption, color = c.ink2)
            V4PillButton("Allow", onClick = askHealth)
        }
    }

    // ── This week's plan ──
    Text("This week's plan", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
    V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
        s.plan.forEachIndexed { i, day ->
            if (i > 0) V4Divider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(day.label, style = V4.type.label, color = c.ink, modifier = Modifier.width(76.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val shown = MealSlot.entries.flatMap { slot -> day.meals[slot].orEmpty().filter { it.planned || i == 0 }.map { slot to it } }
                        .filter { (slot, _) -> slot != MealSlot.SNACK }
                    if (shown.isEmpty()) Text("Nothing planned", style = V4.type.body, color = c.ink3)
                    shown.forEach { (slot, r) ->
                        Text(
                            "${slot.label}: ${r.dish}",
                            style = V4.type.body, color = if (r.planned) c.ink else c.ink3, maxLines = 1,
                            modifier = Modifier.clickable(role = Role.Button) { acting = r },
                        )
                    }
                }
                V4TextButton("Plan", onClick = { planning = viewModel.draft(date = day.date) }, modifier = Modifier.semantics { contentDescription = "Plan a meal for ${day.label}" })
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        V4PillButton("Plan a meal", onClick = { planning = viewModel.draft() }, container = tint.color)
        V4PillButton("Plan with the coach", onClick = { onAskCoach(viewModel.coachPrompt()) }, filled = false)
    }

    // ── Shopping list ──
    var adding by remember { mutableStateOf("") }
    Text("Shopping list", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
    V4Card(verticalSpacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { TravelField(adding, { adding = it }, "Eggs, milk, 2 onions", "Add to the shopping list") }
            V4PillButton("Add", onClick = { viewModel.addToShopping(adding); adding = "" })
        }
        if (s.toBuy.isEmpty() && s.bought.isEmpty()) {
            Text("Empty. Plan a meal with its ingredients and they land here, sorted by aisle.", style = V4.type.caption, color = c.ink2)
        }
        s.toBuy.forEach { (aisle, items) ->
            Text(aisle.label, style = V4.type.micro, color = c.ink3, modifier = Modifier.padding(top = 4.dp))
            items.forEach { item -> ShoppingRow(item, tint.color, { viewModel.toggleBought(item) }, { viewModel.removeItem(item) }) }
        }
        if (s.bought.isNotEmpty()) {
            Text("In the basket", style = V4.type.micro, color = c.ink3, modifier = Modifier.padding(top = 4.dp))
            s.bought.forEach { item -> ShoppingRow(item, tint.color, { viewModel.toggleBought(item) }, { viewModel.removeItem(item) }) }
        }
        if (s.toBuy.isNotEmpty() || s.bought.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.bought.isNotEmpty()) V4PillButton("Done shopping", onClick = { finishing = true }, container = tint.color)
                if (s.toBuy.isNotEmpty()) {
                    V4PillButton("Share", onClick = { sharer.shareFile(viewModel.shoppingText(), "shopping-list.txt", "text/plain") }, filled = false)
                    V4PillButton(if (copied) "Copied" else "Copy", onClick = { sharer.copyToClipboard(viewModel.shoppingText()); copied = true }, filled = false)
                }
            }
            if (copied) LaunchedEffect(Unit) { delay(2_000); copied = false }
        }
    }

    // ── Last 7 days ──
    s.week?.let { w ->
        Text("Last 7 days", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
        V4Card {
            Text(
                if (w.eaten == 0) "No meals logged yet" else "${w.eaten} ${if (w.eaten == 1) "meal" else "meals"}, ${w.home} at home, ${w.out} out",
                style = V4.type.bodyStrong, color = c.ink,
            )
            Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                val max = (w.perDay.maxOfOrNull { it.second } ?: 0).coerceAtLeast(3)
                w.perDay.forEach { (d, n) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height((6 + 34 * n / max).dp).clip(RoundedCornerShape(6.dp))
                                .background(if (n > 0) tint.color else c.trackOff)
                                .semantics { contentDescription = "${az.tribe.lifeplanner.domain.service.FitnessWeek.dayName(d.dayOfWeek)}, $n ${if (n == 1) "meal" else "meals"}" },
                        )
                        Text(az.tribe.lifeplanner.domain.service.FitnessWeek.dayLetter(d.dayOfWeek), style = V4.type.micro, color = c.ink3)
                    }
                }
            }
            if (w.foodSpent > 0) Text("${MoneyFormat.format(w.foodSpent, s.currency)} on food, groceries and eating out.", style = V4.type.caption, color = c.ink2)
            if (!s.hasFoodBudget) {
                if (budgetFor) {
                    var amount by remember { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { TravelField(amount, { v -> amount = v.filter { it.isDigit() || it == '.' || it == ',' } }, "${MoneyFormat.symbol(s.currency)}a week for food", "Food budget per week", KeyboardType.Decimal) }
                        V4PillButton("Save", onClick = { amount.toAmount()?.let { viewModel.setFoodBudget(it); budgetFor = false } })
                    }
                } else V4TextButton("Set a weekly food budget", onClick = { budgetFor = true })
            }
        }
    }

    // ── Eaten lately ──
    if (s.recent.isNotEmpty()) {
        Text("Eaten lately", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
        V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            s.recent.forEachIndexed { i, r ->
                if (i > 0) V4Divider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { acting = r }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        OneLine(r.dish, V4.type.bodyStrong, c.ink)
                        OneLine(listOfNotNull(az.tribe.lifeplanner.domain.service.MealPlanner.slotOf(r.log).label, dayWord(r.log.date), r.meta).joinToString(", "), V4.type.caption, c.ink3)
                    }
                }
            }
        }
    }

    logging?.let { slot ->
        LogMealSheet(
            slot = slot,
            favourites = viewModel.favouritesFor(slot),
            currency = s.currency,
            onDismiss = { logging = null },
            onSave = { dish, sl, date, cost, kcal -> viewModel.logMeal(dish, sl, date, cost, kcal); logging = null },
        )
    }

    planning?.let { draft ->
        PlanMealSheet(
            initial = draft,
            favourites = s.favourites,
            canCalendar = s.canCalendar,
            viewModel = viewModel,
            onDismiss = { planning = null },
            onSave = { d, extra, cal -> viewModel.plan(d, extra, cal); planning = null },
        )
    }

    acting?.let { r ->
        AlertDialog(
            onDismissRequest = { acting = null },
            title = { Text(r.dish) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(listOfNotNull(az.tribe.lifeplanner.domain.service.MealPlanner.slotOf(r.log).label, dayWord(r.log.date), r.meta).joinToString(", "))
                    val notes = az.tribe.lifeplanner.domain.service.MealNotes.decode(r.log.notes)
                    if (notes.ingredients.isNotEmpty()) Text("Needs: ${notes.ingredients.joinToString(", ")}", style = V4.type.caption)
                    notes.url?.let { Text(it, style = V4.type.caption, maxLines = 1) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (r.planned) viewModel.toggleEaten(r) else planning = viewModel.draft(dish = r.dish, slot = az.tribe.lifeplanner.domain.service.MealPlanner.slotOf(r.log))
                    acting = null
                }) { Text(if (r.planned) "Ate it" else "Plan again") }
            },
            dismissButton = {
                Row {
                    if (r.planned && r.log.date != Clock.System.todayIn(TimeZone.currentSystemDefault())) {
                        TextButton(onClick = { viewModel.moveMeal(r, Clock.System.todayIn(TimeZone.currentSystemDefault()), az.tribe.lifeplanner.domain.service.MealPlanner.slotOf(r.log)); acting = null }) { Text("Today") }
                    }
                    TextButton(onClick = { viewModel.removeMeal(r); acting = null }) { Text("Remove") }
                }
            },
        )
    }

    if (finishing) {
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { finishing = false },
            title = { Text("Done shopping") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("What did it cost? It goes to Money as groceries. Leave it empty to just clear the basket.")
                    TravelField(amount, { v -> amount = v.filter { it.isDigit() || it == '.' || it == ',' } }, "${MoneyFormat.symbol(s.currency)}0.00", "Grocery total", KeyboardType.Decimal)
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.finishShopping(amount.toAmount()); finishing = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { finishing = false }) { Text("Not yet") } },
        )
    }
}

@Composable
private fun ShoppingRow(item: LifeLog, color: androidx.compose.ui.graphics.Color, onToggle: () -> Unit, onRemove: () -> Unit) {
    val c = V4.colors
    val done = item.status == LogStatus.DONE
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CheckCircleButton(done, (if (done) "Put back: " else "Got it: ") + item.title, onToggle, color = color)
        Text(item.title, style = V4.type.body, color = if (done) c.ink3 else c.ink, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onRemove).semantics { contentDescription = "Remove ${item.title}" },
            contentAlignment = Alignment.Center,
        ) { Text("×", style = V4.type.headline, color = c.ink3) }
    }
}

private fun dayWord(d: LocalDate): String {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    return when (d.toEpochDays() - today.toEpochDays()) {
        0L -> "today"; -1L -> "yesterday"; 1L -> "tomorrow"
        else -> az.tribe.lifeplanner.domain.service.FitnessWeek.dayName(d.dayOfWeek)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogMealSheet(
    slot: MealSlot,
    favourites: List<String>,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (String, MealSlot, LocalDate, Double?, Double?) -> Unit,
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var dish by remember { mutableStateOf("") }
    var sl by remember { mutableStateOf(slot) }
    var day by remember { mutableStateOf(today) }
    var bought by remember { mutableStateOf(false) }
    var cost by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }

    AreaSheet("What did you eat?", onDismiss) {
        TravelField(dish, { dish = it }, "Oats with berries", "What you ate")
        if (favourites.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                favourites.forEach { f -> Choice(f, dish == f) { dish = f } }
            }
        }
        FormLabel("Which meal")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MealSlot.entries.forEach { m -> Choice(m.label, sl == m) { sl = m } }
        }
        FormLabel("When")
        DayChoices(day, { day = it }, days = 2, includeYesterday = true)
        V4SwitchRow("Bought it (eaten out, takeaway)", bought, { bought = it })
        if (bought) TravelField(cost, { v -> cost = v.filter { it.isDigit() || it == '.' || it == ',' } }, "${MoneyFormat.symbol(currency)}0.00, goes to Money", "What it cost", KeyboardType.Decimal)
        FormLabel("Calories, if you know")
        TravelField(kcal, { v -> kcal = v.filter { it.isDigit() } }, "Skip it, or about 600", "Calories", KeyboardType.Number)
        V4PrimaryButton("Save", onClick = { onSave(dish, sl, day, if (bought) cost.toAmount() else null, kcal.toAmount()) }, enabled = dish.isNotBlank(), modifier = Modifier.fillMaxWidth())
        SheetNote("No counting needed. The name is enough; calories only go to Health when you add them.")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanMealSheet(
    initial: MealDraft,
    favourites: List<String>,
    canCalendar: Boolean,
    viewModel: V4MealsViewModel,
    onDismiss: () -> Unit,
    onSave: (MealDraft, Int, Boolean) -> Unit,
) {
    val c = V4.colors
    var d by remember { mutableStateOf(initial) }
    var ingredients by remember { mutableStateOf(initial.ingredients.joinToString("\n")) }
    var link by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf(0) }
    var kcal by remember { mutableStateOf(initial.kcal?.toInt()?.toString() ?: "") }
    var toCalendar by remember { mutableStateOf(false) }
    val importing by viewModel.importing.collectAsState()
    val failed by viewModel.importFailed.collectAsState()

    fun apply(next: MealDraft) {
        d = next
        ingredients = next.ingredients.joinToString("\n")
        kcal = next.kcal?.toInt()?.toString() ?: ""
    }

    AreaSheet("Plan a meal", onDismiss) {
        FormLabel("From a recipe link, optional")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { TravelField(link, { link = it }, "Paste a recipe page", "Recipe link", KeyboardType.Uri) }
            V4PillButton(if (importing) "Reading" else "Get it", onClick = { if (link.isNotBlank() && !importing) viewModel.importRecipe(link, d.copy(ingredients = ingredients.lines().filter { it.isNotBlank() })) { apply(it) } }, filled = false)
        }
        if (failed) Text("Could not read a recipe there. Type it in instead.", style = V4.type.caption, color = c.ink2)
        d.url?.let { Text("From ${it.substringAfter("://").substringBefore('/')}" + (d.servings?.let { n -> ", serves $n" } ?: "") + (d.minutes?.let { m -> ", $m min" } ?: ""), style = V4.type.caption, color = c.ink2) }

        FormLabel("What")
        TravelField(d.dish, { d = d.copy(dish = it) }, "Chilli, stir fry, pasta bake", "Dish name")
        if (favourites.isNotEmpty() && d.url == null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                favourites.forEach { f -> Choice(f, d.dish == f) { apply(viewModel.draft(d.date, d.slot, f)) } }
            }
        }
        FormLabel("When")
        DayChoices(d.date, { d = d.copy(date = it) })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MealSlot.entries.forEach { m -> Choice(m.label, d.slot == m) { d = d.copy(slot = m) } }
        }
        FormLabel("What you need, one per line")
        MultiLineField(ingredients, { ingredients = it }, "Onion\nMince\nTinned tomatoes", "Ingredients")
        Text("Goes on the shopping list, minus what is already there.", style = V4.type.caption, color = c.ink3)
        FormLabel("Cook once, eat again")
        CountChoices(listOf(0, 1, 2, 3), extra, { if (it == 0) "Just this meal" else "+$it ${if (it == 1) "meal" else "meals"}" }) { extra = it }
        if (extra > 0) Text("Leftovers go in your next free lunch and dinner.", style = V4.type.caption, color = c.ink3)
        FormLabel("Calories a portion, optional")
        TravelField(kcal, { v -> kcal = v.filter { it.isDigit() } }, "Skip it, or about 550", "Calories a portion", KeyboardType.Number)
        if (canCalendar) V4SwitchRow("Add cooking time to your calendar", toCalendar, { toCalendar = it })
        V4PrimaryButton(
            "Plan it",
            onClick = { onSave(d.copy(ingredients = ingredients.lines().map { it.trim() }.filter { it.isNotEmpty() }, kcal = kcal.toAmount()), extra, toCalendar) },
            enabled = d.dish.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        SheetNote("It shows on Today on the day. Tick it when you have eaten it.")
    }
}
