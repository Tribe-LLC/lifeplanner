package az.tribe.lifeplanner.ui

import androidx.compose.animation.*
import co.touchlab.kermit.Logger
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.HabitFrequency
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.ui.components.SwipeableHabitCard
import az.tribe.lifeplanner.ui.GoalViewModel
import az.tribe.lifeplanner.ui.habit.HabitViewModel
import az.tribe.lifeplanner.ui.journal.JournalViewModel
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

// Habit template data class
data class HabitTemplate(
    val title: String,
    val description: String,
    val category: GoalCategory,
    val frequency: HabitFrequency,
    val icon: ImageVector,
    val gradientColors: List<Color>
)

// Predefined habit templates
val habitTemplates = listOf(
    HabitTemplate(
        title = "Morning Meditation",
        description = "10 minutes of mindfulness",
        category = GoalCategory.SPIRITUAL,
        frequency = HabitFrequency.DAILY,
        icon = Icons.Rounded.SelfImprovement,
        gradientColors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
    ),
    HabitTemplate(
        title = "Exercise",
        description = "30 minutes workout",
        category = GoalCategory.PHYSICAL,
        frequency = HabitFrequency.DAILY,
        icon = Icons.Rounded.FitnessCenter,
        gradientColors = listOf(Color(0xFFFF6B6B), Color(0xFFFF8E53))
    ),
    HabitTemplate(
        title = "Read",
        description = "Read for 20 minutes",
        category = GoalCategory.CAREER,
        frequency = HabitFrequency.DAILY,
        icon = Icons.Rounded.Book,
        gradientColors = listOf(Color(0xFF11998E), Color(0xFF38EF7D))
    ),
    HabitTemplate(
        title = "Drink Water",
        description = "8 glasses of water",
        category = GoalCategory.PHYSICAL,
        frequency = HabitFrequency.DAILY,
        icon = Icons.Rounded.WaterDrop,
        gradientColors = listOf(Color(0xFF4FACFE), Color(0xFF00F2FE))
    ),
    HabitTemplate(
        title = "Journal",
        description = "Write daily reflections",
        category = GoalCategory.EMOTIONAL,
        frequency = HabitFrequency.DAILY,
        icon = Icons.Rounded.Edit,
        gradientColors = listOf(Color(0xFFF093FB), Color(0xFFF5576C))
    ),
    HabitTemplate(
        title = "Sleep Early",
        description = "Be in bed by 10 PM",
        category = GoalCategory.PHYSICAL,
        frequency = HabitFrequency.DAILY,
        icon = Icons.Rounded.Bedtime,
        gradientColors = listOf(Color(0xFF5B247A), Color(0xFF1BCEDF))
    ),
    HabitTemplate(
        title = "Connect with Family",
        description = "Quality time with loved ones",
        category = GoalCategory.FAMILY,
        frequency = HabitFrequency.DAILY,
        icon = Icons.Rounded.Favorite,
        gradientColors = listOf(Color(0xFFFC466B), Color(0xFF3F5EFB))
    ),
    HabitTemplate(
        title = "Save Money",
        description = "Track daily expenses",
        category = GoalCategory.FINANCIAL,
        frequency = HabitFrequency.DAILY,
        icon = Icons.Rounded.Savings,
        gradientColors = listOf(Color(0xFF434343), Color(0xFF000000))
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitTrackerScreen(
    onNavigateBack: () -> Unit,
    isFromBottomNav: Boolean = false,
    viewModel: HabitViewModel = koinViewModel(),
    journalViewModel: JournalViewModel = koinViewModel(),
    goalViewModel: GoalViewModel = koinInject(),
    aiProxy: AiProxyService = koinInject()
) {
    val habits by viewModel.habits.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val showAddSheet by viewModel.showAddHabitDialog.collectAsState()
    val recentCheckIn by viewModel.recentCheckIn.collectAsState()
    val goals by goalViewModel.goals.collectAsState()

    var habitToEdit by remember { mutableStateOf<Habit?>(null) }
    var showReflectionSheet by remember { mutableStateOf(false) }
    var habitForReflection by remember { mutableStateOf<Habit?>(null) }
    var linkedGoalForReflection by remember { mutableStateOf<Goal?>(null) }

    // Onboarding: keep template setup visible until all added or user skips
    var onboardingDismissed by remember { mutableStateOf(false) }
    val existingHabitTitles = remember(habits) {
        habits.map { it.habit.title.lowercase() }.toSet()
    }
    val allTemplatesAdded = habitTemplates.all { it.title.lowercase() in existingHabitTitles }
    val showOnboarding = !onboardingDismissed && !allTemplatesAdded

    val snackbarHostState = remember { SnackbarHostState() }

    // Show smart reminder snackbar events
    LaunchedEffect(Unit) {
        viewModel.reminderEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Show error in snackbar
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    // Show reflection prompt after habit check-in
    LaunchedEffect(recentCheckIn) {
        recentCheckIn?.let { checkIn ->
            val result = snackbarHostState.showSnackbar(
                message = "Great job completing ${checkIn.habit.title}!",
                actionLabel = "Add Reflection",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                habitForReflection = checkIn.habit
                // Find linked goal if any
                linkedGoalForReflection = checkIn.habit.linkedGoalId?.let { goalId ->
                    goals.find { it.id == goalId }
                }
                showReflectionSheet = true
            }
            viewModel.clearRecentCheckIn()
        }
    }

    val todayCompleted = viewModel.getTodayCompletedCount()
    val totalHabits = viewModel.getTotalHabitsCount()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Habit Tracker",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        if (totalHabits > 0) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (todayCompleted == totalHabits)
                                    Color(0xFF4CAF50).copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = "$todayCompleted/$totalHabits",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = if (todayCompleted == totalHabits)
                                        Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (!isFromBottomNav) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            // Show FAB when not in onboarding
            // Wrapped in Box with bottom padding to stay above bottom nav
            if (!showOnboarding && habits.isNotEmpty()) {
                Box(modifier = Modifier.padding(bottom = 96.dp)) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.showAddHabitDialog() },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("New Habit", color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionColor = MaterialTheme.colorScheme.primary,
                    actionContentColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues = PaddingValues(top = padding.calculateTopPadding()))
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (showOnboarding) {
                // Template setup — persists until all added or user skips
                EmptyHabitState(
                    existingHabitTitles = existingHabitTitles,
                    onTemplateClick = { template ->
                        viewModel.createHabit(
                            title = template.title,
                            description = template.description,
                            category = template.category,
                            frequency = template.frequency
                        )
                    },
                    onCreateCustomClick = { viewModel.showAddHabitDialog() },
                    onSkip = { onboardingDismissed = true }
                )
            } else {
                val completionRate = if (totalHabits > 0) (todayCompleted.toFloat() / totalHabits * 100).toInt() else 0
                val streakLeader = viewModel.getStreakLeader()

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = 136.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Stats banner — scrolls away with the list
                    item(key = "stats") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatItem(
                                    value = "$todayCompleted/$totalHabits",
                                    label = "Done",
                                    icon = Icons.Rounded.CheckCircle,
                                    color = if (todayCompleted == totalHabits && totalHabits > 0)
                                        Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                                )
                                StatItem(
                                    value = "$completionRate%",
                                    label = "Rate",
                                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                streakLeader?.let {
                                    StatItem(
                                        value = "${it.currentStreak}d",
                                        label = "Streak",
                                        icon = Icons.Rounded.LocalFireDepartment,
                                        color = Color(0xFFFF6B35)
                                    )
                                }
                            }
                        }
                    }

                    items(
                        items = habits,
                        key = { it.habit.id }
                    ) { habitWithStatus ->
                        SwipeableHabitCard(
                            habitWithStatus = habitWithStatus,
                            onCheckIn = { viewModel.toggleCheckIn(habitWithStatus.habit.id) },
                            onDelete = { viewModel.deleteHabit(habitWithStatus.habit.id) },
                            onEdit = { habitToEdit = habitWithStatus.habit },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }

        // Add Habit Bottom Sheet
        if (showAddSheet) {
            AddHabitBottomSheet(
                existingHabitTitles = existingHabitTitles,
                onDismiss = { viewModel.hideAddHabitDialog() },
                onConfirm = { title, description, category, frequency ->
                    viewModel.createHabit(
                        title = title,
                        description = description,
                        category = category,
                        frequency = frequency
                    )
                }
            )
        }

        // Edit Habit Bottom Sheet
        habitToEdit?.let { habit ->
            EditHabitBottomSheet(
                habit = habit,
                onDismiss = { habitToEdit = null },
                onConfirm = { updatedHabit ->
                    viewModel.updateHabit(updatedHabit)
                    habitToEdit = null
                }
            )
        }

        // Quick Reflection Bottom Sheet
        val currentHabitForReflection = habitForReflection
        if (showReflectionSheet && currentHabitForReflection != null) {
            QuickReflectionBottomSheet(
                habit = currentHabitForReflection,
                linkedGoal = linkedGoalForReflection,
                aiProxy = aiProxy,
                onDismiss = {
                    showReflectionSheet = false
                    habitForReflection = null
                    linkedGoalForReflection = null
                },
                onSave = { title, content, mood ->
                    journalViewModel.createEntry(
                        title = title,
                        content = content,
                        mood = mood,
                        linkedGoalId = linkedGoalForReflection?.id,
                        linkedHabitId = currentHabitForReflection.id
                    )
                    showReflectionSheet = false
                    habitForReflection = null
                    linkedGoalForReflection = null
                }
            )
        }
    }
}

@Composable
private fun EmptyHabitState(
    existingHabitTitles: Set<String>,
    onTemplateClick: (HabitTemplate) -> Unit,
    onCreateCustomClick: () -> Unit,
    onSkip: () -> Unit
) {
    val addedCount = habitTemplates.count { it.title.lowercase() in existingHabitTitles }
    val sortedTemplates = remember(existingHabitTitles) {
        habitTemplates.sortedBy { it.title.lowercase() in existingHabitTitles }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 100.dp // Space for bottom nav
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Progress section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Build Your Routine",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$addedCount/${habitTemplates.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                LinearProgressIndicator(
                    progress = { addedCount.toFloat() / habitTemplates.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF4CAF50),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Text(
                    text = when {
                        addedCount == 0 -> "Add starter habits for the best experience"
                        addedCount < habitTemplates.size / 2 -> "Great start! Keep going for better results"
                        addedCount < habitTemplates.size -> "Almost there! A full routine drives real progress"
                        else -> "All set! You're ready to build great habits"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Template Grid (2 columns) — unadded first, added last
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                sortedTemplates.chunked(2).forEach { rowTemplates ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowTemplates.forEach { template ->
                            val isAdded = template.title.lowercase() in existingHabitTitles
                            HabitTemplateCard(
                                template = template,
                                isAdded = isAdded,
                                onClick = { onTemplateClick(template) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill empty space if odd number
                        if (rowTemplates.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Create Custom + Skip buttons
        item {
            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(
                onClick = onCreateCustomClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Create Custom Habit",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (addedCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "I'm all set — start tracking",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun HabitTemplateCard(
    template: HabitTemplate,
    isAdded: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isAdded) 0.dp else 2.dp,
        onClick = { if (!isAdded) onClick() }
    ) {
        Box {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Icon with gradient background
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                if (isAdded) listOf(Color(0xFF4CAF50), Color(0xFF66BB6A))
                                else template.gradientColors
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isAdded) Icons.Rounded.Check else template.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = template.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = if (isAdded) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isAdded) "Added" else template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isAdded) Color(0xFF4CAF50)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isAdded) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Frequency chip
                Surface(
                    shape = RoundedCornerShape(50),
                    color = template.gradientColors.first().copy(alpha = 0.1f)
                ) {
                    Text(
                        text = template.frequency.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = template.gradientColors.first(),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}


@Composable
private fun StatItem(
    value: String,
    label: String,
    icon: ImageVector,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddHabitBottomSheet(
    existingHabitTitles: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onConfirm: (String, String, GoalCategory, HabitFrequency) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(GoalCategory.PHYSICAL) }
    var selectedFrequency by remember { mutableStateOf(HabitFrequency.DAILY) }
    var showTemplates by remember { mutableStateOf(false) }

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
                if (showTemplates) {
                    IconButton(onClick = { showTemplates = false }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Text(
                        "Pick a Template",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        "New Habit",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close"
                    )
                }
            }

            if (showTemplates) {
                // Template selection — picking a template saves immediately
                TemplateSelectionContent(
                    existingHabitTitles = existingHabitTitles,
                    onTemplateClick = { template ->
                        onConfirm(template.title, template.description, template.category, template.frequency)
                    },
                    onCustomClick = { showTemplates = false }
                )
            } else {
                // Custom form first (like goals), with template option
                CustomHabitForm(
                    title = title,
                    onTitleChange = { title = it },
                    description = description,
                    onDescriptionChange = { description = it },
                    selectedCategory = selectedCategory,
                    onCategoryChange = { selectedCategory = it },
                    selectedFrequency = selectedFrequency,
                    onFrequencyChange = { selectedFrequency = it },
                    onConfirm = {
                        if (title.isNotBlank()) {
                            onConfirm(title, description, selectedCategory, selectedFrequency)
                        }
                    },
                    onPickTemplate = { showTemplates = true }
                )
            }
        }
    }
}

@Composable
private fun TemplateSelectionContent(
    existingHabitTitles: Set<String> = emptySet(),
    onTemplateClick: (HabitTemplate) -> Unit,
    onCustomClick: () -> Unit
) {
    val sortedTemplates = remember(existingHabitTitles) {
        habitTemplates.sortedBy { it.title.lowercase() in existingHabitTitles }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Quick pick section
        item {
            Text(
                "Quick Pick",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Template grid (2 columns) — unadded first
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                sortedTemplates.chunked(2).forEach { rowTemplates ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowTemplates.forEach { template ->
                            val isAdded = template.title.lowercase() in existingHabitTitles
                            CompactTemplateCard(
                                template = template,
                                isAdded = isAdded,
                                onClick = { onTemplateClick(template) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowTemplates.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Divider
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    "or",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Custom button
        item {
            OutlinedButton(
                onClick = onCustomClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Create Your Own",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CompactTemplateCard(
    template: HabitTemplate,
    isAdded: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isAdded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        onClick = { if (!isAdded) onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with gradient background
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            if (isAdded) listOf(Color(0xFF4CAF50), Color(0xFF66BB6A))
                            else template.gradientColors
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAdded) Icons.Rounded.Check else template.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = if (isAdded) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isAdded) "Added" else template.frequency.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isAdded) Color(0xFF4CAF50)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isAdded) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomHabitForm(
    title: String,
    onTitleChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    selectedCategory: GoalCategory,
    onCategoryChange: (GoalCategory) -> Unit,
    selectedFrequency: HabitFrequency,
    onFrequencyChange: (HabitFrequency) -> Unit,
    onConfirm: () -> Unit,
    onPickTemplate: (() -> Unit)? = null
) {
    var expandedCategory by remember { mutableStateOf(false) }
    var expandedFrequency by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Habit name") },
            placeholder = { Text("e.g., Morning meditation") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text("Description (optional)") },
            placeholder = { Text("e.g., 10 minutes of mindfulness") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Category dropdown
        ExposedDropdownMenuBox(
            expanded = expandedCategory,
            onExpandedChange = { expandedCategory = !expandedCategory }
        ) {
            OutlinedTextField(
                value = selectedCategory.name.lowercase().replaceFirstChar { it.uppercase() },
                onValueChange = {},
                readOnly = true,
                label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                shape = RoundedCornerShape(12.dp)
            )

            ExposedDropdownMenu(
                expanded = expandedCategory,
                onDismissRequest = { expandedCategory = false }
            ) {
                GoalCategory.entries.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        onClick = {
                            onCategoryChange(category)
                            expandedCategory = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Frequency dropdown
        ExposedDropdownMenuBox(
            expanded = expandedFrequency,
            onExpandedChange = { expandedFrequency = !expandedFrequency }
        ) {
            OutlinedTextField(
                value = selectedFrequency.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Frequency") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFrequency) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                shape = RoundedCornerShape(12.dp)
            )

            ExposedDropdownMenu(
                expanded = expandedFrequency,
                onDismissRequest = { expandedFrequency = false }
            ) {
                HabitFrequency.entries.forEach { frequency ->
                    DropdownMenuItem(
                        text = { Text(frequency.displayName) },
                        onClick = {
                            onFrequencyChange(frequency)
                            expandedFrequency = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = title.isNotBlank(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "Create Habit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (onPickTemplate != null) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onPickTemplate,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Pick from Templates",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditHabitBottomSheet(
    habit: Habit,
    onDismiss: () -> Unit,
    onConfirm: (Habit) -> Unit
) {
    var title by remember { mutableStateOf(habit.title) }
    var description by remember { mutableStateOf(habit.description) }
    var selectedCategory by remember { mutableStateOf(habit.category) }
    var selectedFrequency by remember { mutableStateOf(habit.frequency) }
    var expandedCategory by remember { mutableStateOf(false) }
    var expandedFrequency by remember { mutableStateOf(false) }

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
                    "Edit Habit",
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

            // Form
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Habit name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Category dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedCategory,
                    onExpandedChange = { expandedCategory = !expandedCategory }
                ) {
                    OutlinedTextField(
                        value = selectedCategory.name.lowercase().replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = expandedCategory,
                        onDismissRequest = { expandedCategory = false }
                    ) {
                        GoalCategory.entries.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = {
                                    selectedCategory = category
                                    expandedCategory = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Frequency dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedFrequency,
                    onExpandedChange = { expandedFrequency = !expandedFrequency }
                ) {
                    OutlinedTextField(
                        value = selectedFrequency.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Frequency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFrequency) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = expandedFrequency,
                        onDismissRequest = { expandedFrequency = false }
                    ) {
                        HabitFrequency.entries.forEach { frequency ->
                            DropdownMenuItem(
                                text = { Text(frequency.displayName) },
                                onClick = {
                                    selectedFrequency = frequency
                                    expandedFrequency = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onConfirm(
                                habit.copy(
                                    title = title,
                                    description = description,
                                    category = selectedCategory,
                                    frequency = selectedFrequency
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = title.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Save Changes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Quick reflection bottom sheet shown after habit check-in with AI-generated content
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickReflectionBottomSheet(
    habit: Habit,
    linkedGoal: Goal?,
    aiProxy: AiProxyService,
    onDismiss: () -> Unit,
    onSave: (String, String, Mood) -> Unit
) {
    var title by remember { mutableStateOf("Reflection: ${habit.title}") }
    var content by remember { mutableStateOf("") }
    var selectedMood by remember { mutableStateOf(Mood.NEUTRAL) }
    var isGenerating by remember { mutableStateOf(false) }
    var hasGenerated by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Generate AI reflection when mood changes (after first selection)
    LaunchedEffect(selectedMood) {
        if (!hasGenerated) return@LaunchedEffect
        isGenerating = true
        try {
            val aiContent = generateAiReflection(
                aiProxy = aiProxy,
                habit = habit,
                linkedGoal = linkedGoal,
                mood = selectedMood
            )
            if (aiContent != null) {
                title = aiContent.first
                content = aiContent.second
            }
        } catch (e: Exception) {
            // Fallback to simple content
            content = "Completed ${habit.title}. Feeling ${selectedMood.emoji}"
        }
        isGenerating = false
    }

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
            // Header with AI icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Quick Reflection",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        // AI sparkle icon
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "AI Generated",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        "How was your ${habit.title}?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    linkedGoal?.let {
                        Text(
                            "Linked to: ${it.title}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close"
                    )
                }
            }

            // Mood selector
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    "How are you feeling?",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Mood.entries.forEach { mood ->
                        MoodOption(
                            mood = mood,
                            isSelected = mood == selectedMood,
                            onClick = {
                                selectedMood = mood
                                // Trigger AI generation on first mood selection
                                if (!hasGenerated) {
                                    hasGenerated = true
                                    isGenerating = true
                                    coroutineScope.launch {
                                        try {
                                            val aiContent = generateAiReflection(
                                                aiProxy = aiProxy,
                                                habit = habit,
                                                linkedGoal = linkedGoal,
                                                mood = mood
                                            )
                                            if (aiContent != null) {
                                                title = aiContent.first
                                                content = aiContent.second
                                            }
                                        } catch (e: Exception) {
                                            content = "Completed ${habit.title}. Feeling ${mood.emoji}"
                                        }
                                        isGenerating = false
                                    }
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI Generated Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Title")
                        if (hasGenerated) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = "AI",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isGenerating
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Content input with loading state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(140.dp)
            ) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Your reflection")
                            if (hasGenerated) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "AI",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    placeholder = {
                        Text(
                            if (!hasGenerated) "Select a mood to generate AI reflection..."
                            else "How did it go? Any insights?"
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 5,
                    enabled = !isGenerating
                )

                // Loading overlay
                if (isGenerating) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                "Generating reflection...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Skip")
                }

                Button(
                    onClick = {
                        val finalTitle = title.ifBlank { "Reflection: ${habit.title}" }
                        val finalContent = content.ifBlank {
                            "Completed ${habit.title}. Feeling ${selectedMood.emoji}"
                        }
                        onSave(finalTitle, finalContent, selectedMood)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isGenerating
                ) {
                    Text("Save")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Generate AI reflection content based on habit, goal, and mood
 */
private suspend fun generateAiReflection(
    aiProxy: AiProxyService,
    habit: Habit,
    linkedGoal: Goal?,
    mood: Mood
): Pair<String, String>? = withContext(Dispatchers.IO) {
    try {
        val prompt = buildString {
            append("Generate a short, personal journal reflection (2-3 sentences) for someone who just completed their habit.\n\n")
            append("Habit: ${habit.title}\n")
            append("Description: ${habit.description}\n")
            append("Current streak: ${habit.currentStreak} days\n")
            append("Total completions: ${habit.totalCompletions}\n")
            append("Mood: ${mood.displayName} (${mood.emoji})\n")

            linkedGoal?.let {
                append("\nThis habit is linked to their goal: ${it.title}\n")
                append("Goal progress: ${(it.progress ?: 0).toInt()}%\n")
                append("Goal status: ${it.status}\n")
            }

            append("\nWrite in first person, be encouraging and reflective. Match the tone to their ${mood.displayName.lowercase()} mood.")
            append("\nAlso suggest a better, more personal title for this reflection (not just 'Reflection: habit name').")
        }

        val schema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("title") { put("type", "string") }
                putJsonObject("content") { put("type", "string") }
            }
            putJsonArray("required") { add("title"); add("content") }
        }

        val responseText = aiProxy.generateStructuredJson(prompt, schema)

        val json = Json { ignoreUnknownKeys = true }
        val parsed = json.parseToJsonElement(responseText).jsonObject
        val aiTitle = parsed["title"]?.jsonPrimitive?.content ?: "Reflection: ${habit.title}"
        val aiContent = parsed["content"]?.jsonPrimitive?.content ?: ""
        Pair(aiTitle, aiContent)
    } catch (e: Exception) {
        Logger.e("HabitTrackerScreen") { "AI habit reflection generation failed: ${e.message}" }
        null
    }
}

@Composable
private fun MoodOption(
    mood: Mood,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent,
            modifier = Modifier.size(48.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = mood.emoji,
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = mood.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
