// GoalDetailScreen.kt
package az.tribe.lifeplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.ui.components.AddDependencyBottomSheet
import az.tribe.lifeplanner.ui.components.DependenciesCard
import az.tribe.lifeplanner.ui.components.GlassCard
import az.tribe.lifeplanner.ui.components.GoalDetailActionSheet
import az.tribe.lifeplanner.ui.components.GoalDetailDialogs
import az.tribe.lifeplanner.ui.components.GoalDetailHeroHeader
import az.tribe.lifeplanner.ui.components.StatusToggleButtons
import az.tribe.lifeplanner.ui.components.backgroundColor
import az.tribe.lifeplanner.ui.dependency.GoalDependencyViewModel
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign
import az.tribe.lifeplanner.ui.theme.gradientColors
import kotlinx.datetime.LocalDate
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    goalId: String,
    viewModel: GoalViewModel,
    dependencyViewModel: GoalDependencyViewModel = koinInject(),
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onViewDependencyGraph: (String) -> Unit = {},
    onNavigateToGoal: (String) -> Unit = {}
) {
    val goals by viewModel.goals.collectAsState()
    val goal = goals.find { it.id == goalId }
    val dependencyUiState by dependencyViewModel.uiState.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var showAddMilestoneDialog by remember { mutableStateOf(false) }
    var showCompleteConfirmDialog by remember { mutableStateOf(false) }
    var showAllMilestonesCompletedDialog by remember { mutableStateOf(false) }
    var showHistoryModal by remember { mutableStateOf(false) }
    var showAddDependencySheet by remember { mutableStateOf(false) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Watch for prompt to complete goal when all milestones are done
    val promptCompleteGoalId by viewModel.promptCompleteGoal.collectAsState()
    LaunchedEffect(promptCompleteGoalId) {
        if (promptCompleteGoalId == goalId) {
            showAllMilestonesCompletedDialog = true
            viewModel.clearCompleteGoalPrompt()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadAllGoals()
        dependencyViewModel.loadData()
    }

    // Load dependencies for current goal when goal changes
    LaunchedEffect(goalId) {
        goal?.let { dependencyViewModel.selectGoal(it) }
    }

    if (goal == null) {
        GoalNotFoundState(onBackClick = onBackClick)
        return
    }

    val primaryColor = goal.category.backgroundColor()

    val gradientColors = goal.category.gradientColors()

    Scaffold(
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                actions = {
                    // Overflow menu
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    onEditClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("History") },
                                leadingIcon = { Icon(Icons.Rounded.History, null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showHistoryModal = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = gradientColors.first(),
                    scrolledContainerColor = gradientColors.first(),
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            // Simple FAB that opens action sheet
            FloatingActionButton(
                onClick = { showActionSheet = true },
                containerColor = primaryColor,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 8.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Actions"
                )
            }
        },
        containerColor = goal.category.gradientColors().first(),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .background(color = MaterialTheme.colorScheme.background)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Hero Header with Progress (auto-calculated from milestones)
            item {
                GoalDetailHeroHeader(
                    modifier = Modifier,
                    goal = goal
                )
            }

            // Status Toggle Buttons
            item {
                StatusToggleButtons(
                    currentStatus = goal.status,
                    onStatusChange = { newStatus ->
                        if (newStatus == GoalStatus.COMPLETED && goal.status != GoalStatus.COMPLETED) {
                            showCompleteConfirmDialog = true
                        } else {
                            viewModel.updateGoalStatus(goalId, newStatus)
                            viewModel.loadAllGoals()
                        }
                    }
                )
            }

            // Notes Card
            item {
                ModernNotesCard(
                    notes = goal.notes,
                    onNotesClick = { showNotesDialog = true }
                )
            }

            // Milestones Card
            item {
                if (goal.milestones.isNotEmpty()) {
                    ModernMilestonesCard(
                        milestones = goal.milestones,
                        onMilestoneToggle = { milestoneId ->
                            viewModel.toggleMilestoneCompletion(goalId, milestoneId)
                            viewModel.loadAllGoals()
                        },
                        onAddMilestone = { showAddMilestoneDialog = true }
                    )
                } else {
                    EmptyMilestonesCard(
                        onAddMilestone = { showAddMilestoneDialog = true }
                    )
                }
            }

            // Dependencies section
            item {
                DependenciesCard(
                    dependencies = dependencyUiState.selectedGoalDependencies,
                    goals = dependencyUiState.allGoals,
                    currentGoalId = goalId,
                    suggestedDependencies = dependencyUiState.suggestedDependencies,
                    onAddDependency = { showAddDependencySheet = true },
                    onRemoveDependency = { dependencyId ->
                        dependencyViewModel.removeDependency(dependencyId)
                    },
                    onViewDependencyGraph = { onViewDependencyGraph(goalId) },
                    onGoalClick = { linkedGoalId ->
                        onNavigateToGoal(linkedGoalId)
                    }
                )
            }
        }
    }

    // Action Sheet
    GoalDetailActionSheet(
        isVisible = showActionSheet,
        goal = goal,
        onDismiss = { showActionSheet = false },
        onEditClick = onEditClick,
        onAddMilestoneClick = { showAddMilestoneDialog = true },
        onHistoryClick = { showHistoryModal = true },
        onDeleteClick = { showDeleteDialog = true }
    )

    // Add Dependency Bottom Sheet
    AddDependencyBottomSheet(
        isVisible = showAddDependencySheet,
        currentGoal = goal,
        availableGoals = dependencyUiState.allGoals.filter { it.id != goalId },
        onDismiss = { showAddDependencySheet = false },
        onAddDependency = { targetGoalId, dependencyType ->
            dependencyViewModel.addDependency(goalId, targetGoalId, dependencyType)
        }
    )

    // History Modal
    GoalHistoryModal(
        isVisible = showHistoryModal,
        goalId = goalId,
        viewModel = viewModel,
        onDismiss = { showHistoryModal = false }
    )

    // Dialogs
    GoalDetailDialogs(
        goal = goal,
        goalId = goalId,
        viewModel = viewModel,
        showDeleteDialog = showDeleteDialog,
        showNotesDialog = showNotesDialog,
        showAddMilestoneDialog = showAddMilestoneDialog,
        showCompleteConfirmDialog = showCompleteConfirmDialog,
        showAllMilestonesCompletedDialog = showAllMilestonesCompletedDialog,
        onDismissDelete = { showDeleteDialog = false },
        onDismissNotes = { showNotesDialog = false },
        onDismissAddMilestone = { showAddMilestoneDialog = false },
        onDismissComplete = { showCompleteConfirmDialog = false },
        onDismissAllMilestonesCompleted = { showAllMilestonesCompletedDialog = false },
        onBackClick = onBackClick
    )
}

@Composable
fun GoalNotFoundState(
    onBackClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Goal not found",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            FilledTonalButton(onClick = onBackClick) {
                Text("Go Back")
            }
        }
    }
}

@Composable
private fun ModernNotesCard(
    notes: String,
    onNotesClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onNotesClick() }
    ) {
        Column(
            modifier = Modifier.padding(LifePlannerDesign.Padding.standard),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Notes,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Notes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onNotesClick,
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "Edit Notes",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = if (notes.isBlank()) "Tap to add notes..." else notes,
                style = MaterialTheme.typography.bodyMedium,
                color = if (notes.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ModernMilestonesCard(
    milestones: List<Milestone>,
    onMilestoneToggle: (String) -> Unit,
    onAddMilestone: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(LifePlannerDesign.Padding.standard),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Flag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Milestones",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    // Progress badge
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${milestones.count { it.isCompleted }}/${milestones.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                IconButton(
                    onClick = onAddMilestone,
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "Add Milestone",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            milestones.forEach { milestone ->
                MilestoneItem(
                    milestone = milestone,
                    onClick = { onMilestoneToggle(milestone.id) }
                )
            }
        }
    }
}

@Composable
private fun MilestoneItem(
    milestone: Milestone,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (milestone.isCompleted)
                Icons.Rounded.CheckCircle
            else
                Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (milestone.isCompleted)
                Color(0xFF66BB6A)
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = milestone.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            milestone.dueDate?.let { date ->
                Text(
                    text = formatDate(date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyMilestonesCard(
    onAddMilestone: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Rounded.Flag,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )

            Text(
                text = "No milestones yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Break down your goal into smaller milestones",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onAddMilestone,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add First Milestone")
            }
        }
    }
}

// Helper function to format dates
private fun formatDate(date: LocalDate): String {
    val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    return "${months[date.monthNumber - 1]} ${date.dayOfMonth}, ${date.year}"
}
