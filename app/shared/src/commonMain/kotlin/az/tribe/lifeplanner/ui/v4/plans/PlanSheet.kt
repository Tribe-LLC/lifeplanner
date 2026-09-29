package az.tribe.lifeplanner.ui.v4.plans

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.PlanQuestion
import az.tribe.lifeplanner.ui.v4.areas.Choice
import az.tribe.lifeplanner.ui.v4.areas.SheetNote
import az.tribe.lifeplanner.ui.v4.areas.toAmount
import az.tribe.lifeplanner.ui.v4.components.AreaChip
import az.tribe.lifeplanner.ui.v4.components.NeutralChip
import az.tribe.lifeplanner.ui.v4.components.V4BackLink
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4Divider
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.components.V4Switch
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.components.areaName
import az.tribe.lifeplanner.ui.v4.theme.V4
import com.adamglin.phosphoricons.Bold
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.bold.Check
import kotlinx.coroutines.delay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import org.koin.compose.viewmodel.koinViewModel

/**
 * "New plan": one line in, a dated plan out. Replaces the v3 wizard inside v4. Opened from an area
 * page (that area preset), from Add anything (the line prefilled) or from an idea.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSheet(
    request: PlanSheetRequest,
    onDismiss: () -> Unit,
    onOpenPlan: (String) -> Unit,
    viewModel: PlanSheetViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(request) { viewModel.open(request) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = V4.colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val p = state.preview
            when {
                state.stage == SheetStage.SAVED && p != null -> SavedStage(p, state, onOpen = { state.savedId?.let(onOpenPlan) }, onDone = onDismiss)
                state.stage == SheetStage.PREVIEW && p != null -> PreviewStage(p, state, viewModel)
                else -> TypeStage(state, viewModel)
            }
        }
    }
}

// ── Type ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TypeStage(state: PlanSheetState, vm: PlanSheetViewModel) {
    val c = V4.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (state.inputs.line.isEmpty()) {
            delay(150)
            runCatching { focus.requestFocus() }
        }
    }
    Text("New plan", style = V4.type.title, color = c.ink, modifier = Modifier.semantics { heading() })
    Text("Say it in one line. We work out the steps, the dates and how it moves.", style = V4.type.body, color = c.ink2)
    PlanField(
        value = state.inputs.line, onChange = vm::onLine,
        hint = if (state.inputs.preset == null) "Run a 5K by December" else state.ideas.firstOrNull() ?: "Run a 5K by December",
        label = "Your plan in one line", onDone = vm::make, big = true, modifier = Modifier.focusRequester(focus),
    )
    state.preview?.let { p ->
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AreaChip(p.area)
            PlanSheetModel.pills(p).drop(1).forEach { NeutralChip(it) }
        }
        Text(PlanSheetModel.understood(p), style = V4.type.caption, color = c.ink2)
    }
    Text("Or start from one of these", style = V4.type.label, color = c.ink3)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.ideas.forEach { idea -> Choice(idea, state.inputs.line == idea) { vm.pickIdea(idea) } }
    }
    V4PrimaryButton("Make my plan", onClick = vm::make, enabled = state.preview != null, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
}

// ── Preview ──────────────────────────────────────────────────────────────────

@Composable
private fun PreviewStage(p: SheetPreview, state: PlanSheetState, vm: PlanSheetViewModel) {
    val c = V4.colors
    V4BackLink("Back", onClick = vm::back)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${areaName(p.area).uppercase()} PLAN", style = V4.type.micro, color = c.area(p.area).ink)
        Text(p.title, style = V4.type.title, color = c.ink, modifier = Modifier.semantics { heading() })
    }
    Facts(p, vm)
    p.recipe.question?.let { QuestionCard(it, state.inputs.answer, p.recipe.answerNote, vm::answer) }
    if (p.needsSteps) NeedsSteps(state, vm)
    else StepsList(p, vm)
    V4Card(modifier = Modifier.fillMaxWidth(), color = c.surfaceMuted, bordered = false, verticalSpacing = 4.dp) {
        Text("How it moves", style = V4.type.label, color = c.ink2)
        Text(p.recipe.moves, style = V4.type.body, color = c.ink)
    }
    if (p.hasRoutine) RoutineRow(p, vm::toggleRoutine)
    V4PrimaryButton(
        if (state.saving) "Starting" else "Start plan", onClick = vm::start,
        enabled = p.steps.isNotEmpty() && !state.saving, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    )
    SheetNote("Change anything later, on the plan's page.")
}

private enum class Fact { AMOUNT, DATE, AREA }

/** Amount, date and area as tappable facts; one opens its own small editor underneath. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Facts(p: SheetPreview, vm: PlanSheetViewModel) {
    var editing by remember { mutableStateOf<Fact?>(null) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        p.recipe.target?.takeIf { p.recipe.track == az.tribe.lifeplanner.domain.service.PlanTrack.SAVE }?.let {
            FactChip(MoneyFormat.format(it, p.currency), editing == Fact.AMOUNT) { editing = if (editing == Fact.AMOUNT) null else Fact.AMOUNT }
        }
        FactChip(PlanSheetModel.dateFact(p), editing == Fact.DATE) { editing = if (editing == Fact.DATE) null else Fact.DATE }
        FactChip(areaName(p.area), editing == Fact.AREA) { editing = if (editing == Fact.AREA) null else Fact.AREA }
    }
    when (editing) {
        Fact.AMOUNT -> AmountEditor(p) { vm.setAmount(it); editing = null }
        Fact.DATE -> DateEditor(p) { vm.setTarget(it); editing = null }
        Fact.AREA -> AreaEditor(p.area) { vm.setArea(it); editing = null }
        null -> {}
    }
}

@Composable
private fun FactChip(label: String, open: Boolean, onClick: () -> Unit) {
    val c = V4.colors
    Row(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(if (open) c.surfaceMuted else c.surface)
            .border(1.5.dp, c.line, RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label. Change" }.padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = V4.type.label, color = c.ink)
        Text("Change", style = V4.type.label, color = c.accentInk)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DateEditor(p: SheetPreview, onPick: (kotlinx.datetime.LocalDate) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val choices = listOf("In a month" to 1, "In 3 months" to 3, "In 6 months" to 6).map { (l, m) -> l to p.start.plus(DatePeriod(months = m)) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (label, d) -> Choice(label, p.dated && p.target == d) { onPick(d) } }
        Choice("Pick a date", false) { picking = true }
    }
    if (picking) PlanDateDialog(p.target, p.start.plus(DatePeriod(days = 1)), null, onPick = onPick, onDismiss = { picking = false })
}

@Composable
private fun AmountEditor(p: SheetPreview, onSet: (Double) -> Unit) {
    var text by remember { mutableStateOf(p.recipe.target?.let { MoneyFormat.plain(it) } ?: "") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PlanField(
            text, { text = it }, hint = "2000", label = "Amount in ${p.currency}",
            onDone = { text.toAmount()?.let(onSet) }, keyboard = KeyboardType.Decimal, modifier = Modifier.weight(1f),
        )
        V4PillButton("Set", onClick = { text.toAmount()?.let(onSet) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AreaEditor(current: PlanArea, onPick: (PlanArea) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlanArea.entries.forEach { a -> Choice(areaName(a), a == current) { onPick(a) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuestionCard(q: PlanQuestion, picked: String?, note: String?, onPick: (String) -> Unit) {
    val c = V4.colors
    val on = q.answer(picked).key
    V4Card(modifier = Modifier.fillMaxWidth(), verticalSpacing = 10.dp) {
        Text(q.text, style = V4.type.bodyStrong, color = c.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            q.answers.forEach { a -> Choice(a.label, a.key == on) { onPick(a.key) } }
        }
        note?.let { Text(it, style = V4.type.caption, color = c.ink2) }
    }
}

@Composable
private fun NeedsSteps(state: PlanSheetState, vm: PlanSheetViewModel) {
    val c = V4.colors
    V4Card(modifier = Modifier.fillMaxWidth(), color = c.surfaceMuted, bordered = false, verticalSpacing = 8.dp) {
        Text("No ready plan for this one yet", style = V4.type.bodyStrong, color = c.ink)
        Text("The coach can suggest steps once. They are kept, so the next plan like this is instant and free.", style = V4.type.caption, color = c.ink2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            V4PillButton(if (state.suggesting) "Asking the coach" else "Suggest steps", onClick = vm::suggest)
            V4PillButton("I will add my own", onClick = vm::ownSteps, filled = false)
        }
        state.suggestNote?.let { Text(it, style = V4.type.caption, color = c.ink2) }
    }
}

@Composable
private fun StepsList(p: SheetPreview, vm: PlanSheetViewModel) {
    val c = V4.colors
    val tint = c.area(p.area).color
    var moving by remember { mutableStateOf<SheetStep?>(null) }
    var adding by remember { mutableStateOf(p.steps.isEmpty()) }
    var newStep by remember { mutableStateOf("") }
    if (p.steps.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Steps", style = V4.type.headline, color = c.ink, modifier = Modifier.weight(1f))
            Text(PlanSheetModel.stepCount(p), style = V4.type.caption, color = c.ink2)
        }
        V4Card(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp), verticalSpacing = 0.dp) {
            val firstOpen = p.steps.firstOrNull { !it.done }?.key
            p.steps.forEachIndexed { n, s ->
                if (n > 0) V4Divider()
                StepRow(s, PlanSheetModel.stepMeta(s, s.key == firstOpen, p.start), tint, onMove = { moving = s }, onRemove = { vm.removeStep(s.key) })
            }
        }
    }
    if (adding) {
        val add = { vm.addStep(newStep); newStep = "" }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlanField(newStep, { newStep = it }, hint = "A step, in a few words", label = "New step", onDone = add, modifier = Modifier.weight(1f))
            V4PillButton("Add", onClick = add)
        }
    } else {
        V4TextButton("+ Add a step", onClick = { adding = true })
    }
    if (p.suggested) Text("Suggested by the coach, once. Remove any, or add your own.", style = V4.type.caption, color = c.ink2)
    moving?.let { s ->
        PlanDateDialog(s.date ?: p.start, p.start, if (p.open) null else p.target, onPick = { vm.moveStep(s.key, it) }, onDismiss = { moving = null })
    }
}

@Composable
private fun StepRow(s: SheetStep, meta: String, tint: androidx.compose.ui.graphics.Color, onMove: () -> Unit, onRemove: () -> Unit) {
    val c = V4.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StepRing(s.done, tint)
        Column(
            Modifier.weight(1f).let { if (s.done) it else it.clickable(role = Role.Button, onClick = onMove) }
                .semantics { if (!s.done) contentDescription = "${s.title}, $meta. Move to another day" }.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(s.title, style = V4.type.bodyStrong, color = if (s.done) c.ink2 else c.ink)
            Text(meta, style = V4.type.caption, color = c.ink2)
        }
        RemoveButton("Remove step: ${s.title}", onRemove)
    }
}

@Composable
private fun RoutineRow(p: SheetPreview, onToggle: () -> Unit) {
    val c = V4.colors
    V4Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.recipe.routineTitle.orEmpty(), style = V4.type.bodyStrong, color = c.ink)
                Text(PlanSheetModel.routineLine(p), style = V4.type.caption, color = c.ink2)
            }
            V4Switch(p.routineOn, { onToggle() }, "Add the routine")
        }
    }
}

// ── Saved ────────────────────────────────────────────────────────────────────

@Composable
private fun SavedStage(p: SheetPreview, state: PlanSheetState, onOpen: () -> Unit, onDone: () -> Unit) {
    val c = V4.colors
    val ac = c.area(p.area)
    Box(Modifier.size(64.dp).clip(CircleShape).background(ac.soft), contentAlignment = Alignment.Center) {
        Icon(PhosphorIcons.Bold.Check, contentDescription = null, tint = ac.color, modifier = Modifier.size(32.dp))
    }
    Text("Plan started", style = V4.type.title, color = c.ink, modifier = Modifier.semantics { heading() })
    Text(state.savedLine, style = V4.type.body, color = c.ink2)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        state.savedFacts.forEach { f ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape).background(ac.color))
                Text(f, style = V4.type.body, color = c.ink)
            }
        }
    }
    V4PrimaryButton("Open the plan", onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
    V4TextButton("Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
}
