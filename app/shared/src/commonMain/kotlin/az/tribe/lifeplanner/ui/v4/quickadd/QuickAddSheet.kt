package az.tribe.lifeplanner.ui.v4.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.ParsedEntry
import az.tribe.lifeplanner.domain.service.Bills
import az.tribe.lifeplanner.domain.service.TripPlanner
import kotlinx.datetime.toLocalDateTime
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.components.areaName
import az.tribe.lifeplanner.ui.v4.illustration.AreaIllustration
import az.tribe.lifeplanner.ui.v4.theme.V4
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/**
 * The "Add anything" sheet. [onAskCoach] takes whatever the parser could not place, so nothing
 * typed here is a dead end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    onDismiss: () -> Unit,
    onAskCoach: (String) -> Unit,
    viewModel: QuickAddViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focus = remember { FocusRequester() }
    val c = V4.colors

    LaunchedEffect(Unit) {
        viewModel.reset()
        delay(150)
        runCatching { focus.requestFocus() }
    }
    // A saved entry stays on screen long enough to read where it went, then the sheet closes.
    LaunchedEffect(state.savedMessage) {
        if (state.savedMessage?.startsWith("Saved") == true) {
            delay(1200)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Add anything", style = V4.type.title, color = c.ink, modifier = Modifier.semantics { heading() })
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(2.dp, c.accent, RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.text.isEmpty()) Text("What happened?", style = V4.type.body.copy(fontSize = V4.type.headline.fontSize), color = c.ink3)
                BasicTextField(
                    value = state.text,
                    onValueChange = viewModel::onText,
                    textStyle = V4.type.bodyStrong.copy(fontSize = V4.type.headline.fontSize, color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { viewModel.save() }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "What happened?" },
                )
            }

            val entries = state.parsed?.entries.orEmpty()
            when {
                state.text.isBlank() -> Hint()
                entries.isEmpty() -> {
                    Text("Not sure where this goes.", style = V4.type.label, color = c.ink3)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        V4PillButton("Ask the coach", onClick = { onAskCoach(state.text) })
                    }
                    Hint()
                }
                else -> {
                    Text(
                        if (entries.size == 1) "I got this. It goes in one place:" else "I got this. It goes in ${entries.size} places:",
                        style = V4.type.label,
                        color = c.ink3,
                    )
                    entries.forEach { EntryCard(it) }
                    state.approx?.let { Text(it + (state.tripPlace?.let { p -> ". Plain amounts are in local money while you are in $p." } ?: ""), style = V4.type.caption, color = c.ink2) }
                    val saved = state.savedMessage
                    V4PrimaryButton(
                        text = saved ?: "Save to ${QuickAddViewModel.joinNames(PlanArea.entries.filter { a -> entries.any { it.area == a } }.map { areaName(it) })}",
                        onClick = { if (saved == null) viewModel.save() },
                        enabled = !state.saving,
                        container = if (saved != null) c.successSoft else c.accent,
                        content = if (saved != null) c.success else c.onAccent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint() {
    Text(
        "Try: \"coffee 4.50\", \"ran 5k in 28 min\", \"netflix 12 monthly\", \"slept badly\", \"drink water every day\".",
        style = V4.type.caption,
        color = V4.colors.ink2,
    )
}

@Composable
private fun EntryCard(e: ParsedEntry) {
    val ac = V4.colors.area(e.area)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ac.soft).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AreaIllustration(e.area, size = 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                when {
                    e.isRoutine -> "${areaName(e.area)}, new routine"
                    e.isBill -> "${areaName(e.area)}, new bill"
                    else -> areaName(e.area)
                },
                style = V4.type.micro, color = ac.ink,
            )
            Text(headline(e), style = V4.type.bodyStrong, color = V4.colors.ink)
            detail(e)?.let { Text(it, style = V4.type.caption, color = V4.colors.ink2) }
        }
    }
}

private fun headline(e: ParsedEntry): String = when (e.kind) {
    LogKind.EXPENSE, LogKind.INCOME -> "${MoneyFormat.format(e.amount ?: 0.0, e.currency)}, ${e.title.lowercase()}"
    else -> e.title
}

private fun detail(e: ParsedEntry): String? = when {
    e.isRoutine -> "Every day, on Today from tomorrow morning"
    e.bill != null && e.firstDue != null -> Bills.everyLabel(e.bill, e.firstDue) + ". Next: " +
        (if (e.firstDue == kotlinx.datetime.TimeZone.currentSystemDefault().let { kotlin.time.Clock.System.now().toLocalDateTime(it).date }) "today"
        else "${e.firstDue.day} ${TripPlanner.monthName(e.firstDue.month)}")
    e.kind == LogKind.EXPENSE -> "Spent on ${e.category ?: "other"}"
    e.kind == LogKind.INCOME -> "Money in"
    e.kind == LogKind.WORKOUT -> listOfNotNull(e.durationMin?.let { "$it min" }, "counts toward Move").joinToString(", ")
    e.kind == LogKind.STUDY -> e.durationMin?.let { "$it min of study time" }
    e.kind == LogKind.SLEEP -> "Tells the coach how rested you are"
    e.kind == LogKind.WATER -> "Ticks your water habit if you have one"
    e.kind == LogKind.MEAL -> "Logged as a meal"
    else -> null
}
