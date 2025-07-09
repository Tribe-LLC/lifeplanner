package az.tribe.lifeplanner.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.model.Goal
import kotlinx.datetime.LocalDate

@Composable
fun GoalCard(
    goal: Goal,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title and status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = goal.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.width(8.dp))

            StatusChip(status = goal.status)
        }

        // Description
        if (goal.description.isNotBlank()) {
            Text(
                text = goal.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Progress indicator with animation
        if (goal.progress != null && goal.progress > 0) {
            ProgressSection(
                progress = goal.progress.toInt(),
                color = goal.category.backgroundColor()
            )
        }

        // Footer with metadata
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Due date with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarToday,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = formatDate(goal.dueDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                )
            }

            // Milestones indicator
            if (goal.milestones.isNotEmpty()) {
                MilestoneIndicator(
                    completed = goal.milestones.count { it.isCompleted },
                    total = goal.milestones.size
                )
            }
        }
    }
}

@Composable
private fun ProgressSection(
    progress: Int,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Progress",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Animated progress percentage
            val animatedProgress by animateIntAsState(
                targetValue = progress,
                animationSpec = tween(800, easing = FastOutSlowInEasing),
                label = "progress"
            )

            Text(
                text = "$animatedProgress%",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Modern progress bar with gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            // Animated progress fill
            val animatedWidth by animateFloatAsState(
                targetValue = progress / 100f,
                animationSpec = tween(800, easing = FastOutSlowInEasing),
                label = "width"
            )

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedWidth)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                color,
                                color.copy(alpha = 0.8f)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun MilestoneIndicator(
    completed: Int,
    total: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.ListAlt,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )

        // Milestone dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(total) { index ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (index < completed)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = "$completed/$total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
fun StatusChip(status: GoalStatus) {
    val (icon, text, colors) = when (status) {
        GoalStatus.NOT_STARTED -> Triple(
            Icons.Rounded.HourglassEmpty,
            "Not Started",
            ChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        GoalStatus.IN_PROGRESS -> Triple(
            Icons.Rounded.PlayArrow,
            "In Progress",
            ChipColors(
                containerColor = Color(0xFFFFF8E1),
                contentColor = Color(0xFFFF8F00)
            )
        )
        GoalStatus.COMPLETED -> Triple(
            Icons.Rounded.CheckCircle,
            "Completed",
            ChipColors(
                containerColor = Color(0xFFE8F5E9),
                contentColor = Color(0xFF2E7D32)
            )
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.containerColor,
        modifier = Modifier.height(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.contentColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = colors.contentColor
            )
        }
    }
}

private data class ChipColors(
    val containerColor: Color,
    val contentColor: Color
)

// Extension function to get category background color with more vibrant colors
fun GoalCategory.backgroundColor(): Color {
    return when (this) {
        GoalCategory.CAREER -> Color(0xFF2196F3)
        GoalCategory.FINANCIAL -> Color(0xFF4CAF50)
        GoalCategory.PHYSICAL -> Color(0xFFFF9800)
        GoalCategory.SOCIAL -> Color(0xFF9C27B0)
        GoalCategory.EMOTIONAL -> Color(0xFF009688)
        GoalCategory.SPIRITUAL -> Color(0xFFE91E63)
        GoalCategory.FAMILY -> Color(0xFF3F51B5)
        else -> Color(0xFF757575)
    }
}

// Helper function to format date
private fun formatDate(date: LocalDate): String {
    val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    return "${months[date.monthNumber - 1]} ${date.dayOfMonth}, ${date.year}"
}