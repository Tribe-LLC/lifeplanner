package az.tribe.lifeplanner.ui.v4.firstrun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.ui.v4.components.V4IconButton
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.components.areaBlurb
import az.tribe.lifeplanner.ui.v4.components.areaName
import az.tribe.lifeplanner.ui.v4.illustration.AreaIllustration
import az.tribe.lifeplanner.ui.v4.theme.V4
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.Check
import com.adamglin.phosphoricons.regular.CaretLeft

/**
 * "What do you want to plan?" Only the areas picked here appear in the app. Also reached later from
 * Life ("Change areas"), in which case [stepLabel] is null and [ctaPrefix] reads "Save".
 */
@Composable
fun AreasScreen(
    initial: Set<PlanArea>,
    stepLabel: String?,
    onBack: (() -> Unit)?,
    onContinue: (Set<PlanArea>) -> Unit,
    ctaPrefix: String = "Continue with",
) {
    var picked by remember { mutableStateOf(initial) }
    Column(
        Modifier
            .fillMaxSize()
            .background(V4.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                if (onBack != null) {
                    V4IconButton(PhosphorIcons.Regular.CaretLeft, "Back", onBack)
                } else {
                    Spacer(Modifier.size(44.dp))
                }
                if (stepLabel != null) Text(stepLabel, style = V4.type.label, color = V4.colors.ink3)
                Spacer(Modifier.size(44.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("What do you want to plan?", style = V4.type.title.copy(fontSize = V4.type.title.fontSize * 1.15f), color = V4.colors.ink)
                Text(
                    "Pick as many as you like. Only these show up in your app, and you can change them any time.",
                    style = V4.type.body,
                    color = V4.colors.ink2,
                )
            }
            PlanArea.entries.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { area ->
                        val on = area in picked
                        AreaPickCard(
                            area = area,
                            selected = on,
                            // Habits is the everyday area every user has; it cannot be switched off.
                            locked = area == PlanArea.HABITS,
                            onToggle = { picked = if (on) picked - area else picked + area },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        val count = (picked + PlanArea.HABITS).size
        V4PrimaryButton(
            text = "$ctaPrefix $count ${if (count == 1) "area" else "areas"}",
            onClick = { onContinue(picked + PlanArea.HABITS) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun AreaPickCard(
    area: PlanArea,
    selected: Boolean,
    locked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .heightIn(min = 140.dp)
            .clip(shape)
            .background(V4.colors.surface)
            .border(2.dp, if (selected || locked) V4.colors.ink else V4.colors.line, shape)
            .clickable(enabled = !locked, role = Role.Checkbox, onClick = onToggle)
            .semantics { stateDescription = if (locked) "Always on" else if (selected) "Selected" else "Not selected" }
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AreaIllustration(area, size = 56.dp)
            Text(areaName(area), style = V4.type.bodyStrong.copy(fontSize = V4.type.headline.fontSize * 0.95f), color = V4.colors.ink)
            Text(if (locked) "Always on" else areaBlurb(area), style = V4.type.caption, color = V4.colors.ink2)
        }
        if (selected || locked) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(V4.colors.ink),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PhosphorIcons.Bold.Check, contentDescription = null, tint = if (V4.colors.isDark) Color.Black else Color.White, modifier = Modifier.size(15.dp))
            }
        }
    }
}
