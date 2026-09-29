package az.tribe.lifeplanner.ui.v4.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.ui.v4.theme.V4
import com.adamglin.phosphoricons.Bold
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.bold.Check
import com.adamglin.phosphoricons.bold.X
import kotlinx.datetime.LocalDate

private const val DAY_MS = 86_400_000L

private fun LocalDate.millis(): Long = (toEpochDays() - LocalDate(1970, 1, 1).toEpochDays()) * DAY_MS

/** A calendar to pick a plan's or a step's day, from [min] to [max]. Nobody types a date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanDateDialog(initial: LocalDate, min: LocalDate, max: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val lo = min.millis()
    val hi = max?.millis()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.millis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= lo && (hi == null || utcTimeMillis <= hi)
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(LocalDate.fromEpochDays((it / DAY_MS).toInt())) }
                onDismiss()
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state, showModeToggle = false) }
}

/** The ring in front of a step: filled with a tick once it is done. */
@Composable
internal fun StepRing(done: Boolean, tint: Color, size: Dp = 22.dp) {
    val c = V4.colors
    Box(
        Modifier.size(size).clip(CircleShape).background(if (done) tint else c.surface).border(2.dp, if (done) tint else c.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(PhosphorIcons.Bold.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

/** A 44dp × that takes something off a list. */
@Composable
internal fun RemoveButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(PhosphorIcons.Bold.X, contentDescription = null, tint = V4.colors.ink3, modifier = Modifier.size(16.dp))
    }
}

/** A one-line bordered field that submits on Done: a step's name, an amount. */
@Composable
internal fun PlanField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    label: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    big: Boolean = false,
) {
    val c = V4.colors
    val style = if (big) V4.type.bodyStrong.copy(fontSize = V4.type.headline.fontSize) else V4.type.body
    Box(
        modifier.fillMaxWidth().heightIn(min = if (big) 56.dp else 48.dp).clip(RoundedCornerShape(if (big) 18.dp else 16.dp))
            .border(if (big) 2.dp else 1.5.dp, if (big) c.accent else c.line, RoundedCornerShape(if (big) 18.dp else 16.dp))
            .padding(horizontal = 16.dp, vertical = if (big) 16.dp else 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(hint, style = style.copy(fontWeight = V4.type.body.fontWeight), color = c.ink3)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = style.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}
