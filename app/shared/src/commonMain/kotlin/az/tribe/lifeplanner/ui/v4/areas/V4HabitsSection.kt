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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.data.habits.HabitRow
import az.tribe.lifeplanner.domain.enum.HabitType
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.FitnessWeek
import az.tribe.lifeplanner.domain.service.HabitSchedule
import az.tribe.lifeplanner.domain.service.Schedule
import az.tribe.lifeplanner.ui.v4.components.CheckCircleButton
import az.tribe.lifeplanner.ui.v4.components.OneLine
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4Divider
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.theme.V4
import az.tribe.lifeplanner.ui.v4.travel.TravelField
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Habits page's own part: today's habits by time of day, anything that slipped (said kindly),
 * the habits not due today and why, and twelve weeks of history. Tapping a habit opens its sheet
 * with the schedule, reminder, past days to fix, skip days and breaks.
 */
@Composable
fun HabitsSection(onAskCoach: (String) -> Unit, viewModel: V4HabitsViewModel = koinViewModel()) {
    val s by viewModel.state.collectAsState()
    val c = V4.colors
    val tint = c.area(PlanArea.HABITS)
    var open by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }

    V4Card(modifier = Modifier.fillMaxWidth(), color = tint.soft, bordered = false, verticalSpacing = 6.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Today", style = V4.type.label, color = tint.ink)
            if (s.dueToday > 0) Text("${s.doneToday} of ${s.dueToday} done", style = V4.type.label, color = c.ink2)
        }
        when {
            !s.loaded -> {}
            s.all.isEmpty() -> {
                Text("No habits yet", style = V4.type.headline, color = c.ink)
                Text("Start with one small thing. You can pick the days, and skip a day without losing the streak.", style = V4.type.caption, color = c.ink2)
                V4PillButton("Pick a first habit", onClick = { adding = true }, container = tint.color)
            }
            s.dueToday == 0 -> {
                Text("Nothing due today", style = V4.type.headline, color = c.ink)
                Text("Your habits are set for other days. See below.", style = V4.type.caption, color = c.ink2)
            }
            else -> s.today.forEach { (slot, rows) ->
                if (s.today.size > 1 || slot != HabitSchedule.Slot.ANYTIME) {
                    Text(slot.label, style = V4.type.caption, color = c.ink3, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
                }
                rows.forEach { r -> TodayHabitRow(r, tint.color, onOpen = { open = r.habit.id }, onToggle = { viewModel.toggle(r) }, onPlus = { viewModel.plusOne(r) }) }
            }
        }
    }

    s.slipped?.let { r ->
        V4Card(modifier = Modifier.fillMaxWidth(), onClick = { open = r.habit.id }) {
            Text("${r.habit.title} slipped yesterday", style = V4.type.bodyStrong, color = c.ink)
            Text(
                (r.stats.score?.let { "One miss does not undo the work. Its score is still ${(it * 100).toInt()}%. " } ?: "Pick it back up today. ") +
                    "Days you skip ahead of time never count against you.",
                style = V4.type.caption, color = c.ink2,
            )
        }
    }

    if (s.notToday.isNotEmpty()) {
        SectionTitle("Not today")
        V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            s.notToday.forEachIndexed { i, (r, why) ->
                if (i > 0) V4Divider()
                Column(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { open = r.habit.id }.heightIn(min = 44.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    OneLine(r.habit.title, V4.type.bodyStrong, c.ink)
                    Text(why, style = V4.type.caption, color = c.ink3)
                }
            }
        }
    }

    if (s.all.isNotEmpty()) {
        SectionTitle("Last 12 weeks")
        V4Card {
            Text(s.keptShare?.let { "${(it * 100).toInt()}% of habit days kept" } ?: "Your history fills in here", style = V4.type.headline, color = c.ink)
            HeatGrid(s.heat, tint.color, "Habit days kept over the last 12 weeks")
            Text("Each column is a week, Monday at the top. The stronger the colour, the more habits you kept.", style = V4.type.caption, color = c.ink3)
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        V4PillButton("New habit", onClick = { adding = true }, container = tint.color)
        V4PillButton("Ask the coach", onClick = { onAskCoach("Help me pick one small habit that fits my week, and when to do it.") }, filled = false)
    }

    open?.let { id ->
        val row = s.all.firstOrNull { it.habit.id == id }
        if (row == null) open = null
        else HabitSheet(row, viewModel, onDismiss = { open = null })
    }
    if (adding) NewHabitSheet(onDismiss = { adding = false }, onCreate = { viewModel.create(it); adding = false })
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = V4.type.headline, color = V4.colors.ink, modifier = Modifier.padding(top = 4.dp).semantics { heading() })

@Composable
private fun TodayHabitRow(r: HabitRow, color: Color, onOpen: () -> Unit, onToggle: () -> Unit, onPlus: () -> Unit) {
    val c = V4.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClickLabel = "Open ${r.habit.title}", onClick = onOpen).padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            OneLine(r.habit.title, V4.type.bodyStrong, if (r.doneToday) c.ink3 else c.ink)
            OneLine(r.meta, V4.type.caption, c.ink2)
        }
        if (r.habit.targetCount > 1 && !r.doneToday) {
            Box(
                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(c.surface)
                    .border(1.5.dp, c.line, RoundedCornerShape(22.dp))
                    .clickable(role = Role.Button, onClickLabel = "Add one to ${r.habit.title}", onClick = onPlus).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("+1", style = V4.type.bodyStrong, color = c.accentInk) }
        }
        CheckCircleButton(
            r.doneToday,
            (if (r.doneToday) "Undo: " else if (r.habit.type == HabitType.QUIT) "Resisted today: " else "Mark done: ") + r.habit.title,
            onToggle, color = color,
        )
    }
}

/** A weeks-by-days grid. Levels: null future, below 0 off, 0..1 how much was kept. */
@Composable
internal fun HeatGrid(days: List<HeatDay>, color: Color, description: String, onTap: ((HeatDay) -> Unit)? = null) {
    val c = V4.colors
    val weeks = days.chunked(7)
    Row(
        Modifier.fillMaxWidth().let { m -> if (onTap == null) m.clearAndSetSemantics { contentDescription = description } else m },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        weeks.forEach { week ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { d ->
                    val level = d.level
                    val fill = when {
                        level == null -> Color.Transparent
                        level < 0f -> c.surfaceMuted
                        level == 0f -> c.trackOff
                        else -> color.copy(alpha = 0.25f + 0.75f * level)
                    }
                    val skipped = level == 0.5f && onTap != null
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(4.dp))
                            .background(if (skipped) c.surface else fill)
                            .let { m -> if (skipped) m.border(1.5.dp, color, RoundedCornerShape(4.dp)) else m }
                            .let { m ->
                                if (onTap != null && level != null) m.clickable(role = Role.Checkbox) { onTap(d) }.semantics {
                                    contentDescription = "${FitnessWeek.dayName(d.date.dayOfWeek)} ${d.date.day} ${d.date.month.name.lowercase().replaceFirstChar { it.uppercase() }}"
                                    stateDescription = when { level >= 1f -> "Done"; skipped -> "Skipped"; level < 0f -> "Not scheduled"; else -> "Not done" }
                                } else m
                            },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitSheet(r: HabitRow, vm: V4HabitsViewModel, onDismiss: () -> Unit) {
    val c = V4.colors
    val tint = c.area(PlanArea.HABITS)
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var title by remember(r.habit.id) { mutableStateOf(r.habit.title) }
    var schedule by remember(r.habit.id) { mutableStateOf(HabitSchedule.normal(r.schedule)) }
    var target by remember(r.habit.id) { mutableIntStateOf(r.habit.targetCount) }
    var unit by remember(r.habit.id) { mutableStateOf(r.habit.unit ?: "") }
    var reminder by remember(r.habit.id) { mutableStateOf(r.habit.reminderTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }) }
    var note by remember { mutableStateOf("") }
    var breakPick by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    val changed = title.trim() != r.habit.title || schedule != HabitSchedule.normal(r.schedule) || target != r.habit.targetCount ||
        unit.trim() != (r.habit.unit ?: "") || reminder?.let(::fmtTime) != r.habit.reminderTime
    val onBreak = r.skipped.any { it > today }

    AreaSheet(r.habit.title, onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(r.stats.score?.let { "${(it * 100).toInt()}%" } ?: "None yet", "last 30 days", Modifier.weight(1f))
            Stat("${r.stats.streak}", if (r.stats.streakInWeeks) "weeks in a row" else "day streak", Modifier.weight(1f))
            Stat("${maxOf(r.stats.best, r.habit.longestStreak.takeIf { !r.stats.streakInWeeks } ?: 0)}", "best", Modifier.weight(1f))
        }

        FormLabel("Last 12 weeks, tap a day to fix it")
        HeatGrid(V4HabitsViewModel.habitHeat(r, today), tint.color, "History", onTap = { d -> if (d.date >= r.habit.createdAt.date) vm.setDay(r, d.date, (d.level ?: 0f) < 1f) })

        FormLabel("Name")
        TravelField(title, { title = it }, "What you want to do", "Habit name")

        FormLabel("When")
        ScheduleChoices(schedule) { schedule = it }

        if (r.habit.type == HabitType.BUILD && r.habit.healthMetricType == null) {
            FormLabel("Each day")
            CountChoices(listOf(1, 2, 3, 5, 8, 10), target, { if (it == 1) "Once" else "$it times" }) { target = it }
            if (target > 1) TravelField(unit, { unit = it }, "glasses, pages, minutes", "Unit")
        }

        FormLabel("Reminder")
        ReminderChoices(reminder) { reminder = it }

        if (changed) V4PrimaryButton("Save changes", onClick = { vm.save(r, title, schedule, target, unit, reminder) }, container = tint.color, modifier = Modifier.fillMaxWidth())

        FormLabel("Note for today")
        TravelField(note, { note = it }, "How it went, or why not", "Note for today")
        if (note.isNotBlank()) V4PillButton("Save note", onClick = { vm.addNote(r, note); note = "" }, filled = false)
        r.notes.take(3).forEach { n ->
            Text("${n.date.day} ${n.date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}: ${n.title}", style = V4.type.caption, color = c.ink2)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            V4PillButton(if (r.stats.skippedToday) "Unskip today" else "Skip today", onClick = { vm.toggleSkip(r) }, filled = false)
            if (onBreak) V4PillButton("End the break", onClick = { vm.endBreak(r) }, filled = false)
            else V4PillButton("Take a break", onClick = { breakPick = !breakPick }, filled = false)
            V4TextButton("Stop tracking", onClick = { stopping = true }, color = Color(0xFFB42318))
        }
        if (breakPick && !onBreak) {
            CountChoices(listOf(3, 7, 14), 0, { if (it == 7) "1 week" else if (it == 14) "2 weeks" else "$it days" }) { vm.takeBreak(r, it); breakPick = false }
        }
        SheetNote("Skipped and break days are neutral: the streak waits for you. Stop tracking keeps the history.")
    }

    if (stopping) {
        AlertDialog(
            onDismissRequest = { stopping = false },
            title = { Text("Stop tracking ${r.habit.title}?") },
            text = { Text("It leaves Today and its reminder stops. The history stays.") },
            confirmButton = { TextButton(onClick = { vm.stop(r); stopping = false; onDismiss() }) { Text("Stop tracking") } },
            dismissButton = { TextButton(onClick = { stopping = false }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    val c = V4.colors
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(c.background).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = V4.type.headline, color = c.ink)
        Text(label, style = V4.type.caption, color = c.ink3)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleChoices(schedule: Schedule, onChange: (Schedule) -> Unit) {
    val c = V4.colors
    val mode = when (schedule) {
        Schedule.Daily -> 0
        is Schedule.Days -> if (schedule.days == HabitSchedule.WEEKDAYS) 1 else 2
        is Schedule.PerWeek -> 3
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Choice("Every day", mode == 0) { onChange(Schedule.Daily) }
        Choice("Weekdays", mode == 1) { onChange(Schedule.Days(HabitSchedule.WEEKDAYS)) }
        Choice("Pick days", mode == 2) { if (mode != 2) onChange(Schedule.Days(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))) }
        Choice("Times a week", mode == 3) { if (mode != 3) onChange(Schedule.PerWeek(3)) }
    }
    when (schedule) {
        is Schedule.Days -> if (mode == 2) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { d ->
                val on = d in schedule.days
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(if (on) c.inverse else c.surface)
                        .border(1.5.dp, if (on) c.inverse else c.line, CircleShape)
                        .clickable(role = Role.Checkbox) {
                            val next = if (on) schedule.days - d else schedule.days + d
                            if (next.isNotEmpty()) onChange(HabitSchedule.normal(Schedule.Days(next)))
                        }
                        .semantics { contentDescription = FitnessWeek.dayName(d); stateDescription = if (on) "On" else "Off" },
                    contentAlignment = Alignment.Center,
                ) { Text(FitnessWeek.dayName(d).take(1), style = V4.type.label, color = if (on) c.onInverse else c.ink) }
            }
        }
        is Schedule.PerWeek -> CountChoices((1..6).toList(), schedule.times, { if (it == 1) "Once" else "$it times" }) { onChange(Schedule.PerWeek(it)) }
        else -> {}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderChoices(time: LocalTime?, onSelect: (LocalTime?) -> Unit) {
    val options = listOf<LocalTime?>(null, LocalTime(7, 0), LocalTime(8, 30), LocalTime(12, 30), LocalTime(18, 0), LocalTime(21, 0), LocalTime(22, 30))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (options + listOfNotNull(time?.takeIf { it !in options })).forEach { t ->
            Choice(t?.let(::fmtTime) ?: "None", time == t) { onSelect(t) }
        }
    }
}

private fun fmtTime(t: LocalTime) = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewHabitSheet(onDismiss: () -> Unit, onCreate: (Starter) -> Unit) {
    val tint = V4.colors.area(PlanArea.HABITS)
    var title by remember { mutableStateOf("") }
    var quit by remember { mutableStateOf(false) }
    var schedule by remember { mutableStateOf<Schedule>(Schedule.Daily) }
    var target by remember { mutableIntStateOf(1) }
    var unit by remember { mutableStateOf("") }
    var reminder by remember { mutableStateOf<LocalTime?>(null) }
    AreaSheet("New habit", onDismiss) {
        FormLabel("Start from one of these")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            V4HabitsViewModel.STARTERS.forEach { st -> Choice(st.title, false) { onCreate(st) } }
        }
        FormLabel("Or your own")
        TravelField(title, { title = it }, "Floss, journal, no sugar after 8", "Habit name")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("To build", !quit) { quit = false }
            Choice("To break", quit) { quit = true }
        }
        FormLabel("When")
        ScheduleChoices(schedule) { schedule = it }
        if (!quit) {
            FormLabel("Each day")
            CountChoices(listOf(1, 2, 3, 5, 8, 10), target, { if (it == 1) "Once" else "$it times" }) { target = it }
            if (target > 1) TravelField(unit, { unit = it }, "glasses, pages, minutes", "Unit")
        }
        FormLabel("Reminder")
        ReminderChoices(reminder) { reminder = it }
        V4PrimaryButton(
            "Add habit",
            onClick = {
                onCreate(
                    Starter(
                        title.trim(), type = if (quit) HabitType.QUIT else HabitType.BUILD, schedule = schedule,
                        target = if (quit) 1 else target, unit = unit.takeIf { target > 1 }, reminder = reminder,
                    )
                )
            },
            enabled = title.isNotBlank(), container = tint.color, modifier = Modifier.fillMaxWidth(),
        )
        SheetNote("Keep it small enough to do on a bad day.")
    }
}
