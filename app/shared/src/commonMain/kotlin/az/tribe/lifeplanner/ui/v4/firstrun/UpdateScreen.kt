package az.tribe.lifeplanner.ui.v4.firstrun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.ui.v4.components.LifePlannerMark
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.components.areaName
import az.tribe.lifeplanner.ui.v4.illustration.AreaIllustration
import az.tribe.lifeplanner.ui.v4.theme.V4
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.Check

/**
 * The first screen an existing user sees after updating. It answers "where did my stuff go?"
 * before anything else, then shows the areas already switched on from what they used.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateScreen(
    viewModel: V4FirstRunViewModel,
    onChangeAreas: () -> Unit,
    onOpenToday: () -> Unit,
) {
    val kept by viewModel.kept.collectAsState()
    val areas by viewModel.enabledAreas.collectAsState()

    LaunchedEffect(Unit) { viewModel.carryOverHealth() }
    LaunchedEffect(kept?.suggested) { kept?.suggested?.let { viewModel.applySuggestedIfUnset(it) } }

    Column(Modifier.fillMaxSize().background(V4.colors.background).statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LifePlannerMark()
                Text("LifePlanner", style = V4.type.headline.copy(fontSize = V4.type.headline.fontSize * 1.15f), color = V4.colors.ink)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("LifePlanner grew up, and got simpler.", style = V4.type.title.copy(fontSize = V4.type.title.fontSize * 1.2f, lineHeight = V4.type.title.lineHeight * 1.2f), color = V4.colors.ink)
                Text("Same account, same data. Here is where everything lives now.", style = V4.type.body, color = V4.colors.ink2)
            }
            V4Card(verticalSpacing = 10.dp) {
                Text("Everything you had is still here", style = V4.type.bodyStrong, color = V4.colors.ink)
                keptLines(kept).forEach { line -> KeptRow(line) }
            }
            V4Card {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Your areas, set from what you used", style = V4.type.bodyStrong, color = V4.colors.ink)
                    Text("You can add Money, Travel, Study and more whenever you like.", style = V4.type.caption, color = V4.colors.ink2)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanArea.entries.filter { it in areas }.forEach { AreaPill(it) }
                }
                V4TextButton("Change areas", onClick = onChangeAreas, modifier = Modifier.padding(start = 0.dp))
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            V4PrimaryButton("Open Today", onClick = onOpenToday, modifier = Modifier.fillMaxWidth())
            Text(
                "Tools you used before, like Wheel of life, are under You.",
                style = V4.type.caption,
                color = V4.colors.ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun keptLines(kept: KeptData?): List<String> {
    if (kept == null) return listOf("Checking what you have...")
    val lines = buildList {
        if (kept.goals > 0) add(plural(kept.goals, "goal") + if (kept.steps > 0) " and all their steps" else "")
        if (kept.habits > 0) add(plural(kept.habits, "habit") + " and every check-in")
        if (kept.journal > 0) add(plural(kept.journal, "journal entry", "journal entries"))
        if (kept.chats > 0) add("Your coach chats")
    }
    return lines.ifEmpty { listOf("Your account and settings") }
}

private fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

@Composable
private fun KeptRow(text: String) {
    val c = V4.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(c.successSoft), contentAlignment = Alignment.Center) {
            Icon(PhosphorIcons.Bold.Check, contentDescription = null, tint = c.success, modifier = Modifier.size(14.dp))
        }
        Text(text, style = V4.type.body, color = c.ink)
    }
}

@Composable
private fun AreaPill(area: PlanArea) {
    val c = V4.colors.area(area)
    Row(
        Modifier.clip(RoundedCornerShape(22.dp)).background(c.soft).padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AreaIllustration(area, size = 32.dp)
        Text(areaName(area), style = V4.type.label.copy(fontSize = V4.type.body.fontSize * 0.93f), color = c.ink)
    }
}
