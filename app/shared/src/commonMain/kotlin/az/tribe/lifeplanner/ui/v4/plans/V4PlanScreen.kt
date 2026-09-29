package az.tribe.lifeplanner.ui.v4.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.data.plans.PlanState
import az.tribe.lifeplanner.data.plans.PlanView
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.CatchUpChoice
import az.tribe.lifeplanner.domain.service.Pace
import az.tribe.lifeplanner.domain.service.PlanScheduler
import az.tribe.lifeplanner.domain.service.PlanTrack
import az.tribe.lifeplanner.ui.v4.components.V4BackLink
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4Divider
import az.tribe.lifeplanner.ui.v4.components.V4IconButton
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.components.V4ProgressBar
import az.tribe.lifeplanner.ui.v4.components.V4SectionTitle
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.components.areaName
import az.tribe.lifeplanner.ui.v4.theme.V4
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.DotsThree
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Which sheet is open on the page. */
private enum class PageSheet { DATE, PAUSE, LET_GO, DELETE, RENAME, PUT_ASIDE, STEP }

/**
 * A plan's own page in v4, in place of the v3 goal detail: where it stands against an even pace,
 * the one next step, catching up when it slipped, the steps, what keeps it moving, and the calm
 * ways out (a new date, a pause, letting it go).
 */
@Composable
fun V4PlanScreen(
    goalId: String,
    onBack: () -> Unit,
    onOpenArea: (PlanArea) -> Unit,
    onNewPlan: (PlanSheetRequest) -> Unit,
    viewModel: V4PlanViewModel = koinViewModel(key = "plan_$goalId") { parametersOf(goalId) },
) {
    val s by viewModel.state.collectAsState()
    val c = V4.colors
    val v = s.view
    var sheet by remember { mutableStateOf<PageSheet?>(null) }
    var step by remember { mutableStateOf<Milestone?>(null) }
    fun editStep(m: Milestone?) { step = m; sheet = PageSheet.STEP }

    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TopRow(v, onBack, onRename = { sheet = PageSheet.RENAME }, onDelete = { sheet = PageSheet.DELETE })
        if (v == null) {
            if (!s.loading) Text("This plan is not here any more.", style = V4.type.body, color = c.ink2)
            return@Column
        }
        Header(v, s)
        PlanPageModel.banner(v, s.beforeNewDate != null)?.let { b ->
            Banner(b) {
                when (v.state) {
                    PlanState.PAUSED -> viewModel.resume()
                    PlanState.LET_GO -> viewModel.bringBack()
                    else -> viewModel.undoDate()
                }
            }
        }
        if (v.state == PlanState.DONE) Finished(v, s, onNext = { onNewPlan(PlanSheetRequest(v.area, it, "next")) }, onKeep = viewModel::keepRoutine)
        ProgressCard(v)
        if (v.catchUp != null) CatchUpCard(v, viewModel::catchUp)
        else if (v.state == PlanState.ACTIVE) s.note?.let { Note(it) }
        if (v.state == PlanState.ACTIVE) v.next?.let { m ->
            NextStep(v, m, s, onTick = { viewModel.setStep(m, true) }, onLog = {
                if (v.track == PlanTrack.SAVE) sheet = PageSheet.PUT_ASIDE else viewModel.addOne()
            })
        }
        StepsSection(v, s, onToggle = viewModel::setStep, onEdit = ::editStep, onAdd = { editStep(null) })
        PlanPageModel.routine(v, s.routineName)?.let { (title, line) -> RoutineCard(v.area, title, line) { onOpenArea(v.area) } }
        V4Card(modifier = Modifier.fillMaxWidth(), color = c.surfaceMuted, bordered = false, verticalSpacing = 4.dp) {
            Text("How it moves", style = V4.type.label, color = c.ink2)
            Text(PlanPageModel.moves(v), style = V4.type.body, color = c.ink)
        }
        if (v.state == PlanState.ACTIVE || v.state == PlanState.PAUSED) Actions(v, onDate = { sheet = PageSheet.DATE }, onPause = {
            if (v.state == PlanState.PAUSED) viewModel.resume() else sheet = PageSheet.PAUSE
        }, onLetGo = { sheet = PageSheet.LET_GO })
    }

    if (v != null) when (sheet) {
        PageSheet.DATE -> DateSheet(v, s.today, onPick = viewModel::changeDate, onDismiss = { sheet = null })
        PageSheet.PAUSE -> PauseSheet(onPause = viewModel::pause, onDismiss = { sheet = null })
        PageSheet.LET_GO -> LetGoSheet(v, onLetGo = viewModel::letGo, onDismiss = { sheet = null })
        PageSheet.DELETE -> DeleteSheet(onDelete = { viewModel.delete(onBack) }, onDismiss = { sheet = null })
        PageSheet.RENAME -> RenameSheet(v.title, onSave = viewModel::rename, onDismiss = { sheet = null })
        PageSheet.PUT_ASIDE -> PutAsideSheet(v, s.currency, onSave = viewModel::putAside, onDismiss = { sheet = null })
        PageSheet.STEP -> StepSheet(
            step, step?.dueDate ?: viewModel.newStepDate(),
            onSave = { title, date -> step?.let { viewModel.editStep(it, title, date) } ?: viewModel.addStep(title, date) },
            onRemove = { step?.let(viewModel::removeStep) },
            onDismiss = { sheet = null },
        )
        null -> {}
    }
}

@Composable
private fun TopRow(v: PlanView?, onBack: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val c = V4.colors
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        V4BackLink(v?.let { areaName(it.area) } ?: "Back", onBack)
        if (v != null) Box {
            V4IconButton(PhosphorIcons.Regular.DotsThree, "More for this plan", { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.surface) {
                DropdownMenuItem(
                    text = { Text("Rename", style = V4.type.bodyStrong, color = c.ink) },
                    onClick = { menu = false; onRename() }, modifier = Modifier.heightIn(min = 48.dp),
                )
                DropdownMenuItem(
                    text = { Text("Delete this plan", style = V4.type.bodyStrong, color = c.ink2) },
                    onClick = { menu = false; onDelete() }, modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun Header(v: PlanView, s: PlanPageState) {
    val c = V4.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${areaName(v.area).uppercase()} PLAN", style = V4.type.micro, color = c.area(v.area).ink)
        Text(v.title, style = V4.type.display, color = c.ink, modifier = Modifier.semantics { heading() })
        Text(PlanPageModel.sub(v, s.today), style = V4.type.label, color = c.ink2)
    }
}

@Composable
private fun Banner(b: PlanBanner, onAction: () -> Unit) {
    val c = V4.colors
    V4Card(modifier = Modifier.fillMaxWidth(), color = c.inverse, bordered = false, verticalSpacing = 6.dp) {
        Text(b.title, style = V4.type.bodyStrong, color = c.onInverse)
        Text(b.text, style = V4.type.caption, color = c.onInverse.copy(alpha = 0.8f))
        V4PillButton(b.action, onClick = onAction, container = c.surface, contentColor = c.ink)
    }
}

@Composable
private fun Note(text: String) {
    val c = V4.colors
    Text(
        text, style = V4.type.body, color = c.ink,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.successSoft).padding(14.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Finished(v: PlanView, s: PlanPageState, onNext: (String) -> Unit, onKeep: () -> Unit) {
    val c = V4.colors
    val ac = c.area(v.area)
    val (headline, body) = v.recap ?: ("You did it." to "")
    V4Card(modifier = Modifier.fillMaxWidth(), color = ac.soft, bordered = false, verticalSpacing = 8.dp) {
        Text("DONE, ${PlanScheduler.dayLabel(v.spec?.finished ?: s.today).uppercase()}", style = V4.type.micro, color = ac.ink)
        Text(headline, style = V4.type.title, color = c.ink)
        if (body.isNotEmpty()) Text(body, style = V4.type.body, color = c.ink2)
        Text("What next?", style = V4.type.bodyStrong, color = c.ink, modifier = Modifier.padding(top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlanPageModel.nextIdea(v, s.currency)?.let { idea -> V4PillButton(PlanPageModel.nextLabel(idea), onClick = { onNext(idea) }, container = ac.color) }
            PlanPageModel.routine(v, s.routineName)?.let { (title, _) ->
                V4PillButton("Just keep ${title.replaceFirstChar { it.lowercase() }}", onClick = onKeep, filled = false, contentColor = c.ink)
            }
        }
        Text(s.note ?: "Or do nothing. It stays in Done on its own.", style = V4.type.caption, color = c.ink2)
    }
}

@Composable
private fun ProgressCard(v: PlanView) {
    val c = V4.colors
    val ac = c.area(v.area)
    val showMarker = v.state == PlanState.ACTIVE && v.progress.fraction < 1f
    V4Card(modifier = Modifier.fillMaxWidth(), verticalSpacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(v.progress.headline, style = V4.type.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
            PacePill(v.pace, v.area)
        }
        PaceBar(v.progress.fraction, if (showMarker) v.pace.expected else null, ac.color)
        if (showMarker) Text("The dark line is where an even pace would be today.", style = V4.type.caption, color = c.ink3)
        if (v.progress.stats.isNotEmpty()) {
            V4Divider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                v.progress.stats.forEach { st ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(st.label, style = V4.type.caption, color = c.ink2)
                        Text(st.value, style = V4.type.bodyStrong, color = c.ink)
                    }
                }
            }
        }
        v.progress.source?.let { Text(it, style = V4.type.caption, color = c.ink2) }
    }
}

@Composable
internal fun PacePill(pace: Pace, area: PlanArea) {
    val c = V4.colors
    val (bg, ink) = when (PlanPageModel.tone(pace.kind)) {
        PaceTone.GOOD -> c.successSoft to c.success
        PaceTone.WARN -> c.area(area).soft to c.area(area).ink
        PaceTone.QUIET -> c.surfaceMuted to c.ink2
    }
    Text(
        pace.label, style = V4.type.micro, color = ink, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** The progress bar with a thin dark line where an even pace would be today. */
@Composable
internal fun PaceBar(fraction: Float, expected: Float?, color: Color, height: androidx.compose.ui.unit.Dp = 8.dp) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(height + 6.dp).semantics(mergeDescendants = true) {
        contentDescription = "${(fraction * 100).toInt()} percent" + (expected?.let { ", an even pace would be ${(it * 100).toInt()} percent" } ?: "")
    }) {
        V4ProgressBar(fraction, color, Modifier.align(Alignment.Center), height = height)
        expected?.let {
            val x = (maxWidth * it.coerceIn(0f, 1f) - 1.dp).coerceIn(0.dp, maxWidth - 2.dp)
            Box(Modifier.offset(x = x).width(2.dp).fillMaxHeight().clip(RoundedCornerShape(1.dp)).background(V4.colors.ink))
        }
    }
}

@Composable
private fun CatchUpCard(v: PlanView, onPick: (CatchUpChoice) -> Unit) {
    val c = V4.colors
    val ac = c.area(v.area)
    V4Card(modifier = Modifier.fillMaxWidth(), color = ac.soft, bordered = false, verticalSpacing = 10.dp) {
        Text(PlanPageModel.catchUpTitle(v), style = V4.type.bodyStrong, color = c.ink)
        Text(PlanPageModel.catchUpText(v), style = V4.type.caption, color = c.ink2)
        Choice2(PlanPageModel.pushLabel(v), PlanPageModel.pushSub(v)) { onPick(CatchUpChoice.PUSH) }
        if (v.catchUp?.canKeep == true) Choice2(PlanPageModel.keepLabel(v), PlanPageModel.keepSub(v)) { onPick(CatchUpChoice.KEEP) }
        V4TextButton("Leave it as it is", onClick = { onPick(CatchUpChoice.LEAVE) }, color = c.ink2)
    }
}

/** A two-line choice on the catch-up card. */
@Composable
private fun Choice2(title: String, sub: String, onClick: () -> Unit) {
    val c = V4.colors
    Column(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(c.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = V4.type.bodyStrong, color = c.ink)
        Text(sub, style = V4.type.caption, color = c.ink2)
    }
}

@Composable
private fun NextStep(v: PlanView, m: Milestone, s: PlanPageState, onTick: () -> Unit, onLog: () -> Unit) {
    val c = V4.colors
    val ac = c.area(v.area)
    val log = PlanPageModel.logAction(v)
    // One filled button: the log when there is one, the tick when nothing else moves the step.
    val tickFilled = !PlanPageModel.ticksItself(v, m) && log == null
    V4Card(modifier = Modifier.fillMaxWidth(), verticalSpacing = 8.dp) {
        Text("NEXT STEP", style = V4.type.micro, color = ac.ink)
        Text(m.title, style = V4.type.headline, color = c.ink)
        Text(PlanPageModel.nextText(v, m, s.today), style = V4.type.caption, color = c.ink2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            log?.let { V4PillButton(it, onClick = onLog, container = ac.color) }
            V4PillButton(PlanPageModel.tickLabel(v, m), onClick = onTick, filled = tickFilled, container = ac.color, contentColor = if (tickFilled) c.onAccent else c.ink)
        }
    }
}

@Composable
private fun StepsSection(v: PlanView, s: PlanPageState, onToggle: (Milestone, Boolean) -> Unit, onEdit: (Milestone) -> Unit, onAdd: () -> Unit) {
    val c = V4.colors
    val ac = c.area(v.area)
    V4SectionTitle("Steps")
    if (v.steps.isNotEmpty()) {
        V4Card(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            v.steps.forEachIndexed { n, m ->
                if (n > 0) V4Divider()
                val next = m.id == v.next?.id && v.state == PlanState.ACTIVE
                Row(
                    Modifier.fillMaxWidth().background(if (next) ac.soft.copy(alpha = 0.5f) else Color.Transparent)
                        .clickable(role = Role.Button) { onEdit(m) }.padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(role = Role.Checkbox) { onToggle(m, !m.isCompleted) }
                            .semantics { contentDescription = m.title; stateDescription = if (m.isCompleted) "Done" else "Not done" },
                        contentAlignment = Alignment.Center,
                    ) { StepRing(m.isCompleted, ac.color) }
                    Column(Modifier.weight(1f).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(m.title, style = V4.type.bodyStrong, color = if (m.isCompleted) c.ink2 else c.ink)
                        Text(PlanPageModel.stepMeta(v, m, s.today), style = V4.type.caption, color = c.ink2)
                    }
                }
            }
        }
    } else {
        Text("No steps yet. Add the first one and it lands on Today on its day.", style = V4.type.caption, color = c.ink2)
    }
    V4TextButton("+ Add a step", onClick = onAdd)
    Text(
        "Tap a step to rename it or move its day." + if (v.track != PlanTrack.CHECKLIST && v.track != PlanTrack.STUDY) " Steps with a number tick themselves, so there is nothing to type." else "",
        style = V4.type.caption, color = c.ink3,
    )
}

@Composable
private fun RoutineCard(area: PlanArea, title: String, line: String, onOpen: () -> Unit) {
    val c = V4.colors
    V4SectionTitle("Keeps it moving")
    V4Card(modifier = Modifier.fillMaxWidth(), verticalSpacing = 4.dp, onClick = onOpen) {
        Text(title, style = V4.type.bodyStrong, color = c.ink)
        Text(line, style = V4.type.caption, color = c.ink2)
        Text("Open ${areaName(area)}", style = V4.type.label, color = c.accentInk, modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(v: PlanView, onDate: () -> Unit, onPause: () -> Unit, onLetGo: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        V4PillButton("Change date", onClick = onDate, filled = false)
        V4PillButton(if (v.state == PlanState.PAUSED) "Resume" else "Pause", onClick = onPause, filled = false)
        V4PillButton("Let it go", onClick = onLetGo, filled = false)
    }
}
