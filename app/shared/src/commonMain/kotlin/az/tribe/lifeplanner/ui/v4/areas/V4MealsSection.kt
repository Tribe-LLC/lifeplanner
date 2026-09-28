package az.tribe.lifeplanner.ui.v4.areas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.di.createFileSharer
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.MealPlanner
import az.tribe.lifeplanner.domain.service.MealSlot
import az.tribe.lifeplanner.domain.service.MealWeek
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
    var notice by remember { mutableStateOf<String?>(null) }
    var editingStaples by remember { mutableStateOf(false) }
    val coachWeek by viewModel.coachWeek.collectAsState()
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
    if (s.repeatable > 0 || s.freeEvenings.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.repeatable > 0) {
                V4PillButton("Repeat last week", onClick = {
                    viewModel.repeatLastWeek { meals, items ->
                        notice = if (meals == 0) "Last week is already in your plan."
                        else "Planned $meals ${if (meals == 1) "meal" else "meals"} from last week" + if (items > 0) ". $items ${if (items == 1) "thing" else "things"} went on your list." else "."
                    }
                }, filled = false)
            }
            if (s.freeEvenings.isNotEmpty()) V4PillButton("Plan my week", onClick = viewModel::openCoachWeek, container = tint.color)
        }
    }
    notice?.let {
        LaunchedEffect(it) { delay(5_000); notice = null }
        Text(it, style = V4.type.label, color = c.success)
    }
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
    V4PillButton("Plan a meal", onClick = { planning = viewModel.draft() }, filled = false)

    // ── Your rotation ──
    if (s.rotation.isNotEmpty()) {
        Text("Your rotation", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
        Text("What you make most. Tap one to plan it with its ingredients.", style = V4.type.caption, color = c.ink2)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.rotation.forEach { dish ->
                RotationChip(dish.name, if (dish.times > 1) "${dish.times}x" else null) {
                    val slot = s.history.filter { az.tribe.lifeplanner.domain.service.MealPlanner.dishName(it).equals(dish.name, ignoreCase = true) }
                        .maxByOrNull { it.occurredAt }?.let { az.tribe.lifeplanner.domain.service.MealPlanner.slotOf(it) } ?: MealSlot.DINNER
                    val day = s.plan.firstOrNull { it.meals[slot].isNullOrEmpty() }?.date ?: Clock.System.todayIn(TimeZone.currentSystemDefault())
                    planning = viewModel.draft(date = day, slot = slot, dish = dish.name)
                    az.tribe.lifeplanner.data.analytics.PostHogAnalytics.capture("v4_meals_rotation_tap", mapOf("times" to dish.times))
                }
            }
        }
    }

    // ── Shopping list ──
    var adding by remember { mutableStateOf("") }
    var staplesAdded by remember { mutableStateOf(0) }
    if (staplesAdded > 0) LaunchedEffect(staplesAdded) { delay(3_000); staplesAdded = 0 }
    Text("Shopping list", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
    V4Card(verticalSpacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { TravelField(adding, { adding = it }, "Eggs, milk, 2 onions", "Add to the shopping list") }
            V4PillButton("Add", onClick = { viewModel.addToShopping(adding); adding = "" })
        }
        // Always buy
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(tint.soft).padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Always buy", style = V4.type.label, color = tint.ink)
                Text(s.staples.joinToString(", ").ifEmpty { "Nothing yet. Add what you buy every week." }, style = V4.type.body, color = if (s.staples.isEmpty()) c.ink3 else c.ink, maxLines = 2)
            }
            if (s.staples.isNotEmpty()) {
                if (s.staplesMissing > 0) V4PillButton("Add them", onClick = { viewModel.addStaples { n -> if (n > 0) staplesAdded = n } }, container = tint.color)
                else Text("On the list", style = V4.type.label, color = c.ink3, modifier = Modifier.padding(horizontal = 6.dp))
            }
            V4TextButton("Edit", onClick = { editingStaples = true }, modifier = Modifier.semantics { contentDescription = "Change what you always buy" })
        }
        if (s.toBuy.isEmpty() && s.bought.isEmpty()) {
            Text("Empty. Plan a meal with its ingredients and they land here, sorted by aisle. What you have in stays off.", style = V4.type.caption, color = c.ink2)
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
        if (staplesAdded > 0) Text("Added $staplesAdded from Always buy.", style = V4.type.label, color = c.success)
    }

    // ── Last 7 days, one line (Money has the rest) ──
    s.week?.let { w ->
        Text(
            if (w.eaten == 0) "Last 7 days: no meals logged yet."
            else "Last 7 days: ${w.eaten} ${if (w.eaten == 1) "meal" else "meals"}, ${w.home} at home, ${w.out} out.",
            style = V4.type.body, color = c.ink2,
        )
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
            pantry = s.pantry,
            canCalendar = s.canCalendar,
            viewModel = viewModel,
            onDismiss = { planning = null },
            onSave = { d, extra, cal, have -> viewModel.plan(d, extra, cal, have); planning = null },
        )
    }

    coachWeek?.let { ui ->
        CoachWeekSheet(
            ui = ui,
            onAsk = viewModel::askCoach,
            onSwap = viewModel::swapDinner,
            onDrop = viewModel::dropDinner,
            onPut = {
                viewModel.putCoachWeek { dinners, items ->
                    notice = "Planned $dinners ${if (dinners == 1) "dinner" else "dinners"}" + if (items > 0) ". $items ${if (items == 1) "thing" else "things"} went on your list." else "."
                }
            },
            onChat = { viewModel.closeCoachWeek(); onAskCoach(viewModel.coachPrompt()) },
            onDismiss = viewModel::closeCoachWeek,
        )
    }

    if (editingStaples) {
        StaplesSheet(
            initial = s.staples,
            onDismiss = { editingStaples = false },
            onSave = { viewModel.saveStaples(it); editingStaples = false },
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
        MoreFields(kcal.isNotEmpty()) {
            FormLabel("Calories, if you know")
            TravelField(kcal, { v -> kcal = v.filter { it.isDigit() } }, "Skip it, or about 600", "Calories", KeyboardType.Number)
            Text("Only for Health. Skip it and nothing is missing.", style = V4.type.caption, color = V4.colors.ink3)
        }
        V4PrimaryButton("Save", onClick = { onSave(dish, sl, day, if (bought) cost.toAmount() else null, kcal.toAmount()) }, enabled = dish.isNotBlank(), modifier = Modifier.fillMaxWidth())
        SheetNote("No counting needed. The name is enough.")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanMealSheet(
    initial: MealDraft,
    favourites: List<String>,
    pantry: Set<String>,
    canCalendar: Boolean,
    viewModel: V4MealsViewModel,
    onDismiss: () -> Unit,
    onSave: (MealDraft, Int, Boolean, Set<String>) -> Unit,
) {
    val c = V4.colors
    var d by remember { mutableStateOf(initial) }
    var ingredients by remember { mutableStateOf(initial.ingredients) }
    // Marked "have it": pre-marked from what you said you have before.
    var have by remember { mutableStateOf(initial.ingredients.filter { MealWeek.ingredientKey(it) in pantry }.toSet()) }
    var newItem by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf(0) }
    var kcal by remember { mutableStateOf(initial.kcal?.toInt()?.toString() ?: "") }
    var toCalendar by remember { mutableStateOf(false) }
    val importing by viewModel.importing.collectAsState()
    val failed by viewModel.importFailed.collectAsState()

    fun apply(next: MealDraft) {
        d = next
        ingredients = next.ingredients
        have = next.ingredients.filter { MealWeek.ingredientKey(it) in pantry }.toSet()
        kcal = next.kcal?.toInt()?.toString() ?: ""
    }

    AreaSheet("Plan a meal", onDismiss) {
        FormLabel("From a recipe link, optional")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { TravelField(link, { link = it }, "Paste a recipe page", "Recipe link", KeyboardType.Uri) }
            V4PillButton(if (importing) "Reading" else "Get it", onClick = { if (link.isNotBlank() && !importing) viewModel.importRecipe(link, d.copy(ingredients = ingredients)) { apply(it) } }, filled = false)
        }
        if (failed) Text("Could not read a recipe there. Type it in instead.", style = V4.type.caption, color = c.ink2)
        d.url?.let { Text("From ${it.substringAfter("://").substringBefore('/')}" + (d.servings?.let { n -> ", serves $n" } ?: "") + (d.minutes?.let { m -> ", $m min" } ?: ""), style = V4.type.caption, color = c.ink2) }
        if (d.url == null && Clock.System.todayIn(TimeZone.currentSystemDefault()) <= MEALIME_CLOSES) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.area(PlanArea.MEALS).soft).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Coming from Mealime?", style = V4.type.label, color = c.area(PlanArea.MEALS).ink)
                Text("It closes on 21 October with no export. Paste a recipe's web link above and it comes with you, ingredients and all.", style = V4.type.caption, color = c.ink2)
            }
        }

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

        FormLabel("What you need")
        if (ingredients.isNotEmpty()) {
            Text("Tap what you already have. It stays off the list, now and next time.", style = V4.type.caption, color = c.ink3)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ingredients.forEach { item -> HaveChip(item, item in have) { have = if (item in have) have - item else have + item } }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { TravelField(newItem, { newItem = it }, if (ingredients.isEmpty()) "Onion, mince, tinned tomatoes" else "Add an ingredient", "Add an ingredient") }
            V4PillButton("Add", onClick = {
                val more = MealPlanner.parseShopping(newItem).filter { n -> ingredients.none { it.equals(n, ignoreCase = true) } }
                ingredients = ingredients + more
                newItem = ""
            }, filled = false)
        }
        if (ingredients.isNotEmpty()) {
            val need = ingredients.count { it !in have }
            Text(
                if (need == 0) "You have everything. Nothing goes on the list."
                else "$need ${if (need == 1) "goes" else "go"} on your list" + if (have.isNotEmpty()) ", ${have.size} you have." else ", minus what is already there.",
                style = V4.type.label, color = c.success,
            )
        }

        FormLabel("Cook once, eat again")
        CountChoices(listOf(0, 1, 2, 3), extra, { if (it == 0) "Just this meal" else "+$it ${if (it == 1) "meal" else "meals"}" }) { extra = it }
        if (extra > 0) Text("Leftovers go in your next free lunch and dinner.", style = V4.type.caption, color = c.ink3)
        MoreFields(kcal.isNotEmpty()) {
            FormLabel("Calories a portion, optional")
            TravelField(kcal, { v -> kcal = v.filter { it.isDigit() } }, "Skip it, or about 550", "Calories a portion", KeyboardType.Number)
            Text("Only for Health. Skip it and nothing is missing.", style = V4.type.caption, color = c.ink3)
        }
        if (canCalendar) V4SwitchRow("Add cooking time to your calendar", toCalendar, { toCalendar = it })
        V4PrimaryButton(
            "Plan it",
            onClick = { onSave(d.copy(ingredients = ingredients.map { it.trim() }.filter { it.isNotEmpty() }, kcal = kcal.toAmount()), extra, toCalendar, have) },
            enabled = d.dish.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        SheetNote("It shows on Today on the day. Tick it when you have eaten it.")
    }
}

/** Mealime shuts down on this day with no export; the hint goes away after it. */
private val MEALIME_CLOSES = LocalDate(2026, 10, 21)

/** Calories and protein, folded away behind "More" (they only feed Health). Opens by itself when filled. */
@Composable
private fun MoreFields(filled: Boolean, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(filled) }
    if (open) content()
    else V4TextButton("More: calories for Health", onClick = { open = true }, modifier = Modifier.semantics { stateDescription = "Collapsed" })
}

/** An ingredient that can be marked "have it": crossed through and kept off the list. */
@Composable
private fun HaveChip(item: String, have: Boolean, onToggle: () -> Unit) {
    val c = V4.colors
    Text(
        if (have) "$item, have it" else item,
        style = V4.type.label.copy(textDecoration = if (have) TextDecoration.LineThrough else null),
        color = if (have) c.ink3 else c.ink,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (have) c.surfaceMuted else c.surface)
            .border(1.5.dp, if (have) c.surfaceMuted else c.line, RoundedCornerShape(22.dp))
            .toggleable(value = have, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { stateDescription = if (have) "Have it" else "Need it" }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

/** A dish in "Your rotation": its name and how many times. */
@Composable
private fun RotationChip(name: String, times: String?, onClick: () -> Unit) {
    val c = V4.colors
    Row(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(c.surface)
            .border(1.5.dp, c.line, RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClickLabel = "Plan $name", onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(name, style = V4.type.label, color = c.ink, maxLines = 1)
        times?.let { Text(it, style = V4.type.micro, color = c.ink3) }
    }
}

/** "Always buy", one per line. */
@Composable
private fun StaplesSheet(initial: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var text by remember { mutableStateOf(initial.joinToString("\n")) }
    AreaSheet("Always buy", onDismiss) {
        Text("What you buy every week. One tap puts whatever is not on the list yet onto it.", style = V4.type.body, color = V4.colors.ink2)
        MultiLineField(text, { text = it }, "Milk\nEggs\nBread", "Things you always buy, one per line", minLines = 5)
        V4PrimaryButton("Save", onClick = { onSave(MealWeek.decodeList(text)) }, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * "Plan my week": how many you cook for, then the coach's dinners to keep, swap or drop, then
 * "Put it in my week". Nothing is saved before that last tap, so a failure costs nothing.
 */
@Composable
private fun CoachWeekSheet(
    ui: CoachWeekUi,
    onAsk: (Int) -> Unit,
    onSwap: (LocalDate) -> Unit,
    onDrop: (LocalDate) -> Unit,
    onPut: () -> Unit,
    onChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = V4.colors
    val tint = c.area(PlanArea.MEALS)
    var household by remember { mutableStateOf(ui.household) }
    val days = ui.dates.joinToString(", ") { az.tribe.lifeplanner.domain.service.FitnessWeek.shortDay(it.dayOfWeek) }
    AreaSheet("Your week of dinners", onDismiss) {
        when {
            !ui.asked -> {
                Text(
                    "The coach plans ${ui.dates.size} ${if (ui.dates.size == 1) "dinner" else "dinners"} for your free evenings ($days), around your food budget and what you like, reusing ingredients across days.",
                    style = V4.type.body, color = c.ink2,
                )
                FormLabel("Cooking for")
                CountChoices(listOf(1, 2, 3, 4, 5), household, { if (it == 5) "5+" else "$it" }) { household = it }
                V4PrimaryButton("Plan my week", onClick = { onAsk(household) }, container = tint.color, modifier = Modifier.fillMaxWidth())
                SheetNote("You see every dinner before anything is saved.")
            }
            ui.loading -> {
                Text("The coach is planning $days...", style = V4.type.body, color = c.ink2, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            ui.failed -> {
                Text("Could not reach the coach. Your plan is unchanged. Try again when you are online.", style = V4.type.body, color = c.ink, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                V4PrimaryButton("Try again", onClick = { onAsk(household) }, modifier = Modifier.fillMaxWidth())
                V4TextButton("Talk it through in chat instead", onClick = onChat)
            }
            ui.dinners.isEmpty() -> {
                Text("You dropped them all. Ask again for a fresh set, or close this.", style = V4.type.body, color = c.ink2)
                V4PrimaryButton("Ask again", onClick = { onAsk(household) }, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
                    ui.dinners.forEachIndexed { i, dn ->
                        if (i > 0) V4Divider()
                        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(az.tribe.lifeplanner.domain.service.FitnessWeek.shortDay(dn.date.dayOfWeek), style = V4.type.label, color = tint.ink, modifier = Modifier.width(40.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(if (ui.swapping == dn.date) "Finding another..." else dn.title, style = V4.type.bodyStrong, color = if (ui.swapping == dn.date) c.ink3 else c.ink)
                                    Text(
                                        listOfNotNull(dn.minutes?.let { "$it min" }, dn.ingredients.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.item }).joinToString(". "),
                                        style = V4.type.caption, color = c.ink3, maxLines = 2,
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                V4TextButton("Swap", onClick = { onSwap(dn.date) }, modifier = Modifier.semantics { contentDescription = "Swap ${dn.title}" })
                                V4TextButton("Drop", onClick = { onDrop(dn.date) }, color = c.ink2, modifier = Modifier.semantics { contentDescription = "Drop ${dn.title}" })
                            }
                        }
                    }
                }
                V4PrimaryButton(
                    if (ui.saving) "Putting it in" else "Put it in my week",
                    onClick = onPut, enabled = !ui.saving && ui.swapping == null, container = tint.color, modifier = Modifier.fillMaxWidth(),
                )
                SheetNote("Only what you do not have goes on the shopping list.")
            }
        }
    }
}
