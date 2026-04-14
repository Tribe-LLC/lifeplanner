package az.tribe.lifeplanner.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.CheckCircle
import com.adamglin.phosphoricons.regular.PencilSimple
import com.adamglin.phosphoricons.regular.Flag
import com.adamglin.phosphoricons.regular.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.ui.components.GlassCard
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

@Composable
fun ActivityNodesSection(
    nodes: List<ActivityNode>,
    onNodeClick: (ActivityNode) -> Unit,
    modifier: Modifier = Modifier
) {
    if (nodes.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Recent Activity",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                val displayNodes = nodes.take(5)
                displayNodes.forEachIndexed { index, node ->
                    ActivityNodeRow(
                        node = node,
                        onClick = { onNodeClick(node) }
                    )
                    if (index < displayNodes.size - 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityNodeRow(
    node: ActivityNode,
    onClick: () -> Unit
) {
    val (icon, iconColor) = nodeIconAndColor(node)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon circle
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title + subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = node.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Relative time
        Text(
            text = relativeTime(node.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun nodeIconAndColor(node: ActivityNode): Pair<ImageVector, Color> {
    return when (node) {
        is ActivityNode.GoalCreated -> PhosphorIcons.Regular.Flag to Color(0xFF667EEA)
        is ActivityNode.GoalCompleted -> PhosphorIcons.Regular.CheckCircle to Color(0xFF4CAF50)
        is ActivityNode.HabitCheckedIn -> PhosphorIcons.Regular.Check to Color(0xFF4CAF50)
        is ActivityNode.JournalWritten -> PhosphorIcons.Regular.PencilSimple to Color(0xFF667EEA)
        is ActivityNode.FocusCompleted -> PhosphorIcons.Regular.Timer to Color(0xFFFF6B35)
    }
}

private fun relativeTime(dateTime: LocalDateTime): String {
    val now = Clock.System.now()
    val tz = TimeZone.currentSystemDefault()
    val instant = dateTime.toInstant(tz)
    val diffMs = now.toEpochMilliseconds() - instant.toEpochMilliseconds()
    val diffMinutes = diffMs / 60_000

    return when {
        diffMinutes < 1 -> "Just now"
        diffMinutes < 60 -> "${diffMinutes}m ago"
        diffMinutes < 1440 -> "${diffMinutes / 60}h ago"
        diffMinutes < 2880 -> "Yesterday"
        else -> "${diffMinutes / 1440}d ago"
    }
}
