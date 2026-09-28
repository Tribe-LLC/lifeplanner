package az.tribe.lifeplanner.ui.v4.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.domain.service.MindCheckIns
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.theme.V4

/** A small outlined pill button, 44dp tall. */
@Composable
internal fun PillButton(text: String, onClick: () -> Unit, primary: Boolean = false, modifier: Modifier = Modifier) {
    val c = V4.colors
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (primary) c.accent else c.surface)
            .let { if (primary) it else it.border(1.dp, c.line, RoundedCornerShape(22.dp)) }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = V4.type.bodyStrong, color = if (primary) c.onAccent else c.ink) }
}

/**
 * "From yesterday": earlier plans that did not happen, greyed, each with Today, Tomorrow or Let it
 * go. Shows three at a time so a long list never becomes a pile.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarryCard(items: List<CarryItem>, onDecide: (CarryItem, CarryChoice) -> Unit, modifier: Modifier = Modifier) {
    val c = V4.colors
    var all by rememberSaveable { mutableStateOf(false) }
    V4Card(modifier.fillMaxWidth(), color = c.surface) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("From yesterday", style = V4.type.bodyStrong, color = c.ink, modifier = Modifier.semantics { heading() })
            Text("No rush, pick one", style = V4.type.caption, color = c.ink3)
        }
        val shown = if (all) items else items.take(3)
        shown.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.title, style = V4.type.bodyStrong, color = c.ink2)
                    Text(item.sub, style = V4.type.caption, color = c.ink3)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Today", { onDecide(item, CarryChoice.TODAY) }, primary = true)
                    PillButton("Tomorrow", { onDecide(item, CarryChoice.TOMORROW) })
                    PillButton("Let it go", { onDecide(item, CarryChoice.LET_GO) })
                }
            }
        }
        if (items.size > 3) {
            V4TextButton(if (all) "Show fewer" else "Show ${items.size - 3} more", onClick = { all = !all })
        }
    }
}

/** From 18:00: wins first, then what is left, habits via the deck, and one tap for how it felt. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WrapUpCard(
    w: WrapUp,
    onDecide: (DayItem, CarryChoice) -> Unit,
    onCheckIn: () -> Unit,
    onMood: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = V4.colors
    V4Card(modifier.fillMaxWidth(), color = c.accentSoft, bordered = false) {
        Text("WRAP UP THE DAY", style = V4.type.label.copy(letterSpacing = 0.6.sp), color = c.accentInk)
        Text(
            when (w.winCount) {
                0 -> "A quiet day so far"
                1 -> "1 thing done today"
                else -> "${w.winCount} things done today"
            },
            style = V4.type.display.copy(fontSize = 22.sp, lineHeight = 26.sp),
            color = c.ink,
            modifier = Modifier.semantics { heading() },
        )
        if (w.wins.isNotEmpty()) {
            val names = w.wins.take(5).joinToString(", ") + if (w.wins.size > 5) " and ${w.wins.size - 5} more." else "."
            Text(names, style = V4.type.body, color = c.ink2)
        } else {
            Text("Some days are like that. Tomorrow is a fresh one.", style = V4.type.body, color = c.ink2)
        }
        w.open.forEach { item ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(item.title, style = V4.type.bodyStrong, color = c.ink)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Tomorrow", { onDecide(item, CarryChoice.TOMORROW) })
                    PillButton(if (item.type == DayItemType.STEP) "Let it go" else "Not today", { onDecide(item, CarryChoice.LET_GO) })
                }
            }
        }
        if (w.habitsLeft > 0) {
            V4PrimaryButton(
                if (w.habitsLeft == 1) "Check in on 1 habit" else "Check in on ${w.habitsLeft} habits",
                onCheckIn,
                Modifier.fillMaxWidth(),
            )
        }
        Text("How was today?", style = V4.type.bodyStrong, color = c.ink)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (1..5).forEach { score ->
                val on = w.mood == score
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(c.surface)
                        .border(2.dp, if (on) c.accent else c.line, CircleShape)
                        .clickable(role = Role.Button) { onMood(score) }
                        .semantics { contentDescription = MindCheckIns.label(score); selected = on },
                    contentAlignment = Alignment.Center,
                ) { Text(FACES[score - 1], fontSize = 24.sp) }
            }
        }
        if (w.mood != null) Text("Saved to Sleep and mind.", style = V4.type.caption, color = c.accentInk)
        V4TextButton("Done for today", onClick = onClose, modifier = Modifier.align(Alignment.End))
    }
}

private val FACES = listOf("😞", "😕", "😐", "🙂", "😄")
