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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.di.createFileSharer
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.CareerPlanner
import az.tribe.lifeplanner.domain.service.Stage
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
import az.tribe.lifeplanner.ui.v4.today.V4TodayViewModel
import az.tribe.lifeplanner.ui.v4.travel.TravelField
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Career page's own part, in two modes. Growing where you are: the career plan, wins you can
 * copy into a review, skills, and people. Looking for a job: next actions first, then applications
 * by stage; wins, skills and people stay below. Every open application always has a next action.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CareerSection(onNewPlan: () -> Unit, onOpenGoal: (String) -> Unit, onRoute: (String) -> Unit, viewModel: V4CareerViewModel = koinViewModel()) {
    val s by viewModel.state.collectAsState()
    val c = V4.colors
    val tint = c.area(PlanArea.CAREER)
    val sharer = remember { createFileSharer() }
    var copied by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf<Stage?>(null) }
    var addingWin by remember { mutableStateOf(false) }
    var addingApp by remember { mutableStateOf(false) }
    var openApp by remember { mutableStateOf<String?>(null) }
    var addingPerson by remember { mutableStateOf(false) }
    var openPerson by remember { mutableStateOf<String?>(null) }
    var addingSkill by remember { mutableStateOf(false) }
    var openSkill by remember { mutableStateOf<String?>(null) }

    // ── Mode ──
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.trackOff).padding(3.dp)) {
        listOf(false to "Growing where I am", true to "Looking for a job").forEach { (searching, label) ->
            val on = s.searching == searching
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(15.dp)).background(if (on) c.surface else Color.Transparent)
                    .clickable(role = Role.Tab) { viewModel.setSearching(searching) }.semantics { selected = on },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = V4.type.label, color = c.ink) }
        }
    }

    if (s.searching) {
        // ── Next actions ──
        V4Card(modifier = Modifier.fillMaxWidth(), color = tint.soft, bordered = false, verticalSpacing = 6.dp) {
            Text("Next actions", style = V4.type.label, color = tint.ink)
            if (s.actions.isEmpty()) {
                Text(if (s.applications.none { CareerPlanner.isActive(it) }) "Add the jobs you are going for" else "Nothing due today", style = V4.type.headline, color = c.ink)
                Text("Each application gets a next step: apply, follow up a week later, get ready for the interview.", style = V4.type.caption, color = c.ink2)
            }
            s.actions.forEach { a ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        OneLine(a.title, V4.type.bodyStrong, c.ink)
                        Text(a.meta + if (a.due < today()) ", was due ${CareerPlanner.ago(a.due, today())}" else "", style = V4.type.caption, color = c.ink2, maxLines = 2)
                    }
                    CheckCircleButton(a.log.status == LogStatus.DONE, "Done: ${a.title}", { viewModel.complete(a) }, color = tint.color)
                }
            }
        }

        // ── Applications ──
        Heading("Applications")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val active = s.applications.count { CareerPlanner.isActive(it) }
            Choice("Active $active", filter == null) { filter = null }
            listOf(Stage.SAVED, Stage.APPLIED, Stage.INTERVIEW, Stage.OFFER, Stage.CLOSED).forEach { st ->
                val n = s.stageCounts[st] ?: 0
                if (n > 0 || st == Stage.INTERVIEW) Choice("${st.label} $n", filter == st) { filter = st }
            }
        }
        val shown = s.applications.filter { a -> filter?.let { CareerPlanner.stage(a) == it } ?: CareerPlanner.isActive(a) }
        if (shown.isNotEmpty()) {
            V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
                shown.forEachIndexed { i, a ->
                    if (i > 0) V4Divider()
                    val st = CareerPlanner.stage(a)
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { openApp = a.id }.heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            OneLine(CareerPlanner.roleLine(a), V4.type.bodyStrong, c.ink)
                            Text(appMeta(a, s.interviews[a.id]), style = V4.type.caption, color = c.ink3)
                        }
                        StagePill(st)
                    }
                }
            }
        }
        V4PillButton("Add an application", onClick = { addingApp = true }, container = tint.color)
    } else {
        // ── Career plan ──
        val plan = s.plan
        V4Card(modifier = Modifier.fillMaxWidth(), color = tint.soft, bordered = false, onClick = plan?.let { { onOpenGoal(it.id) } }) {
            Text("Career plan", style = V4.type.label, color = tint.ink)
            if (plan == null) {
                Text("Where do you want to be in a year?", style = V4.type.headline, color = c.ink)
                Text("A role, a raise, a switch. The coach can break it into steps.", style = V4.type.caption, color = c.ink2)
                V4PillButton("Set a career goal", onClick = onNewPlan, container = tint.color)
            } else {
                val done = plan.milestones.count { it.isCompleted }
                Text(plan.title, style = V4.type.headline, color = c.ink)
                Text(
                    if (plan.milestones.isEmpty()) "No steps yet" else "$done of ${plan.milestones.size} steps" + (plan.milestones.firstOrNull { !it.isCompleted }?.let { ". Next: ${it.title}" } ?: ""),
                    style = V4.type.caption, color = c.ink2,
                )
                if (plan.milestones.isNotEmpty()) V4ProgressBar(done.toFloat() / plan.milestones.size, tint.color)
            }
        }
    }

    // ── Wins ──
    Heading("Wins")
    V4Card {
        Text(if (s.wins.isEmpty()) "No wins logged this quarter" else "${s.wins.size} ${if (s.wins.size == 1) "win" else "wins"} this quarter", style = V4.type.bodyStrong, color = c.ink)
        s.wins.take(4).forEach { w ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(w.title, style = V4.type.body, color = c.ink)
                Text(listOfNotNull(CareerPlanner.monthName(w.date.month), w.notes).joinToString(". "), style = V4.type.caption, color = c.ink3, maxLines = 2)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            V4PillButton("Log a win", onClick = { addingWin = true }, container = tint.color)
            if (s.wins.isNotEmpty()) {
                V4PillButton(if (copied) "Copied" else "Copy for review", onClick = { sharer.copyToClipboard(s.review); copied = true }, filled = false)
                V4TextButton("Share", onClick = { sharer.shareFile(s.review, "wins.txt", "text/plain") })
            }
        }
        if (copied) LaunchedEffect(Unit) { delay(2_000); copied = false }
        Text("What you did and what it changed. Small ones count: they are the ones you forget by review time.", style = V4.type.caption, color = c.ink3)
    }

    // ── Skills ──
    Heading("Skills")
    if (s.skills.isNotEmpty()) {
        V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            s.skills.forEachIndexed { i, k ->
                if (i > 0) V4Divider()
                Column(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { openSkill = k.log.id }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        OneLine(k.log.title, V4.type.bodyStrong, c.ink, Modifier.weight(1f))
                        Text(CareerPlanner.levelName(k.level), style = V4.type.label, color = tint.ink)
                    }
                    V4ProgressBar(k.level.toFloat() / k.want.coerceAtLeast(1), tint.color, height = 6.dp)
                    Text(
                        (if (k.level >= k.want) "Where you wanted it. " else "Aiming for ${CareerPlanner.levelName(k.want).lowercase()}. ") +
                            (if (k.practiceMin > 0) "${StudyPlanner.formatMinutes(k.practiceMin)} practised this month" else "Practise it from here or in Study"),
                        style = V4.type.caption, color = c.ink3,
                    )
                }
            }
        }
    }
    V4PillButton("Add a skill", onClick = { addingSkill = true }, filled = false)

    // ── People ──
    Heading("People")
    if (s.people.isNotEmpty()) {
        V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            s.people.forEachIndexed { i, p ->
                if (i > 0) V4Divider()
                val due = p.date <= today()
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { openPerson = p.id }.heightIn(min = 52.dp).padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        OneLine(p.title + (CareerPlanner.about(p)?.let { ", $it" } ?: ""), V4.type.bodyStrong, c.ink)
                        Text(
                            cadence(p.quantity?.toInt() ?: 30) + ". " + (if (due) "Due now" else "Next ${V4TodayViewModel.dayLabel(p.date)}"),
                            style = V4.type.caption, color = if (due) tint.ink else c.ink3,
                        )
                    }
                    V4PillButton("Talked", onClick = { viewModel.talked(p) }, filled = false)
                }
            }
        }
    }
    V4PillButton("Add a person", onClick = { addingPerson = true }, filled = false)
    Text("Pick how often to keep in touch. When it is due, it shows on Today.", style = V4.type.caption, color = c.ink3)

    // ── Sheets ──
    if (addingWin) WinSheet(onDismiss = { addingWin = false }, onSave = { w, i, d -> viewModel.logWin(w, i, d); addingWin = false })
    if (addingApp) ApplicationSheet(null, null, s.canCalendar, viewModel, onDismiss = { addingApp = false })
    openApp?.let { id -> s.applications.firstOrNull { it.id == id }?.let { ApplicationSheet(it, s.interviews[id], s.canCalendar, viewModel, onDismiss = { openApp = null }) } ?: run { openApp = null } }
    if (addingPerson) PersonSheet(null, viewModel, onDismiss = { addingPerson = false })
    openPerson?.let { id -> s.people.firstOrNull { it.id == id }?.let { PersonSheet(it, viewModel, onDismiss = { openPerson = null }) } ?: run { openPerson = null } }
    if (addingSkill) SkillSheet(null, viewModel, onRoute, onDismiss = { addingSkill = false })
    openSkill?.let { id -> s.skills.firstOrNull { it.log.id == id }?.let { SkillSheet(it, viewModel, onRoute, onDismiss = { openSkill = null }) } ?: run { openSkill = null } }
}

private fun today() = Clock.System.todayIn(TimeZone.currentSystemDefault())

@Composable
private fun Heading(text: String) =
    Text(text, style = V4.type.headline, color = V4.colors.ink, modifier = Modifier.padding(top = 4.dp).semantics { heading() })

@Composable
private fun StagePill(st: Stage) {
    val c = V4.colors
    val (bg, fg) = when (st) {
        Stage.SAVED -> c.surfaceMuted to c.ink2
        Stage.APPLIED -> c.accentSoft to c.accentInk
        Stage.INTERVIEW -> c.area(PlanArea.MEALS).soft to c.area(PlanArea.MEALS).ink
        Stage.OFFER -> c.successSoft to c.success
        Stage.CLOSED -> c.surfaceMuted to c.ink3
    }
    Text(st.label, style = V4.type.label, color = fg, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp))
}

private fun appMeta(a: LifeLog, interview: LifeLog?): String {
    val today = today()
    return when (CareerPlanner.stage(a)) {
        Stage.SAVED -> if (a.date <= today) "Apply today" else "Apply by ${V4TodayViewModel.dayLabel(a.date)}"
        Stage.APPLIED -> if (a.date <= today) "Follow up today" else "Follow up ${V4TodayViewModel.dayLabel(a.date)}"
        Stage.INTERVIEW -> interview?.let { "Interview ${V4TodayViewModel.dayLabel(it.date)} ${V4TodayViewModel.hhmm(it.occurredAt)}" } ?: "Interview stage. Add the date"
        Stage.OFFER -> "Offer. Reply by ${V4TodayViewModel.dayLabel(a.date)}"
        Stage.CLOSED -> CareerPlanner.field(a, "closed") ?: "Closed"
    }
}

private fun cadence(days: Int) = when (days) {
    14 -> "Every 2 weeks"; 30 -> "Every month"; 60 -> "Every 2 months"; 90 -> "Every 3 months"; else -> "Every $days days"
}

@Composable
private fun WinSheet(onDismiss: () -> Unit, onSave: (String, String?, LocalDate) -> Unit) {
    var what by remember { mutableStateOf("") }
    var impact by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(today()) }
    AreaSheet("Log a win", onDismiss) {
        FormLabel("What you did")
        TravelField(what, { what = it }, "Shipped the new onboarding", "What you did")
        FormLabel("What it changed")
        TravelField(impact, { impact = it }, "Sign-ups up 12%, fewer support tickets", "What it changed")
        FormLabel("When")
        DayChoices(day, { day = it }, days = 2, includeYesterday = true, allowPicker = true)
        V4PrimaryButton("Save", onClick = { onSave(what, impact, day) }, enabled = what.isNotBlank(), container = V4.colors.area(PlanArea.CAREER).color, modifier = Modifier.fillMaxWidth())
        SheetNote("A number or a person it helped makes it land in a review.")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApplicationSheet(app: LifeLog?, interview: LifeLog?, canCalendar: Boolean, vm: V4CareerViewModel, onDismiss: () -> Unit) {
    val c = V4.colors
    val tint = c.area(PlanArea.CAREER)
    var role by remember { mutableStateOf(app?.title ?: "") }
    var company by remember { mutableStateOf(app?.let { CareerPlanner.company(it) } ?: "") }
    var link by remember { mutableStateOf(app?.let { CareerPlanner.link(it) } ?: "") }
    var stage by remember { mutableStateOf(app?.let { CareerPlanner.stage(it) } ?: Stage.SAVED) }
    var planning by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    AreaSheet(if (app == null) "Add an application" else CareerPlanner.roleLine(app), onDismiss) {
        FormLabel("Role")
        TravelField(role, { role = it }, "Android engineer", "Role")
        FormLabel("Company")
        TravelField(company, { company = it }, "Wolt", "Company")
        FormLabel("Link to the job, if you have it")
        TravelField(link, { link = it }, "https://", "Link")
        FormLabel("Stage")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Stage.SAVED, Stage.APPLIED, Stage.INTERVIEW, Stage.OFFER).forEach { st ->
                Choice(st.label, stage == st) {
                    stage = st
                    if (app != null && st != CareerPlanner.stage(app)) vm.setStage(app, st)
                }
            }
        }
        if (app == null) {
            V4PrimaryButton(
                "Save", onClick = { vm.addApplication(role, company, link.ifBlank { null }, stage); onDismiss() },
                enabled = role.isNotBlank() || company.isNotBlank(), container = tint.color, modifier = Modifier.fillMaxWidth(),
            )
            SheetNote(if (stage == Stage.SAVED) "It shows on Today until you apply." else "A follow-up lands on Today a week after applying.")
        } else {
            if (role != app.title || company != (CareerPlanner.company(app) ?: "") || link != (CareerPlanner.link(app) ?: "")) {
                V4PrimaryButton("Save changes", onClick = { vm.updateApplication(app, role, company, link.ifBlank { null }) }, container = tint.color, modifier = Modifier.fillMaxWidth())
            }
            interview?.let { Text("Interview ${V4TodayViewModel.dayLabel(it.date)} at ${V4TodayViewModel.hhmm(it.occurredAt)}", style = V4.type.bodyStrong, color = c.ink) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                V4PillButton(if (interview == null) "Add an interview" else "Add another interview", onClick = { planning = !planning }, filled = false)
                if (CareerPlanner.stage(app) != Stage.CLOSED) V4PillButton("Close it", onClick = { closing = !closing }, filled = false)
                else V4PillButton("Reopen", onClick = { vm.setStage(app, Stage.APPLIED) }, filled = false)
                V4TextButton("Delete", onClick = { vm.remove(app); onDismiss() }, color = Color(0xFFB42318))
            }
            if (planning) InterviewForm(canCalendar) { d, t, m, cal -> vm.scheduleInterview(app, d, t, m, cal); planning = false }
            if (closing) {
                FormLabel("Why is it closing?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("No reply", "Not selected", "I withdrew", "Took another offer", "Accepted this one").forEach { r ->
                        Choice(r, false) { vm.setStage(app, Stage.CLOSED, r); closing = false; onDismiss() }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterviewForm(canCalendar: Boolean, onSave: (LocalDate, LocalTime, Int, Boolean) -> Unit) {
    var day by remember { mutableStateOf(today()) }
    var time by remember { mutableStateOf(LocalTime(10, 0)) }
    var minutes by remember { mutableIntStateOf(60) }
    var calendar by remember { mutableStateOf(canCalendar) }
    FormLabel("Day")
    DayChoices(day, { day = it }, allowPicker = true)
    FormLabel("Time")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(LocalTime(9, 0), LocalTime(10, 0), LocalTime(11, 0), LocalTime(13, 0), LocalTime(14, 0), LocalTime(15, 0), LocalTime(16, 0), LocalTime(17, 0)).forEach { t ->
            Choice(V4FitnessViewModel.fmt(t), time == t) { time = t }
        }
    }
    FormLabel("How long")
    CountChoices(listOf(30, 45, 60, 90), minutes, { "$it min" }) { minutes = it }
    if (canCalendar) V4SwitchRow("Add to my calendar", calendar, { calendar = it })
    V4PrimaryButton("Save interview", onClick = { onSave(day, time, minutes, calendar) }, container = V4.colors.area(PlanArea.CAREER).color, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PersonSheet(person: LifeLog?, vm: V4CareerViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(person?.title ?: "") }
    var about by remember { mutableStateOf(person?.let { CareerPlanner.about(it) } ?: "") }
    var every by remember { mutableIntStateOf(person?.quantity?.toInt() ?: 30) }
    AreaSheet(person?.title ?: "Add a person", onDismiss) {
        if (person == null) {
            FormLabel("Name")
            TravelField(name, { name = it }, "Aysel", "Name")
            FormLabel("Who they are to you")
            TravelField(about, { about = it }, "Ex-manager, recruiter at Wolt", "Who they are")
        } else {
            CareerPlanner.lastTalked(person)?.let { Text("Last talked ${CareerPlanner.ago(it, today())}", style = V4.type.bodyStrong, color = V4.colors.ink) }
        }
        FormLabel("Keep in touch")
        CountChoices(listOf(14, 30, 60, 90), every, ::cadence) { every = it; if (person != null) vm.setCadence(person, it) }
        if (person == null) {
            V4PrimaryButton("Save", onClick = { vm.addContact(name, about.ifBlank { null }, every); onDismiss() }, enabled = name.isNotBlank(), container = V4.colors.area(PlanArea.CAREER).color, modifier = Modifier.fillMaxWidth())
        } else {
            V4PillButton("Talked today", onClick = { vm.talked(person); onDismiss() }, container = V4.colors.area(PlanArea.CAREER).color)
            V4TextButton("Remove", onClick = { vm.remove(person); onDismiss() }, color = Color(0xFFB42318))
        }
    }
}

@Composable
private fun SkillSheet(skill: SkillRow?, vm: V4CareerViewModel, onRoute: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(skill?.log?.title ?: "") }
    var level by remember { mutableIntStateOf(skill?.level ?: 2) }
    var want by remember { mutableIntStateOf(skill?.want ?: 4) }
    val tint = V4.colors.area(PlanArea.CAREER)
    AreaSheet(skill?.log?.title ?: "Add a skill", onDismiss) {
        if (skill == null) {
            FormLabel("Skill")
            TravelField(name, { name = it }, "System design, public speaking", "Skill")
        }
        FormLabel("Where you are")
        CountChoices((1..5).toList(), level, CareerPlanner::levelName) { level = it; if (skill != null) vm.setSkill(skill.log, it, want) }
        FormLabel("Where you want to be")
        CountChoices((1..5).toList(), want, CareerPlanner::levelName) { want = it; if (skill != null) vm.setSkill(skill.log, level, it) }
        if (skill == null) {
            V4PrimaryButton("Save", onClick = { vm.addSkill(name, level, want); onDismiss() }, enabled = name.isNotBlank(), container = tint.color, modifier = Modifier.fillMaxWidth())
            SheetNote("Study sessions with the same name count as practice.")
        } else {
            V4PillButton("Practise now", onClick = { vm.practise(skill.log); onDismiss(); onRoute("v4_area/study") }, container = tint.color)
            SheetNote("Starts the Study timer on ${skill.log.title}. The time counts here too.")
            V4TextButton("Remove", onClick = { vm.remove(skill.log); onDismiss() }, color = Color(0xFFB42318))
        }
    }
}
