package az.tribe.lifeplanner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.model.Goal

/**
 * Swipeable wrapper for GoalItem with complete and delete actions
 *
 * - Swipe right (start-to-end): Complete the goal
 * - Swipe left (end-to-start): Delete the goal (with confirmation)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableGoalItem(
    goal: Goal,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    scrollState: LazyListState,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val haptic = rememberHapticManager()

    // Don't show complete action for already completed goals
    val isCompleted = goal.status == GoalStatus.COMPLETED

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Complete action
                    if (!isCompleted) {
                        haptic.strongSuccess()
                        onComplete()
                    }
                    false // Don't dismiss, just trigger action
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // Delete action - show confirmation
                    haptic.warning()
                    showDeleteDialog = true
                    false // Don't dismiss, show dialog first
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.25f }
    )

    // Reset state after action
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            SwipeBackground(
                dismissDirection = dismissState.dismissDirection,
                isCompleted = isCompleted
            )
        },
        enableDismissFromStartToEnd = !isCompleted, // Only enable complete for non-completed goals
        enableDismissFromEndToStart = true
    ) {
        GoalItem(
            goal = goal,
            onClick = onClick,
            scrollState = scrollState
        )
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
            title = {
                Text("Delete Goal?")
            },
            text = {
                Text("Are you sure you want to delete \"${goal.title}\"? This action cannot be undone.")
            },
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
private fun SwipeBackground(
    dismissDirection: SwipeToDismissBoxValue,
    isCompleted: Boolean
) {
    val color by animateColorAsState(
        targetValue = when (dismissDirection) {
            SwipeToDismissBoxValue.StartToEnd -> {
                if (isCompleted) Color.Gray else Color(0xFF4CAF50) // Green for complete
            }
            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error // Red for delete
            SwipeToDismissBoxValue.Settled -> Color.Transparent
        },
        animationSpec = tween(300),
        label = "swipeBackgroundColor"
    )

    val icon = when (dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd -> Icons.Rounded.CheckCircle
        SwipeToDismissBoxValue.EndToStart -> Icons.Rounded.Delete
        SwipeToDismissBoxValue.Settled -> null
    }

    val alignment = when (dismissDirection) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        SwipeToDismissBoxValue.Settled -> Alignment.Center
    }

    val scale by animateFloatAsState(
        targetValue = if (dismissDirection != SwipeToDismissBoxValue.Settled) 1f else 0.8f,
        animationSpec = tween(300),
        label = "iconScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .padding(horizontal = 24.dp),
        contentAlignment = alignment
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = when (dismissDirection) {
                    SwipeToDismissBoxValue.StartToEnd -> "Complete"
                    SwipeToDismissBoxValue.EndToStart -> "Delete"
                    else -> null
                },
                tint = Color.White,
                modifier = Modifier
                    .size(32.dp)
                    .scale(scale)
            )
        }
    }
}
