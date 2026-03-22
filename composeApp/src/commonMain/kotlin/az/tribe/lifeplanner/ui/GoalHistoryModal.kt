package az.tribe.lifeplanner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import az.tribe.lifeplanner.domain.model.GoalChange
import az.tribe.lifeplanner.ui.theme.modernColors

@Composable
fun GoalHistoryModal(
    isVisible: Boolean,
    goalId: String,
    viewModel: GoalViewModel,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(300)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .zIndex(10f)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(8.dp)
                    .zIndex(11f),
                color = MaterialTheme.colorScheme.background
            ) {
                GoalHistoryContent(
                    goalId = goalId,
                    viewModel = viewModel,
                    onBackClick = onDismiss
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalHistoryContent(
    goalId: String,
    viewModel: GoalViewModel,
    onBackClick: () -> Unit
) {
    val historyState by viewModel.goalHistory.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val goal = goals.find { it.id == goalId }

    LaunchedEffect(goalId) {
        viewModel.loadGoalHistory(goalId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Goal History",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        goal?.let {
                            Text(
                                text = it.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (historyState.isEmpty()) {
            EmptyHistoryState(Modifier.padding(innerPadding))
        } else {
            val sorted = historyState.sortedByDescending { it.changedAt }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp)
            ) {
                itemsIndexed(sorted) { index, change ->
                    TimelineHistoryItem(
                        change = change,
                        isLast = index == sorted.size - 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineHistoryItem(
    change: GoalChange,
    isLast: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Timeline column: time + dot + connector
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(56.dp).padding(end = 8.dp)
        ) {
            Text(
                formatTimestamp(change.changedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor(change.field))
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
            }
        }

        // Content column
        Column(modifier = Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 12.dp)) {
            Text(
                text = formatChangeDescription(change),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (change.oldValue != null && change.oldValue.isNotBlank() && change.field != "GOAL_CREATED") {
                Text(
                    text = formatOldValue(change),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun dotColor(field: String): Color {
    return when (field) {
        "STATUS_CHANGE" -> MaterialTheme.colorScheme.primary
        "PROGRESS_UPDATE" -> Color(0xFF4CAF50)
        "GOAL_CREATED" -> Color(0xFFFF9800)
        "MILESTONE_COMPLETED" -> Color(0xFF4CAF50)
        "MILESTONE_ADDED" -> Color(0xFF2196F3)
        "MILESTONE_REMOVED", "MILESTONE_UNCOMPLETED" -> Color(0xFFFF5722)
        else -> MaterialTheme.colorScheme.tertiary
    }
}

@Composable
fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No history yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Changes to this goal will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatChangeDescription(change: GoalChange): String {
    return when (change.field) {
        "STATUS_CHANGE" -> "Status changed to ${formatStatusName(change.newValue)}"
        "PROGRESS_UPDATE" -> {
            val old = change.oldValue?.toIntOrNull()
            val new = change.newValue.toIntOrNull() ?: 0
            if (old != null && new > old) "Progress increased to $new%"
            else "Progress updated to ${new}%"
        }
        "TITLE_CHANGE" -> "Renamed to \"${change.newValue}\""
        "DESCRIPTION_CHANGE" -> "Description updated"
        "DUE_DATE_CHANGE" -> "Due date changed to ${change.newValue}"
        "MILESTONE_ADDED" -> "Added milestone: ${change.newValue}"
        "MILESTONE_COMPLETED" -> "Completed milestone: ${change.newValue}"
        "MILESTONE_UNCOMPLETED" -> "Uncompleted milestone: ${change.newValue}"
        "MILESTONE_REMOVED" -> "Removed milestone: ${change.newValue}"
        "NOTES_UPDATED" -> "Notes updated"
        "CATEGORY_CHANGE" -> "Category changed to ${formatCategoryName(change.newValue)}"
        "TIMELINE_CHANGE" -> "Timeline changed to ${change.newValue.replace("_", " ").lowercase()}"
        "GOAL_CREATED" -> "Goal created"
        else -> "${change.field.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}"
    }
}

private fun formatOldValue(change: GoalChange): String {
    return when (change.field) {
        "STATUS_CHANGE" -> "was ${formatStatusName(change.oldValue ?: "")}"
        "PROGRESS_UPDATE" -> "was ${change.oldValue}%"
        "TITLE_CHANGE" -> "was \"${change.oldValue}\""
        "CATEGORY_CHANGE" -> "was ${formatCategoryName(change.oldValue ?: "")}"
        else -> ""
    }
}

private fun formatStatusName(status: String): String {
    return when (status.uppercase()) {
        "NOT_STARTED" -> "Not Started"
        "IN_PROGRESS" -> "In Progress"
        "COMPLETED" -> "Completed"
        "ON_HOLD" -> "On Hold"
        "ABANDONED" -> "Abandoned"
        else -> status.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    }
}

private fun formatCategoryName(category: String): String {
    return category.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
}

private fun formatTimestamp(timestamp: String): String {
    return try {
        // Extract time portion from ISO timestamp (e.g., "2026-03-21T15:30:00Z" → "3:30 PM")
        val parts = timestamp.split("T")
        if (parts.size >= 2) {
            val timePart = parts[1].substringBefore("Z").substringBefore("+").substringBefore(".")
            val hourMin = timePart.split(":")
            if (hourMin.size >= 2) {
                val hour = hourMin[0].toIntOrNull() ?: 0
                val min = hourMin[1]
                val period = if (hour < 12) "AM" else "PM"
                val displayHour = when {
                    hour == 0 -> 12
                    hour > 12 -> hour - 12
                    else -> hour
                }
                "$displayHour:$min\n$period"
            } else timePart
        } else {
            timestamp.takeLast(8)
        }
    } catch (_: Exception) {
        timestamp.takeLast(8)
    }
}
