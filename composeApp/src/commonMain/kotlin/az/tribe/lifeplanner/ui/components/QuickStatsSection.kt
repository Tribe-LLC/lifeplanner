package az.tribe.lifeplanner.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.GoalAnalytics

@Composable
fun QuickStatsSection(
    analytics: GoalAnalytics,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Insights,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Overview",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // First row of stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickStatCard(
                    title = "Active Goals",
                    value = analytics.activeGoals.toString(),
                    icon = StatIcons.Active,
                    color = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f)
                )

                QuickStatCard(
                    title = "Completed",
                    value = analytics.completedGoals.toString(),
                    icon = StatIcons.Completed,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f)
                )

                QuickStatCard(
                    title = "Overall Progress",
                    value = "${analytics.completionRate}%",
                    icon = StatIcons.Progress,
                    color = Color(0xFF9C27B0),
                    modifier = Modifier.weight(1f)
                )
            }

            // Progress visualization
            ProgressSummary(analytics = analytics)
        }
    }
}

@Composable
fun ProgressSummary(
    analytics: GoalAnalytics
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Progress header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Progress by Category",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "${analytics.completionRate}% Complete",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Category progress bars
        analytics.averageProgressPerCategory.forEach { (category, progress) ->
            CategoryProgressBar(
                category = category.name,
                progress = progress.toInt()
            )
        }
    }
}

@Composable
fun CategoryProgressBar(
    category: String,
    progress: Int
) {
    val categoryColor = when (category.lowercase()) {
        "career" -> Color(0xFF2196F3)
        "financial" -> Color(0xFF4CAF50)
        "physical" -> Color(0xFFFF9800)
        "social" -> Color(0xFF9C27B0)
        "emotional" -> Color(0xFF00BCD4)
        "spiritual" -> Color(0xFFE91E63)
        "family" -> Color(0xFF3F51B5)
        else -> MaterialTheme.colorScheme.primary
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress / 100f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "progress"
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = category.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "$progress%",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = categoryColor
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                categoryColor,
                                categoryColor.copy(alpha = 0.7f)
                            )
                        ),
                        shape = RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}

@Composable
fun CompactQuickStatsSection(
    analytics: GoalAnalytics,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CompactStatCard(
            title = "Active",
            value = analytics.activeGoals.toString(),
            icon = StatIcons.Active,
            color = Color(0xFF2196F3),
            modifier = Modifier.weight(1f)
        )

        CompactStatCard(
            title = "Completed",
            value = analytics.completedGoals.toString(),
            icon = StatIcons.Completed,
            color = Color(0xFF4CAF50),
            modifier = Modifier.weight(1f)
        )

        CompactStatCard(
            title = "Progress",
            value = "${analytics.averageProgressPerCategory}%",
            icon = StatIcons.Progress,
            color = Color(0xFF9C27B0),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun CompactStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = color.copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Text content
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}