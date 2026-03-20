package az.tribe.lifeplanner.ui

import androidx.compose.animation.*
import co.touchlab.kermit.Logger
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.JournalPrompts
import az.tribe.lifeplanner.ui.components.rememberHapticManager
import az.tribe.lifeplanner.ui.journal.JournalViewModel
import az.tribe.lifeplanner.ui.journal.generateAiJournalEntry
import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.util.NetworkConnectivityObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

enum class JournalWizardStep {
    MOOD, PROMPT, CONTEXT_GENERATE, REVIEW_SAVE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalCreationWizardScreen(
    onNavigateBack: () -> Unit,
    preSelectedGoalId: String? = null,
    viewModel: JournalViewModel = koinViewModel(),
    goalViewModel: GoalViewModel = koinInject(),
    habitViewModel: az.tribe.lifeplanner.ui.habit.HabitViewModel = koinViewModel(),
    aiProxy: AiProxyService = koinInject()
) {
    val goals by goalViewModel.goals.collectAsState()
    val habitsWithStatus by habitViewModel.habits.collectAsState()
    val habits = habitsWithStatus.map { it.habit }
    val haptic = rememberHapticManager()
    val coroutineScope = rememberCoroutineScope()
    val connectivityObserver: NetworkConnectivityObserver = koinInject()
    val isConnected by connectivityObserver.isConnected.collectAsState()
    val isOffline = !isConnected

    // Wizard state
    var currentStep by remember { mutableStateOf(JournalWizardStep.MOOD) }
    var selectedMood by remember { mutableStateOf<Mood?>(null) }
    var selectedPrompt by remember { mutableStateOf<String?>(null) }
    var userNote by remember { mutableStateOf("") }
    var selectedGoalId by remember { mutableStateOf<String?>(preSelectedGoalId) }
    var selectedHabitId by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var generatedTitle by remember { mutableStateOf("") }
    var generatedContent by remember { mutableStateOf("") }
    var generatedTags by remember { mutableStateOf<List<String>>(emptyList()) }

    // Back handler
    val canGoBack = !isGenerating
    val onBack: () -> Unit = {
        when (currentStep) {
            JournalWizardStep.MOOD -> onNavigateBack()
            JournalWizardStep.PROMPT -> currentStep = JournalWizardStep.MOOD
            JournalWizardStep.CONTEXT_GENERATE -> currentStep = JournalWizardStep.PROMPT
            JournalWizardStep.REVIEW_SAVE -> {
                if (!isGenerating) currentStep = JournalWizardStep.CONTEXT_GENERATE
            }
        }
    }

    Scaffold(
        topBar = {
            JournalWizardTopBar(
                currentStep = currentStep,
                onBackClick = if (canGoBack) onBack else ({})
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { step ->
            when (step) {
                JournalWizardStep.MOOD -> MoodSelectionStep(
                    selectedMood = selectedMood,
                    onMoodSelected = { mood ->
                        selectedMood = mood
                        haptic.click()
                        coroutineScope.launch {
                            delay(400)
                            currentStep = JournalWizardStep.PROMPT
                        }
                    }
                )

                JournalWizardStep.PROMPT -> PromptSelectionStep(
                    mood = selectedMood ?: Mood.NEUTRAL,
                    selectedPrompt = selectedPrompt,
                    onPromptSelected = { prompt ->
                        selectedPrompt = prompt
                        haptic.click()
                        coroutineScope.launch {
                            delay(300)
                            currentStep = JournalWizardStep.CONTEXT_GENERATE
                        }
                    }
                )

                JournalWizardStep.CONTEXT_GENERATE -> ContextAndGenerateStep(
                    goals = goals,
                    habits = habits,
                    selectedGoalId = selectedGoalId,
                    selectedHabitId = selectedHabitId,
                    userNote = userNote,
                    isGenerating = isGenerating,
                    isOffline = isOffline,
                    onGoalSelected = { selectedGoalId = it },
                    onHabitSelected = { selectedHabitId = it },
                    onNoteChanged = { userNote = it },
                    onGenerateClick = {
                        isGenerating = true
                        currentStep = JournalWizardStep.REVIEW_SAVE
                        coroutineScope.launch {
                            try {
                                val linkedGoal = selectedGoalId?.let { id -> goals.find { it.id == id } }
                                val linkedHabit = selectedHabitId?.let { id -> habits.find { it.id == id } }
                                val result = generateAiJournalEntry(
                                    aiProxy = aiProxy,
                                    mood = selectedMood ?: Mood.NEUTRAL,
                                    prompt = selectedPrompt ?: "",
                                    userNote = userNote,
                                    linkedGoal = linkedGoal,
                                    linkedHabit = linkedHabit
                                )
                                result?.let {
                                    generatedTitle = it.title
                                    generatedContent = it.content
                                    generatedTags = it.tags
                                }
                            } catch (e: Exception) {
                                Logger.e("JournalCreationWizard", e) { "AI journal generation failed" }
                                // Stay on current step so user can retry
                                currentStep = JournalWizardStep.CONTEXT_GENERATE
                            } finally {
                                isGenerating = false
                            }
                        }
                    },
                    onSkipAiClick = {
                        generatedTitle = ""
                        generatedContent = ""
                        generatedTags = emptyList()
                        currentStep = JournalWizardStep.REVIEW_SAVE
                    }
                )

                JournalWizardStep.REVIEW_SAVE -> {
                    if (isGenerating) {
                        GeneratingOverlay()
                    } else {
                        ReviewAndSaveStep(
                            mood = selectedMood ?: Mood.NEUTRAL,
                            prompt = selectedPrompt,
                            title = generatedTitle,
                            content = generatedContent,
                            tags = generatedTags,
                            onTitleChanged = { generatedTitle = it },
                            onContentChanged = { generatedContent = it },
                            onTagsChanged = { generatedTags = it },
                            onSave = {
                                haptic.success()
                                viewModel.createEntry(
                                    title = generatedTitle,
                                    content = generatedContent,
                                    mood = selectedMood ?: Mood.NEUTRAL,
                                    linkedGoalId = selectedGoalId,
                                    linkedHabitId = selectedHabitId,
                                    tags = generatedTags,
                                    promptUsed = selectedPrompt
                                )
                                onNavigateBack()
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JournalWizardTopBar(
    currentStep: JournalWizardStep,
    onBackClick: () -> Unit
) {
    val progress = when (currentStep) {
        JournalWizardStep.MOOD -> 0.25f
        JournalWizardStep.PROMPT -> 0.50f
        JournalWizardStep.CONTEXT_GENERATE -> 0.75f
        JournalWizardStep.REVIEW_SAVE -> 1.0f
    }

    val title = when (currentStep) {
        JournalWizardStep.MOOD -> "How are you feeling?"
        JournalWizardStep.PROMPT -> "What to reflect on?"
        JournalWizardStep.CONTEXT_GENERATE -> "Add context"
        JournalWizardStep.REVIEW_SAVE -> "Review & save"
    }

    Column {
        TopAppBar(
            title = {
                Text(
                    title,
                    fontWeight = FontWeight.Bold
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
                if (currentStep == JournalWizardStep.MOOD) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        val animatedProgress by animateFloatAsState(progress, label = "progress")
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

// --- Step 1: Mood Selection ---

@Composable
private fun MoodSelectionStep(
    selectedMood: Mood?,
    onMoodSelected: (Mood) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Tap how you feel right now",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 40.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Mood.entries.forEach { mood ->
                val isSelected = mood == selectedMood
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.2f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "moodScale"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onMoodSelected(mood) }
                        .scale(scale)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mood.emoji,
                            fontSize = 36.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = mood.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// --- Step 2: Prompt Selection ---

@Composable
private fun PromptSelectionStep(
    mood: Mood,
    selectedPrompt: String?,
    onPromptSelected: (String) -> Unit
) {
    val recommendedPrompts = remember(mood) { JournalPrompts.getPromptsForMood(mood) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Recommended section
        item {
            Text(
                text = "Recommended for you",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        items(recommendedPrompts) { prompt ->
            PromptCard(
                prompt = prompt,
                isSelected = prompt == selectedPrompt,
                isRecommended = true,
                onClick = { onPromptSelected(prompt) }
            )
        }

        // All Prompts section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "All Prompts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // Daily Reflection
        item {
            PromptCategoryHeader(emoji = "\uD83C\uDF05", title = "Daily Reflection")
        }
        items(JournalPrompts.dailyReflection.filter { it !in recommendedPrompts }) { prompt ->
            PromptCard(
                prompt = prompt,
                isSelected = prompt == selectedPrompt,
                isRecommended = false,
                onClick = { onPromptSelected(prompt) }
            )
        }

        // Goal Reflection
        item {
            PromptCategoryHeader(emoji = "\uD83C\uDFAF", title = "Goal Reflection")
        }
        items(JournalPrompts.goalReflection.filter { it !in recommendedPrompts }) { prompt ->
            PromptCard(
                prompt = prompt,
                isSelected = prompt == selectedPrompt,
                isRecommended = false,
                onClick = { onPromptSelected(prompt) }
            )
        }

        // Mood Exploration
        item {
            PromptCategoryHeader(emoji = "\uD83D\uDCAD", title = "Mood Exploration")
        }
        items(JournalPrompts.moodExploration.filter { it !in recommendedPrompts }) { prompt ->
            PromptCard(
                prompt = prompt,
                isSelected = prompt == selectedPrompt,
                isRecommended = false,
                onClick = { onPromptSelected(prompt) }
            )
        }

        // Weekly Review
        item {
            PromptCategoryHeader(emoji = "\uD83D\uDCCA", title = "Weekly Review")
        }
        items(JournalPrompts.weeklyReview) { prompt ->
            PromptCard(
                prompt = prompt,
                isSelected = prompt == selectedPrompt,
                isRecommended = false,
                onClick = { onPromptSelected(prompt) }
            )
        }

        // Write my own option
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                onClick = { onPromptSelected("Free write") },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Write my own",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Start with a blank page",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun PromptCategoryHeader(emoji: String, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(emoji, fontSize = 18.sp)
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PromptCard(
    prompt: String,
    isSelected: Boolean,
    isRecommended: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isRecommended -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isRecommended) Icons.Rounded.AutoAwesome else Icons.Rounded.FormatQuote,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// --- Step 3: Context & Generate ---

@Composable
private fun ContextAndGenerateStep(
    goals: List<Goal>,
    habits: List<Habit>,
    selectedGoalId: String?,
    selectedHabitId: String?,
    userNote: String,
    isGenerating: Boolean,
    isOffline: Boolean = false,
    onGoalSelected: (String?) -> Unit,
    onHabitSelected: (String?) -> Unit,
    onNoteChanged: (String) -> Unit,
    onGenerateClick: () -> Unit,
    onSkipAiClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var showGoalDropdown by remember { mutableStateOf(false) }
    var showHabitDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Link a goal or habit and add any extra context for a richer entry.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Goal/Habit pickers
        if (goals.isNotEmpty() || habits.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                                            .clickable { onGoalSelected(null) },
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
                                        onGoalSelected(goal.id)
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
                                            .clickable { onHabitSelected(null) },
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
                                        onHabitSelected(habit.id)
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

        // Optional note
        OutlinedTextField(
            value = userNote,
            onValueChange = onNoteChanged,
            label = { Text("Quick note (optional)") },
            placeholder = { Text("Any extra context for AI...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        // Offline hint
        if (isOffline) {
            Text(
                text = "AI generation requires internet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        // Generate button
        Button(
            onClick = onGenerateClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = !isGenerating && !isOffline
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Generate with AI",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Skip AI link
        TextButton(
            onClick = onSkipAiClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Skip AI, write manually",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// --- Generating Overlay ---

@Composable
private fun GeneratingOverlay() {
    var dotCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            dotCount = (dotCount + 1) % 4
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Writing your entry${".".repeat(dotCount)}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Creating a thoughtful reflection based on your mood and prompt",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}

// --- Step 4: Review & Save ---

@Composable
private fun ReviewAndSaveStep(
    mood: Mood,
    prompt: String?,
    title: String,
    content: String,
    tags: List<String>,
    onTitleChanged: (String) -> Unit,
    onContentChanged: (String) -> Unit,
    onTagsChanged: (List<String>) -> Unit,
    onSave: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var tagsText by remember(tags) { mutableStateOf(tags.joinToString(", ")) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary chips
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "${mood.emoji} ${mood.displayName}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                if (prompt != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 200.dp)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Title
        item {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChanged,
                label = { Text("Title") },
                placeholder = { Text("Entry title") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Content
        item {
            OutlinedTextField(
                value = content,
                onValueChange = onContentChanged,
                label = { Text("Your reflection") },
                placeholder = { Text("Write your thoughts...") },
                minLines = 8,
                maxLines = 15,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Tags
        item {
            OutlinedTextField(
                value = tagsText,
                onValueChange = {
                    tagsText = it
                    onTagsChanged(it.split(",").map { t -> t.trim() }.filter { t -> t.isNotBlank() })
                },
                label = { Text("Tags") },
                placeholder = { Text("gratitude, goals, reflection") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Save button
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = title.isNotBlank() && content.isNotBlank(),
                shape = RoundedCornerShape(16.dp)
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
