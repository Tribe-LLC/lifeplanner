package az.tribe.lifeplanner.ui.v4.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.core.CurrencyPrefs
import az.tribe.lifeplanner.data.plans.PlanState
import az.tribe.lifeplanner.data.plans.PlanView
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.PlanScheduler
import az.tribe.lifeplanner.domain.service.PlanTemplates
import az.tribe.lifeplanner.ui.v4.areas.Choice
import az.tribe.lifeplanner.ui.v4.components.OneLine
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4Divider
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.components.areaName
import az.tribe.lifeplanner.ui.v4.theme.V4
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.compose.koinInject
import kotlin.time.Clock

/**
 * An area page's plans: each with its next step, pace and progress; finished and let go ones
 * folded behind one link; then New plan and three ideas that open the sheet already filled in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AreaPlansList(
    area: PlanArea,
    plans: List<PlanView>,
    done: List<PlanView>,
    letGo: List<PlanView>,
    onOpen: (String) -> Unit,
    onNewPlan: (String) -> Unit,
) {
    val c = V4.colors
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val currency = koinInject<CurrencyPrefs>().code
    var showPast by remember { mutableStateOf(false) }

    if (plans.isNotEmpty()) {
        V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            plans.forEachIndexed { i, v ->
                if (i > 0) V4Divider()
                PlanRow(v, today) { onOpen(v.id) }
            }
        }
    }
    PlanPageModel.doneLink(done.size, letGo.size)?.let { link ->
        V4TextButton(if (showPast) "Hide $link" else link, onClick = { showPast = !showPast }, color = c.ink2)
        if (showPast) {
            V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
                (done + letGo).forEachIndexed { i, v ->
                    if (i > 0) V4Divider()
                    PastRow(v) { onOpen(v.id) }
                }
            }
        }
    }
    V4PillButton("New plan", onClick = { onNewPlan("") }, filled = false)
    Text("Ideas for ${areaName(area)}, one tap each", style = V4.type.caption, color = c.ink2)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlanTemplates.ideas(area, currency).forEach { idea -> Choice(idea, false) { onNewPlan(idea) } }
    }
}

@Composable
private fun PlanRow(v: PlanView, today: LocalDate, onClick: () -> Unit) {
    val c = V4.colors
    Column(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OneLine(v.title, V4.type.bodyStrong, c.ink, Modifier.weight(1f))
            PacePill(v.pace, v.area)
        }
        Text(PlanPageModel.listNext(v, today), style = V4.type.caption, color = c.ink2, maxLines = 2)
        if (v.steps.isNotEmpty()) PaceBar(v.progress.fraction, v.pace.expected.takeIf { v.state == PlanState.ACTIVE }, c.area(v.area).color, height = 6.dp)
        v.progress.source?.let { Text(it, style = V4.type.caption, color = c.ink3, maxLines = 2) }
    }
}

@Composable
private fun PastRow(v: PlanView, onClick: () -> Unit) {
    val c = V4.colors
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OneLine(v.title, V4.type.bodyStrong, c.ink2, Modifier.weight(1f))
        Text(
            if (v.state == PlanState.DONE) v.spec?.finished?.let { "Done ${PlanScheduler.dayLabel(it)}" } ?: "Done" else "Let go",
            style = V4.type.caption, color = c.ink3,
        )
    }
}
