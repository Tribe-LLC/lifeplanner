package az.tribe.lifeplanner.ui.v4.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.ui.viewmodel.AuthState
import az.tribe.lifeplanner.ui.v4.components.OneLine
import az.tribe.lifeplanner.ui.v4.components.V4BackLink
import az.tribe.lifeplanner.ui.v4.components.V4Card
import az.tribe.lifeplanner.ui.v4.components.V4Divider
import az.tribe.lifeplanner.ui.v4.theme.V4
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.User

private data class YouRow(val title: String, val sub: String?, val route: String)

private val tools = listOf(
    YouRow("All goals", "Every plan, in one list", "goals_redesign"),
    YouRow("All habits", "Streaks and history", "habit_tracker"),
    YouRow("Journal", null, "journal"),
    YouRow("Wheel of life", null, "wheel_of_life"),
    YouRow("Focus timer", null, "focus_setup"),
    YouRow("Learn", "Short reads on habits and planning", "learn_hub"),
    YouRow("Achievements", null, "achievements"),
)

/**
 * "You": the account, connected apps, areas, and every v3 tool that has no home in the three
 * tabs. The update screen promises that tools like Wheel of life live here.
 */
@Composable
fun V4YouScreen(
    authState: AuthState,
    onBack: () -> Unit,
    onConnectedApps: () -> Unit,
    onChangeAreas: () -> Unit,
    onRoute: (String) -> Unit,
) {
    val c = V4.colors
    val user = (authState as? AuthState.Authenticated)?.user ?: (authState as? AuthState.Guest)?.user
    val isGuest = authState is AuthState.Guest

    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        V4BackLink("Today", onBack)
        Text("You", style = V4.type.display, color = c.ink, modifier = Modifier.semantics { heading() })

        V4Card(onClick = { onRoute("profile") }) {
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
                        if (isGuest) "Create an account to keep your data safe on every phone" else user?.email ?: "Profile and account",
                        style = V4.type.caption,
                        color = c.ink2,
                    )
                }
                Icon(PhosphorIcons.Regular.CaretRight, contentDescription = null, tint = c.ink3, modifier = Modifier.size(18.dp))
            }
        }

        RowsCard(
            listOf(
                YouRow("Connected apps", "Health and Calendar, both ways", "connected"),
                YouRow("Your areas", "Choose what shows up in the app", "areas"),
                YouRow("Settings", "Theme, reminders, backup", "settings"),
            ),
        ) { row ->
            when (row.route) {
                "connected" -> onConnectedApps()
                "areas" -> onChangeAreas()
                else -> onRoute(row.route)
            }
        }

        Text("Tools you had before", style = V4.type.headline, color = c.ink, modifier = Modifier.semantics { heading() })
        RowsCard(tools) { onRoute(it.route) }
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
