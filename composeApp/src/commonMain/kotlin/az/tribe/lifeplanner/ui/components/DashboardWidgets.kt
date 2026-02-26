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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
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
import az.tribe.lifeplanner.domain.enum.BadgeType
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.ui.habit.HabitWithStatus
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
    streak: Int,
    level: Int = 1,
    levelTitle: String = "Novice",
    xpProgress: Float = 0f,
    totalXp: Int = 0
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

            Spacer(Modifier.height(12.dp))

            // Level and XP row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Level badge
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "⭐",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Lv.$level",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Level title
                Text(
                    levelTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )

                Spacer(Modifier.weight(1f))

                // Streak badge
                if (streak > 0) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🔥",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "$streak",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // XP Progress bar
            Spacer(Modifier.height(12.dp))
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "$totalXp XP",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        "Next level",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                // XP Progress bar with glass effect
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(xpProgress.coerceIn(0f, 1f))
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White)
                    )
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
            icon = Icons.AutoMirrored.Rounded.TrendingUp,
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

// ============== NEW COMPONENTS ==============

@Composable
fun QuickActionsGrid(
    onAddGoalClick: () -> Unit,
    onAiSuggestClick: () -> Unit,
    onNewHabitClick: () -> Unit,
    onJournalClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // First row
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
        // Second row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                icon = Icons.Rounded.Loop,
                label = "New Habit",
                color = MaterialTheme.colorScheme.tertiary,
                onClick = onNewHabitClick,
                modifier = Modifier.weight(1f),
                useGradient = true,
                gradientColors = listOf(
                    Color(0xFF11998E),
                    Color(0xFF38EF7D)
                )
            )
            QuickActionCard(
                icon = Icons.Rounded.Edit,
                label = "Journal",
                color = MaterialTheme.colorScheme.secondary,
                onClick = onJournalClick,
                modifier = Modifier.weight(1f),
                useGradient = true,
                gradientColors = listOf(
                    Color(0xFFFC466B),
                    Color(0xFF3F5EFB)
                )
            )
        }
    }
}

@Composable
fun TodayProgressCard(
    streak: Int,
    habitsCompleted: Int,
    totalHabits: Int,
    goalsDueToday: Int
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = LifePlannerDesign.CornerRadius.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LifePlannerDesign.Padding.standard),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Streak
            ProgressStatItem(
                icon = "🔥",
                value = "$streak",
                label = "day streak",
                color = Color(0xFFFF6B35)
            )

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            // Habits
            ProgressStatItem(
                icon = "✓",
                value = "$habitsCompleted/$totalHabits",
                label = "habits",
                color = Color(0xFF4CAF50)
            )

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            // Goals due
            ProgressStatItem(
                icon = "📌",
                value = "$goalsDueToday",
                label = "due today",
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ProgressStatItem(
    icon: String,
    value: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                icon,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TodayHabitsSection(
    habits: List<HabitWithStatus>,
    onCheckIn: (String) -> Unit,
    onSeeAllClick: () -> Unit
) {
    Column {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Today's Habits",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (habits.isNotEmpty()) {
                Surface(
                    onClick = onSeeAllClick,
                    shape = RoundedCornerShape(50),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "See all",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (habits.isEmpty()) {
            // Empty state
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = LifePlannerDesign.CornerRadius.medium
            ) {
                Row(
                    modifier = Modifier.padding(LifePlannerDesign.Padding.large),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Loop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            "No habits yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Start building daily routines",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Habit list
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = LifePlannerDesign.CornerRadius.medium
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    habits.take(3).forEachIndexed { index, habitWithStatus ->
                        CompactHabitRow(
                            habit = habitWithStatus.habit,
                            isCompleted = habitWithStatus.isCompletedToday,
                            onCheckIn = { onCheckIn(habitWithStatus.habit.id) }
                        )
                        if (index < minOf(habits.size - 1, 2)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompactHabitRow(
    habit: Habit,
    isCompleted: Boolean,
    onCheckIn: () -> Unit
) {
    val checkColor = if (isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
    val checkBackground = if (isCompleted) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCheckIn)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Check button
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(checkBackground)
                .border(
                    width = 2.dp,
                    color = checkColor,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Completed",
                    tint = checkColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Habit title
        Text(
            habit.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCompleted) FontWeight.Normal else FontWeight.Medium,
            color = if (isCompleted)
                MaterialTheme.colorScheme.onSurfaceVariant
            else
                MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Streak badge
        if (habit.currentStreak > 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = Color(0xFFFF6B35),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "${habit.currentStreak}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFF6B35)
                )
            }
        }
    }
}

@Composable
fun PriorityGoalsSection(
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
                "Priority Goals",
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
fun AchievementsCard(
    earnedBadges: Int,
    totalBadges: Int,
    recentBadges: List<BadgeType>,
    onSeeAllClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSeeAllClick),
        cornerRadius = LifePlannerDesign.CornerRadius.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LifePlannerDesign.Padding.standard),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Trophy icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFD700).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Text(
                        "Badges",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "$earnedBadges of $totalBadges earned",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Recent badges or chevron
            if (recentBadges.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy((-8).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    recentBadges.take(3).forEach { badgeType ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(badgeType.color)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getBadgeIcon(badgeType),
                                contentDescription = badgeType.displayName,
                                modifier = Modifier.size(14.dp),
                                tint = Color.White
                            )
                        }
                    }

                }
            } else {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Personal Coach card for HomeScreen - quick access to Luna AI coach
 */
@Composable
fun PersonalCoachCard(
    lastMessage: String?,
    onChatClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onChatClick),
        cornerRadius = LifePlannerDesign.CornerRadius.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LifePlannerDesign.Padding.standard),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Luna avatar with gradient
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF667EEA),
                                    Color(0xFF764BA2)
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Psychology,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        "Personal Coach",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        lastMessage ?: "Chat with Luna for personalized guidance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "Open coach",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
