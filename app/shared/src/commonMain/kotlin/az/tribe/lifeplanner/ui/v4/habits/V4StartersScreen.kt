package az.tribe.lifeplanner.ui.v4.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.domain.enum.HabitCompletionSource
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.components.V4Switch
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.components.areaName
import az.tribe.lifeplanner.ui.v4.illustration.AreaIllustration
import az.tribe.lifeplanner.ui.v4.theme.V4
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.Check
import com.adamglin.phosphoricons.bold.Plus
import com.adamglin.phosphoricons.bold.X
import com.adamglin.phosphoricons.regular.CaretLeft
import com.adamglin.phosphoricons.Regular
import org.koin.compose.viewmodel.koinViewModel

/**
 * First run, step 3: swipe through starter habits from the picked areas (right adds, left skips,
 * up to three), then tick any already done today and choose the evening check-in.
 */
@Composable
fun V4StartersScreen(
    stepLabel: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: V4StartersViewModel = koinViewModel(),
) {
    val s by viewModel.state.collectAsState()
    val c = V4.colors
    var command by remember { mutableStateOf<Swipe?>(null) }
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (s.finished && s.added.isNotEmpty()) {
            FirstTick(s, onTick = viewModel::tick, onEvening = viewModel::setEvening, onDone = onDone)
            return@Column
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(c.surface).border(1.dp, c.line, CircleShape)
                    .clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) { Icon(PhosphorIcons.Regular.CaretLeft, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp)) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                stepLabel?.let { Text(it, style = V4.type.label, color = c.ink2) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.semantics { contentDescription = "${s.added.size} of ${StarterDeck.PICK} picked" }) {
                    repeat(StarterDeck.PICK) { i ->
                        Box(Modifier.width(28.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (i < s.added.size) c.accent else c.line))
                    }
                }
            }
            Box(
                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(22.dp))
                    .clickable(enabled = s.canUndo, role = Role.Button, onClick = viewModel::undo).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Undo", style = V4.type.bodyStrong, color = if (s.canUndo) c.accentInk else c.ink3) }
        }
        if (s.finished) {
            // Nothing picked: straight on, Today still has plans and the calendar.
            EndScreen(DeckArt.BALANCED, "No habits yet, that is fine", "Today still shows your plans and calendar. Add a habit whenever you like.", "Open Today", onDone)
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Start with up to ${StarterDeck.PICK}", style = V4.type.display.copy(fontSize = 26.sp, lineHeight = 29.sp), color = c.ink, modifier = Modifier.semantics { heading() })
            Text("From the areas you picked. Small ones win, you can add more later.", style = V4.type.body, color = c.ink2)
        }
        val card = s.current ?: return@Column
        Box(Modifier.weight(1f).fillMaxWidth()) {
            DeckBacking(s.cards.size - s.index)
            SwipeCard(
                key = card.starter.title + s.index,
                command = command,
                onGone = { dir -> command = null; viewModel.swipe(dir) },
                labels = mapOf(Swipe.RIGHT to "ADD", Swipe.LEFT to "SKIP"),
                actions = mapOf(Swipe.RIGHT to "Add", Swipe.LEFT to "Skip"),
                modifier = Modifier.fillMaxSize().padding(bottom = 12.dp),
            ) { StarterFace(card) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally)) {
            RoundAction(PhosphorIcons.Bold.X, "Skip", { if (command == null && !s.busy) command = Swipe.LEFT }, big = true, primary = false)
            RoundAction(PhosphorIcons.Bold.Plus, "Add", { if (command == null && !s.busy) command = Swipe.RIGHT }, big = true, primary = true)
        }
        V4TextButton("Skip for now", onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun StarterFace(card: StarterCard) {
    CardFace(card.tint) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            LightPill(areaName(card.area), V4.colors.area(card.area).ink)
            LightPill(card.whenText)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(130.dp).clip(RoundedCornerShape(36.dp)).background(Color(0xE6FFFFFF)), contentAlignment = Alignment.Center) {
                AreaIllustration(card.area, size = 96.dp)
            }
        }
        Text(card.starter.title, style = V4.type.display.copy(fontSize = 30.sp, lineHeight = 32.sp), color = Color.White, maxLines = 3)
        Text(card.meta, style = V4.type.bodyStrong, color = Color.White)
        Text(card.why, style = V4.type.body, color = Color(0xEBFFFFFF))
    }
}

/** "Done any of these today?": the first tick, before Today opens. */
@Composable
private fun FirstTick(s: StartersState, onTick: (Habit) -> Unit, onEvening: (Boolean) -> Unit, onDone: () -> Unit) {
    val c = V4.colors
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ArtPlate(DeckArt.DONE, 150.dp)
                Text("Done any of these today?", style = V4.type.display.copy(fontSize = 28.sp, lineHeight = 31.sp), color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                Text("Tick what you already did. It counts.", style = V4.type.body, color = c.ink2, textAlign = TextAlign.Center)
            }
            s.added.forEach { (card, habit) -> TickRow(card, habit, s.ticked[habit.id], onTick) }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.accentSoft).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.eveningText, style = V4.type.bodyStrong, color = c.ink)
                    Text(
                        "One nudge with what is still open. Nothing if you are done. Once it knows when you usually check in, it moves there.",
                        style = V4.type.caption, color = c.ink2,
                    )
                }
                V4Switch(s.evening, onEvening, label = "Evening check-in")
            }
        }
        V4PrimaryButton("Open Today", onDone, Modifier.fillMaxWidth())
    }
}

@Composable
private fun TickRow(card: StarterCard, habit: Habit, ticked: Int?, onTick: (Habit) -> Unit) {
    val c = V4.colors
    val selfTicking = habit.healthMetricType != null || habit.completionSource != HabitCompletionSource.MANUAL
    val count = habit.targetCount > 1
    val done = if (count) (ticked ?: 0) >= habit.targetCount else ticked != null
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(18.dp)).background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AreaIllustration(card.area, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(habit.title, style = V4.type.bodyStrong, color = c.ink)
            Text(
                when {
                    habit.healthMetricType != null -> "Ticks itself from Health"
                    habit.completionSource == HabitCompletionSource.BREATHING -> "Ticks itself after a breathing break"
                    count -> "${ticked ?: 0} of ${habit.targetCount}${habit.unit?.let { " $it" } ?: ""}"
                    else -> card.meta
                },
                style = V4.type.caption, color = c.ink3,
            )
        }
        if (!selfTicking) {
            val label = when { count -> "+1"; done -> null; else -> "Done" }
            Box(
                Modifier.heightIn(min = 44.dp).widthIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
                    .background(if (done) c.accent else c.surface).border(2.dp, c.accent, RoundedCornerShape(22.dp))
                    .clickable(role = Role.Button, enabled = !(count && done)) { onTick(habit) }
                    .semantics {
                        contentDescription = if (count) "One more for ${habit.title}" else "${habit.title} done today"
                        stateDescription = if (done) "Done" else "Not done"
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (label != null) Text(label, style = V4.type.bodyStrong, color = if (done) c.onAccent else c.accentInk)
                else Icon(PhosphorIcons.Bold.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(20.dp))
            }
        } else Spacer(Modifier.width(4.dp))
    }
}
