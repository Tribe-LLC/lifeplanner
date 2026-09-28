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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.data.fitness.WorkoutService
import az.tribe.lifeplanner.data.health.WorkoutKind
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.FitnessWeek
import az.tribe.lifeplanner.domain.service.RingState
import az.tribe.lifeplanner.ui.v4.components.CheckCircleButton
import az.tribe.lifeplanner.ui.v4.components.OneLine
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4Divider
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.components.V4ProgressBar
import az.tribe.lifeplanner.ui.v4.components.V4Switch
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.theme.V4
import az.tribe.lifeplanner.ui.v4.today.V4TodayViewModel
import kotlinx.coroutines.delay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Fitness page's own part, per the canvas: today's workout with a timer that saves to Health,
 * the last seven days against a weekly goal, what came in from Health, how Fitness touches the
 * other areas, and what is coming up.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FitnessSection(health: AreaHealth, onOpenHealth: () -> Unit, viewModel: V4FitnessViewModel = koinViewModel()) {
    val s by viewModel.state.collectAsState()
    val active by viewModel.active.collectAsState()
    val c = V4.colors
    val fit = c.area(PlanArea.FITNESS)
    var planning by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var editingTarget by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<LifeLog?>(null) }
    var savedNote by remember { mutableStateOf<String?>(null) }

    // ── Today ──
    val a = active
    V4Card(modifier = Modifier.fillMaxWidth(), color = fit.soft, bordered = false) {
        when {
            a != null -> {
                var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
                LaunchedEffect(a.startEpochMs) {
                    while (true) { now = Clock.System.now().toEpochMilliseconds(); delay(1_000) }
                }
                Text("${a.title} now", style = V4.type.label, color = fit.ink)
                Text(elapsed(now - a.startEpochMs), style = V4.type.number, color = c.ink, modifier = Modifier.semantics { contentDescription = "Elapsed ${elapsed(now - a.startEpochMs)}" })
                Text(
                    if (s.canWriteHealth) "Saves to Health when you stop." else "Saves here when you stop. Connect Health to send it there too.",
                    style = V4.type.caption, color = c.ink2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    V4PillButton("Stop and save", onClick = {
                        viewModel.stop { toHealth -> savedNote = if (toHealth) "Saved here and to Health." else "Saved." }
                    }, container = fit.color)
                    V4TextButton("Cancel", onClick = viewModel::cancel, color = c.ink2)
                }
            }
            s.today != null -> {
                val t = s.today!!
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Today" + if (WorkoutService.hasTime(t)) ", ${V4FitnessViewModel.fmt(t.occurredAt.time)}" else "",
                            style = V4.type.label, color = fit.ink,
                        )
                        Text(t.title + (t.durationMin?.let { ", $it min" } ?: ""), style = V4.type.headline, color = if (s.todayDone) c.ink3 else c.ink)
                        t.notes?.let { Text(it, style = V4.type.caption, color = c.ink2) }
                    }
                    CheckCircleButton(s.todayDone, (if (s.todayDone) "Undo: " else "Mark done: ") + t.title, { viewModel.toggleToday(t) }, color = fit.color)
                }
                if (!s.todayDone) {
                    val kind = WorkoutKind.fromTitle(t.title)
                    V4PillButton("Start ${t.title.lowercase()}", onClick = { viewModel.start(kind, t.title, t.id) }, container = fit.color)
                } else {
                    Text("Done. That counts toward your week.", style = V4.type.caption, color = c.ink2)
                }
            }
            else -> {
                Text("Today", style = V4.type.label, color = fit.ink)
                Text("Nothing planned", style = V4.type.headline, color = c.ink)
                Text("Start one now, or plan one for later so it shows on Today.", style = V4.type.caption, color = c.ink2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    V4PillButton("Start a workout", onClick = { picking = true }, container = fit.color)
                    V4PillButton("Plan one", onClick = { planning = true }, filled = false)
                }
            }
        }
        savedNote?.let {
            LaunchedEffect(it) { delay(3_000); savedNote = null }
            Text(it, style = V4.type.label, color = c.success)
        }
    }

    // ── Last 7 days ──
    V4Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Last 7 days", style = V4.type.label, color = c.ink2)
                Text("${s.doneThisWeek} of ${s.target} workouts", style = V4.type.headline, color = c.ink)
            }
            V4TextButton("Goal", onClick = { editingTarget = !editingTarget })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            s.rings.forEach { r ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val (fill, ring) = when (r.state) {
                        RingState.DONE -> fit.color to fit.color
                        RingState.TODAY -> c.surface to c.ink
                        RingState.MISSED -> c.surface to c.ink3
                        RingState.REST -> c.surface to c.line
                    }
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(fill).border(2.dp, ring, CircleShape)
                            .semantics {
                                contentDescription = FitnessWeek.dayName(r.date.dayOfWeek)
                                stateDescription = when (r.state) {
                                    RingState.DONE -> "Worked out"; RingState.TODAY -> "Today"
                                    RingState.MISSED -> "Missed"; RingState.REST -> "Rest day"
                                }
                            },
                    )
                    Text(r.label, style = V4.type.micro, color = c.ink3)
                }
            }
        }
        if (editingTarget) {
            Text("How many a week?", style = V4.type.label, color = c.ink2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (2..6).forEach { n -> Choice("$n", s.target == n) { viewModel.setTarget(n); editingTarget = false } }
            }
        }
    }

    // ── From Health ──
    V4Card {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("From Health", style = V4.type.bodyStrong, color = c.ink)
            Text(
                if (s.canWriteHealth) "Workouts from your watch show up here. Workouts you finish here are saved to Health."
                else "Steps, heart rate and watch workouts. Connect Health under You, Connected apps.",
                style = V4.type.caption, color = c.ink2,
            )
        }
        health.stepsToday?.let { steps ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Steps today", style = V4.type.body, color = c.ink)
                    Text("${V4TodayViewModel.formatThousands(steps.toLong())} of ${V4TodayViewModel.formatThousands(health.stepsTarget.toLong())}", style = V4.type.bodyStrong, color = c.ink)
                }
                V4ProgressBar((steps / health.stepsTarget).toFloat(), fit.color)
            }
        }
        s.lastWatch?.let { w ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    OneLine(w.title, V4.type.body, c.ink)
                    Text("${FitnessWeek.dayName(w.date.dayOfWeek)}, from your watch", style = V4.type.micro, color = c.ink3)
                }
                Text("${w.durationMin ?: 0} min", style = V4.type.bodyStrong, color = c.ink)
            }
        }
        health.restingHr?.let {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Heart rate", style = V4.type.body, color = c.ink)
                    Text("7 day average", style = V4.type.micro, color = c.ink3)
                }
                Text("${it.toInt()} bpm", style = V4.type.bodyStrong, color = c.ink)
            }
        }
        V4PillButton("Open health details", onClick = onOpenHealth, filled = false)
    }

    // ── Works with your other areas ──
    if (s.links.isNotEmpty()) {
        Text("Works with your other areas", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
        V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            s.links.forEachIndexed { i, l ->
                if (i > 0) V4Divider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(c.area(l.area).color))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(l.label, style = V4.type.label, color = c.area(l.area).ink)
                        Text(l.text, style = V4.type.body, color = c.ink)
                    }
                }
            }
        }
    }

    // ── Coming up ──
    Text("Coming up", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
    if (s.comingUp.isEmpty()) {
        Text("Nothing planned this week. Plan a workout and it lands on Today on the day.", style = V4.type.caption, color = c.ink2)
    } else {
        V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            s.comingUp.forEachIndexed { i, u ->
                if (i > 0) V4Divider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .let { m -> u.log?.let { l -> m.clickable(role = Role.Button) { selected = l } } ?: m }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(u.day, style = V4.type.label, color = c.ink)
                        Text(u.time, style = V4.type.micro, color = c.ink3)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        OneLine(u.title, V4.type.bodyStrong, c.ink)
                        OneLine(u.meta, V4.type.caption, c.ink3)
                    }
                }
            }
        }
    }
    V4PillButton("Plan a workout", onClick = { planning = true }, filled = false)

    if (planning) {
        PlanWorkoutSheet(
            canCalendar = s.canCalendar,
            onDismiss = { planning = false },
            onSave = { title, date, time, minutes, cal -> viewModel.plan(title, date, time, minutes, cal); planning = false },
        )
    }

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("What are you doing?") },
            text = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkoutKind.entries.forEach { k ->
                        Choice(k.label, false) { viewModel.start(k, k.label); picking = false }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Not now") } },
        )
    }

    selected?.let { l ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(l.title) },
            text = { Text((l.durationMin?.let { "$it min. " } ?: "") + (l.notes ?: "Planned here.")) },
            confirmButton = { TextButton(onClick = { viewModel.moveToToday(l); selected = null }) { Text("Do it today") } },
            dismissButton = { TextButton(onClick = { viewModel.remove(l); selected = null }) { Text("Remove") } },
        )
    }
}

private fun elapsed(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    fun two(n: Long) = n.toString().padStart(2, '0')
    return if (h > 0) "$h:${two(m)}:${two(sec)}" else "${two(m)}:${two(sec)}"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlanWorkoutSheet(
    canCalendar: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, date: LocalDate, time: LocalTime?, minutes: Int, toCalendar: Boolean) -> Unit,
) {
    val c = V4.colors
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var title by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(today) }
    var time by remember { mutableStateOf<LocalTime?>(LocalTime(18, 0)) }
    var minutes by remember { mutableStateOf(30) }
    var toCalendar by remember { mutableStateOf(canCalendar) }

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
            Text("Plan a workout", style = V4.type.title, color = c.ink, modifier = Modifier.semantics { heading() })

            Text("What", style = V4.type.label, color = c.ink2)
            Box(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, c.line, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (title.isEmpty()) Text("Leg day, 5 km run, yoga", style = V4.type.body, color = c.ink3)
                BasicTextField(
                    value = title, onValueChange = { title = it }, singleLine = true,
                    textStyle = V4.type.bodyStrong.copy(color = c.ink), cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Workout name" },
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Run", "Walk", "Strength", "Yoga", "Ride", "Swim").forEach { k -> Choice(k, title == k) { title = k } }
            }

            Text("When", style = V4.type.label, color = c.ink2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..6).forEach { i ->
                    val d = today.plus(DatePeriod(days = i))
                    val label = when (i) { 0 -> "Today"; 1 -> "Tomorrow"; else -> FitnessWeek.shortDay(d.dayOfWeek) }
                    Choice(label, day == d) { day = d }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf<LocalTime?>(null, LocalTime(7, 0), LocalTime(12, 30), LocalTime(18, 0), LocalTime(20, 0)).forEach { t ->
                    Choice(t?.let { V4FitnessViewModel.fmt(it) } ?: "Any time", time == t) { time = t }
                }
            }

            Text("How long", style = V4.type.label, color = c.ink2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(20, 30, 45, 60).forEach { m -> Choice("$m min", minutes == m) { minutes = m } }
            }

            if (canCalendar && time != null) V4Switch(toCalendar, { toCalendar = it }, "Add to your calendar")

            V4PrimaryButton(
                "Plan it",
                onClick = { onSave(title.trim(), day, time, minutes, toCalendar && time != null) },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "It shows on Today on the day. Finish it with the timer and it goes to Health.",
                style = V4.type.caption, color = c.ink3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
