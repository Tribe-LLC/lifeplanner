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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import az.tribe.lifeplanner.domain.service.MoneySummary
import az.tribe.lifeplanner.domain.service.QuickAddParser
import az.tribe.lifeplanner.ui.v4.components.OneLine
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4Divider
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.components.V4ProgressBar
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.theme.V4
import org.koin.compose.viewmodel.koinViewModel

/** The Money page's own part: the budget, where it went, and the list of spends. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoneySection(onAddSpend: () -> Unit, viewModel: V4MoneyViewModel = koinViewModel()) {
    val s by viewModel.state.collectAsState()
    val c = V4.colors
    val ink = c.area(PlanArea.MONEY)
    var editing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<LifeLog?>(null) }

    val status = s.status
    if (status == null || editing) {
        BudgetForm(
            currency = s.currency,
            initialAmount = status?.budget?.amount,
            initialPeriod = status?.budget?.period ?: BudgetPeriod.WEEK,
            initialCategory = status?.budget?.category,
            onSave = { amount, period, category -> viewModel.setBudget(amount, period, category); editing = false },
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
                    style = V4.type.bodyStrong, color = c.ink,
                )
                V4TextButton("Change", onClick = { editing = true })
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(MoneyFormat.format(kotlin.math.abs(status.left), b.currency), style = V4.type.number, color = if (over) c.ink else c.ink)
                Text(
                    if (over) "over ${MoneyFormat.format(b.amount, b.currency)}" else "left of ${MoneyFormat.format(b.amount, b.currency)}",
                    style = V4.type.body, color = c.ink2, modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            V4ProgressBar(status.fraction, if (over) c.area(PlanArea.FITNESS).color else ink.color, height = 10.dp)
            Text(
                "${MoneyFormat.format(status.spent, b.currency)} spent, ${status.daysLeft} ${if (status.daysLeft == 1) "day" else "days"} left",
                style = V4.type.caption, color = c.ink2,
            )
        }
    }

    V4PillButton("Add a spend", onClick = onAddSpend)

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
        }
    }

    if (s.recent.isNotEmpty()) {
        Text("Recent", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
        V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            s.recent.forEachIndexed { i, l ->
                if (i > 0) V4Divider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { removing = l }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        OneLine(l.title, V4.type.bodyStrong, c.ink)
                        OneLine("${(l.category ?: "other").replaceFirstChar(Char::titlecase)}, ${l.date.day}/${l.date.month.ordinal + 1}", V4.type.caption, c.ink3)
                    }
                    Text(
                        (if (l.kind == LogKind.INCOME) "+" else "") + MoneyFormat.format(l.amount ?: 0.0, l.currency),
                        style = V4.type.bodyStrong,
                        color = if (l.kind == LogKind.INCOME) c.success else c.ink,
                    )
                }
            }
        }
    }

    removing?.let { l ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove this?") },
            text = { Text("${l.title}, ${MoneyFormat.format(l.amount ?: 0.0, l.currency)}") },
            confirmButton = { TextButton(onClick = { viewModel.deleteLog(l.id); removing = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetForm(
    currency: String,
    initialAmount: Double?,
    initialPeriod: BudgetPeriod,
    initialCategory: String?,
    onSave: (Double, BudgetPeriod, String?) -> Unit,
    onCancel: (() -> Unit)?,
    onCurrency: (String) -> Unit,
) {
    val c = V4.colors
    var amount by remember { mutableStateOf(initialAmount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var period by remember { mutableStateOf(initialPeriod) }
    var category by remember { mutableStateOf(initialCategory) }
    var pickCurrency by remember { mutableStateOf(false) }

    V4Card {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Set a budget", style = V4.type.bodyStrong, color = c.ink)
            Text("One number to stay under. Today tells you what is left.", style = V4.type.caption, color = c.ink2)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.clip(RoundedCornerShape(14.dp)).border(1.5.dp, c.line, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) { pickCurrency = !pickCurrency }.padding(horizontal = 14.dp, vertical = 12.dp)
                    .semantics { contentDescription = "Currency, $currency" },
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyFormatCodes.forEach { code -> Choice(code, code == currency) { onCurrency(code); pickCurrency = false } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Per week", period == BudgetPeriod.WEEK) { period = BudgetPeriod.WEEK }
            Choice("Per month", period == BudgetPeriod.MONTH) { period = BudgetPeriod.MONTH }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Everything", category == null) { category = null }
            QuickAddParser.spendCategories().filter { it != "other" }.forEach { cat ->
                Choice(cat.replaceFirstChar(Char::titlecase), category == cat) { category = cat }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            V4PillButton("Save budget", onClick = { amount.replace(',', '.').toDoubleOrNull()?.let { onSave(it, period, category) } })
            if (onCancel != null) V4PillButton("Cancel", onClick = onCancel, filled = false)
        }
    }
}

private val MoneyFormatCodes = MoneyFormat.common

@Composable
private fun Choice(label: String, on: Boolean, onClick: () -> Unit) {
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
