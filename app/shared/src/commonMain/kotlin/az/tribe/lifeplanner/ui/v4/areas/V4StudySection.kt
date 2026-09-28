package az.tribe.lifeplanner.ui.v4.areas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.data.study.FoundDate
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.FitnessWeek
import az.tribe.lifeplanner.domain.service.StudyKind
import az.tribe.lifeplanner.domain.service.StudyPlanner
import az.tribe.lifeplanner.ui.v4.components.CheckCircleButton
import az.tribe.lifeplanner.ui.v4.components.OneLine
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4Divider
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.components.V4ProgressBar
import az.tribe.lifeplanner.ui.v4.components.V4SwitchRow
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.theme.V4
import az.tribe.lifeplanner.ui.v4.travel.TravelField
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
 * The Study page's own part: what to study now, the week against its goal, exams and deadlines
 * with the blocks that prepare for them, and the timer. Missed blocks move forward instead of
 * piling up, and a finished topic can book its own short reviews.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudySection(onOpenFocus: () -> Unit, viewModel: V4StudyViewModel = koinViewModel()) {
    val s by viewModel.state.collectAsState()
    val active by viewModel.active.collectAsState()
    val c = V4.colors
    val tint = c.area(PlanArea.STUDY)
    var starting by remember { mutableStateOf(false) }
    var loggingTime by remember { mutableStateOf(false) }
    var planningBlock by remember { mutableStateOf(false) }
    var addingDue by remember { mutableStateOf(false) }
    var pasting by remember { mutableStateOf(false) }
    var spreading by remember { mutableStateOf<LifeLog?>(null) }
    var dueAction by remember { mutableStateOf<DueRow?>(null) }
    var blockAction by remember { mutableStateOf<BlockRow?>(null) }
    var editingGoal by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Pair<Int, String>?>(null) }

    // ── Now ──
    V4Card(modifier = Modifier.fillMaxWidth(), color = tint.soft, bordered = false) {
        val a = active
        val next = s.todayBlocks.firstOrNull { !it.done }
        when {
            a != null -> {
                var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
                LaunchedEffect(a.startEpochMs) { while (true) { now = Clock.System.now().toEpochMilliseconds(); delay(1_000) } }
                val ms = now - a.startEpochMs
                Text("${a.subject} now", style = V4.type.label, color = tint.ink)
                Text(clock(ms), style = V4.type.number, color = c.ink, modifier = Modifier.semantics { contentDescription = "Studied for ${ms / 60_000} minutes" })
                V4ProgressBar((ms / 60_000f) / a.targetMin, tint.color)
                Text(
                    if (ms / 60_000 >= a.targetMin) "That is your ${a.targetMin} minutes. Keep going or stop and save." else "Aiming for ${a.targetMin} minutes.",
                    style = V4.type.caption, color = c.ink2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    V4PillButton("Stop and save", onClick = { viewModel.stop { m, subj -> saved = m to subj } }, container = tint.color)
                    V4TextButton("Cancel", onClick = viewModel::cancel, color = c.ink2)
                }
            }
            next != null -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Today" + if (next.time != "Any") ", ${next.time}" else "", style = V4.type.label, color = tint.ink)
                        Text(next.log.title + (next.log.durationMin?.let { ", $it min" } ?: ""), style = V4.type.headline, color = c.ink)
                        next.forWhat?.let { Text(it, style = V4.type.caption, color = c.ink2) }
                    }
                    CheckCircleButton(false, "Done: ${next.log.title}", { viewModel.toggleBlock(next) }, color = tint.color)
                }
                V4PillButton("Start ${next.log.title.removePrefix("Review: ")}", onClick = { viewModel.start(next.log.title, next.log) }, container = tint.color)
                val rest = s.todayBlocks.filter { it.log.id != next.log.id }
                if (rest.isNotEmpty()) Text("Also today: " + rest.joinToString(", ") { it.log.title + if (it.done) " (done)" else "" }, style = V4.type.caption, color = c.ink2)
            }
            else -> {
                Text("Today", style = V4.type.label, color = tint.ink)
                Text(if (s.todayBlocks.isNotEmpty()) "All done for today" else "Nothing planned", style = V4.type.headline, color = c.ink)
                Text("Start a session now, or plan blocks so they show on Today.", style = V4.type.caption, color = c.ink2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    V4PillButton("Start studying", onClick = { starting = true }, container = tint.color)
                    V4PillButton("Plan a block", onClick = { planningBlock = true }, filled = false)
                }
            }
        }
        saved?.let { (m, subj) ->
            LaunchedEffect(saved) { delay(60_000); saved = null }
            if (m == 0) Text("Under a minute, so it was not saved.", style = V4.type.label, color = c.ink2)
            else {
                Text("Saved ${StudyPlanner.formatMinutes(m)} of $subj.", style = V4.type.label, color = c.success)
                if (!subj.startsWith("Review:")) V4TextButton("Review it in 1, 3 and 7 days", onClick = { viewModel.scheduleReviews(subj); saved = null })
            }
        }
    }

    if (s.missed > 0) {
        V4Card {
            Text("${s.missed} study ${if (s.missed == 1) "block" else "blocks"} slipped", style = V4.type.bodyStrong, color = c.ink)
            Text("Move them to the next free days, before their exam if they have one.", style = V4.type.caption, color = c.ink2)
            V4PillButton("Move them", onClick = viewModel::rollover)
        }
    }

    // ── This week ──
    s.week?.let { w ->
        V4Card {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Last 7 days", style = V4.type.label, color = c.ink2)
                    Text(
                        StudyPlanner.formatMinutes(w.minutes).let { if (w.minutes == 0) "No study yet" else it } + (s.targetMinutes?.let { " of ${it / 60}h" } ?: ""),
                        style = V4.type.headline, color = c.ink,
                    )
                }
                V4TextButton("Goal", onClick = { editingGoal = !editingGoal })
            }
            s.targetMinutes?.let { V4ProgressBar(w.minutes.toFloat() / it, tint.color, height = 10.dp) }
            Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                val max = (w.perDay.maxOfOrNull { it.second } ?: 0).coerceAtLeast(60)
                w.perDay.forEach { (d, m) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height((6 + 38 * m / max).dp).clip(RoundedCornerShape(6.dp)).background(if (m > 0) tint.color else c.trackOff)
                                .semantics { contentDescription = "${FitnessWeek.dayName(d.dayOfWeek)}, $m minutes" },
                        )
                        Text(FitnessWeek.dayLetter(d.dayOfWeek), style = V4.type.micro, color = c.ink3)
                    }
                }
            }
            if (w.streakDays >= 2) Text("${w.streakDays} days in a row.", style = V4.type.caption, color = c.ink2)
            if (editingGoal) {
                FormLabel("Hours a week")
                CountChoices(listOf(2, 4, 6, 8, 10, 15, 20), (s.targetMinutes ?: 0) / 60, { "${it}h" }) { viewModel.setTarget(it); editingGoal = false }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                V4PillButton("Log time", onClick = { loggingTime = true }, filled = false)
                if (active == null && (s.todayBlocks.any { !it.done })) V4PillButton("Start something else", onClick = { starting = true }, filled = false)
                V4PillButton("Deep focus mode", onClick = onOpenFocus, filled = false)
            }
        }
        if (w.bySubject.isNotEmpty()) {
            V4Card(verticalSpacing = 10.dp) {
                Text("By subject", style = V4.type.label, color = c.ink2)
                val top = w.bySubject.first().second.coerceAtLeast(1)
                w.bySubject.take(6).forEach { (subject, m) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(subject, style = V4.type.body, color = c.ink, maxLines = 1, modifier = Modifier.width(110.dp))
                        Box(Modifier.weight(1f)) { V4ProgressBar(m.toFloat() / top, tint.color) }
                        Text(StudyPlanner.formatMinutes(m), style = V4.type.bodyStrong, color = c.ink)
                    }
                }
            }
        }
    }

    // ── Exams and deadlines ──
    Text("Exams and deadlines", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
    if (s.due.isEmpty()) {
        Text("Add the dates that matter and spread study before them. Or paste a syllabus and the dates are found for you.", style = V4.type.caption, color = c.ink2)
    } else {
        V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            s.due.forEachIndexed { i, d ->
                if (i > 0) V4Divider()
                Column(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { dueAction = d }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            OneLine(StudyPlanner.dueLine(d.log), V4.type.bodyStrong, if (d.log.status == LogStatus.DONE) c.ink3 else c.ink)
                            OneLine("${d.log.date.day} ${monthShort(d.log.date)}, ${d.countdown}" + if (d.onCalendar) ", in your calendar" else "", V4.type.caption, c.ink3)
                        }
                        if (d.blocksTotal > 0) Text("${d.blocksDone} of ${d.blocksTotal}", style = V4.type.label, color = tint.ink)
                    }
                    if (d.blocksTotal > 0) V4ProgressBar(d.blocksDone.toFloat() / d.blocksTotal, tint.color, height = 6.dp)
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        V4PillButton("Add a date", onClick = { addingDue = true }, filled = false)
        V4PillButton("Paste a syllabus", onClick = { pasting = true }, filled = false)
    }

    // ── Coming up ──
    Text("Coming up", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
    if (s.comingUp.isEmpty()) {
        Text("No blocks this week yet. Planned blocks land on Today on the day.", style = V4.type.caption, color = c.ink2)
    } else {
        V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            s.comingUp.forEachIndexed { i, b ->
                if (i > 0) V4Divider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { blockAction = b }.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(b.day, style = V4.type.label, color = c.ink)
                        Text(b.time, style = V4.type.micro, color = c.ink3)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        OneLine(b.log.title + (b.log.durationMin?.let { ", $it min" } ?: ""), V4.type.bodyStrong, c.ink)
                        b.forWhat?.let { OneLine(it, V4.type.caption, c.ink3) }
                    }
                }
            }
        }
    }
    V4PillButton("Plan a block", onClick = { planningBlock = true }, filled = false)

    if (s.recent.isNotEmpty()) {
        Text("Studied lately", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
        V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            s.recent.forEachIndexed { i, l ->
                if (i > 0) V4Divider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        OneLine(l.title, V4.type.bodyStrong, c.ink)
                        OneLine(FitnessWeek.dayName(l.date.dayOfWeek) + (if (l.source == LifeLog.SOURCE_TIMER) ", timer" else ""), V4.type.caption, c.ink3)
                    }
                    Text(StudyPlanner.formatMinutes(l.durationMin ?: 0), style = V4.type.bodyStrong, color = c.ink)
                }
            }
        }
    }

    // ── Sheets and dialogs ──
    if (starting) StartDialog(s.subjects, onDismiss = { starting = false }) { subject, minutes -> viewModel.start(subject, null, minutes); starting = false }
    if (loggingTime) LogTimeSheet(s.subjects, onDismiss = { loggingTime = false }) { subject, m, d -> viewModel.logTime(subject, m, d); loggingTime = false }
    if (planningBlock) PlanBlockSheet(s.subjects, s.canCalendar, onDismiss = { planningBlock = false }) { subject, d, t, m, cal ->
        viewModel.planBlock(subject, d, t, m, cal); planningBlock = false
    }
    if (addingDue) AddDueSheet(s.canCalendar, onDismiss = { addingDue = false }) { title, kind, date, blocks, minutes, time, cal ->
        viewModel.addDue(title, kind, date, blocks, minutes, time, cal); addingDue = false
    }
    spreading?.let { due ->
        SpreadSheet(due, s.canCalendar, onDismiss = { spreading = null }) { blocks, minutes, time, cal -> viewModel.planFor(due, blocks, minutes, time, cal); spreading = null }
    }
    if (pasting) SyllabusSheet(viewModel, s.canCalendar, onDismiss = { pasting = false; viewModel.clearFound() })

    dueAction?.let { d ->
        AlertDialog(
            onDismissRequest = { dueAction = null },
            title = { Text(StudyPlanner.dueLine(d.log)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${FitnessWeek.dayName(d.log.date.dayOfWeek)} ${d.log.date.day} ${monthShort(d.log.date)}, ${d.countdown}.")
                    Text(if (d.blocksTotal == 0) "No study planned for it yet." else "${d.blocksDone} of ${d.blocksTotal} study blocks done.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (s.canCalendar) V4PillButton(if (d.onCalendar) "Off calendar" else "To calendar", onClick = { viewModel.toggleDueCalendar(d); dueAction = null }, filled = false)
                        V4PillButton(if (d.log.status == LogStatus.DONE) "Not done" else "Done", onClick = { viewModel.markDueDone(d); dueAction = null }, filled = false)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { spreading = d.log; dueAction = null }) { Text(if (d.blocksTotal == 0) "Plan study" else "Add blocks") } },
            dismissButton = { TextButton(onClick = { viewModel.remove(d.log); dueAction = null }) { Text("Remove") } },
        )
    }

    blockAction?.let { b ->
        AlertDialog(
            onDismissRequest = { blockAction = null },
            title = { Text(b.log.title) },
            text = { Text(listOfNotNull("${b.day}, ${b.time}", b.log.durationMin?.let { "$it min" }, b.forWhat).joinToString(". ") + ".") },
            confirmButton = { TextButton(onClick = { viewModel.moveToToday(b.log); blockAction = null }) { Text("Do it today") } },
            dismissButton = { TextButton(onClick = { viewModel.remove(b.log); blockAction = null }) { Text("Remove") } },
        )
    }
}

private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    fun two(n: Long) = n.toString().padStart(2, '0')
    val h = total / 3600
    return if (h > 0) "$h:${two((total % 3600) / 60)}:${two(total % 60)}" else "${two(total / 60)}:${two(total % 60)}"
}

private fun monthShort(d: LocalDate) = d.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectField(subject: String, onChange: (String) -> Unit, subjects: List<String>) {
    TravelField(subject, onChange, "Biology, chapter 4, French", "Subject")
    if (subjects.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            subjects.forEach { sub -> Choice(sub, subject == sub) { onChange(sub) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeChoices(time: LocalTime?, onSelect: (LocalTime?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf<LocalTime?>(null, LocalTime(8, 0), LocalTime(12, 0), LocalTime(16, 0), LocalTime(18, 0), LocalTime(20, 0)).forEach { t ->
            Choice(t?.let { V4FitnessViewModel.fmt(it) } ?: "Any time", time == t) { onSelect(t) }
        }
    }
}

@Composable
private fun StartDialog(subjects: List<String>, onDismiss: () -> Unit, onStart: (String, Int) -> Unit) {
    var subject by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(25) }
    AreaSheet("Start studying", onDismiss) {
        FormLabel("What")
        SubjectField(subject, { subject = it }, subjects)
        FormLabel("Aim for")
        CountChoices(listOf(25, 45, 60, 90), minutes, { "$it min" }) { minutes = it }
        V4PrimaryButton("Start", onClick = { onStart(subject, minutes) }, enabled = subject.isNotBlank(), modifier = Modifier.fillMaxWidth())
        SheetNote("The timer keeps running if you leave the app. Stop it to save the time.")
    }
}

@Composable
private fun LogTimeSheet(subjects: List<String>, onDismiss: () -> Unit, onSave: (String, Int, LocalDate) -> Unit) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var subject by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(45) }
    var day by remember { mutableStateOf(today) }
    AreaSheet("Log study time", onDismiss) {
        FormLabel("What")
        SubjectField(subject, { subject = it }, subjects)
        FormLabel("How long")
        CountChoices(listOf(15, 30, 45, 60, 90, 120), minutes, { StudyPlanner.formatMinutes(it) }) { minutes = it }
        FormLabel("When")
        DayChoices(day, { day = it }, days = 2, includeYesterday = true)
        V4PrimaryButton("Save", onClick = { onSave(subject, minutes, day) }, enabled = subject.isNotBlank(), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PlanBlockSheet(subjects: List<String>, canCalendar: Boolean, onDismiss: () -> Unit, onSave: (String, LocalDate, LocalTime?, Int, Boolean) -> Unit) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var subject by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(today) }
    var time by remember { mutableStateOf<LocalTime?>(LocalTime(18, 0)) }
    var minutes by remember { mutableStateOf(45) }
    var toCalendar by remember { mutableStateOf(canCalendar) }
    AreaSheet("Plan a study block", onDismiss) {
        FormLabel("What")
        SubjectField(subject, { subject = it }, subjects)
        FormLabel("When")
        DayChoices(day, { day = it }, allowPicker = true)
        TimeChoices(time) { time = it }
        FormLabel("How long")
        CountChoices(listOf(25, 45, 60, 90), minutes, { "$it min" }) { minutes = it }
        if (canCalendar) V4SwitchRow("Add to your calendar", toCalendar, { toCalendar = it })
        V4PrimaryButton("Plan it", onClick = { onSave(subject, day, time, minutes, toCalendar) }, enabled = subject.isNotBlank(), modifier = Modifier.fillMaxWidth())
        SheetNote("It shows on Today on the day, with a Start button for the timer.")
    }
}

@Composable
private fun AddDueSheet(canCalendar: Boolean, onDismiss: () -> Unit, onSave: (String, StudyKind, LocalDate, Int, Int, LocalTime?, Boolean) -> Unit) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var kind by remember { mutableStateOf(StudyKind.EXAM) }
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today.plus(DatePeriod(days = 14))) }
    var blocks by remember { mutableStateOf(5) }
    var minutes by remember { mutableStateOf(45) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var toCalendar by remember { mutableStateOf(canCalendar) }
    AreaSheet("Add a date", onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Exam or test", kind == StudyKind.EXAM) { kind = StudyKind.EXAM }
            Choice("Deadline", kind == StudyKind.DEADLINE) { kind = StudyKind.DEADLINE }
        }
        FormLabel(if (kind == StudyKind.EXAM) "Which exam" else "What is due")
        TravelField(title, { title = it }, if (kind == StudyKind.EXAM) "Biology midterm" else "History essay", "Title")
        FormLabel("On")
        DayChoices(date, { date = it }, allowPicker = true)
        BlockPlanFields(date, today, blocks, { blocks = it }, minutes, { minutes = it }, time, { time = it })
        if (canCalendar) V4SwitchRow("Add to your calendar", toCalendar, { toCalendar = it })
        V4PrimaryButton("Save", onClick = { onSave(title, kind, date, blocks, minutes, time, toCalendar) }, enabled = title.isNotBlank() && date >= today, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SpreadSheet(due: LifeLog, canCalendar: Boolean, onDismiss: () -> Unit, onSave: (Int, Int, LocalTime?, Boolean) -> Unit) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var blocks by remember { mutableStateOf(5) }
    var minutes by remember { mutableStateOf(45) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var toCalendar by remember { mutableStateOf(canCalendar) }
    AreaSheet("Plan study for ${due.title}", onDismiss) {
        BlockPlanFields(due.date, today, blocks, { blocks = it }, minutes, { minutes = it }, time, { time = it }, allowNone = false)
        if (canCalendar && time != null) V4SwitchRow("Add them to your calendar", toCalendar, { toCalendar = it })
        V4PrimaryButton("Plan them", onClick = { onSave(blocks, minutes, time, toCalendar) }, enabled = due.date > today, modifier = Modifier.fillMaxWidth())
    }
}

/** How many blocks, how long, what time, and a line saying where they will land. */
@Composable
private fun BlockPlanFields(
    due: LocalDate, today: LocalDate,
    blocks: Int, onBlocks: (Int) -> Unit,
    minutes: Int, onMinutes: (Int) -> Unit,
    time: LocalTime?, onTime: (LocalTime?) -> Unit,
    allowNone: Boolean = true,
) {
    val daysBefore = (due.toEpochDays() - today.toEpochDays()).toInt()
    FormLabel("Study blocks before it")
    CountChoices((if (allowNone) listOf(0) else emptyList()) + listOf(3, 5, 8, 10), blocks, { if (it == 0) "None" else "$it" }, onBlocks)
    if (blocks > 0) {
        CountChoices(listOf(30, 45, 60, 90), minutes, { "$it min" }, onMinutes)
        TimeChoices(time, onTime)
        Text(
            when {
                daysBefore <= 0 -> "Pick a date after today to plan study before it."
                blocks > daysBefore -> "Only $daysBefore ${if (daysBefore == 1) "day" else "days"} left, so some days get two blocks."
                else -> "Spread over the $daysBefore days before it, closer together near the end, ${StudyPlanner.formatMinutes(blocks * minutes)} in all."
            },
            style = V4.type.caption, color = V4.colors.ink3,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyllabusSheet(viewModel: V4StudyViewModel, canCalendar: Boolean, onDismiss: () -> Unit) {
    val c = V4.colors
    val reading by viewModel.reading.collectAsState()
    val found by viewModel.found.collectAsState()
    val failed by viewModel.readFailed.collectAsState()
    var text by remember { mutableStateOf("") }
    var keep by remember(found) { mutableStateOf(found.orEmpty().toSet()) }
    var blocksEach by remember { mutableStateOf(3) }
    var toCalendar by remember { mutableStateOf(canCalendar) }

    AreaSheet("Find dates in a syllabus", onDismiss) {
        val list = found
        if (list == null) {
            Text("Paste the syllabus, a timetable or the email with the dates. Nothing is saved until you choose.", style = V4.type.caption, color = c.ink2)
            MultiLineField(text, { text = it }, "Week 6: midterm, 14 November\nEssay due 2 December", "Syllabus text", minLines = 6)
            if (failed) Text("Could not read that just now. Check your connection and try again.", style = V4.type.caption, color = c.ink2)
            V4PrimaryButton(if (reading) "Reading" else "Find the dates", onClick = { if (!reading) viewModel.readSyllabus(text) }, enabled = text.isNotBlank() && !reading, modifier = Modifier.fillMaxWidth())
        } else if (list.isEmpty()) {
            Text("No dates found in that. Add them one by one instead.", style = V4.type.body, color = c.ink)
            V4PillButton("Try other text", onClick = viewModel::clearFound, filled = false)
        } else {
            Text("Found ${list.size}. Untick anything that is wrong.", style = V4.type.caption, color = c.ink2)
            list.forEach { f -> FoundRow(f, f in keep) { keep = if (f in keep) keep - f else keep + f } }
            FormLabel("Study blocks before each")
            CountChoices(listOf(0, 3, 5), blocksEach, { if (it == 0) "None" else "$it" }) { blocksEach = it }
            if (canCalendar) V4SwitchRow("Add the dates to your calendar", toCalendar, { toCalendar = it })
            V4PrimaryButton("Keep ${keep.size}", onClick = { viewModel.keepFound(list.filter { it in keep }, blocksEach, toCalendar); onDismiss() }, enabled = keep.isNotEmpty(), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FoundRow(f: FoundDate, on: Boolean, onToggle: () -> Unit) {
    val c = V4.colors
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CheckCircleButton(on, (if (on) "Drop " else "Keep ") + f.title, onToggle, color = c.area(PlanArea.STUDY).color)
        Column(Modifier.weight(1f)) {
            Text(f.title, style = V4.type.bodyStrong, color = if (on) c.ink else c.ink3)
            Text("${if (f.kind == StudyKind.EXAM) "Exam" else "Due"}, ${f.date.day} ${monthShort(f.date)}, ${StudyPlanner.countdown(f.date, today)}", style = V4.type.caption, color = c.ink3)
        }
    }
}
