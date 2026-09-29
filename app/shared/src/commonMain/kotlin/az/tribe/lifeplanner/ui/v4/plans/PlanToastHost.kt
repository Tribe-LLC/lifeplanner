package az.tribe.lifeplanner.ui.v4.plans

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.data.plans.PlanEvent
import az.tribe.lifeplanner.data.plans.PlanMaker
import az.tribe.lifeplanner.ui.v4.theme.V4
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * Short lines about plans, over whatever screen is open: a step ticked by a run, the next step
 * after a tick, a plan finished. One at a time, a few seconds each; tapping one opens its plan.
 */
@Composable
fun BoxScope.PlanToastHost(bottom: Dp, openPlanId: String?, onOpen: (String) -> Unit, maker: PlanMaker = koinInject()) {
    val skip by rememberUpdatedState(openPlanId)
    val queue = remember { Channel<PlanEvent>(capacity = 4, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST) }
    var shown by remember { mutableStateOf<PlanEvent?>(null) }
    LaunchedEffect(maker) { maker.events.collect { queue.trySend(it) } }
    LaunchedEffect(queue) {
        for (e in queue) {
            if (e.goalId == skip) continue
            shown = e
            delay(if (e.finished) 5_000 else 3_500)
            shown = null
            delay(300)
        }
    }
    AnimatedVisibility(
        visible = shown != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = bottom),
    ) {
        val e = shown ?: return@AnimatedVisibility
        val c = V4.colors
        Box(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.inverse)
                .clickable(role = Role.Button) { shown = null; onOpen(e.goalId) }
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(e.text, style = V4.type.body, color = c.onInverse)
        }
    }
}
