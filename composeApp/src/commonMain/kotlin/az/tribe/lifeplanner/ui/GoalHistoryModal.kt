package az.tribe.lifeplanner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    // Animation constants
    val animationDuration = 300

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(animationDuration)
        ) + fadeIn(animationSpec = tween(animationDuration)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(animationDuration)
        ) + fadeOut(animationSpec = tween(animationDuration))
    ) {
        // Semi-transparent background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .zIndex(10f)
        ) {
            // Content container
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
    // Collect the history data
    val historyState by viewModel.goalHistory.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val goal = goals.find { it.id == goalId }
    
    // Load history when the screen becomes visible
    LaunchedEffect(goalId) {
        viewModel.loadGoalHistory(goalId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Goal History",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Goal title header
            goal?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Goal",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.modernColors.textSecondary
                        )
                        Text(
                            text = it.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.modernColors.textPrimary
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (historyState.isEmpty()) {
                // Empty state
                EmptyHistoryState()
            } else {
                // History list
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(historyState) { change ->
                        HistoryItem(change = change)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryState() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.modernColors.surface
        ),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.modernColors.textSecondary
            )
            Text(
                text = "No history yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.modernColors.textPrimary
            )
            Text(
                text = "Changes to this goal will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.modernColors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun HistoryItem(
    change: GoalChange
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.modernColors.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = formatChangeDescription(change),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.modernColors.textPrimary
            )
            Text(
                text = change.changedAt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.modernColors.textSecondary
            )
        }
    }
}

// Helper function to format change descriptions in a user-friendly way
private fun formatChangeDescription(change: GoalChange): String {
    return when (change.field) {
        "STATUS_CHANGE" -> "Status changed to ${change.newValue}"
        "PROGRESS_UPDATE" -> "Progress updated to ${change.newValue}%"
        "TITLE_CHANGE" -> "Title changed to \"${change.newValue}\""
        "DESCRIPTION_CHANGE" -> "Description updated"
        "DUE_DATE_CHANGE" -> "Due date changed to ${change.newValue}"
        "MILESTONE_ADDED" -> "Added milestone: \"${change.newValue}\""
        "MILESTONE_COMPLETED" -> "Completed milestone: \"${change.newValue}\""
        "MILESTONE_UNCOMPLETED" -> "Uncompleted milestone: \"${change.newValue}\""
        "MILESTONE_REMOVED" -> "Removed milestone: \"${change.newValue}\""
        "NOTES_UPDATED" -> "Notes updated"
        "CATEGORY_CHANGE" -> "Category changed to ${change.newValue}"
        "TIMELINE_CHANGE" -> "Timeline changed to ${change.newValue}"
        "GOAL_CREATED" -> "Goal created"
        else -> "${change.field.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }}: ${change.newValue}"
    }
}