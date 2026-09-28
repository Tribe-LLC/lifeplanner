package az.tribe.lifeplanner.ui.v4.areas

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.MindCheckIns
import az.tribe.lifeplanner.domain.service.MindInsights
import az.tribe.lifeplanner.ui.components.GuidedBreathSession
import az.tribe.lifeplanner.ui.health.rememberHealthPermissionLauncher
import az.tribe.lifeplanner.ui.v4.components.OneLine
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.theme.V4
import az.tribe.lifeplanner.ui.v4.today.V4TodayViewModel
import az.tribe.lifeplanner.ui.v4.travel.TravelField
import kotlin.time.Clock
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Sleep and mind page's own part: a two-tap mood check-in, the last two weeks, what lifts the
 * user's mood (worked out on the phone), sleep against a goal, a breathing break that becomes
 * mindful minutes, the journal, and a way to reach a person when it is needed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MindSection(onRoute: (String) -> Unit, viewModel: V4MindViewModel = koinViewModel()) {
    val s by viewModel.state.collectAsState()
    val c = V4.colors
    val tint = c.area(PlanArea.MIND)
    val askHealth = rememberHealthPermissionLauncher { viewModel.onHealthGranted() }
    var breathing by remember { mutableStateOf(false) }
    var breathStart by remember { mutableLongStateOf(0L) }
    var writing by remember { mutableStateOf<String?>(null) }
    var threeGood by remember { mutableStateOf(false) }
    var goalSheet by remember { mutableStateOf(false) }

    if (s.lowRun) HelpCard()

    // ── Check-in ──
    V4Card(modifier = Modifier.fillMaxWidth(), color = tint.soft, bordered = false) {
        val e = s.editing
        val score = e?.let { MindCheckIns.score(it) }
        Text(
            if (score != null) "Feeling ${MindCheckIns.label(score).lowercase()}, saved" else "How are you right now?",
            style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() },
        )
        if (e == null) s.lastToday?.let { l ->
            MindCheckIns.score(l)?.let { Text("Earlier today: ${MindCheckIns.label(it).lowercase()} at ${V4TodayViewModel.hhmm(l.occurredAt)}", style = V4.type.caption, color = c.ink2) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (1..5).forEach { level ->
                val on = score == level
                Column(
                    Modifier.weight(1f).padding(horizontal = 2.dp).clip(RoundedCornerShape(18.dp)).background(c.surface)
                        .border(2.dp, if (on) tint.color else c.surface, RoundedCornerShape(18.dp))
                        .clickable(role = Role.RadioButton) { viewModel.pick(level) }
                        .semantics { contentDescription = MindCheckIns.label(level); selected = on }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    MoodFace(level, Modifier.size(36.dp))
                    Text(MindCheckIns.label(level), style = V4.type.micro, color = c.ink2)
                }
            }
        }
        if (e != null) {
            val tags = MindCheckIns.tags(e)
            val feelings = MindCheckIns.feelings(e)
            FormLabel("What's part of it? Pick any")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MindCheckIns.TAGS.forEach { t -> Choice(t, t in tags) { viewModel.toggleTag(t) } }
            }
            FormLabel("Any words for it?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MindCheckIns.FEELINGS.forEach { f -> Choice(f, f in feelings) { viewModel.toggleFeeling(f) } }
            }
            var note by remember(e.id) { mutableStateOf(MindCheckIns.note(e) ?: "") }
            TravelField(note, { note = it }, "A note, if you like", "Note")
            V4PillButton("Done", onClick = { if (note != (MindCheckIns.note(e) ?: "")) viewModel.setNote(note); viewModel.done() }, container = tint.color)
            Text(
                if (s.canWriteHealth) "Saved here and to Health." else "Saved here. Only you can see it.",
                style = V4.type.caption, color = c.ink2,
            )
        }
    }

    // ── Last 14 days ──
    SectionHeading("Last 14 days")
    V4Card {
        Text(
            if (s.checkInDays14 == 0) "Check in a few times and your days show here" else "${s.moodSummary}, ${s.checkInDays14} ${if (s.checkInDays14 == 1) "day" else "days"} with a check-in",
            style = V4.type.bodyStrong, color = c.ink,
        )
        Row(
            Modifier.fillMaxWidth().height(70.dp).clearAndSetSemantics { contentDescription = "Mood over the last 14 days: ${s.moodSummary}" },
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom,
        ) {
            s.moods.forEach { m ->
                Box(
                    Modifier.weight(1f).height(if (m == null) 6.dp else (10 + m * 12).dp).clip(RoundedCornerShape(5.dp))
                        .background(if (m == null) c.trackOff else moodColor(m, tint.color)),
                )
            }
        }
    }

    // ── What lifts you ──
    V4Card {
        Text("What lifts you", style = V4.type.bodyStrong, color = c.ink)
        if (s.lifts.isEmpty()) {
            val left = (MindInsights.MIN_CHECK_IN_DAYS - s.checkInDaysTotal).coerceAtLeast(0)
            Text(
                if (left > 0) "After ${MindInsights.MIN_CHECK_IN_DAYS} days of check-ins you will see what goes with your better days. $left to go."
                else "Nothing stands out yet. Keep checking in, and tag what is part of it.",
                style = V4.type.caption, color = c.ink2,
            )
        } else {
            s.lifts.forEach { l ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(l.what, style = V4.type.body, color = c.ink, modifier = Modifier.weight(1f))
                    Text(
                        MindInsights.formatDelta(l.delta), style = V4.type.label,
                        color = if (l.delta > 0) c.success else c.ink2,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (l.delta > 0) c.successSoft else c.surfaceMuted).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Text("Worked out on your phone from your check-ins, habits, steps and sleep. A difference in mood, not a cause.", style = V4.type.caption, color = c.ink3)
        }
    }

    // ── Sleep ──
    SectionHeading("Sleep")
    V4Card {
        if (s.sleep.isEmpty()) {
            Text("No sleep yet", style = V4.type.bodyStrong, color = c.ink)
            Text("Sleep comes in from Health once it is connected, and helps explain your mood.", style = V4.type.caption, color = c.ink2)
        } else {
            val avg = s.sleep.map { it.second }.average()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${V4TodayViewModel.formatHours(avg)} average", style = V4.type.headline, color = c.ink)
                V4TextButton(s.sleepGoal?.let { "Goal ${V4TodayViewModel.formatHours(it)}" } ?: "Set a goal", onClick = { goalSheet = true })
            }
            Row(
                Modifier.fillMaxWidth().height(76.dp).clearAndSetSemantics { contentDescription = "Sleep over the last week, ${V4TodayViewModel.formatHours(avg)} on average" },
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom,
            ) {
                s.sleep.forEach { (d, hrs) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val goal = s.sleepGoal ?: 7.0
                        Box(Modifier.fillMaxWidth().height((hrs.coerceIn(0.0, 10.0) * 5.5).dp).clip(RoundedCornerShape(6.dp)).background(if (hrs >= goal) tint.color else tint.color.copy(alpha = 0.35f)))
                        Text(d.dayOfWeek.name.take(1), style = V4.type.micro, color = c.ink3)
                    }
                }
            }
            Text("From Health. Short nights also tell the coach to go easy on your plans.", style = V4.type.caption, color = c.ink3)
        }
    }

    // ── Calm ──
    SectionHeading("Calm")
    V4Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (s.mindfulWeek == 0) "A short breathing break" else "${s.mindfulWeek} mindful ${if (s.mindfulWeek == 1) "minute" else "minutes"} this week", style = V4.type.bodyStrong, color = c.ink)
                Text(
                    when {
                        s.canWriteHealth -> "Saved to Health as mindful minutes"
                        s.supportsMindful -> "Kept here. Health can take them too"
                        else -> "Under a minute, whenever you need it"
                    },
                    style = V4.type.caption, color = c.ink2,
                )
            }
            V4PillButton("Breathe", onClick = { breathStart = Clock.System.now().toEpochMilliseconds(); breathing = true }, container = tint.color)
        }
        if (s.supportsMindful && !s.canWriteHealth) V4TextButton("Allow Health to take them", onClick = askHealth)
    }

    // ── Journal ──
    SectionHeading("Journal")
    V4Card {
        val prompt = V4MindViewModel.prompt(s.promptOffset)
        Text("Today's question", style = V4.type.label, color = tint.ink)
        Text(prompt, style = V4.type.headline, color = c.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            V4PillButton("Write", onClick = { writing = prompt }, container = tint.color)
            V4PillButton("Three good things", onClick = { threeGood = true }, filled = false)
            V4TextButton("Another question", onClick = viewModel::nextPrompt)
        }
        s.entries.forEach { en ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onRoute("journal_entry_detail/${en.id}") }.heightIn(min = 44.dp).padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                OneLine(en.title.ifBlank { "Journal entry" }, V4.type.bodyStrong, c.ink)
                Text("${V4TodayViewModel.dayLabel(en.date)}, feeling ${MindCheckIns.label(en.mood.score).lowercase()}", style = V4.type.caption, color = c.ink3)
            }
        }
        V4TextButton("All entries", onClick = { onRoute("journal") })
    }

    if (!s.lowRun) HelpCard()

    if (breathing) GuidedBreathSession(onClose = { breathing = false }, onFinished = { viewModel.breathed(breathStart) })
    writing?.let { p -> WriteSheet(p, onDismiss = { writing = null }, onSave = { viewModel.write(it, p); writing = null }) }
    if (threeGood) ThreeGoodSheet(onDismiss = { threeGood = false }, onSave = { viewModel.threeGoodThings(it); threeGood = false })
    if (goalSheet) {
        AreaSheet("Sleep goal", { goalSheet = false }) {
            val options = listOf(6.5, 7.0, 7.5, 8.0, 8.5, 9.0)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { h -> Choice(V4TodayViewModel.formatHours(h), s.sleepGoal == h) { viewModel.setSleepGoal(h); goalSheet = false } }
                Choice("No goal", s.sleepGoal == null) { viewModel.setSleepGoal(null); goalSheet = false }
            }
            SheetNote("Most adults do best on 7 to 9 hours.")
        }
    }
}

@Composable
private fun SectionHeading(text: String) =
    Text(text, style = V4.type.headline, color = V4.colors.ink, modifier = Modifier.padding(top = 4.dp).semantics { heading() })

/** Always on the page, never pushy: moves to the top after a run of low check-ins. */
@Composable
private fun HelpCard() {
    val c = V4.colors
    val uri = LocalUriHandler.current
    V4Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Need to talk to someone?", style = V4.type.bodyStrong, color = c.ink)
                Text("Free, confidential helplines in your country", style = V4.type.caption, color = c.ink2)
            }
            V4PillButton("Find help", onClick = { runCatching { uri.openUri("https://findahelpline.com") } }, filled = false)
        }
    }
}

private fun moodColor(m: Double, strong: Color): Color = when {
    m >= 4.5 -> strong
    m >= 3.5 -> strong.copy(alpha = 0.6f)
    m >= 2.5 -> strong.copy(alpha = 0.3f)
    else -> Color(0xFF9CA3AF)
}

/** A simple drawn face per level, so the scale reads without words or emoji. */
@Composable
internal fun MoodFace(level: Int, modifier: Modifier = Modifier) {
    val fills = listOf(Color(0xFFC9CCD6), Color(0xFFDDD0F0), Color(0xFFF2E3F5), Color(0xFFF0B8F7), Color(0xFFE879F9))
    val ink = Color(0xFF15171C)
    Canvas(modifier) {
        val w = size.width
        val r = w / 2f - 1.5f
        drawCircle(fills[(level - 1).coerceIn(0, 4)], radius = r)
        drawCircle(ink, radius = r, style = Stroke(width = w * 0.055f))
        drawCircle(ink, radius = w * 0.055f, center = Offset(w * 0.35f, w * 0.42f))
        drawCircle(ink, radius = w * 0.055f, center = Offset(w * 0.65f, w * 0.42f))
        val stroke = Stroke(width = w * 0.06f, cap = StrokeCap.Round)
        val left = w * 0.32f
        val width = w * 0.36f
        when (level) {
            3 -> drawLine(ink, Offset(left, w * 0.66f), Offset(left + width, w * 0.66f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
            1, 2 -> {
                val h = if (level == 1) w * 0.26f else w * 0.16f
                drawArc(ink, 200f, 140f, false, topLeft = Offset(left, w * 0.64f), size = Size(width, h), style = stroke)
            }
            else -> {
                val h = if (level == 5) w * 0.3f else w * 0.2f
                drawArc(ink, 20f, 140f, false, topLeft = Offset(left, w * 0.56f - h / 2f), size = Size(width, h), style = stroke)
            }
        }
    }
}

@Composable
private fun WriteSheet(prompt: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AreaSheet("Write", onDismiss) {
        Text(prompt, style = V4.type.bodyStrong, color = V4.colors.ink)
        MultiLineField(text, { text = it }, "Whatever comes to mind", "Journal entry", minLines = 6)
        V4PrimaryButton("Save", onClick = { onSave(text) }, enabled = text.isNotBlank(), container = V4.colors.area(PlanArea.MIND).color, modifier = Modifier.fillMaxWidth())
        SheetNote("Only you can read your journal.")
    }
}

@Composable
private fun ThreeGoodSheet(onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var d by remember { mutableStateOf("") }
    AreaSheet("Three good things", onDismiss) {
        Text("Small counts. A good coffee, a kind word, a thing that went right.", style = V4.type.caption, color = V4.colors.ink2)
        TravelField(a, { a = it }, "One", "First good thing")
        TravelField(b, { b = it }, "Two", "Second good thing")
        TravelField(d, { d = it }, "Three", "Third good thing")
        V4PrimaryButton("Save", onClick = { onSave(listOf(a, b, d)) }, enabled = a.isNotBlank(), container = V4.colors.area(PlanArea.MIND).color, modifier = Modifier.fillMaxWidth())
    }
}
