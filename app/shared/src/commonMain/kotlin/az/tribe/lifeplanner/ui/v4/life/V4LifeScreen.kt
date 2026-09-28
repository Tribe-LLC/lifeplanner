package az.tribe.lifeplanner.ui.v4.life

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.ui.v4.components.OneLine
import az.tribe.lifeplanner.ui.v4.components.Sparkline
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4Divider
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.components.areaName
import az.tribe.lifeplanner.ui.v4.illustration.AreaIllustration
import az.tribe.lifeplanner.ui.v4.theme.V4
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun V4LifeScreen(
    onOpenArea: (PlanArea) -> Unit,
    onChangeAreas: () -> Unit,
    bottomInset: PaddingValues,
    viewModel: V4LifeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val c = V4.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = bottomInset.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Your whole life, one view", style = V4.type.label.copy(fontSize = V4.type.body.fontSize * 0.93f), color = c.ink3)
                Text("Life", style = V4.type.display, color = c.ink, modifier = Modifier.semantics { heading() })
            }
            RangeToggle(state.range, viewModel::setRange)
        }

        V4Card(contentPadding = PaddingValues(18.dp), verticalSpacing = 14.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(state.score, style = V4.type.number.copy(fontSize = V4.type.number.fontSize * 1.5f, lineHeight = V4.type.number.lineHeight * 1.5f), color = c.ink)
                    Text(state.scoreCaption, style = V4.type.caption, color = c.ink2)
                }
                state.delta?.let {
                    Text(
                        it,
                        style = V4.type.label,
                        color = if (state.deltaUp) c.success else c.ink2,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (state.deltaUp) c.successSoft else c.surfaceMuted).padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
            if (state.bars.isNotEmpty()) WeekBars(state.bars)
        }

        Text("Your areas", style = V4.type.headline, color = c.ink, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
        state.areas.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { a -> AreaCard(a, onClick = { onOpenArea(a.area) }, modifier = Modifier.weight(1f)) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
        V4TextButton("Change areas", onClick = onChangeAreas)

        if (state.recent.isNotEmpty()) {
            Text("Recent", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
            V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
                state.recent.forEachIndexed { i, r ->
                    if (i > 0) V4Divider()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AreaIllustration(r.area, size = 36.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            OneLine(r.title, V4.type.bodyStrong, c.ink)
                            OneLine(r.meta, V4.type.caption.copy(fontSize = V4.type.micro.fontSize), c.ink3)
                        }
                        Text(r.whenLabel, style = V4.type.micro, color = c.ink3)
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeToggle(range: LifeRange, onPick: (LifeRange) -> Unit) {
    val c = V4.colors
    Row(Modifier.clip(RoundedCornerShape(16.dp)).background(c.surfaceMuted).padding(3.dp)) {
        listOf(LifeRange.WEEK to "Week", LifeRange.MONTH to "Month").forEach { (r, label) ->
            val on = r == range
            Box(
                Modifier
                    .heightIn(min = 38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (on) c.surface else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(role = Role.Tab) { onPick(r) }
                    .semantics { selected = on }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = V4.type.label.copy(fontSize = V4.type.body.fontSize * 0.93f), color = c.ink) }
        }
    }
}

@Composable
private fun WeekBars(bars: List<LifeBar>) {
    val c = V4.colors
    Row(
        Modifier.fillMaxWidth().height(84.dp).semantics(mergeDescendants = true) {
            contentDescription = bars.joinToString { b -> "${b.label} ${b.fraction?.let { "${(it * 100).toInt()}%" } ?: "not yet"}" }
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        bars.forEach { b ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom)) {
                val f = b.fraction
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((6 + 56 * (f ?: 0f)).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                f == null -> c.surfaceMuted
                                b.current -> c.accent.copy(alpha = 0.4f)
                                else -> c.accent
                            },
                        ),
                )
                Text(b.label, style = V4.type.micro.copy(fontSize = V4.type.micro.fontSize * 0.92f), color = c.ink3)
            }
        }
    }
}

@Composable
private fun AreaCard(a: AreaSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = V4.colors
    val ac = c.area(a.area)
    Column(
        modifier
            .heightIn(min = 172.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AreaIllustration(a.area, size = 44.dp)
            if (a.trend.size >= 2 && a.trend.any { it > 0f }) Sparkline(a.trend, ac.color)
        }
        Text(areaName(a.area), style = V4.type.label.copy(fontSize = V4.type.body.fontSize * 0.93f), color = ac.ink)
        Text(a.stat, style = V4.type.title, color = c.ink, maxLines = 1)
        Text(a.caption, style = V4.type.caption, color = c.ink2, maxLines = 3)
    }
}
