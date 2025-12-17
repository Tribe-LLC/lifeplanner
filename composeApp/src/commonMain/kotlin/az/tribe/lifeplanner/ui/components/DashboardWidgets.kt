package az.tribe.lifeplanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign
import az.tribe.lifeplanner.ui.theme.LifePlannerGradients
import az.tribe.lifeplanner.ui.theme.gradient
import az.tribe.lifeplanner.ui.theme.gradientColors
import az.tribe.lifeplanner.ui.theme.backgroundColor
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun DailyMotivationCard() {
    val quotes = listOf(
        "The journey of a thousand miles begins with a single step." to "Lao Tzu",
        "Your future is created by what you do today." to "Robert Kiyosaki",
        "Small steps lead to big changes." to "Unknown",
        "Success is the sum of small efforts repeated day in and day out." to "Robert Collier",
        "The only way to do great work is to love what you do." to "Steve Jobs",
        "Progress, not perfection." to "Unknown",
        "Every accomplishment starts with the decision to try." to "John F. Kennedy",
        "Your limitation—it's only your imagination." to "Unknown"
    )

    val todayDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val todayQuote = remember(todayDate) {
        quotes[todayDate.dayOfYear % quotes.size]
    }

    // Modern gradient border card for daily inspiration
    GradientBorderCard(
        modifier = Modifier.fillMaxWidth(),
        gradientColors = listOf(
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.secondary
        ),
        borderWidth = 1.5.dp,
        cornerRadius = LifePlannerDesign.CornerRadius.large,
        backgroundColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(LifePlannerDesign.Padding.large)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Daily Inspiration",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "\"${todayQuote.first}\"",
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "— ${todayQuote.second}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun QuickActionsRow(
    onAddGoalClick: () -> Unit,
    onAiSuggestClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionCard(
            icon = Icons.Rounded.Add,
            label = "Add Goal",
            color = MaterialTheme.colorScheme.primary,
            onClick = onAddGoalClick,
            modifier = Modifier.weight(1f),
            useGradient = true,
            gradientColors = listOf(
                Color(0xFF667EEA),
                Color(0xFF764BA2)
            )
        )
        QuickActionCard(
            icon = Icons.Rounded.AutoAwesome,
            label = "AI Suggest",
            color = MaterialTheme.colorScheme.secondary,
            onClick = onAiSuggestClick,
            modifier = Modifier.weight(1f),
            useGradient = true,
            gradientColors = listOf(
                Color(0xFFF093FB),
                Color(0xFFF5576C)
            )
        )
    }
}

@Composable
fun QuickActionCard(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    useGradient: Boolean = false,
    gradientColors: List<Color>? = null
) {
    val backgroundModifier = if (useGradient && gradientColors != null) {
        Modifier.background(
            brush = Brush.horizontalGradient(gradientColors),
            shape = RoundedCornerShape(50)
        )
    } else {
        Modifier.background(
            color = color.copy(alpha = 0.12f),
            shape = RoundedCornerShape(50)
        )
    }

    val contentColor = if (useGradient) Color.White else color

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50)),
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(backgroundModifier)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

@Composable
fun TodaysFocusSection(
    upcomingGoals: List<Goal>,
    onGoalClick: (Goal) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Today's Focus",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (upcomingGoals.isNotEmpty()) {
                Text(
                    "${upcomingGoals.size} upcoming",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (upcomingGoals.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = LifePlannerDesign.Alpha.overlay)
                ),
                shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.medium)
            ) {
                Row(
                    modifier = Modifier.padding(LifePlannerDesign.Padding.large),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            "All caught up!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "No urgent deadlines. Keep up the great work!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                upcomingGoals.take(3).forEach { goal ->
                    CompactGoalCard(goal = goal, onClick = { onGoalClick(goal) })
                }
            }
        }
    }
}

@Composable
fun CompactGoalCard(
    goal: Goal,
    onClick: () -> Unit
) {
    val categoryColor = goal.category.backgroundColor()
    val categoryGradientColors = goal.category.gradientColors()
    val progress = (goal.progress ?: 0L).toInt()

    // Modern card with gradient left accent bar
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = LifePlannerDesign.CornerRadius.medium
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Gradient accent bar on the left
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(72.dp)
                    .background(
                        brush = Brush.verticalGradient(categoryGradientColors)
                    )
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(LifePlannerDesign.Padding.medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category indicator with gradient background
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = categoryGradientColors.map { it.copy(alpha = 0.15f) }
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(categoryGradientColors)
                            )
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        goal.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Schedule,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            goal.dueDate.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Progress indicator with gradient
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "$progress%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = categoryGradientColors.first()
                    )
                    Spacer(Modifier.height(4.dp))
                    GradientProgressBar(
                        progress = progress / 100f,
                        gradient = Brush.horizontalGradient(categoryGradientColors),
                        modifier = Modifier.width(56.dp),
                        height = 6.dp
                    )
                }
            }
        }
    }
}

@Composable
fun WelcomeHeader(
    userName: String?,
    streak: Int
) {
    val greeting = remember {
        val hour = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).hour
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
    }

    // Modern gradient header card
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LifePlannerDesign.CornerRadius.large))
            .background(LifePlannerGradients.primary)
            .padding(LifePlannerDesign.Padding.large)
    ) {
        Column {
            Text(
                "$greeting${userName?.let { ", $it" } ?: ""}!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            if (streak > 0) {
                Spacer(Modifier.height(8.dp))
                // Streak badge with glass effect
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🔥",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "$streak day streak",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardStatsRow(
    activeGoals: Int,
    completedGoals: Int,
    totalProgress: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DashboardStatCard(
            value = "$activeGoals",
            label = "Active Goals",
            icon = Icons.Rounded.Flag,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        DashboardStatCard(
            value = "$completedGoals",
            label = "Completed",
            icon = Icons.Rounded.CheckCircle,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f)
        )
        DashboardStatCard(
            value = "$totalProgress%",
            label = "Progress",
            icon = Icons.Rounded.TrendingUp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun DashboardStatCard(
    value: String,
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    // Modern glass-style stat card with gradient accent
    GlassCard(
        modifier = modifier,
        cornerRadius = LifePlannerDesign.CornerRadius.large
    ) {
        Column {
            // Gradient accent bar at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(color, color.copy(alpha = 0.5f))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(LifePlannerDesign.Padding.standard),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon with colored background
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(LifePlannerDesign.IconSize.small)
                    )
                }
                Spacer(Modifier.height(LifePlannerDesign.Spacing.sm))
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
