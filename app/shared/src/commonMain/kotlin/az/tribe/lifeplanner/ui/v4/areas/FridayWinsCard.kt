package az.tribe.lifeplanner.ui.v4.areas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.career.CareerService
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.PlanAreasRepository
import az.tribe.lifeplanner.domain.service.CareerPlanner
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.theme.V4
import az.tribe.lifeplanner.ui.v4.travel.TravelField
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import org.koin.compose.koinInject

/** Whether Today shows "Any wins this week?", and whether one was just saved from it. */
@Stable
class FridayWins internal constructor(private val due: Boolean, savedState: MutableState<Boolean>, internal val career: CareerService) {
    internal var saved by savedState
    val visible: Boolean get() = due || saved
}

/**
 * Fridays from 15:00, while Career is one of the user's areas and the week has not had its answer:
 * Today asks for the week's wins. Everything lives here; Today only places the card.
 */
@Composable
fun rememberFridayWins(): FridayWins {
    val career: CareerService = koinInject()
    val areas: PlanAreasRepository = koinInject()
    val closed by career.fridayClosed.collectAsState()
    val enabled by areas.enabledAreas.collectAsState()
    var now by remember { mutableStateOf(localNow()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = localNow() } }
    val saved = remember { mutableStateOf(false) }
    return FridayWins(PlanArea.CAREER in enabled && CareerPlanner.showFridayWins(now, closed), saved, career)
}

@Composable
fun FridayWinsCard(state: FridayWins, modifier: Modifier = Modifier) {
    val c = V4.colors
    val tint = c.area(PlanArea.CAREER)
    val scope = rememberCoroutineScope()
    var what by remember { mutableStateOf("") }
    if (state.saved) {
        V4Card(modifier.fillMaxWidth(), color = c.successSoft, bordered = false, verticalSpacing = 4.dp) {
            Text("Saved to your wins", style = V4.type.bodyStrong, color = c.success)
            Text("Nice week. It is in Career, ready for your next review.", style = V4.type.caption, color = c.success)
        }
        return
    }
    V4Card(modifier.fillMaxWidth(), color = tint.soft, bordered = false, verticalSpacing = 10.dp) {
        Text("Any wins this week?", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
        Text("One line is enough. Small ones count too.", style = V4.type.caption, color = c.ink2)
        TravelField(what, { what = it }, "Shipped the new login", "A win this week")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            V4TextButton("Not this week", onClick = {
                state.career.closeFriday()
                PostHogAnalytics.capture("v4_career_friday_dismissed")
            }, color = c.ink2)
            V4PillButton("Save", onClick = {
                val line = what.trim()
                if (line.isEmpty()) return@V4PillButton
                state.saved = true
                scope.launch {
                    runCatching { state.career.logWin(line, null) }
                    state.career.closeFriday()
                }
                PostHogAnalytics.capture("v4_career_friday_win")
            }, container = tint.color)
        }
    }
}

private fun localNow(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
