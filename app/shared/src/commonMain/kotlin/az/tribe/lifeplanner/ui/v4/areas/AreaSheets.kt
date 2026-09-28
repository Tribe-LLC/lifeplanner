package az.tribe.lifeplanner.ui.v4.areas

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.service.FitnessWeek
import az.tribe.lifeplanner.ui.v4.theme.V4
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** The bottom sheet every area form uses: a title, then the form, scrolling above the keyboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AreaSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = V4.colors
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
            Text(title, style = V4.type.title, color = c.ink, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
internal fun FormLabel(text: String) = Text(text, style = V4.type.label, color = V4.colors.ink2)

/** A bordered box for longer text: ingredients, a pasted syllabus. */
@Composable
internal fun MultiLineField(value: String, onChange: (String) -> Unit, hint: String, label: String, minLines: Int = 3) {
    val c = V4.colors
    Box(
        Modifier.fillMaxWidth().heightIn(min = (24 * minLines + 28).dp).clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, c.line, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) Text(hint, style = V4.type.body, color = c.ink3)
        BasicTextField(
            value = value, onValueChange = onChange,
            textStyle = V4.type.body.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}

/**
 * Today and the next days as pills, plus "Pick a date" for anything further out (exams, trips).
 * [days] is how many pills, today included.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun DayChoices(selected: LocalDate, onSelect: (LocalDate) -> Unit, days: Int = 7, allowPicker: Boolean = false, includeYesterday: Boolean = false) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var picking by remember { mutableStateOf(false) }
    val range = if (includeYesterday) (-1 until days - 1) else (0 until days)
    val labels = range.map { i -> today.plus(DatePeriod(days = i)) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { d ->
            val i = (d.toEpochDays() - today.toEpochDays()).toInt()
            val label = when (i) { -1 -> "Yesterday"; 0 -> "Today"; 1 -> "Tomorrow"; else -> FitnessWeek.shortDay(d.dayOfWeek) }
            Choice(label, selected == d) { onSelect(d) }
        }
        if (allowPicker) {
            val far = selected !in labels
            Choice(if (far) "${selected.day} ${selected.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}" else "Pick a date", far) { picking = true }
        }
    }
    if (picking) {
        val ms = (selected.toEpochDays() - LocalDate(1970, 1, 1).toEpochDays()) * 86_400_000L
        val state = rememberDatePickerState(initialSelectedDateMillis = ms)
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onSelect(LocalDate.fromEpochDays((it / 86_400_000L).toInt())) }
                    picking = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state = state, showModeToggle = false) }
    }
}

/** A row of number choices with a word after each: "1 block", "2 blocks". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CountChoices(values: List<Int>, selected: Int, label: (Int) -> String, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { n -> Choice(label(n), selected == n) { onSelect(n) } }
    }
}

/** Parses a typed amount, accepting a comma for the decimal point. */
internal fun String.toAmount(): Double? = trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

/** Text centred under a sheet's main button. */
@Composable
internal fun SheetNote(text: String) = Text(
    text, style = V4.type.caption, color = V4.colors.ink3,
    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth(),
)
