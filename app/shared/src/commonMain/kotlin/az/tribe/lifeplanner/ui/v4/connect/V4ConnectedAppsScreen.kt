package az.tribe.lifeplanner.ui.v4.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.data.integrations.DataFlow
import az.tribe.lifeplanner.data.integrations.FlowDirection
import az.tribe.lifeplanner.data.integrations.IntegrationPrefs
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.ui.calendar.CalendarPermissionState
import az.tribe.lifeplanner.ui.calendar.rememberCalendarPermission
import az.tribe.lifeplanner.ui.health.rememberHealthPermissionLauncher
import az.tribe.lifeplanner.ui.v4.components.V4BackLink
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4Divider
import az.tribe.lifeplanner.ui.v4.components.V4PillButton
import az.tribe.lifeplanner.ui.v4.components.V4Switch
import az.tribe.lifeplanner.ui.v4.theme.V4
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.adamglin.phosphoricons.regular.ArrowRight
import com.adamglin.phosphoricons.regular.ArrowsLeftRight
import com.adamglin.phosphoricons.regular.CalendarBlank
import com.adamglin.phosphoricons.regular.Heartbeat
import org.koin.compose.koinInject

/** "Choose what flows in and what flows out." One switch per kind of data, grouped by app. */
@Composable
fun V4ConnectedAppsScreen(
    onBack: () -> Unit,
    onCalendars: () -> Unit,
    prefs: IntegrationPrefs = koinInject(),
) {
    val snap by prefs.state.collectAsState()
    val c = V4.colors
    val requestHealth = rememberHealthPermissionLauncher { granted -> prefs.setHealth(granted) }
    val calendar = rememberCalendarPermission()
    val calendarLive = snap.calendar && calendar.state == CalendarPermissionState.GRANTED

    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        V4BackLink("You", onBack)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Connected apps", style = V4.type.title, color = c.ink, modifier = Modifier.semantics { heading() })
            Text("Choose what flows in and what flows out.", style = V4.type.body, color = c.ink2)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(PhosphorIcons.Regular.ArrowsLeftRight, "Both ways")
            Legend(PhosphorIcons.Regular.ArrowLeft, "Comes in")
            Legend(PhosphorIcons.Regular.ArrowRight, "Goes out")
        }

        val fitness = c.area(PlanArea.FITNESS)
        Group(
            icon = PhosphorIcons.Regular.Heartbeat, tint = fitness.soft, ink = fitness.ink,
            name = "Health", sub = if (snap.health) "Health Connect or Apple Health" else "Not connected",
            connected = snap.health,
            onConnect = requestHealth,
            flows = DataFlow.entries.filter { it.source == DataFlow.Source.HEALTH },
            snap = snap,
            onFlow = prefs::setFlow,
        )
        val habits = c.area(PlanArea.HABITS)
        Group(
            icon = PhosphorIcons.Regular.CalendarBlank, tint = habits.soft, ink = habits.ink,
            name = "Calendar", sub = if (calendarLive) "Plans become events, events shape Today" else "Not connected",
            connected = calendarLive,
            onConnect = { prefs.setCalendar(true); if (calendar.state != CalendarPermissionState.GRANTED) calendar.request() },
            flows = DataFlow.entries.filter { it.source == DataFlow.Source.CALENDAR },
            snap = snap,
            onFlow = prefs::setFlow,
            extra = if (calendarLive) ({ V4PillButton("Choose calendars", onClick = onCalendars, filled = false) }) else null,
        )
    }
}

@Composable
private fun Legend(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = V4.colors.ink2, modifier = Modifier.size(16.dp))
        Text(label, style = V4.type.caption, color = V4.colors.ink2)
    }
}

@Composable
private fun Group(
    icon: ImageVector,
    tint: Color,
    ink: Color,
    name: String,
    sub: String,
    connected: Boolean,
    onConnect: () -> Unit,
    flows: List<DataFlow>,
    snap: IntegrationPrefs.Snapshot,
    onFlow: (DataFlow, Boolean) -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val c = V4.colors
    V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = V4.type.headline, color = c.ink)
                Text(sub, style = V4.type.caption, color = c.ink3)
            }
            if (!connected) V4PillButton("Connect", onClick = onConnect)
        }
        if (connected) {
            flows.forEach { f ->
                V4Divider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 14.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(c.surfaceMuted), contentAlignment = Alignment.Center) {
                        Icon(
                            when (f.direction) {
                                FlowDirection.BOTH -> PhosphorIcons.Regular.ArrowsLeftRight
                                FlowDirection.IN -> PhosphorIcons.Regular.ArrowLeft
                                FlowDirection.OUT -> PhosphorIcons.Regular.ArrowRight
                            },
                            contentDescription = null,
                            tint = c.ink2,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(f.label, style = V4.type.bodyStrong, color = c.ink)
                        Text(f.note, style = V4.type.caption.copy(fontSize = V4.type.micro.fontSize), color = c.ink3)
                    }
                    val on = snap.isOn(f)
                    V4Switch(on, { onFlow(f, it) }, (if (on) "Stop syncing " else "Sync ") + f.label)
                }
            }
            if (extra != null) {
                V4Divider()
                Box(Modifier.padding(14.dp)) { extra() }
            }
        }
    }
}
