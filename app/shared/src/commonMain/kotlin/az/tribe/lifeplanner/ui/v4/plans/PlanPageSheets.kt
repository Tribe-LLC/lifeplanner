package az.tribe.lifeplanner.ui.v4.plans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.data.plans.PlanView
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.service.PlanScheduler
import az.tribe.lifeplanner.domain.service.PlanTemplates
import az.tribe.lifeplanner.ui.v4.areas.AreaSheet
import az.tribe.lifeplanner.ui.v4.areas.Choice
import az.tribe.lifeplanner.ui.v4.areas.DayChoices
import az.tribe.lifeplanner.ui.v4.areas.FormLabel
import az.tribe.lifeplanner.ui.v4.areas.SheetNote
import az.tribe.lifeplanner.ui.v4.areas.toAmount
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.theme.V4
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

// The small sheets on a plan's page. Each one says what will happen before the button does it.

@Composable
private fun SheetText(text: String) = Text(text, style = V4.type.body, color = V4.colors.ink2)

@Composable
private fun SheetButtons(confirm: String, onConfirm: () -> Unit, cancel: String, onDismiss: () -> Unit, enabled: Boolean = true, dark: Boolean = false) {
    val c = V4.colors
    V4PrimaryButton(
        confirm, onClick = { onConfirm(); onDismiss() }, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        container = if (dark) c.inverse else c.accent, content = if (dark) c.onInverse else c.onAccent,
    )
    V4TextButton(cancel, onClick = onDismiss, modifier = Modifier.fillMaxWidth(), color = c.ink2)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DateSheet(v: PlanView, today: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val choices = remember(v.target) { PlanPageModel.dateChoices(v, today) }
    var picked by remember { mutableStateOf(choices.getOrNull(1) ?: choices.first()) }
    var calendar by remember { mutableStateOf(false) }
    AreaSheet("A new finish date", onDismiss) {
        SheetText("The steps that are left spread out again to fit it. Done steps stay done.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { d -> Choice(PlanScheduler.dayLabel(d), picked == d) { picked = d } }
            val other = picked !in choices
            Choice(if (other) PlanScheduler.dayLabel(picked) else "Pick a day", other) { calendar = true }
        }
        SheetButtons("Use this date", { onPick(picked) }, "Cancel", onDismiss)
    }
    if (calendar) PlanDateDialog(picked, today.plus(DatePeriod(days = 1)), null, onPick = { picked = it }, onDismiss = { calendar = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PauseSheet(onPause: (Int?) -> Unit, onDismiss: () -> Unit) {
    val options = listOf("1 week" to 7, "2 weeks" to 14, "Until I say" to null)
    var picked by remember { mutableStateOf(options.first()) }
    AreaSheet("Pause this plan", onDismiss) {
        SheetText("Life happens. Its steps and routine leave Today, and every date moves by the same time, so nothing looks late when you are back.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o -> Choice(o.first, picked == o) { picked = o } }
        }
        SheetButtons("Pause", { onPause(picked.second) }, "Cancel", onDismiss)
    }
}

@Composable
internal fun LetGoSheet(v: PlanView, onLetGo: () -> Unit, onDismiss: () -> Unit) {
    AreaSheet("Let this plan go?", onDismiss) {
        SheetText(PlanPageModel.letGoText(v))
        SheetButtons("Let it go", onLetGo, "Keep it", onDismiss, dark = true)
        SheetNote("Rather remove it for good? Delete is in the plan's menu.")
    }
}

@Composable
internal fun DeleteSheet(onDelete: () -> Unit, onDismiss: () -> Unit) {
    AreaSheet("Delete this plan?", onDismiss) {
        SheetText("It goes for good, with its steps. Its routine stays. Letting it go instead keeps what you did.")
        SheetButtons("Delete", onDelete, "Keep it", onDismiss, dark = true)
    }
}

@Composable
internal fun RenameSheet(title: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(title) }
    AreaSheet("Rename the plan", onDismiss) {
        PlanField(text, { text = it }, hint = title, label = "Plan name", onDone = { onSave(text); onDismiss() })
        SheetButtons("Save", { onSave(text) }, "Cancel", onDismiss, enabled = text.isNotBlank())
    }
}

@Composable
internal fun PutAsideSheet(v: PlanView, currency: String, onSave: (Double) -> Unit, onDismiss: () -> Unit) {
    val cur = v.spec?.currency ?: currency
    val debt = v.spec?.template == PlanTemplates.DEBT
    var text by remember { mutableStateOf("") }
    AreaSheet(if (debt) "Paid some off" else "Put aside", onDismiss) {
        SheetText(
            (if (debt) "What you paid off" else "What you put aside") + " counts toward ${v.title}. It stays out of your spending.",
        )
        FormLabel("Amount in ${MoneyFormat.symbol(cur).trim()}")
        PlanField(text, { text = it }, hint = "100", label = "Amount", keyboard = KeyboardType.Decimal, onDone = {
            text.toAmount()?.let { onSave(it); onDismiss() }
        })
        SheetButtons("Save", { text.toAmount()?.let(onSave) }, "Cancel", onDismiss, enabled = text.toAmount() != null)
    }
}

/** Rename a step or move its day; or, with no [step], add one on [day]. The day is picked, never typed. */
@Composable
internal fun StepSheet(step: Milestone?, day: LocalDate, onSave: (String, LocalDate) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val c = V4.colors
    var text by remember { mutableStateOf(step?.title ?: "") }
    var date by remember { mutableStateOf(day) }
    AreaSheet(if (step == null) "New step" else "Step", onDismiss) {
        PlanField(text, { text = it }, hint = "A step, in a few words", label = "Step name", onDone = {})
        FormLabel("Day")
        DayChoices(selected = date, onSelect = { date = it }, days = 7, allowPicker = true)
        SheetButtons(if (step == null) "Add step" else "Save", { onSave(text.trim().ifEmpty { step?.title ?: "" }, date) }, "Cancel", onDismiss, enabled = text.isNotBlank())
        if (step != null) V4TextButton("Remove this step", onClick = { onRemove(); onDismiss() }, modifier = Modifier.fillMaxWidth(), color = c.ink2)
    }
}
