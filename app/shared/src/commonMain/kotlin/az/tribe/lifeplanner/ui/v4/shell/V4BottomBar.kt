package az.tribe.lifeplanner.ui.v4.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.ui.v4.theme.V4
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.bold.Plus
import com.adamglin.phosphoricons.fill.ChatCircle as ChatCircleFill
import com.adamglin.phosphoricons.fill.SquaresFour as SquaresFourFill
import com.adamglin.phosphoricons.fill.Sun as SunFill
import com.adamglin.phosphoricons.regular.ChatCircle
import com.adamglin.phosphoricons.regular.SquaresFour
import com.adamglin.phosphoricons.regular.Sun

private data class V4Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    V4Tab(V4Routes.TODAY, "Today", PhosphorIcons.Regular.Sun, PhosphorIcons.Fill.SunFill),
    V4Tab(V4Routes.LIFE, "Life", PhosphorIcons.Regular.SquaresFour, PhosphorIcons.Fill.SquaresFourFill),
    V4Tab(V4Routes.COACH, "Coach", PhosphorIcons.Regular.ChatCircle, PhosphorIcons.Fill.ChatCircleFill),
)

/** Three tabs, nothing else. Areas live on the Life tab; everything added goes through the add bar. */
@Composable
fun V4BottomBar(currentRoute: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(V4.colors.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(V4.colors.line))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                val tint = if (selected) V4.colors.accentInk else V4.colors.ink3
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Tab) { if (!selected) onSelect(tab.route) }
                        .semantics { this.selected = selected },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    Text(tab.label, style = V4.type.micro, color = tint)
                }
            }
        }
    }
}

/**
 * "Add anything": one box for every kind of log. Opens the quick-add sheet, which works out which
 * areas the words belong to.
 */
@Composable
fun V4AddAnythingBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(V4.colors.inverse)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Add anything" }
            .padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "Add anything: “coffee 4.50”, “ran 5k”",
            style = V4.type.body,
            color = V4.colors.onInverse,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(40.dp).clip(CircleShape).background(V4.colors.accent), contentAlignment = Alignment.Center) {
            Icon(PhosphorIcons.Bold.Plus, contentDescription = null, tint = V4.colors.onAccent, modifier = Modifier.size(20.dp))
        }
    }
}
