package az.tribe.lifeplanner.ui.v4.areas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.domain.model.BudgetPeriod
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.BillRepeat
import az.tribe.lifeplanner.domain.service.BillRule
import az.tribe.lifeplanner.domain.service.Bills
import az.tribe.lifeplanner.domain.service.MoneySummary
import az.tribe.lifeplanner.domain.service.QuickAddParser
import az.tribe.lifeplanner.ui.v4.components.OneLine
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4Divider
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.components.V4ProgressBar
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.theme.V4
import az.tribe.lifeplanner.ui.v4.travel.TravelField
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Check
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import org.koin.compose.viewmodel.koinViewModel

/** The Money page's own part: the budget and daily allowance, bills coming up, where it went, and the spends. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoneySection(onAddSpend: () -> Unit, viewModel: V4MoneyViewModel = koinViewModel()) {
    val s by viewModel.state.collectAsState()
    val c = V4.colors
    val ink = c.area(PlanArea.MONEY)
    var editing by remember { mutableStateOf(false) }
    var editingSpend by remember { mutableStateOf<LifeLog?>(null) }
    var editingBill by remember { mutableStateOf<LifeLog?>(null) }
    var addingBill by remember { mutableStateOf(false) }

    val status = s.status
    if (status == null || editing) {
        BudgetForm(
            currency = s.currency,
            initialAmount = status?.budget?.amount,
            initialPeriod = status?.budget?.period ?: BudgetPeriod.WEEK,
            onSave = { amount, period -> viewModel.setBudget(amount, period); editing = false },
            onCancel = if (status != null) ({ editing = false }) else null,
            onCurrency = viewModel::setCurrency,
        )
    } else {
        val b = status.budget
        val over = status.left < 0
        V4Card {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (b.category?.let { "${it.replaceFirstChar(Char::titlecase)} budget" } ?: "Budget") + ", " + MoneySummary.periodWord(b.period),
                    style = V4.type.bodyStrong, color = c.ink, modifier = Modifier.semantics { heading() },
                )
                V4TextButton("Change", onClick = { editing = true })
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(MoneyFormat.format(kotlin.math.abs(status.left), b.currency), style = V4.type.number, color = c.ink)
                Text(
                    if (over) "over ${MoneyFormat.format(b.amount, b.currency)}" else "left of ${MoneyFormat.format(b.amount, b.currency)}",
                    style = V4.type.body, color = c.ink2, modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            V4ProgressBar(status.fraction, if (over) c.area(PlanArea.FITNESS).color else ink.color, height = 10.dp)
            if (s.perDay != null || s.afterBills != null) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ink.soft).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(s.perDay ?: "Nothing left to spread after bills", style = V4.type.bodyStrong, color = ink.ink)
                    s.afterBills?.let { Text(it, style = V4.type.caption, color = ink.ink) }
                }
            }
            Text(
                "${MoneyFormat.format(status.spent, b.currency)} spent, ${status.daysLeft} ${if (status.daysLeft == 1) "day" else "days"} left",
                style = V4.type.caption, color = c.ink2,
            )
            s.unconverted?.let { Text("$it. It counts once the rates load.", style = V4.type.caption, color = c.ink3) }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        V4PillButton("Add a spend", onClick = onAddSpend)
        V4PillButton("Add a bill", onClick = { addingBill = true }, filled = false)
    }

    if (s.bills.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Coming up this month", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
            s.billsDue?.let { Text(it, style = V4.type.label, color = c.ink2) }
        }
        V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            s.bills.forEachIndexed { i, row ->
                if (i > 0) V4Divider()
                BillLine(row, onOpen = { row.bill?.let { editingBill = it } }, onPaid = { viewModel.togglePaid(row) })
            }
        }
    }
    if (s.laterBills.isNotEmpty()) {
        Text(if (s.bills.isEmpty()) "Bills" else "Later", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
        V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            s.laterBills.forEachIndexed { i, row ->
                if (i > 0) V4Divider()
                BillLine(row, onOpen = { row.bill?.let { editingBill = it } }, onPaid = { viewModel.togglePaid(row) })
            }
        }
    }

    if (s.byCategory.isNotEmpty()) {
        Text("Where it went", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
        V4Card(verticalSpacing = 10.dp) {
            val max = s.byCategory.maxOf { it.second }.takeIf { it > 0 } ?: 1.0
            s.byCategory.forEach { (cat, sum) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(cat.replaceFirstChar(Char::titlecase), style = V4.type.body, color = c.ink, modifier = Modifier.width(92.dp))
                    Box(Modifier.weight(1f)) { V4ProgressBar((sum / max).toFloat(), ink.color) }
                    Text(MoneyFormat.format(sum, s.currency), style = V4.type.bodyStrong, color = c.ink)
                }
            }
            if (s.periodIn > 0) Text("Money in: ${MoneyFormat.format(s.periodIn, s.currency)}", style = V4.type.caption, color = c.ink2)
            Text("All in ${s.currency}, at today's rates.", style = V4.type.caption, color = c.ink3)
        }
    }

    if (s.recent.isNotEmpty()) {
        Text("Recent", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
        V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            s.recent.forEachIndexed { i, row ->
                val l = row.log
                if (i > 0) V4Divider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .clickable(role = Role.Button, onClickLabel = "Change") { editingSpend = l }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        OneLine(l.title, V4.type.bodyStrong, c.ink)
                        OneLine("${(l.category ?: "other").replaceFirstChar(Char::titlecase)}, ${l.date.day}/${l.date.month.ordinal + 1}", V4.type.caption, c.ink3)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(row.amount, style = V4.type.bodyStrong, color = if (l.kind == LogKind.INCOME) c.success else c.ink)
                        row.home?.let { Text(it, style = V4.type.caption, color = c.ink3) }
                    }
                }
            }
        }
        Text(
            "Tap a spend to change it or remove it.", style = V4.type.caption, color = c.ink3,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }

    editingSpend?.let { l ->
        SpendSheet(
            log = l,
            home = s.currency,
            onDismiss = { editingSpend = null },
            onSave = { amount, cur, cat, title, date -> viewModel.updateSpend(l, amount, cur, cat, title, date); editingSpend = null },
            onDelete = { viewModel.deleteLog(l.id); editingSpend = null },
        )
    }
    if (addingBill || editingBill != null) {
        BillSheet(
            bill = editingBill,
            home = s.currency,
            onDismiss = { addingBill = false; editingBill = null },
            onSave = { title, amount, cur, repeat, due -> viewModel.saveBill(editingBill, title, amount, cur, repeat, due); addingBill = false; editingBill = null },
            onStop = editingBill?.let { b -> { viewModel.stopBill(b); editingBill = null } },
        )
    }
}

@Composable
private fun BillLine(row: BillRow, onOpen: () -> Unit, onPaid: () -> Unit) {
    val c = V4.colors
    val ink = c.area(PlanArea.MONEY)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(enabled = row.bill != null, role = Role.Button, onClickLabel = "Change bill", onClick = onOpen)
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OneLine(row.title, V4.type.bodyStrong, if (row.paid) c.ink3 else c.ink)
            Text(row.meta, style = V4.type.caption, color = c.ink3, maxLines = 2)
        }
        Text(row.amount, style = V4.type.bodyStrong, color = if (row.paid) c.ink3 else c.ink)
        Row(
            Modifier.heightIn(min = 44.dp).widthIn(min = 72.dp).clip(RoundedCornerShape(22.dp))
                .background(if (row.paid) ink.soft else c.surface)
                .border(1.5.dp, if (row.paid) ink.soft else c.line, RoundedCornerShape(22.dp))
                .clickable(role = Role.Button, onClick = onPaid)
                .semantics { contentDescription = if (row.paid) "Undo paid: ${row.title}" else "Paid: ${row.title}" }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            if (row.paid) Icon(PhosphorIcons.Regular.Check, contentDescription = null, tint = ink.ink, modifier = Modifier.size(16.dp))
            Text("Paid", style = V4.type.label, color = if (row.paid) ink.ink else c.accentInk)
        }
    }
}

/** Change a spend: amount and its currency, what it was for, a note, the day. Remove sits inside. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpendSheet(
    log: LifeLog,
    home: String,
    onDismiss: () -> Unit,
    onSave: (Double, String, String?, String, LocalDate) -> Unit,
    onDelete: () -> Unit,
) {
    var amount by remember(log.id) { mutableStateOf(MoneyFormat.plain(log.amount ?: 0.0)) }
    var currency by remember(log.id) { mutableStateOf(log.currency ?: home) }
    var category by remember(log.id) { mutableStateOf(log.category) }
    var title by remember(log.id) { mutableStateOf(log.title) }
    var date by remember(log.id) { mutableStateOf(log.date) }
    val income = log.kind == LogKind.INCOME
    AreaSheet(if (income) "Change this money in" else "Change this spend", onDismiss) {
        FormLabel("Amount")
        AmountRow(amount, { amount = it }, currency, { currency = it }, "Amount")
        FormLabel("Note")
        TravelField(title, { title = it }, "What was it?", "Note")
        if (!income) {
            FormLabel("What for")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAddParser.spendCategories().forEach { cat -> Choice(cat.replaceFirstChar(Char::titlecase), category == cat) { category = cat } }
            }
        }
        FormLabel("When")
        DayChoices(date, { date = it }, days = 2, allowPicker = true, includeYesterday = true)
        V4PrimaryButton("Save", onClick = { amount.toAmount()?.let { onSave(it, currency, category, title, date) } }, enabled = amount.toAmount() != null, modifier = Modifier.fillMaxWidth())
        V4TextButton(if (income) "Remove this" else "Remove this spend", onClick = onDelete, color = V4.colors.ink2, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

/** Add or change a bill: what, how much, how often, and when the next one is due. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BillSheet(
    bill: LifeLog?,
    home: String,
    onDismiss: () -> Unit,
    onSave: (String, Double, String, BillRepeat, LocalDate) -> Unit,
    onStop: (() -> Unit)?,
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val rule = bill?.let { Bills.ruleOf(it) }
    var title by remember(bill?.id) { mutableStateOf(bill?.title ?: "") }
    var amount by remember(bill?.id) { mutableStateOf(bill?.amount?.let { MoneyFormat.plain(it) } ?: "") }
    var currency by remember(bill?.id) { mutableStateOf(bill?.currency ?: home) }
    var repeat by remember(bill?.id) { mutableStateOf(rule?.repeat ?: BillRepeat.MONTHLY) }
    var due by remember(bill?.id) { mutableStateOf(bill?.date ?: today) }
    AreaSheet(if (bill == null) "A bill" else "Change this bill", onDismiss) {
        FormLabel("What is it")
        TravelField(title, { title = it }, "Rent, phone, Netflix", "Bill name")
        FormLabel("How much")
        AmountRow(amount, { amount = it }, currency, { currency = it }, "Bill amount")
        FormLabel("How often")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BillRepeat.entries.forEach { r -> Choice(r.label, repeat == r) { repeat = r } }
        }
        FormLabel("Next one is due")
        DayChoices(due, { due = it }, days = 7, allowPicker = true)
        SheetNote(Bills.everyLabel(BillRule.startingOn(repeat, due), due) + ". On Today the day before.")
        V4PrimaryButton(
            "Save bill",
            onClick = { amount.toAmount()?.let { onSave(title, it, currency, repeat, due) } },
            enabled = title.isNotBlank() && amount.toAmount() != null,
            modifier = Modifier.fillMaxWidth(),
        )
        onStop?.let { V4TextButton("Stop this bill", onClick = it, color = V4.colors.ink2, modifier = Modifier.align(Alignment.CenterHorizontally)) }
    }
}

/** A currency button and an amount field side by side; the button opens the common currencies. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AmountRow(amount: String, onAmount: (String) -> Unit, currency: String, onCurrency: (String) -> Unit, label: String) {
    val c = V4.colors
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).border(1.5.dp, c.line, RoundedCornerShape(16.dp))
                .clickable(role = Role.Button) { picking = !picking }.padding(horizontal = 14.dp, vertical = 14.dp)
                .semantics { contentDescription = "Currency, $currency" },
            contentAlignment = Alignment.Center,
        ) { Text(MoneyFormat.symbol(currency).trim().ifEmpty { currency }, style = V4.type.bodyStrong, color = c.ink) }
        Box(Modifier.weight(1f)) {
            TravelField(amount, { v -> onAmount(v.filter { it.isDigit() || it == '.' || it == ',' }.take(10)) }, "Amount", label, KeyboardType.Decimal)
        }
    }
    if (picking) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf(currency) + MoneyFormat.common).distinct().forEach { code -> Choice(code, code == currency) { onCurrency(code); picking = false } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetForm(
    currency: String,
    initialAmount: Double?,
    initialPeriod: BudgetPeriod,
    onSave: (Double, BudgetPeriod) -> Unit,
    onCancel: (() -> Unit)?,
    onCurrency: (String) -> Unit,
) {
    val c = V4.colors
    var amount by remember { mutableStateOf(initialAmount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var period by remember { mutableStateOf(initialPeriod) }
    var pickCurrency by remember { mutableStateOf(false) }

    V4Card {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Set a budget", style = V4.type.bodyStrong, color = c.ink, modifier = Modifier.semantics { heading() })
            Text("One number to stay under, for everything. Today tells you what you can spend each day.", style = V4.type.caption, color = c.ink2)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).border(1.5.dp, c.line, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) { pickCurrency = !pickCurrency }.padding(horizontal = 14.dp, vertical = 12.dp)
                    .semantics { contentDescription = "Home currency, $currency" },
                contentAlignment = Alignment.Center,
            ) { Text(MoneyFormat.symbol(currency).trim().ifEmpty { currency }, style = V4.type.bodyStrong, color = c.ink) }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).border(1.5.dp, c.accent, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (amount.isEmpty()) Text("Amount", style = V4.type.body, color = c.ink3)
                BasicTextField(
                    value = amount,
                    onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' || it == ',' }.take(9) },
                    textStyle = V4.type.bodyStrong.copy(color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Budget amount" },
                )
            }
        }
        if (pickCurrency) {
            Text("Your home currency. Spends in other currencies are converted into it.", style = V4.type.caption, color = c.ink2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyFormat.common.forEach { code -> Choice(code, code == currency) { onCurrency(code); pickCurrency = false } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Per week", period == BudgetPeriod.WEEK) { period = BudgetPeriod.WEEK }
            Choice("Per month", period == BudgetPeriod.MONTH) { period = BudgetPeriod.MONTH }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            V4PillButton("Save budget", onClick = { amount.replace(',', '.').toDoubleOrNull()?.let { onSave(it, period) } })
            if (onCancel != null) V4PillButton("Cancel", onClick = onCancel, filled = false)
        }
    }
}

/** A single-choice pill, shared by the forms on area pages. */
@Composable
internal fun Choice(label: String, on: Boolean, onClick: () -> Unit) {
    val c = V4.colors
    Text(
        label,
        style = V4.type.label,
        color = if (on) c.onInverse else c.ink,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) c.inverse else c.surface)
            .border(1.5.dp, if (on) c.inverse else c.line, RoundedCornerShape(20.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = on }
            .padding(horizontal = 14.dp, vertical = 11.dp),
    )
}
