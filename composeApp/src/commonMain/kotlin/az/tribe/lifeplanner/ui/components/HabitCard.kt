package az.tribe.lifeplanner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.HabitFrequency
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.ui.habit.HabitWithStatus
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign

@Composable
fun HabitCard(
    habitWithStatus: HabitWithStatus,
    onCheckIn: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val habit = habitWithStatus.habit
    val isCompletedToday = habitWithStatus.isCompletedToday
    var showMenu by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = if (isCompletedToday)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else
            MaterialTheme.colorScheme.surface,
        animationSpec = tween(300),
        label = "backgroundColor"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.medium),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isCompletedToday) LifePlannerDesign.Elevation.none else LifePlannerDesign.Elevation.low
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LifePlannerDesign.Padding.standard),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Check-in button (now toggleable)
            CheckInButton(
                isCompleted = isCompletedToday,
                categoryColor = habit.category.backgroundColor(),
                onClick = onCheckIn // Now toggles check/uncheck
            )

            // Habit info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = habit.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    FrequencyChip(frequency = habit.frequency)
                }

                if (habit.description.isNotBlank()) {
                    Text(
                        text = habit.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Stats row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Current streak
                    StreakBadge(
                        streak = habit.currentStreak,
                        isActive = habit.currentStreak > 0
                    )

                    // Total completions
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${habit.totalCompletions} total",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // More options menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Edit, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Delete, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckInButton(
    isCompleted: Boolean,
    categoryColor: Color,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isCompleted) 1.1f else 1f,
        animationSpec = tween(200),
        label = "scale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isCompleted) categoryColor else Color.Transparent,
        animationSpec = tween(300),
        label = "checkBackground"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isCompleted) categoryColor else MaterialTheme.colorScheme.outline,
        animationSpec = tween(300),
        label = "borderColor"
    )

    Surface(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clickable { onClick() },
        shape = CircleShape,
        color = backgroundColor,
        border = if (!isCompleted) {
            ButtonDefaults.outlinedButtonBorder
        } else null
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Completed",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun StreakBadge(
    streak: Int,
    isActive: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.LocalFireDepartment,
            contentDescription = null,
            tint = if (isActive) Color(0xFFFF6B35) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "$streak day${if (streak != 1) "s" else ""}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (isActive) Color(0xFFFF6B35) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun FrequencyChip(frequency: HabitFrequency) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.height(20.dp)
    ) {
        Text(
            text = frequency.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun CategoryIndicator(category: GoalCategory) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(category.backgroundColor())
    )
}

fun GoalCategory.getIcon(): ImageVector {
    return when (this) {
        GoalCategory.CAREER -> Icons.Rounded.Work
        GoalCategory.FINANCIAL -> Icons.Rounded.AccountBalance
        GoalCategory.PHYSICAL -> Icons.Rounded.FitnessCenter
        GoalCategory.SOCIAL -> Icons.Rounded.People
        GoalCategory.EMOTIONAL -> Icons.Rounded.Favorite
        GoalCategory.SPIRITUAL -> Icons.Rounded.SelfImprovement
        GoalCategory.FAMILY -> Icons.Rounded.Home
    }
}
