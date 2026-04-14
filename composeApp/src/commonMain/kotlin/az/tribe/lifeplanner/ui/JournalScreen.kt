package az.tribe.lifeplanner.ui

import androidx.compose.animation.*
import co.touchlab.kermit.Logger
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.JournalEntry
import az.tribe.lifeplanner.domain.model.JournalPrompts
import az.tribe.lifeplanner.ui.components.DayEntriesBottomSheet
import az.tribe.lifeplanner.ui.components.EmptyStateCard
import az.tribe.lifeplanner.ui.components.GlassCard
import az.tribe.lifeplanner.ui.components.MoodCalendar
import az.tribe.lifeplanner.ui.components.rememberHapticManager
import az.tribe.lifeplanner.ui.GoalViewModel
import az.tribe.lifeplanner.ui.habit.HabitViewModel
import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.ui.journal.JournalViewModel
import kotlinx.coroutines.launch
import kotlinx.datetime.number
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    onNavigateBack: () -> Unit,
    onEntryClick: (String) -> Unit = {},
    onNavigateToWizard: () -> Unit = {},
    isFromBottomNav: Boolean = false,
    viewModel: JournalViewModel = koinViewModel(),
    goalViewModel: GoalViewModel = koinInject(),
    habitViewModel: HabitViewModel = koinViewModel(),
    aiProxy: AiProxyService = koinInject()
) {
    val entries by viewModel.entries.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showNewEntryDialog by viewModel.showNewEntryDialog.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()

    // Get goals and habits for linking
    val goals by goalViewModel.goals.collectAsState()
    val habitsWithStatus by habitViewModel.habits.collectAsState()
    val habits = habitsWithStatus.map { it.habit }

    var isCalendarExpanded by remember { mutableStateOf(false) }
    val error by viewModel.error.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Show error in snackbar
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        floatingActionButton = {
            // Wrapped in Box with bottom padding to stay above bottom nav
            Box(modifier = Modifier.padding(bottom = 72.dp)) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToWizard,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Write")
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.primary,
                    actionContentColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues = PaddingValues(top = padding.calculateTopPadding())),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = 136.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero banner
            item(key = "hero_banner") {
                JournalHeroBanner(
                    entryCount = entries.size,
                    isFromBottomNav = isFromBottomNav,
                    onNavigateBack = onNavigateBack,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // Mood Calendar (has its own 16dp horizontal padding)
            item(key = "mood_calendar") {
                MoodCalendar(
                    entries = entries,
                    selectedMonth = selectedMonth,
                    isExpanded = isCalendarExpanded,
                    onToggleExpand = { isCalendarExpanded = !isCalendarExpanded },
                    onMonthChange = { viewModel.setSelectedMonth(it) },
                    onDayClick = { date ->
                        val entriesForDay = viewModel.getEntriesForDay(date)
                        if (entriesForDay.isNotEmpty()) {
                            viewModel.selectDay(date)
                        }
                    }
                )
            }

            if (isLoading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (entries.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Your story starts here",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tap Write to capture your first thought",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                items(
                    items = entries,
                    key = { it.id }
                ) { entry ->
                    val linkedGoalName = entry.linkedGoalId?.let { goalId ->
                        goals.find { it.id == goalId }?.title
                    }
                    val linkedHabitName = entry.linkedHabitId?.let { habitId ->
                        habits.find { it.id == habitId }?.title
                    }

                    SwipeableJournalEntryCard(
                        entry = entry,
                        onClick = { onEntryClick(entry.id) },
                        onDelete = { viewModel.deleteEntry(entry.id) },
                        linkedGoalName = linkedGoalName,
                        linkedHabitName = linkedHabitName,
                        modifier = Modifier.padding(horizontal = 16.dp).animateItem()
                    )
                }
            }
        }

        if (showNewEntryDialog) {
            NewJournalEntryBottomSheet(
                onDismiss = { viewModel.hideNewEntryDialog() },
                onConfirm = { title, content, mood, tags, linkedGoalId, linkedHabitId, promptUsed ->
                    viewModel.createEntry(
                        title = title,
                        content = content,
                        mood = mood,
                        linkedGoalId = linkedGoalId,
                        linkedHabitId = linkedHabitId,
                        tags = tags,
                        promptUsed = promptUsed
                    )
                },
                goals = goals,
                habits = habits,
                aiProxy = aiProxy
            )
        }

        // Day Entries Bottom Sheet
        selectedDay?.let { date ->
            DayEntriesBottomSheet(
                date = date,
                entries = viewModel.getEntriesForDay(date),
                onDismiss = { viewModel.clearSelectedDay() },
                onEntryClick = { entryId ->
                    viewModel.clearSelectedDay()
                    onEntryClick(entryId)
                },
                onAddEntry = {
                    viewModel.clearSelectedDay()
                    viewModel.showNewEntryDialog()
                }
            )
        }
    }
}

/**
 * Swipeable wrapper for JournalEntryCard
 * - Swipe left (end-to-start): Delete with confirmation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableJournalEntryCard(
    entry: JournalEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    linkedGoalName: String? = null,
    linkedHabitName: String? = null,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val haptic = rememberHapticManager()

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    haptic.warning()
                    showDeleteDialog = true
                    false
                }
                else -> false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.25f }
    )

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            JournalSwipeBackground(dismissDirection = dismissState.dismissDirection)
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        JournalEntryCard(
            entry = entry,
            onClick = onClick,
            linkedGoalName = linkedGoalName,
            linkedHabitName = linkedHabitName
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
            title = { Text("Delete Entry?") },
            text = { Text("Are you sure you want to delete this journal entry? This action cannot be undone.") },
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
private fun JournalSwipeBackground(dismissDirection: SwipeToDismissBoxValue) {
    val color by animateColorAsState(
        targetValue = when (dismissDirection) {
            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
            else -> Color.Transparent
        },
        animationSpec = tween(300),
        label = "swipeBackgroundColor"
    )

    val scale by animateFloatAsState(
        targetValue = if (dismissDirection == SwipeToDismissBoxValue.EndToStart) 1f else 0.8f,
        animationSpec = tween(300),
        label = "iconScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (dismissDirection == SwipeToDismissBoxValue.EndToStart) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Delete",
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .scale(scale)
            )
        }
    }
}

@Composable
private fun JournalEntryCard(
    entry: JournalEntry,
    onClick: () -> Unit,
    linkedGoalName: String? = null,
    linkedHabitName: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = entry.mood.emoji,
                    fontSize = 28.sp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatJournalDate(entry.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Show linked goal or habit chips
            val hasLinks = linkedGoalName != null || linkedHabitName != null
            if (hasLinks) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    linkedGoalName?.let { goalName ->
                        LinkedItemChip(
                            icon = Icons.Rounded.Flag,
                            text = goalName,
                            color = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                    linkedHabitName?.let { habitName ->
                        LinkedItemChip(
                            icon = Icons.Rounded.Repeat,
                            text = habitName,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    }
                }
            }

            if (entry.tags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    entry.tags.take(3).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkedItemChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@Composable
fun MoodPicker(
    selectedMood: Mood,
    onMoodSelected: (Mood) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Mood.entries.sortedBy { it.score }.forEach { mood ->
            MoodButton(
                mood = mood,
                isSelected = mood == selectedMood,
                onClick = { onMoodSelected(mood) }
            )
        }
    }
}

@Composable
private fun MoodButton(
    mood: Mood,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = mood.emoji,
                fontSize = 28.sp
            )
        }
        if (isSelected) {
            Text(
                text = mood.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewJournalEntryBottomSheet(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Mood, List<String>, String?, String?, String?) -> Unit,
    goals: List<Goal> = emptyList(),
    habits: List<Habit> = emptyList(),
    preselectedGoalId: String? = null,
    preselectedHabitId: String? = null,
    aiProxy: AiProxyService
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf(Mood.NEUTRAL) }
    var tagsText by remember { mutableStateOf("") }
    var selectedGoalId by remember { mutableStateOf(preselectedGoalId) }
    var selectedHabitId by remember { mutableStateOf(preselectedHabitId) }
    var showGoalDropdown by remember { mutableStateOf(false) }
    var showHabitDropdown by remember { mutableStateOf(false) }
    var isGeneratingAi by remember { mutableStateOf(false) }
    var aiErrorMessage by remember { mutableStateOf<String?>(null) }
    var selectedPrompt by remember { mutableStateOf<String?>(null) }
    var showPromptLibrary by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val haptic = rememberHapticManager()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "New Journal Entry",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close"
                    )
                }
            }

            // Content
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Mood Picker
                item {
                    Text(
                        "How are you feeling?",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MoodPicker(
                        selectedMood = selectedMood,
                        onMoodSelected = { selectedMood = it }
                    )
                }

                // Link to Goal/Habit section (moved above title)
                if (goals.isNotEmpty() || habits.isNotEmpty()) {
                    item {
                        Text(
                            "Link to (optional)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Goal picker
                            if (goals.isNotEmpty()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        onClick = { showGoalDropdown = true },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selectedGoalId != null)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Flag,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = if (selectedGoalId != null)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = selectedGoalId?.let { id ->
                                                    goals.find { it.id == id }?.title ?: "Goal"
                                                } ?: "Goal",
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (selectedGoalId != null) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Clear",
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable { selectedGoalId = null },
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showGoalDropdown,
                                        onDismissRequest = { showGoalDropdown = false }
                                    ) {
                                        goals.take(10).forEach { goal ->
                                            DropdownMenuItem(
                                                text = { Text(goal.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                onClick = {
                                                    selectedGoalId = goal.id
                                                    showGoalDropdown = false
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.Flag, contentDescription = null)
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Habit picker
                            if (habits.isNotEmpty()) {
                                Box(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        onClick = { showHabitDropdown = true },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selectedHabitId != null)
                                            MaterialTheme.colorScheme.secondaryContainer
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Repeat,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = if (selectedHabitId != null)
                                                    MaterialTheme.colorScheme.secondary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = selectedHabitId?.let { id ->
                                                    habits.find { it.id == id }?.title ?: "Habit"
                                                } ?: "Habit",
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (selectedHabitId != null) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Clear",
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable { selectedHabitId = null },
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showHabitDropdown,
                                        onDismissRequest = { showHabitDropdown = false }
                                    ) {
                                        habits.take(10).forEach { habit ->
                                            DropdownMenuItem(
                                                text = { Text(habit.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                                onClick = {
                                                    selectedHabitId = habit.id
                                                    showHabitDropdown = false
                                                },
                                                leadingIcon = {
                                                    Icon(Icons.Rounded.Repeat, contentDescription = null)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Title field
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        placeholder = { Text("What's on your mind?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Prompt picker (optional)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Prompt (optional)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium
                        )

                        // Selected prompt or picker button
                        Surface(
                            onClick = { showPromptLibrary = true },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedPrompt != null)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (selectedPrompt != null)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = selectedPrompt ?: "Browse prompt library...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selectedPrompt != null)
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontStyle = if (selectedPrompt != null)
                                        androidx.compose.ui.text.font.FontStyle.Italic
                                    else
                                        androidx.compose.ui.text.font.FontStyle.Normal,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (selectedPrompt != null) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear prompt",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { selectedPrompt = null },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // AI Generate Button - requires title or prompt
                        val canUseAi = title.isNotBlank() || selectedPrompt != null

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    isGeneratingAi = true
                                    aiErrorMessage = null
                                    try {
                                        val linkedGoal = selectedGoalId?.let { id -> goals.find { it.id == id } }
                                        val linkedHabit = selectedHabitId?.let { id -> habits.find { it.id == id } }
                                        val result = az.tribe.lifeplanner.ui.journal.generateAiJournalEntry(
                                            aiProxy = aiProxy,
                                            mood = selectedMood,
                                            prompt = selectedPrompt ?: "",
                                            userNote = title,
                                            linkedGoal = linkedGoal,
                                            linkedHabit = linkedHabit
                                        )
                                        if (result != null) {
                                            if (title.isBlank()) {
                                                title = result.title
                                            }
                                            content = result.content
                                            if (result.tags.isNotEmpty()) {
                                                tagsText = result.tags.joinToString(", ")
                                            }
                                        } else {
                                            aiErrorMessage = "AI generation returned no result. Please try again."
                                        }
                                    } catch (e: Exception) {
                                        Logger.e("JournalScreen", e) { "AI journal generation failed" }
                                        aiErrorMessage = when {
                                            e.message?.contains("timeout", ignoreCase = true) == true ||
                                            e.message?.contains("connect", ignoreCase = true) == true ||
                                            e.message?.contains("network", ignoreCase = true) == true ->
                                                "No internet connection. Check your network and try again."
                                            e.message?.contains("authenticated", ignoreCase = true) == true ||
                                            e.message?.contains("sign in", ignoreCase = true) == true ->
                                                "Session expired. Please sign in again."
                                            else -> "AI generation failed. Please try again."
                                        }
                                    } finally {
                                        isGeneratingAi = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canUseAi && !isGeneratingAi,
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = canUseAi)
                        ) {
                            if (isGeneratingAi) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generating...")
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Write with AI")
                            }
                        }

                        // Show AI error inline
                        aiErrorMessage?.let { errorMsg ->
                            Text(
                                text = errorMsg,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Content field
                item {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Your thoughts") },
                        placeholder = { Text("Write your reflection...") },
                        minLines = 5,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Tags field
                item {
                    OutlinedTextField(
                        value = tagsText,
                        onValueChange = { tagsText = it },
                        label = { Text("Tags (AI will suggest)") },
                        placeholder = { Text("gratitude, goals, reflection") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Save button
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank() && content.isNotBlank()) {
                                haptic.success()
                                val tags = tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                onConfirm(title, content, selectedMood, tags, selectedGoalId, selectedHabitId, selectedPrompt)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = title.isNotBlank() && content.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Save Entry",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Prompt Library Bottom Sheet
    if (showPromptLibrary) {
        PromptLibrarySheet(
            onDismiss = { showPromptLibrary = false },
            onPromptSelected = { prompt ->
                selectedPrompt = prompt
                showPromptLibrary = false
            }
        )
    }
}

/**
 * Prompt Library Bottom Sheet - shows categorized prompts for user to choose
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptLibrarySheet(
    onDismiss: () -> Unit,
    onPromptSelected: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Prompt Library",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close"
                    )
                }
            }

            // Prompt categories
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Daily Reflection
                item {
                    PromptCategory(
                        title = "Daily Reflection",
                        emoji = "🌅",
                        prompts = JournalPrompts.dailyReflection,
                        onPromptClick = onPromptSelected
                    )
                }

                // Goal Reflection
                item {
                    PromptCategory(
                        title = "Goal Reflection",
                        emoji = "🎯",
                        prompts = JournalPrompts.goalReflection,
                        onPromptClick = onPromptSelected
                    )
                }

                // Mood Exploration
                item {
                    PromptCategory(
                        title = "Mood Exploration",
                        emoji = "💭",
                        prompts = JournalPrompts.moodExploration,
                        onPromptClick = onPromptSelected
                    )
                }

                // Weekly Review
                item {
                    PromptCategory(
                        title = "Weekly Review",
                        emoji = "📊",
                        prompts = JournalPrompts.weeklyReview,
                        onPromptClick = onPromptSelected
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PromptCategory(
    title: String,
    emoji: String,
    prompts: List<String>,
    onPromptClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(emoji, fontSize = 20.sp)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        prompts.forEach { prompt ->
            Surface(
                onClick = { onPromptClick(prompt) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun formatJournalDate(date: kotlinx.datetime.LocalDate): String {
    val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    return "${months[date.month.number - 1]} ${date.day}, ${date.year}"
}

@Composable
private fun JournalHeroBanner(
    entryCount: Int,
    isFromBottomNav: Boolean,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
            cornerRadius = 20.dp
        ) {
            Column {
                // Gradient accent bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 16.dp,
                            start = if (isFromBottomNav) 20.dp else 8.dp,
                            end = 20.dp,
                            bottom = 16.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isFromBottomNav) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Journal",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = if (entryCount > 0) "$entryCount entries" else "Start reflecting",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
}

