package az.tribe.lifeplanner.ui.v4.firstrun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import az.tribe.lifeplanner.data.integrations.IntegrationPrefs
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.ui.calendar.CalendarPermissionState
import az.tribe.lifeplanner.ui.calendar.rememberCalendarPermission
import az.tribe.lifeplanner.ui.health.rememberHealthPermissionLauncher
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4IconButton
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.components.V4Switch
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.theme.V4
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowsLeftRight
import com.adamglin.phosphoricons.regular.Bell
import com.adamglin.phosphoricons.regular.CalendarBlank
import com.adamglin.phosphoricons.regular.CaretLeft
import com.adamglin.phosphoricons.regular.Clock
import com.adamglin.phosphoricons.regular.Heartbeat
import com.mmk.kmpnotifier.notification.NotifierManager
import org.koin.compose.koinInject

private enum class Ask { NONE, HEALTH, CALENDAR, CALENDAR_WAIT, REMINDERS, DONE }

/**
 * "Connect once. Stay in sync." The switches record intent; the platform dialogs are asked in a
 * row only when the user taps the main button, one after another, so nothing pops up while they
 * are still reading.
 */
@Composable
fun ConnectScreen(
    stepLabel: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    prefs: IntegrationPrefs = koinInject(),
) {
    var health by remember { mutableStateOf(true) }
    var calendar by remember { mutableStateOf(true) }
    var reminders by remember { mutableStateOf(true) }
    var ask by remember { mutableStateOf(Ask.NONE) }

    val calendarPermission = rememberCalendarPermission()
    val requestHealth = rememberHealthPermissionLauncher { granted ->
        prefs.setHealth(granted)
        ask = Ask.CALENDAR
    }

    LaunchedEffect(ask) {
        when (ask) {
            Ask.NONE -> {}
            Ask.HEALTH -> if (health) requestHealth() else { prefs.setHealth(false); ask = Ask.CALENDAR }
            Ask.CALENDAR -> {
                prefs.setCalendar(calendar)
                if (calendar && calendarPermission.state != CalendarPermissionState.GRANTED) {
                    ask = Ask.CALENDAR_WAIT
                    calendarPermission.request()
                } else {
                    ask = Ask.REMINDERS
                }
            }
            // Advanced by the grant result, or by coming back to the screen after a refusal
            // (a refusal leaves the state as it was, so the state alone cannot tell).
            Ask.CALENDAR_WAIT -> {}
            Ask.REMINDERS -> {
                prefs.setReminders(reminders)
                if (reminders) runCatching { NotifierManager.getPermissionUtil().askNotificationPermission() }
                ask = Ask.DONE
            }
            Ask.DONE -> onDone()
        }
    }

    LaunchedEffect(calendarPermission.state) {
        if (ask == Ask.CALENDAR_WAIT && calendarPermission.state == CalendarPermissionState.GRANTED) ask = Ask.REMINDERS
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && ask == Ask.CALENDAR_WAIT) ask = Ask.REMINDERS
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier.fillMaxSize().background(V4.colors.background).statusBarsPadding().navigationBarsPadding(),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                V4IconButton(PhosphorIcons.Regular.CaretLeft, "Back", onBack)
                if (stepLabel != null) Text(stepLabel, style = V4.type.label, color = V4.colors.ink3)
                Spacer(Modifier.size(44.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Connect once. Stay in sync.", style = V4.type.title, color = V4.colors.ink)
                Text(
                    "LifePlanner reads and writes, so you never log the same thing twice.",
                    style = V4.type.body,
                    color = V4.colors.ink2,
                )
            }
            val fitness = V4.colors.area(PlanArea.FITNESS)
            val habits = V4.colors.area(PlanArea.HABITS)
            val mind = V4.colors.area(PlanArea.MIND)
            ConnectCard(
                icon = PhosphorIcons.Regular.Heartbeat, tint = fitness.soft, ink = fitness.ink,
                name = "Health", direction = "Both ways", on = health, onToggle = { health = it },
                body = "Workouts, water, sleep, weight and mindful minutes sync both ways. Steps and heart rate come in. Health Connect on Android, Apple Health on iPhone.",
            )
            ConnectCard(
                icon = PhosphorIcons.Regular.CalendarBlank, tint = habits.soft, ink = habits.ink,
                name = "Calendar", direction = "Both ways", on = calendar, onToggle = { calendar = it },
                body = "Trips and study blocks become events. Your meetings shape what Today suggests.",
            )
            ConnectCard(
                icon = PhosphorIcons.Regular.Bell, tint = mind.soft, ink = mind.ink,
                name = "Reminders", direction = "At the times you pick", directionIcon = PhosphorIcons.Regular.Clock, on = reminders, onToggle = { reminders = it },
                body = "One gentle nudge per plan. Quiet hours respected.",
            )
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            V4PrimaryButton(
                text = "Continue",
                onClick = { if (ask == Ask.NONE) ask = Ask.HEALTH },
                modifier = Modifier.fillMaxWidth(),
            )
            V4TextButton(
                "Skip for now",
                onClick = {
                    prefs.setHealth(false); prefs.setCalendar(false); prefs.setReminders(false)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ConnectCard(
    icon: ImageVector,
    tint: Color,
    ink: Color,
    name: String,
    direction: String,
    directionIcon: ImageVector = PhosphorIcons.Regular.ArrowsLeftRight,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    body: String,
) {
    V4Card(verticalSpacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(tint), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(name, style = V4.type.bodyStrong.copy(fontSize = V4.type.headline.fontSize * 0.95f), color = V4.colors.ink)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(directionIcon, contentDescription = null, tint = ink, modifier = Modifier.size(15.dp))
                    Text(direction, style = V4.type.label, color = ink)
                }
            }
            V4Switch(checked = on, onCheckedChange = onToggle, label = name)
        }
        Text(body, style = V4.type.caption.copy(fontSize = V4.type.body.fontSize * 0.93f), color = V4.colors.ink2)
    }
}
