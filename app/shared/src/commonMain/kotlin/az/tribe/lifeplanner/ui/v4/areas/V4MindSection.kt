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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.data.habits.NudgePlan
import az.tribe.lifeplanner.data.habits.NudgePrefs
import az.tribe.lifeplanner.domain.service.HabitLearning
import az.tribe.lifeplanner.domain.service.MoodYear
import az.tribe.lifeplanner.domain.service.SleepDebt
import az.tribe.lifeplanner.ui.v4.components.V4Switch
import az.tribe.lifeplanner.ui.v4.components.V4TimeDialog
import com.mmk.kmpnotifier.notification.NotifierManager
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
 * The Sleep and mind page's own part: a two-tap mood check-in, a year of moods in pixels, an
 * opt-in daily reminder that can be answered from the notification, what lifts the user's mood
 * (worked out on the phone), sleep against a goal with the week's sleep debt, a breathing break
 * that becomes mindful minutes, one journal card, and a way to reach a person when it is needed.
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
    var writing by remember { mutableStateOf(false) }
    var goalSheet by remember { mutableStateOf(false) }
    var pickedDay by remember { mutableStateOf<LocalDate?>(null) }
    val reminder by viewModel.reminder.collectAsState()

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

    // ── A year in moods ──
    SectionHeading("Your year in moods")
    V4Card {
        Text(
            if (s.checkInDays14 == 0) "Check in a few times and your days fill in here"
            else "${s.moodSummary} lately, ${s.checkInDays14} of the last 14 days",
            style = V4.type.bodyStrong, color = c.ink,
        )
        MoodYearGrid(s.year, pickedDay, tint.color) { d -> pickedDay = d; viewModel.tappedDay() }
        MoodLegend(tint.color)
        val picked = pickedDay
        if (picked == null) {
            Text("Tap or slide along a row to see a day.", style = V4.type.caption, color = c.ink3)
        } else {
            val day = s.moodDays[picked]
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceMuted)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    "${V4TodayViewModel.dayLabel(picked)}: ${day?.let { MindCheckIns.label(it.level) } ?: "no check-in"}",
                    style = V4.type.bodyStrong, color = c.ink,
                )
                day?.note?.let { Text(it, style = V4.type.caption, color = c.ink2, maxLines = 3) }
            }
        }
    }

    // ── Daily reminder ──
    MoodReminderCard(reminder, onToggle = { on ->
        if (on) runCatching { NotifierManager.getPermissionUtil().askNotificationPermission() }
        viewModel.setReminder(on)
    }, onMinute = viewModel::setReminderMinute)

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
            s.sleepDebt?.let { debt -> SleepDebtLine(debt, s.sleepGoal == null, tint.color) }
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
        V4PillButton("Write", onClick = { writing = true }, container = tint.color)
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
    if (writing) WriteSheet(
        prompt = V4MindViewModel.prompt(s.promptOffset),
        onAnother = viewModel::nextPrompt,
        onDismiss = { writing = false },
        onSave = { text, p -> viewModel.write(text, p); writing = false },
        onSaveThree = { viewModel.threeGoodThings(it); writing = false },
    )
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

/**
 * The one way into the journal from this page: today's question, with another question or three
 * good things one tap away inside the sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WriteSheet(
    prompt: String,
    onAnother: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onSaveThree: (List<String>) -> Unit,
) {
    val c = V4.colors
    val tint = c.area(PlanArea.MIND)
    var threeGood by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var d by remember { mutableStateOf("") }
    AreaSheet(if (threeGood) "Three good things" else "Write", onDismiss) {
        if (!threeGood) {
            Text(prompt, style = V4.type.bodyStrong, color = c.ink, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            MultiLineField(text, { text = it }, "Whatever comes to mind", "Journal entry", minLines = 6)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                V4PillButton("Another question", onClick = onAnother, filled = false)
                V4PillButton("Three good things instead", onClick = { threeGood = true }, filled = false)
            }
            V4PrimaryButton("Save", onClick = { onSave(text, prompt) }, enabled = text.isNotBlank(), container = tint.color, modifier = Modifier.fillMaxWidth())
        } else {
            Text("Small counts. A good coffee, a kind word, a thing that went right.", style = V4.type.caption, color = c.ink2)
            TravelField(a, { a = it }, "One", "First good thing")
            TravelField(b, { b = it }, "Two", "Second good thing")
            TravelField(d, { d = it }, "Three", "Third good thing")
            V4TextButton("Back to the question", onClick = { threeGood = false })
            V4PrimaryButton("Save", onClick = { onSaveThree(listOf(a, b, d)) }, enabled = a.isNotBlank(), container = tint.color, modifier = Modifier.fillMaxWidth())
        }
        SheetNote("Only you can read your journal.")
    }
}

/** Mood level to colour: greys for the low days, the Mind colour growing for the good ones. */
private fun moodLevelColor(level: Int, c: az.tribe.lifeplanner.ui.v4.theme.V4Colors, strong: Color): Color = when (level) {
    1 -> c.ink3.copy(alpha = 0.85f)
    2 -> c.ink3.copy(alpha = 0.4f)
    3 -> strong.copy(alpha = 0.3f)
    4 -> strong.copy(alpha = 0.62f)
    5 -> strong
    else -> c.surfaceMuted
}

/**
 * A year in pixels: one row per month, one cell per day. Tap a day, or slide along a row, to see
 * it. The cells are too small to tap one by one with a screen reader, so the grid reads out each
 * month's summary instead, and the check-in history stays a tap away in the journal.
 */
@Composable
private fun MoodYearGrid(rows: List<MoodYear.Row>, picked: LocalDate?, strong: Color, onPick: (LocalDate) -> Unit) {
    if (rows.isEmpty()) return
    val c = V4.colors
    val pick by rememberUpdatedState(onPick)
    val rowH = 12.dp
    val gap = 3.dp
    val cellGap = 1.5.dp
    val labelStyle = V4.type.micro.copy(fontSize = 10.sp, lineHeight = 12.sp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.width(26.dp).clearAndSetSemantics { }, verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.forEach { r ->
                Box(Modifier.height(rowH), contentAlignment = Alignment.CenterStart) { Text(r.label, style = labelStyle, color = c.ink3, maxLines = 1) }
            }
        }
        fun hit(x: Float, y: Float, width: Float, pitchY: Float): LocalDate? {
            val ri = (y / pitchY).toInt()
            if (y < 0 || ri !in rows.indices) return null
            val col = (x / (width / 31f)).toInt().coerceIn(0, 30)
            val row = rows[ri]
            if (col >= row.levels.size || row.levels[col] == MoodYear.FUTURE) return null
            return row.date(col + 1)
        }
        val description = rows.joinToString(". ") { MoodYear.describe(it) }
        Canvas(
            Modifier.weight(1f).height(rowH * rows.size + gap * (rows.size - 1))
                .clearAndSetSemantics { contentDescription = "Your moods by day. $description" }
                .pointerInput(rows) {
                    val pitchY = (rowH + gap).toPx()
                    detectTapGestures { o -> hit(o.x, o.y, size.width.toFloat(), pitchY)?.let(pick) }
                }
                .pointerInput(rows) {
                    val pitchY = (rowH + gap).toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { o -> hit(o.x, o.y, size.width.toFloat(), pitchY)?.let(pick) },
                    ) { change, _ ->
                        change.consume()
                        hit(change.position.x, change.position.y, size.width.toFloat(), pitchY)?.let(pick)
                    }
                },
        ) {
            val pitchX = size.width / 31f
            val pitchY = (rowH + gap).toPx()
            val cellW = pitchX - cellGap.toPx()
            val cellH = rowH.toPx()
            val corner = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            rows.forEachIndexed { ri, row ->
                row.levels.forEachIndexed { i, level ->
                    if (level == MoodYear.FUTURE) return@forEachIndexed
                    val topLeft = Offset(i * pitchX, ri * pitchY)
                    drawRoundRect(moodLevelColor(level, c, strong), topLeft, Size(cellW, cellH), corner)
                    if (picked != null && row.date(i + 1) == picked) {
                        drawRoundRect(c.ink, topLeft, Size(cellW, cellH), corner, style = Stroke(width = 1.5.dp.toPx()))
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodLegend(strong: Color) {
    val c = V4.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = "Grey for awful and low days, stronger colour for good and great ones" },
    ) {
        Text("Awful", style = V4.type.micro, color = c.ink3)
        (1..5).forEach { Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(moodLevelColor(it, c, strong))) }
        Text("Great", style = V4.type.micro, color = c.ink3)
    }
}

/** One kind line about the week's sleep against the goal. Never a scold: on target is said out loud. */
@Composable
private fun SleepDebtLine(debt: SleepDebt.Result, defaultGoal: Boolean, strong: Color) {
    val c = V4.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (debt.onTarget) c.successSoft else c.area(PlanArea.MIND).soft)
            .semantics(mergeDescendants = true) { }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(SleepDebt.line(debt), style = V4.type.bodyStrong, color = if (debt.onTarget) c.success else c.area(PlanArea.MIND).ink)
        val goalNote = if (defaultGoal) "Against ${V4TodayViewModel.formatHours(SleepDebt.DEFAULT_GOAL)}, the low end of what most adults need. Set your own goal above." else null
        val note = when {
            debt.ahead -> "More rest than your goal. Nice."
            debt.onTarget -> "Right where you want to be."
            else -> "An earlier night or two will close it."
        }
        Text(listOfNotNull(note, goalNote).joinToString(" "), style = V4.type.caption, color = c.ink2)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoodReminderCard(r: NudgePrefs.Snapshot, onToggle: (Boolean) -> Unit, onMinute: (Int?) -> Unit) {
    val c = V4.colors
    var picking by remember { mutableStateOf(false) }
    V4Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("A daily nudge to check in", style = V4.type.bodyStrong, color = c.ink)
                Text(
                    when {
                        !r.mood -> "Off. One gentle question a day, if you want it."
                        r.moodMinute == null -> "A different time each day, between ${HabitLearning.clock(NudgePlan.SURPRISE_FROM.hour * 60)} and ${HabitLearning.clock(NudgePlan.SURPRISE_TO.hour * 60)}"
                        else -> "Every day at ${HabitLearning.clock(r.moodMinute)}"
                    },
                    style = V4.type.caption, color = c.ink2,
                )
            }
            V4Switch(r.mood, onToggle, label = "Daily mood reminder")
        }
        if (r.mood) {
            val presets = listOf(9 * 60, 13 * 60, 20 * 60)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (presets + listOfNotNull(r.moodMinute?.takeIf { it !in presets })).forEach { m ->
                    Choice(HabitLearning.clock(m), r.moodMinute == m) { onMinute(m) }
                }
                Choice("Other time", false) { picking = true }
                Choice("Surprise me", r.moodMinute == null) { onMinute(null) }
            }
            Text("Answer Low, Okay or Good right from the notification. It stays quiet on days you already checked in.", style = V4.type.caption, color = c.ink3)
        }
    }
    if (picking) V4TimeDialog(
        r.moodMinute?.let { LocalTime(it / 60, it % 60) },
        onPick = { t -> onMinute(t.hour * 60 + t.minute); picking = false },
        onDismiss = { picking = false },
    )
}
