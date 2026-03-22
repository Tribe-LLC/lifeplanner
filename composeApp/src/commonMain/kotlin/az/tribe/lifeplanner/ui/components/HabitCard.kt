package az.tribe.lifeplanner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.HabitFrequency
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.ui.habit.HabitWithStatus
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

// Haptic feedback
private var lastThresholdState: ThresholdState = ThresholdState.NONE

private enum class ThresholdState {
    NONE, EDIT_ZONE, DELETE_ZONE, CHECK_IN_ZONE
}

// Swipe thresholds
private const val EDIT_THRESHOLD = 0.25f  // 25% for edit
private const val DELETE_THRESHOLD = 0.45f  // 45% for delete

/**
 * Swipeable wrapper for HabitCard
 * - Swipe right (start-to-end): Check-in / Toggle completion
 * - Swipe left (end-to-start):
 *   - Light swipe (25-45%): Edit
 *   - Heavy swipe (>45%): Delete
 */
@Composable
fun SwipeableHabitCard(
    habitWithStatus: HabitWithStatus,
    onCheckIn: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {},
    onFocusClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isCompletedToday = habitWithStatus.isCompletedToday
    val haptic = rememberHapticManager()

    var offsetX by remember { mutableStateOf(0f) }
    var cardWidth by remember { mutableStateOf(1f) }
    val density = LocalDensity.current

    // Track threshold state for haptic feedback
    var currentThresholdState by remember { mutableStateOf(ThresholdState.NONE) }

    // Calculate swipe progress (0 to 1)
    val swipeProgress = (offsetX.absoluteValue / cardWidth).coerceIn(0f, 1f)
    val isSwipingLeft = offsetX < 0
    val isSwipingRight = offsetX > 0

    // Determine action based on threshold
    val isInDeleteZone = isSwipingLeft && swipeProgress >= DELETE_THRESHOLD
    val isInEditZone = isSwipingLeft && swipeProgress >= EDIT_THRESHOLD && swipeProgress < DELETE_THRESHOLD
    val isInCheckInZone = isSwipingRight && swipeProgress >= EDIT_THRESHOLD

    // Haptic feedback when crossing thresholds
    val newThresholdState = when {
        isInDeleteZone -> ThresholdState.DELETE_ZONE
        isInEditZone -> ThresholdState.EDIT_ZONE
        isInCheckInZone -> ThresholdState.CHECK_IN_ZONE
        else -> ThresholdState.NONE
    }

    LaunchedEffect(newThresholdState) {
        if (newThresholdState != currentThresholdState && newThresholdState != ThresholdState.NONE) {
            when (newThresholdState) {
                ThresholdState.DELETE_ZONE -> haptic.swipeDeleteZone()
                ThresholdState.EDIT_ZONE -> haptic.swipeActionZone()
                ThresholdState.CHECK_IN_ZONE -> haptic.swipeActionZone()
                else -> {}
            }
        }
        currentThresholdState = newThresholdState
    }

    // Animated offset for smooth return
    val animatedOffset by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = tween(
            durationMillis = if (offsetX == 0f) 200 else 0
        ),
        label = "offsetAnimation"
    )

    val draggableState = rememberDraggableState { delta ->
        offsetX = (offsetX + delta).coerceIn(-cardWidth * 0.6f, cardWidth * 0.4f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .onSizeChanged { cardWidth = it.width.toFloat() }
    ) {
        // Background layer - uses matchParentSize to match card height
        HabitSwipeBackgroundNew(
            swipeProgress = swipeProgress,
            isSwipingLeft = isSwipingLeft,
            isSwipingRight = isSwipingRight,
            isInDeleteZone = isInDeleteZone,
            isInEditZone = isInEditZone,
            isCompletedToday = isCompletedToday,
            modifier = Modifier.matchParentSize()
        )

        // Foreground card
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        when {
                            isInDeleteZone -> {
                                haptic.warning()
                                showDeleteDialog = true
                            }
                            isInEditZone -> {
                                haptic.click()
                                onEdit()
                            }
                            isInCheckInZone -> {
                                haptic.success()
                                onCheckIn()
                            }
                        }
                        offsetX = 0f
                        currentThresholdState = ThresholdState.NONE
                    }
                )
        ) {
            HabitCard(
                habitWithStatus = habitWithStatus,
                onCheckIn = onCheckIn,
                onFocusClick = onFocusClick
            )
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Delete Habit?") },
            text = { Text("Are you sure you want to delete \"${habitWithStatus.habit.title}\"? This will remove all check-in history. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HabitSwipeBackgroundNew(
    swipeProgress: Float,
    isSwipingLeft: Boolean,
    isSwipingRight: Boolean,
    isInDeleteZone: Boolean,
    isInEditZone: Boolean,
    isCompletedToday: Boolean,
    modifier: Modifier = Modifier
) {
    // Colors for different states
    val editColor = Color(0xFF2196F3) // Blue for edit
    val deleteColor = Color(0xFFF44336) // Red for delete
    val checkInColor = if (isCompletedToday) Color(0xFFFF9800) else Color(0xFF4CAF50)

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isInDeleteZone -> deleteColor
            isInEditZone -> editColor
            isSwipingLeft && swipeProgress > 0.1f -> editColor.copy(alpha = 0.7f)
            isSwipingRight && swipeProgress > 0.1f -> checkInColor
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "backgroundColor"
    )

    val icon = when {
        isInDeleteZone -> Icons.Rounded.Delete
        isSwipingLeft -> Icons.Rounded.Edit
        isSwipingRight && isCompletedToday -> Icons.AutoMirrored.Rounded.Undo
        isSwipingRight -> Icons.Rounded.CheckCircle
        else -> null
    }

    val actionText = when {
        isInDeleteZone -> "Delete"
        isInEditZone -> "Edit"
        isSwipingLeft && swipeProgress > 0.1f -> "Edit"
        isSwipingRight && swipeProgress > EDIT_THRESHOLD -> if (isCompletedToday) "Undo" else "Check in"
        else -> null
    }

    val alignment = if (isSwipingLeft) Alignment.CenterEnd else Alignment.CenterStart

    val iconScale by animateFloatAsState(
        targetValue = when {
            isInDeleteZone -> 1.2f
            swipeProgress > 0.15f -> 1f
            else -> 0.8f
        },
        animationSpec = tween(150),
        label = "iconScale"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(LifePlannerDesign.CornerRadius.medium))
            .background(backgroundColor)
            .padding(horizontal = 20.dp),
        contentAlignment = alignment
    ) {
        if (swipeProgress > 0.1f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isSwipingLeft) Arrangement.End else Arrangement.Start,
                modifier = Modifier.graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
            ) {
                if (!isSwipingLeft) {
                    icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = actionText,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                actionText?.let {
                    Text(
                        text = it,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (isSwipingLeft) {
                    Spacer(modifier = Modifier.width(8.dp))
                    icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = actionText,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HabitCard(
    habitWithStatus: HabitWithStatus,
    onCheckIn: (() -> Unit)? = null,
    onFocusClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val habit = habitWithStatus.habit
    val isCompletedToday = habitWithStatus.isCompletedToday

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isCompletedToday) LifePlannerDesign.Elevation.none else LifePlannerDesign.Elevation.low
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isCompletedToday) {
                        Modifier.background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF4CAF50).copy(alpha = 0.08f),
                                    Color(0xFF4CAF50).copy(alpha = 0.03f)
                                )
                            )
                        )
                    } else Modifier
                )
                .padding(LifePlannerDesign.Padding.standard)
        ) {
            // Top row: icon + title + check circle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryIconBadge(
                    category = habit.category,
                    isCompleted = isCompletedToday
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = habit.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (isCompletedToday) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (isCompletedToday)
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (habit.description.isNotBlank() && !isCompletedToday) {
                        Text(
                            text = habit.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Focus timer shortcut
                if (onFocusClick != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .clickable(onClick = onFocusClick)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = "Focus",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                CheckInCircle(
                    isCompleted = isCompletedToday,
                    onClick = onCheckIn
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom row: weekly dots + stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Weekly completion dots
                if (habitWithStatus.weeklyCompletions.isNotEmpty()) {
                    WeeklyDots(
                        completions = habitWithStatus.weeklyCompletions,
                        categoryColor = habit.category.backgroundColor()
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Stats: streak + completions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (habit.currentStreak > 0) {
                        StreakBadge(streak = habit.currentStreak)
                    }

                    FrequencyChip(frequency = habit.frequency)
                }
            }
        }
    }
}

/**
 * Category icon badge — shows the category symbol.
 * When completed, shows a green background with check overlay.
 */
@Composable
private fun CategoryIconBadge(
    category: GoalCategory,
    isCompleted: Boolean
) {
    val categoryColor = category.backgroundColor()

    val bgColor by animateColorAsState(
        targetValue = if (isCompleted) Color(0xFF4CAF50) else categoryColor.copy(alpha = 0.12f),
        animationSpec = tween(300),
        label = "iconBgColor"
    )

    val iconTint by animateColorAsState(
        targetValue = if (isCompleted) Color.White else categoryColor,
        animationSpec = tween(300),
        label = "iconTint"
    )

    Box(contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(12.dp),
            color = bgColor
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = category.getIcon(),
                    contentDescription = category.name,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Tappable circle for check-in — empty ring when pending, filled green check when done.
 */
@Composable
private fun CheckInCircle(
    isCompleted: Boolean,
    onClick: (() -> Unit)? = null
) {
    val checkScale by animateFloatAsState(
        targetValue = if (isCompleted) 1f else 0f,
        animationSpec = if (isCompleted) keyframes {
            durationMillis = 300
            0f at 0
            1.2f at 150
            1f at 300
        } else tween(150),
        label = "checkScale"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(200),
        label = "borderColor"
    )

    val fillColor by animateColorAsState(
        targetValue = if (isCompleted) Color(0xFF4CAF50) else Color.Transparent,
        animationSpec = tween(200),
        label = "fillColor"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
    ) {
        // Circle: empty ring when not done, filled green when done
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(fillColor)
                .then(
                    if (!isCompleted) Modifier.background(
                        color = Color.Transparent,
                        shape = CircleShape
                    ) else Modifier
                )
        ) {
            // Border ring for uncompleted state
            if (!isCompleted) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(borderColor, CircleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    )
                }
            }

            // Checkmark icon — only visible when completed
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Completed",
                    tint = Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .scale(checkScale)
                )
            }
        }
    }
}

/**
 * Mini weekly completion dots — 7 dots for Mon through Sun.
 * Completed days are filled with the category color, future/incomplete days are hollow.
 */
@Composable
private fun WeeklyDots(
    completions: List<Boolean>,
    categoryColor: Color
) {
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        completions.forEachIndexed { index, completed ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (completed) categoryColor
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                )
                if (index < dayLabels.size) {
                    Text(
                        text = dayLabels[index],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.75f
                    )
                }
            }
        }
    }
}

@Composable
private fun StreakBadge(
    streak: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.LocalFireDepartment,
            contentDescription = null,
            tint = Color(0xFFFF6B35),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "${streak}d",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = Color(0xFFFF6B35)
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
