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
import az.tribe.lifeplanner.domain.service.FitnessStreak
import az.tribe.lifeplanner.domain.service.FitnessWeek
import az.tribe.lifeplanner.domain.service.WeekSlot
import az.tribe.lifeplanner.domain.service.WorkoutNotes
import az.tribe.lifeplanner.domain.service.WorkoutWeek
import az.tribe.lifeplanner.domain.service.WorkoutWeekPlan
import az.tribe.lifeplanner.domain.service.RingState
import az.tribe.lifeplanner.ui.v4.components.CheckCircleButton
import az.tribe.lifeplanner.ui.v4.components.OneLine
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4Divider
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.components.V4ProgressBar
import az.tribe.lifeplanner.ui.v4.components.V4Switch
import az.tribe.lifeplanner.ui.v4.components.V4SwitchRow
import az.tribe.lifeplanner.ui.v4.travel.TravelField
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
import kotlin.math.roundToInt
import kotlinx.datetime.minus
import androidx.compose.foundation.layout.height
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Fitness page's own part, per the canvas: today's workout with a timer that saves to Health,
 * the last seven days against a weekly goal, what came in from Health, how Fitness touches the
 * other areas, and what is coming up.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FitnessSection(
    health: AreaHealth,
    onHealthConnected: () -> Unit,
    viewModel: V4FitnessViewModel = koinViewModel(),
    prefs: az.tribe.lifeplanner.data.integrations.IntegrationPrefs = org.koin.compose.koinInject(),
) {
    val s by viewModel.state.collectAsState()
    val active by viewModel.active.collectAsState()
    val c = V4.colors
    val fit = c.area(PlanArea.FITNESS)
    var planning by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var editingTarget by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<LifeLog?>(null) }
    var savedNote by remember { mutableStateOf<String?>(null) }
    var editingWeek by remember { mutableStateOf(false) }
    /** The workout just finished, while "What did you do?" is open for it. */
    var askFor by remember { mutableStateOf<String?>(null) }

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
                        viewModel.stop { id, toHealth -> savedNote = if (toHealth) "Saved here and to Health." else "Saved."; askFor = id }
                    }, container = fit.color)
                    V4TextButton("Cancel", onClick = viewModel::cancel, color = c.ink2)
                }
            }
            askFor != null -> {
                val id = askFor!!
                var did by remember(id) { mutableStateOf("") }
                Text("What did you do?", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
                TravelField(did, { did = it }, "Squat 3x5 60kg, bench 3x8", "What you did")
                Text("Optional. It shows next time as \"Last time\".", style = V4.type.caption, color = c.ink2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    V4PillButton("Save", onClick = { viewModel.setDid(id, did); askFor = null }, container = fit.color)
                    V4TextButton("Skip", onClick = { askFor = null }, color = c.ink2)
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
                        val note = WorkoutNotes.display(t.notes) ?: if (WorkoutWeekPlan.isGenerated(t) && !s.todayDone) "From your week" else null
                        note?.let { Text(it, style = V4.type.caption, color = c.ink2) }
                    }
                    CheckCircleButton(s.todayDone, (if (s.todayDone) "Undo: " else "Mark done: ") + t.title, { viewModel.toggleToday(t) { id -> askFor = id } }, color = fit.color)
                }
                if (!s.todayDone) {
                    s.lastTime?.let { last ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text("Last time, ${lastDay(last.date)}", style = V4.type.label, color = fit.ink)
                            Text(WorkoutNotes.did(last.notes).orEmpty(), style = V4.type.bodyStrong, color = c.ink)
                        }
                    }
                    val kind = WorkoutKind.fromTitle(t.title)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        V4PillButton("Start ${t.title.lowercase()}", onClick = { viewModel.start(kind, t.title, t.id) }, container = fit.color)
                        V4TextButton("Not today", onClick = { viewModel.moveToNextFreeDay(t) }, color = c.ink2)
                    }
                } else {
                    Text("Done. That counts toward your week.", style = V4.type.caption, color = c.ink2)
                }
            }
            else -> {
                Text("Today", style = V4.type.label, color = fit.ink)
                Text("Nothing planned", style = V4.type.headline, color = c.ink)
                Text(
                    if (s.week?.slots.isNullOrEmpty()) "Start one now, or set your week once and every week plans itself."
                    else if (s.streak?.onBreak != null) "You are on a break. Rest well."
                    else if (s.week?.slotFor(kotlin.time.Clock.System.todayIn(kotlinx.datetime.TimeZone.currentSystemDefault()).dayOfWeek) != null)
                        "Today's slot has passed. Start one anyway, or rest and pick it up next time."
                    else "A rest day in your week. Start one anyway if you feel like it.",
                    style = V4.type.caption, color = c.ink2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    V4PillButton("Start a workout", onClick = { picking = true }, container = fit.color)
                    if (s.week?.slots.isNullOrEmpty()) V4PillButton("Set your week", onClick = { editingWeek = true }, filled = false)
                    else V4PillButton("Plan one", onClick = { planning = true }, filled = false)
                }
            }
        }
        savedNote?.let {
            LaunchedEffect(it) { delay(3_000); savedNote = null }
            Text(it, style = V4.type.label, color = c.success)
        }
    }

    // ── Your week ──
    V4Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Your week", style = V4.type.bodyStrong, color = c.ink, modifier = Modifier.semantics { heading() })
            V4TextButton(if (s.week?.slots.isNullOrEmpty()) "Set it" else "Change", onClick = { editingWeek = true })
        }
        val week = s.week?.takeIf { it.slots.isNotEmpty() }
        if (week == null) {
            Text("Pick your days once, like Mon, Wed, Fri: strength at 07:00. The next 7 days stay planned, so Today always knows what is next.", style = V4.type.caption, color = c.ink2)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                kotlinx.datetime.DayOfWeek.entries.forEach { d ->
                    val on = week.slotFor(d) != null
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(if (on) fit.color else c.trackOff)
                            .semantics { contentDescription = FitnessWeek.dayName(d) + if (on) ", planned" else ", rest" },
                        contentAlignment = Alignment.Center,
                    ) { Text(FitnessWeek.dayLetter(d), style = V4.type.label, color = if (on) c.onAccent else c.ink2) }
                }
            }
            s.weekSummary.forEach { Text(it, style = V4.type.body, color = c.ink) }
            Text("Move or remove a single day and it stays that way.", style = V4.type.caption, color = c.ink3)
        }
    }

    // ── Last 7 days ──
    V4Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Last 7 days", style = V4.type.label, color = c.ink2)
                Text("${s.doneThisWeek} of ${s.target} workouts", style = V4.type.headline, color = c.ink)
            }
            s.streak?.let { st ->
                val (big, small) = FitnessStreak.words(st)
                Column(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(fit.soft).padding(horizontal = 12.dp, vertical = 6.dp)
                        .semantics(mergeDescendants = true) {},
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(big, style = V4.type.bodyStrong, color = fit.ink)
                    Text(small, style = V4.type.micro, color = fit.ink)
                }
            }
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
        } else V4TextButton("Weekly goal: ${s.target}", onClick = { editingTarget = true }, color = c.ink2)
        V4Divider()
        val onBreak = s.streak?.onBreak
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Sick or taking a break", style = V4.type.body, color = c.ink)
                Text(
                    onBreak?.let { "Until ${lastDay(it.to)}. Rest well, nothing breaks." } ?: "Pauses your streak instead of breaking it.",
                    style = V4.type.caption, color = c.ink3,
                )
            }
            V4Switch(onBreak != null, { on -> if (on) viewModel.startBreak(7) else viewModel.endBreak() }, "Sick or taking a break")
        }
        if (onBreak != null) {
            val length = (onBreak.to.toEpochDays() - onBreak.from.toEpochDays() + 1).toInt()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(3, 7, 14).forEach { n ->
                    val days = n + (Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays() - onBreak.from.toEpochDays()).toInt()
                    Choice("$n days", length == days) { viewModel.startBreak(n) }
                }
            }
        }
    }

    // ── From Health ──
    // Everything Health gives us, right here: no separate details page to open.
    val integrations by prefs.state.collectAsState()
    val connectHealth = az.tribe.lifeplanner.ui.health.rememberHealthPermissionLauncher { granted ->
        prefs.setHealth(granted)
        if (granted) onHealthConnected()
    }
    V4Card {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("From Health", style = V4.type.bodyStrong, color = c.ink)
                Text(
                    when {
                        !integrations.health -> "Steps, heart rate, weight, sleep and watch workouts, without typing them in."
                        s.canWriteHealth -> "Workouts from your watch show up here. Workouts you finish here are saved to Health."
                        !health.hasAny -> "Connected. Numbers show up here as soon as Health has some."
                        else -> "Your latest numbers, read from Health."
                    },
                    style = V4.type.caption, color = c.ink2,
                )
            }
            if (!integrations.health) V4PillButton("Connect", onClick = connectHealth)
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
        if (health.stepsWeek.size >= 2) StepsWeek(health.stepsWeek, health.stepsTarget, fit.color)
        val tiles = buildList {
            health.restingHr?.let { add(Triple("Heart rate", "${it.roundToInt()} bpm", "7 day average")) }
            health.weightKg?.let { w ->
                val change = health.weightChangeKg?.let { d ->
                    val r = (d * 10).roundToInt() / 10.0
                    when {
                        r > 0 -> "Up $r kg in a month"
                        r < 0 -> "Down ${-r} kg in a month"
                        else -> "Steady this month"
                    }
                } ?: "Latest"
                add(Triple("Weight", "${(w * 10).roundToInt() / 10.0} kg", change))
            }
            health.sleepNights.firstOrNull()?.let { (_, h) ->
                add(Triple("Sleep", "${h.toInt()}h ${((h - h.toInt()) * 60).roundToInt()}m", "Last night"))
            }
        }
        if (tiles.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tiles.forEach { (label, value, note) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(c.surfaceMuted).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(label, style = V4.type.micro, color = c.ink3)
                        Text(value, style = V4.type.bodyStrong, color = c.ink)
                        Text(note, style = V4.type.micro, color = c.ink3, maxLines = 1)
                    }
                }
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

    if (editingWeek) {
        WeekSheet(
            initial = s.week,
            canCalendar = s.canCalendar,
            onDismiss = { editingWeek = false },
            onSave = { slots, cal -> viewModel.saveWeek(slots, cal); editingWeek = false },
        )
    }

    selected?.let { l ->
        val generated = WorkoutWeekPlan.isGenerated(l)
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(l.title) },
            text = {
                Text(
                    (l.durationMin?.let { "$it min. " } ?: "") + (WorkoutNotes.display(l.notes) ?: if (generated) "From your week." else "Planned here.") +
                        if (generated) " Removing it takes out just this day." else "",
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { viewModel.moveToToday(l); selected = null }) { Text("Do it today") }
                    TextButton(onClick = { viewModel.moveToNextFreeDay(l); selected = null }) { Text("Next free day") }
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.remove(l); selected = null }) { Text(if (generated) "Remove this day" else "Remove") } },
        )
    }
}

/** "today", "yesterday", "tomorrow", a weekday within a week, else "Sun 12 Oct". */
private fun lastDay(d: LocalDate): String {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val diff = d.toEpochDays() - today.toEpochDays()
    return when {
        diff == 0L -> "today"
        diff == -1L -> "yesterday"
        diff == 1L -> "tomorrow"
        diff in -6L..6L -> FitnessWeek.dayName(d.dayOfWeek)
        else -> "${FitnessWeek.shortDay(d.dayOfWeek)} ${d.day} ${d.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}"
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

            if (canCalendar && time != null) V4SwitchRow("Add to your calendar", toCalendar, { toCalendar = it })

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

/**
 * The week that repeats: pick the days, then each day's kind, time and length. A day switched on
 * copies the nearest one before it, so "Mon, Wed, Fri: the same" is three taps.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekSheet(
    initial: WorkoutWeek?,
    canCalendar: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<WeekSlot>, Boolean) -> Unit,
) {
    val c = V4.colors
    val fit = c.area(PlanArea.FITNESS)
    val days = kotlinx.datetime.DayOfWeek.entries
    var slots by remember { mutableStateOf(initial?.slots.orEmpty().associateBy { it.day }) }
    var open by remember { mutableStateOf<kotlinx.datetime.DayOfWeek?>(null) }
    var toCalendar by remember { mutableStateOf(initial?.toCalendar ?: canCalendar) }

    AreaSheet("Your week", onDismiss) {
        Text("Pick your days once. The next 7 days stay planned, so Today always knows what is next.", style = V4.type.body, color = c.ink2)
        FormLabel("Which days")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEach { d ->
                val on = d in slots
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(if (on) fit.color else c.surface)
                        .border(1.5.dp, if (on) fit.color else c.line, CircleShape)
                        .clickable(role = Role.Checkbox) {
                            slots = if (on) slots - d else {
                                val before = days.take(d.ordinal).reversed().firstNotNullOfOrNull { slots[it] }
                                    ?: slots.values.firstOrNull() ?: WeekSlot(d, "Strength", LocalTime(18, 0), 45)
                                open = d
                                slots + (d to before.copy(day = d))
                            }
                        }
                        .semantics {
                            contentDescription = FitnessWeek.dayName(d)
                            stateDescription = if (on) "Planned" else "Rest"
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(FitnessWeek.dayLetter(d), style = V4.type.label, color = if (on) c.onAccent else c.ink) }
            }
        }
        if (slots.isNotEmpty()) {
            V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
                slots.values.sortedBy { it.day.ordinal }.forEachIndexed { i, slot ->
                    if (i > 0) V4Divider()
                    val isOpen = open == slot.day
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button) { open = if (isOpen) null else slot.day },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(FitnessWeek.shortDay(slot.day), style = V4.type.label, color = c.ink, modifier = Modifier.width(40.dp))
                            Text(
                                "${slot.title}, ${slot.time?.let { V4FitnessViewModel.fmt(it) } ?: "any time"}, ${slot.minutes} min",
                                style = V4.type.body, color = c.ink, modifier = Modifier.weight(1f),
                            )
                            Text(if (isOpen) "Done" else "Change", style = V4.type.bodyStrong, color = c.accentInk)
                        }
                        if (isOpen) {
                            fun set(next: WeekSlot) { slots = slots + (slot.day to next) }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Strength", "Run", "Walk", "Yoga", "Ride", "Swim", "HIIT").forEach { k -> Choice(k, slot.title == k) { set(slot.copy(title = k)) } }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf<LocalTime?>(null, LocalTime(7, 0), LocalTime(12, 30), LocalTime(18, 0), LocalTime(20, 0)).forEach { t ->
                                    Choice(t?.let { V4FitnessViewModel.fmt(it) } ?: "Any time", slot.time == t) { set(slot.copy(time = t)) }
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(20, 30, 45, 60).forEach { m -> Choice("$m min", slot.minutes == m) { set(slot.copy(minutes = m)) } }
                            }
                        }
                    }
                }
            }
            Text("A new day copies the one before it. Tap a day to change it.", style = V4.type.caption, color = c.ink3)
        }
        if (canCalendar && slots.values.any { it.time != null }) V4SwitchRow("Add them to your calendar", toCalendar, { toCalendar = it })
        V4PrimaryButton(
            when {
                slots.isNotEmpty() -> "Keep this week"
                initial?.slots.isNullOrEmpty() -> "Pick a day first"
                else -> "Stop planning my week"
            },
            onClick = { onSave(slots.values.sortedBy { it.day.ordinal }, toCalendar && canCalendar) },
            enabled = slots.isNotEmpty() || !initial?.slots.isNullOrEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
        SheetNote(
            if (slots.isNotEmpty()) "Your weekly goal becomes ${slots.size}. Move or remove a single day any time and the rest stays."
            else "Planned days you have not changed come off your plan.",
        )
    }
}


/** Seven small bars, one per day, with today's the strongest. A day at the target fills the bar. */
@Composable
private fun StepsWeek(days: List<Pair<kotlinx.datetime.LocalDate, Double>>, target: Double, color: androidx.compose.ui.graphics.Color) {
    val c = V4.colors
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val byDay = days.toMap()
    val avg = days.map { it.second }.average()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("This week, ${V4TodayViewModel.formatThousands(avg.toLong())} a day on average", style = V4.type.caption, color = c.ink2)
        Row(
            Modifier.fillMaxWidth().height(56.dp).semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            (6 downTo 0).forEach { back ->
                val d = today.minus(kotlinx.datetime.DatePeriod(days = back))
                val v = byDay[d] ?: 0.0
                val f = (v / target).coerceIn(0.04, 1.0).toFloat()
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier.fillMaxWidth().height((40 * f).dp).clip(RoundedCornerShape(4.dp))
                            .background(if (back == 0) color else color.copy(alpha = if (v >= target) 0.7f else 0.35f)),
                    )
                    Text(FitnessWeek.dayName(d.dayOfWeek).take(1), style = V4.type.micro, color = c.ink3)
                }
            }
        }
    }
}
