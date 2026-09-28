package az.tribe.lifeplanner.ui.v4.areas

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.ui.v4.components.CheckCircleButton
import az.tribe.lifeplanner.ui.v4.components.OneLine
import az.tribe.lifeplanner.ui.v4.components.V4BackLink
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4Divider
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.components.V4ProgressBar
import az.tribe.lifeplanner.ui.v4.components.areaBlurb
import az.tribe.lifeplanner.ui.v4.components.areaName
import az.tribe.lifeplanner.ui.v4.illustration.AreaIllustration
import az.tribe.lifeplanner.ui.v4.theme.V4
import az.tribe.lifeplanner.ui.v4.today.V4TodayViewModel
import az.tribe.lifeplanner.ui.v4.travel.TravelSection
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** What an area page can open. Plain routes, so the v3 screens do the editing for now. */
data class AreaActions(
    val onBack: () -> Unit,
    val onOpenGoal: (String) -> Unit,
    val onOpenHabit: (String) -> Unit,
    val onNewPlan: () -> Unit,
    val onNewRoutine: () -> Unit,
    val onRoute: (String) -> Unit,
    val onQuickAdd: () -> Unit,
    /** Opens the coach with this message ready to send. */
    val onAskCoach: (String) -> Unit = {},
)

@Composable
fun V4AreaScreen(
    area: PlanArea,
    actions: AreaActions,
    viewModel: V4AreaViewModel = koinViewModel(key = "area_${area.key}") { parametersOf(area) },
) {
    val state by viewModel.state.collectAsState()
    val c = V4.colors
    val ac = c.area(area)

    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        V4BackLink("Life", actions.onBack)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(areaName(area), style = V4.type.display, color = c.ink, modifier = Modifier.semantics { heading() })
                Text(areaBlurb(area), style = V4.type.label, color = ac.ink)
            }
            AreaIllustration(area, size = 76.dp)
        }

        when (area) {
            PlanArea.MONEY -> MoneySection(onAddSpend = actions.onQuickAdd)
            PlanArea.FITNESS -> FitnessSection(state.health, onOpenHealth = { actions.onRoute("health") })
            PlanArea.TRAVEL -> TravelSection(onOpenTrip = { actions.onRoute("v4_trip/$it") })
            PlanArea.MIND -> MindCard(state.health, onJournal = { actions.onRoute("journal_wizard") }, onJournalList = { actions.onRoute("journal") })
            PlanArea.STUDY -> StudySection(onOpenFocus = { actions.onRoute("focus_setup") })
            PlanArea.MEALS -> MealsSection(onAskCoach = actions.onAskCoach)
            else -> {}
        }

        Section("Plans", if (state.plans.isEmpty()) "Plans are bigger things with steps, like \"Run a 5K by December\"." else null)
        if (state.plans.isNotEmpty()) {
            V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
                state.plans.forEachIndexed { i, g ->
                    if (i > 0) V4Divider()
                    val done = g.milestones.count { it.isCompleted }
                    val total = g.milestones.size
                    Column(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { actions.onOpenGoal(g.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OneLine(g.title, V4.type.bodyStrong, if (g.status == GoalStatus.COMPLETED) c.ink3 else c.ink)
                        Text(
                            when {
                                g.status == GoalStatus.COMPLETED -> "Done"
                                total == 0 -> "No steps yet"
                                else -> "$done of $total steps" + (g.milestones.firstOrNull { !it.isCompleted }?.let { ". Next: ${it.title}" } ?: "")
                            },
                            style = V4.type.caption,
                            color = c.ink2,
                            maxLines = 2,
                        )
                        if (total > 0) V4ProgressBar(done.toFloat() / total, ac.color, height = 6.dp)
                    }
                }
            }
        }
        V4PillButton("New plan", onClick = actions.onNewPlan, filled = false)

        Section("Routines", if (state.routines.isEmpty()) "Routines are the small things you repeat, like \"10 minutes of stretching\"." else null)
        if (state.routines.isNotEmpty()) {
            V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
                state.routines.forEachIndexed { i, (habit, done) ->
                    if (i > 0) V4Divider()
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { actions.onOpenHabit(habit.id) }.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            OneLine(habit.title, V4.type.bodyStrong, if (done) c.ink3 else c.ink)
                            OneLine(V4TodayViewModel.habitMeta(habit, done, 0), V4.type.caption, c.ink3)
                        }
                        CheckCircleButton(done, (if (done) "Undo: " else "Mark done: ") + habit.title, { viewModel.toggleRoutine(habit, done) }, color = ac.color)
                    }
                }
            }
        }
        V4PillButton("New routine", onClick = actions.onNewRoutine, filled = false)
    }
}

@Composable
private fun Section(title: String, hint: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
        Text(title, style = V4.type.headline, color = V4.colors.ink, modifier = Modifier.semantics { heading() })
        if (hint != null) Text(hint, style = V4.type.caption, color = V4.colors.ink2)
    }
}

@Composable
private fun MindCard(h: AreaHealth, onJournal: () -> Unit, onJournalList: () -> Unit) {
    val c = V4.colors
    V4Card {
        Text("Sleep", style = V4.type.bodyStrong, color = c.ink)
        if (h.sleepNights.isEmpty()) {
            Text("Sleep comes in from Health once it is connected.", style = V4.type.caption, color = c.ink2)
        } else {
            h.sleepNights.take(5).forEach { (date, hours) ->
                StatRow("${date.day}/${date.month.ordinal + 1}", null, V4TodayViewModel.formatHours(hours))
            }
        }
    }
    V4Card {
        Text("Journal", style = V4.type.bodyStrong, color = c.ink)
        Text("A few lines a day. Your mood here tells the coach how you are really doing.", style = V4.type.caption, color = c.ink2)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            V4PillButton("Write", onClick = onJournal)
            V4PillButton("All entries", onClick = onJournalList, filled = false)
        }
    }
}

@Composable
private fun StatRow(label: String, sub: String?, value: String) {
    val c = V4.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = V4.type.body, color = c.ink)
            if (sub != null) Text(sub, style = V4.type.caption.copy(fontSize = V4.type.micro.fontSize), color = c.ink3)
        }
        Text(value, style = V4.type.bodyStrong, color = c.ink)
    }
}
