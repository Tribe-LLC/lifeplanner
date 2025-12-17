// GoalDetailScreen.kt
package az.tribe.lifeplanner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.DependencyType
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.GoalDependency
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.ui.components.AddDependencyBottomSheet
import az.tribe.lifeplanner.ui.components.DependenciesCard
import az.tribe.lifeplanner.ui.components.GoalDetailDialogs
import az.tribe.lifeplanner.ui.components.StatusChip
import az.tribe.lifeplanner.ui.components.backgroundColor
import az.tribe.lifeplanner.ui.dependency.GoalDependencyViewModel
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
    var showProgressDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var showAddMilestoneDialog by remember { mutableStateOf(false) }
    var showCompleteConfirmDialog by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    var showHistoryModal by remember { mutableStateOf(false) }
    var showAddDependencySheet by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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



    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GoalDetailTopBar(
                goal = goal,
                primaryColor = primaryColor,
                scrollBehavior = scrollBehavior,
                onBackClick = onBackClick,
                onEditClick = onEditClick,
                onDeleteClick = { showDeleteDialog = true },
                onHistoryClick = { showHistoryModal = true

                println("Goal model $showHistoryModal")} // Update to trigger modal visibility
            )
        },
        floatingActionButton = {
            QuickActionsFAB(
                goal = goal,
                expanded = fabExpanded,
                onExpandedChange = { fabExpanded = it },
                onMarkCompleted = { showCompleteConfirmDialog = true },
                onMarkInProgress = {
                    viewModel.updateGoalStatus(goalId, GoalStatus.IN_PROGRESS)
                    viewModel.loadAllGoals()
                },
                onMarkNotStarted = {
                    viewModel.updateGoalStatus(goalId, GoalStatus.NOT_STARTED)
                    viewModel.loadAllGoals()
                },
                onUpdateProgress = { showProgressDialog = true },
                onAddMilestone = { showAddMilestoneDialog = true }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                GoalHeaderCard(
                    goal = goal,
                    primaryColor = primaryColor,
                    onProgressClick = { showProgressDialog = true }
                )
            }


            item {
                NotesCard(
                    notes = goal.notes,
                    onNotesClick = { showNotesDialog = true }
                )
            }

            if (goal.milestones.isNotEmpty()) {
                item {
                    MilestonesCard(
                        milestones = goal.milestones,
                        onMilestoneToggle = { milestoneId ->
                            viewModel.toggleMilestoneCompletion(goalId, milestoneId)
                            viewModel.loadAllGoals()
                        }
                    )
                }
            } else {
                item {
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
        showProgressDialog = showProgressDialog,
        showNotesDialog = showNotesDialog,
        showAddMilestoneDialog = showAddMilestoneDialog,
        showCompleteConfirmDialog = showCompleteConfirmDialog,
        onDismissDelete = { showDeleteDialog = false },
        onDismissProgress = { showProgressDialog = false },
        onDismissNotes = { showNotesDialog = false },
        onDismissAddMilestone = { showAddMilestoneDialog = false },
        onDismissComplete = { showCompleteConfirmDialog = false },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailTopBar(
    goal: Goal,
    primaryColor: Color,
    scrollBehavior: TopAppBarScrollBehavior,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    LargeTopAppBar(
        title = {
            Column {
                Text(
                    text = goal.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

            }
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
            IconButton(onClick = onHistoryClick) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = "View History"
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Delete Goal",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            IconButton(onClick = onEditClick) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "Edit Goal"
                )
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.largeTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun GoalHeaderCard(
    goal: Goal,
    primaryColor: Color,
    onProgressClick: () -> Unit
) {
    val progressAnimation by animateFloatAsState(
        targetValue = (goal.progress?.toFloat() ?: 0f) / 100f,
        animationSpec = tween(1000),
        label = "progress"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status and Description
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (goal.description.isNotBlank()) {
                        Text(
                            text = goal.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                StatusChip(status = goal.status)
            }

            // Progress section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Progress (${goal.progress ?: 0}%)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Animated progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onProgressClick() }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressAnimation)
                            .fillMaxHeight()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = goal.category.gradientColors()
                                ),
                                shape = RoundedCornerShape(6.dp)
                            )
                    )
                }
            }

            // Details row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DetailItem(
                    icon = Icons.Rounded.Category,
                    label = "Category",
                    value = goal.category.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = primaryColor
                )

                DetailItem(
                    icon = Icons.Rounded.Schedule,
                    label = "Timeline",
                    value = goal.timeline.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = primaryColor
                )

                DetailItem(
                    icon = Icons.Rounded.Event,
                    label = "Due Date",
                    value = formatDate(goal.dueDate),
                    color = primaryColor
                )
            }
        }
    }
}

@Composable
fun DetailItem(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun NotesCard(
    notes: String,
    onNotesClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onNotesClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
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
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Notes",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
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
fun MilestonesCard(
    milestones: List<Milestone>,
    onMilestoneToggle: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Milestones (${milestones.count { it.isCompleted }}/${milestones.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
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
fun MilestoneItem(
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
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
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
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

@Composable
fun QuickActionsFAB(
    goal: Goal,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onMarkCompleted: () -> Unit,
    onMarkInProgress: () -> Unit,
    onMarkNotStarted: () -> Unit,
    onUpdateProgress: () -> Unit,
    onAddMilestone: () -> Unit
) {
    val primaryColor = goal.category.backgroundColor()

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Extended FAB options
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + androidx.compose.animation.slideInVertically { it },
            exit = fadeOut() + androidx.compose.animation.slideOutVertically { it }
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Add Milestone
                SmallFloatingActionButton(
                    onClick = {
                        onAddMilestone()
                        onExpandedChange(false)
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null
                        )
                        Text("Add Milestone", style = MaterialTheme.typography.labelLarge)
                    }
                }

                // Update Progress (always show unless completed)
                if (goal.status != GoalStatus.COMPLETED) {
                    SmallFloatingActionButton(
                        onClick = {
                            onUpdateProgress()
                            onExpandedChange(false)
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Timeline,
                                contentDescription = null
                            )
                            Text("Update Progress", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                // Status-based action - suggest next status
                when (goal.status) {
                    GoalStatus.NOT_STARTED -> {
                        SmallFloatingActionButton(
                            onClick = {
                                onMarkInProgress()
                                onExpandedChange(false)
                            },
                            containerColor = Color(0xFFFFA726),
                            contentColor = Color.White
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null
                                )
                                Text("Start Goal", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    GoalStatus.IN_PROGRESS -> {
                        SmallFloatingActionButton(
                            onClick = {
                                onMarkCompleted()
                                onExpandedChange(false)
                            },
                            containerColor = Color(0xFF66BB6A),
                            contentColor = Color.White
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null
                                )
                                Text("Complete Goal", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    GoalStatus.COMPLETED -> {
                        // For completed goals, show reset option
                        SmallFloatingActionButton(
                            onClick = {
                                onMarkNotStarted()
                                onExpandedChange(false)
                            },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null
                                )
                                Text("Reset Goal", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                // Reset option (always available except for NOT_STARTED goals)
                if (goal.status != GoalStatus.NOT_STARTED) {
                    SmallFloatingActionButton(
                        onClick = {
                            onMarkNotStarted()
                            onExpandedChange(false)
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null
                            )
                            Text("Reset", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // Main FAB
        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            containerColor = primaryColor,
            contentColor = Color.White,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 8.dp
            )
        ) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.Close else Icons.Filled.Add,
                contentDescription = if (expanded) "Close menu" else "Quick Actions"
            )
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