package az.tribe.lifeplanner.ui.v4.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.BuildKonfig
import az.tribe.lifeplanner.core.PremiumGate
import az.tribe.lifeplanner.data.habits.NudgePrefs
import az.tribe.lifeplanner.data.habits.NudgeService
import az.tribe.lifeplanner.data.sync.SyncState
import az.tribe.lifeplanner.domain.service.HabitLearning
import az.tribe.lifeplanner.ui.theme.ThemeController
import az.tribe.lifeplanner.ui.theme.ThemeMode
import az.tribe.lifeplanner.ui.v4.areas.AreaSheet
import az.tribe.lifeplanner.ui.v4.areas.Choice
import az.tribe.lifeplanner.ui.v4.components.OneLine
import az.tribe.lifeplanner.ui.v4.components.V4BackLink
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4Divider
import az.tribe.lifeplanner.ui.v4.components.V4PrimaryButton
import az.tribe.lifeplanner.ui.v4.components.V4Switch
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.shell.V4Routes
import az.tribe.lifeplanner.ui.v4.theme.V4
import az.tribe.lifeplanner.ui.v4.travel.TravelField
import az.tribe.lifeplanner.ui.viewmodel.AuthState
import az.tribe.lifeplanner.ui.viewmodel.AuthViewModel
import az.tribe.lifeplanner.ui.viewmodel.signOut
import az.tribe.lifeplanner.ui.viewmodel.updateDisplayName
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.User
import org.koin.compose.koinInject

private data class YouRow(val title: String, val sub: String?, val route: String)

/**
 * "You": the account, what the app connects to and nudges about, and how it looks. Kept short on
 * purpose. The v3 tools that used to hang off here moved into their areas (journal to Mind, focus
 * timer to Study) or were retired from v4 with their data kept.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun V4YouScreen(
    authState: AuthState,
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    onConnectedApps: () -> Unit,
    onChangeAreas: () -> Unit,
    onRoute: (String) -> Unit,
    nudgePrefs: NudgePrefs = koinInject(),
    nudges: NudgeService = koinInject(),
    themeController: ThemeController = koinInject(),
    premiumGate: PremiumGate = koinInject(),
) {
    val c = V4.colors
    val user = (authState as? AuthState.Authenticated)?.user ?: (authState as? AuthState.Guest)?.user
    val isGuest = authState is AuthState.Guest
    val sync by authViewModel.syncStatus.collectAsState()
    val nudge by nudgePrefs.state.collectAsState()
    val learned by nudges.learned.collectAsState()
    val theme by themeController.mode.collectAsState()
    var account by remember { mutableStateOf(false) }
    // Re-read on every visit, so coming back from the paywall after a purchase flips the row.
    var premium by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { premium = premiumGate.isPremium() }

    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        V4BackLink("Today", onBack)
        Text("You", style = V4.type.display, color = c.ink, modifier = Modifier.semantics { heading() })

        V4Card(onClick = { if (isGuest) onRoute("sign_in") else account = true }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
                    Icon(PhosphorIcons.Regular.User, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    OneLine(
                        if (isGuest) "Guest" else user?.displayName?.takeIf { it.isNotBlank() } ?: user?.email ?: "Your account",
                        V4.type.bodyStrong,
                        c.ink,
                    )
                    Text(
                        if (isGuest) "Create an account to keep your data safe on every phone" else syncLine(sync.state),
                        style = V4.type.caption,
                        color = c.ink2,
                    )
                }
                Icon(PhosphorIcons.Regular.CaretRight, contentDescription = null, tint = c.ink3, modifier = Modifier.size(18.dp))
            }
        }

        // Only when billing is configured. Without RevenueCat keys there is nothing to buy and the
        // row would be a dead end, so it simply is not there.
        if (premiumGate.billingAvailable && premium != null) {
            RowsCard(
                listOf(
                    if (premium == true) YouRow("LifePlanner Plus", "Active. Manage or restore your plan", "subscription")
                    else YouRow("Get LifePlanner Plus", "See the plans and what Plus adds", "plus"),
                ),
            ) { row -> onRoute(if (row.route == "subscription") V4Routes.SUBSCRIPTION else V4Routes.PLUS) }
        }

        RowsCard(
            listOf(
                YouRow("Connected apps", "Health and Calendar, both ways", "connected"),
                YouRow("Your areas", "Choose what shows up in the app", "areas"),
            ),
        ) { row -> if (row.route == "connected") onConnectedApps() else onChangeAreas() }

        Text("Nudges", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
        V4Card {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Evening check-in", style = V4.type.bodyStrong, color = c.ink)
                    Text(
                        if (nudge.eveningMinute == null) "When you usually check in, learned from your ticks. Only if something is still open."
                        else "At the time you picked. Only if something is still open.",
                        style = V4.type.caption, color = c.ink2,
                    )
                }
                V4Switch(nudge.evening, { nudgePrefs.setEvening(it); nudges.replan() }, label = "Evening check-in")
            }
            if (nudge.evening) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice(learned?.let { "Usual, ~${HabitLearning.clock(it)}" } ?: "Learn it, ${HabitLearning.clock(HabitLearning.DEFAULT_CHECK_IN)} for now", nudge.eveningMinute == null) {
                        nudgePrefs.setEveningMinute(null); nudges.replan()
                    }
                    listOf(20, 21, 22).forEach { h ->
                        Choice("$h:00", nudge.eveningMinute == h * 60) { nudgePrefs.setEveningMinute(h * 60); nudges.replan() }
                    }
                }
            }
            V4Divider()
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Slipped habits", style = V4.type.bodyStrong, color = c.ink)
                    Text("At most once a week, Saturday 10:00, only when something slipped.", style = V4.type.caption, color = c.ink2)
                }
                V4Switch(nudge.slipped, { nudgePrefs.setSlipped(it); nudges.replan() }, label = "Slipped habits")
            }
            Text(
                "Each habit's own reminder is set on the habit, and the daily mood reminder on Sleep and mind.",
                style = V4.type.caption, color = c.ink3,
            )
        }

        Text("App", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
        V4Card(modifier = Modifier.fillMaxWidth()) {
            Text("Look", style = V4.type.bodyStrong, color = c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    Choice(
                        when (m) { ThemeMode.SYSTEM -> "Like the phone"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" },
                        theme == m,
                    ) { themeController.setMode(m) }
                }
            }
        }
        RowsCard(
            listOf(
                YouRow("Habit reminders", "Quiet hours and how reminders behave", "reminders"),
                YouRow("Backup and export", "Save or restore everything", "backup_settings"),
                YouRow("Send feedback", "Tell us what to fix or add", "feedback"),
            ),
        ) { onRoute(it.route) }

        Text(
            "Life Planner ${BuildKonfig.APP_VERSION}",
            style = V4.type.caption, color = c.ink3, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }

    if (account) AccountSheet(authState, authViewModel, sync.state, onDismiss = { account = false })
}

private fun syncLine(state: SyncState): String = when (state) {
    SyncState.SYNCED, SyncState.IDLE -> "Synced to your account"
    SyncState.SYNCING -> "Syncing..."
    SyncState.OFFLINE -> "Offline, changes wait on this phone"
    SyncState.ERROR -> "Sync is stuck, it will try again"
}

/** Name, sync and sign out, without leaving v4. */
@Composable
private fun AccountSheet(authState: AuthState, authViewModel: AuthViewModel, sync: SyncState, onDismiss: () -> Unit) {
    val c = V4.colors
    val user = (authState as? AuthState.Authenticated)?.user
    var name by remember { mutableStateOf(user?.displayName.orEmpty()) }
    var confirm by remember { mutableStateOf(false) }
    AreaSheet("Your account", onDismiss) {
        user?.email?.let { Text(it, style = V4.type.body, color = c.ink2) }
        Text(syncLine(sync), style = V4.type.caption, color = c.ink3)
        TravelField(name, { name = it }, "Your name", "Name")
        V4PrimaryButton(
            "Save name",
            onClick = { authViewModel.updateDisplayName(name.trim()); onDismiss() },
            enabled = name.isNotBlank() && name.trim() != user?.displayName,
            modifier = Modifier.fillMaxWidth(),
        )
        V4TextButton("Sign out", onClick = { confirm = true }, color = c.ink2, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
    if (confirm) {
        val unsynced = sync == SyncState.OFFLINE || sync == SyncState.ERROR
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(if (unsynced) "Some changes are not saved yet" else "Sign out?") },
            text = {
                Text(
                    if (unsynced) "Recent changes have not reached your account. Signing out now could lose them. Try again when you are online."
                    else "Everything is synced. Sign back in any time to pick up where you left off."
                )
            },
            confirmButton = {
                V4TextButton(if (unsynced) "Sign out anyway" else "Sign out", onClick = { confirm = false; onDismiss(); authViewModel.signOut() })
            },
            dismissButton = { V4TextButton("Stay", onClick = { confirm = false }) },
        )
    }
}

@Composable
private fun RowsCard(rows: List<YouRow>, onClick: (YouRow) -> Unit) {
    val c = V4.colors
    V4Card(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
        rows.forEachIndexed { i, row ->
            if (i > 0) V4Divider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onClick(row) }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.title, style = V4.type.bodyStrong, color = c.ink)
                    row.sub?.let { Text(it, style = V4.type.caption.copy(fontSize = V4.type.micro.fontSize), color = c.ink3) }
                }
                Icon(PhosphorIcons.Regular.CaretRight, contentDescription = null, tint = c.ink3, modifier = Modifier.size(18.dp))
            }
        }
    }
}
