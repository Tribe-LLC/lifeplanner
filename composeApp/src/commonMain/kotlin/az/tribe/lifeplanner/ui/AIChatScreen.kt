package az.tribe.lifeplanner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.ChatMessage
import az.tribe.lifeplanner.domain.model.ChatSession
import az.tribe.lifeplanner.domain.model.CoachPersona
import az.tribe.lifeplanner.domain.model.CoachSuggestion
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.MessageRole
import az.tribe.lifeplanner.data.repository.ChatRepositoryImpl
import az.tribe.lifeplanner.domain.model.CoachGroup
import az.tribe.lifeplanner.domain.model.CustomCoach
import az.tribe.lifeplanner.ui.balance.InsightMessageHolder
import az.tribe.lifeplanner.ui.chat.ChatViewModel
import az.tribe.lifeplanner.ui.components.CoachListContent
import az.tribe.lifeplanner.ui.components.CoachListContentExtended
import az.tribe.lifeplanner.ui.components.CoachSuggestionButtons
import az.tribe.lifeplanner.ui.components.OfflineBanner
import az.tribe.lifeplanner.util.NetworkConnectivityObserver
import kotlinx.datetime.LocalDateTime
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen(
    viewModel: ChatViewModel = koinInject(),
    coachId: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToCoach: (String) -> Unit = {},
    onNavigateToCreateCoach: () -> Unit = {},
    onNavigateToCreateGroup: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val connectivityObserver: NetworkConnectivityObserver = koinInject()
    val isConnected by connectivityObserver.isConnected.collectAsState()
    val isOffline = !isConnected

    LaunchedEffect(Unit) {
        viewModel.loadSessions()
        viewModel.refreshUserContext()
    }

    // If coachId is provided, load that coach's session
    LaunchedEffect(coachId) {
        if (coachId != null) {
            viewModel.selectCoachById(coachId)
        }
    }

    // Auto-send pending insight message from Life Balance screen (skip when offline)
    LaunchedEffect(uiState.showSessionList, uiState.isLoading, isOffline) {
        if (!isOffline && !uiState.showSessionList && !uiState.isLoading && uiState.messages.isEmpty()) {
            val pending = InsightMessageHolder.pendingMessage
            if (pending != null) {
                InsightMessageHolder.pendingMessage = null
                viewModel.sendMessage(pending)
            }
        }
    }

    // Show feedback snackbar when action is executed
    LaunchedEffect(uiState.actionFeedback) {
        uiState.actionFeedback?.let { feedback ->
            snackbarHostState.showSnackbar(feedback)
            viewModel.clearActionFeedback()
        }
    }

    // Determine the title based on current state
    val screenTitle = when {
        uiState.showSessionList -> "Personal Coach"
        uiState.isCouncilMode -> "The Council"
        uiState.isCustomCoachMode && uiState.currentCustomCoach != null -> uiState.currentCustomCoach!!.name
        uiState.isCustomGroupMode && uiState.currentCoachGroup != null -> uiState.currentCoachGroup!!.name
        uiState.currentCoach != null -> uiState.currentCoach!!.name
        else -> "Luna"
    }

    val screenSubtitle = when {
        uiState.showSessionList -> null
        uiState.isCouncilMode -> "All coaches united"
        uiState.isCustomCoachMode && uiState.currentCustomCoach != null -> "Custom Coach"
        uiState.isCustomGroupMode && uiState.currentCoachGroup != null -> "${uiState.currentCoachGroup!!.members.size} coaches"
        uiState.currentCoach != null -> uiState.currentCoach!!.title
        else -> "Life Coach"
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Coach avatar or icon
                        if (!uiState.showSessionList && uiState.currentCoach != null) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uiState.currentCoach!!.emoji,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        } else if (uiState.isCouncilMode) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary
                                            )
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Groups,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Icon(
                                Icons.Rounded.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column {
                            Text(
                                text = screenTitle,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            if (screenSubtitle != null) {
                                Text(
                                    text = screenSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.showSessionList) {
                CoachListContentExtended(
                    builtinCoaches = CoachPersona.ALL_COACHES,
                    customCoaches = uiState.customCoaches,
                    coachGroups = uiState.coachGroups,
                    sessions = uiState.sessionsByCoach,
                    onBuiltinCoachClick = { coach ->
                        onNavigateToCoach(coach.id)
                    },
                    onCustomCoachClick = { customCoach ->
                        viewModel.selectCustomCoach(customCoach)
                    },
                    onGroupClick = { group ->
                        viewModel.selectCoachGroup(group)
                    },
                    onCouncilClick = {
                        onNavigateToCoach(CoachPersona.COUNCIL_ID)
                    },
                    onCreateCoach = onNavigateToCreateCoach,
                    onCreateGroup = onNavigateToCreateGroup
                )
            } else {
                ChatContent(
                    messages = uiState.messages,
                    isSending = uiState.isSending,
                    isExecutingAction = uiState.executingAction,
                    onSendMessage = { viewModel.sendMessage(it) },
                    onExecuteSuggestion = { viewModel.executeCoachSuggestion(it) },
                    executedSuggestionIds = uiState.executedSuggestionIds,
                    isCouncilMode = uiState.isCouncilMode,
                    isOffline = isOffline
                )
            }

            // Loading overlay
            if (uiState.isLoading && !uiState.showSessionList) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatContent(
    messages: List<ChatMessage>,
    isSending: Boolean,
    isExecutingAction: Boolean,
    onSendMessage: (String) -> Unit,
    onExecuteSuggestion: (CoachSuggestion) -> Unit,
    executedSuggestionIds: Set<String> = emptySet(),
    goals: List<Goal> = emptyList(),
    habits: List<Habit> = emptyList(),
    isCouncilMode: Boolean = false,
    isOffline: Boolean = false
) {
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var attachedGoal by remember { mutableStateOf<Goal?>(null) }
    var attachedHabit by remember { mutableStateOf<Habit?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }

    // For council mode: staggered message reveal
    var revealedMessageCount by remember { mutableStateOf(messages.size) }
    var typingCoachName by remember { mutableStateOf<String?>(null) }

    // When new messages arrive in council mode, reveal them one by one
    LaunchedEffect(messages.size, isCouncilMode) {
        if (isCouncilMode && messages.size > revealedMessageCount) {
            // New messages arrived - reveal them one by one with delays
            val newMessages = messages.drop(revealedMessageCount)
            for ((index, message) in newMessages.withIndex()) {
                // Extract coach name for typing indicator
                if (index < newMessages.size - 1) {
                    val nextMessage = newMessages.getOrNull(index + 1)
                    if (nextMessage != null && nextMessage.role == MessageRole.ASSISTANT) {
                        val content = nextMessage.content
                        if (content.contains(": ")) {
                            val colonIndex = content.indexOf(": ")
                            val potentialCoach = content.substring(0, colonIndex)
                            if (potentialCoach.length <= 10 && potentialCoach.first().isUpperCase()) {
                                typingCoachName = potentialCoach
                            }
                        }
                    }
                }

                // Reveal current message
                revealedMessageCount = revealedMessageCount + 1

                // Random delay between 500-1000ms before showing next message
                if (index < newMessages.size - 1) {
                    kotlinx.coroutines.delay((500..1000).random().toLong())
                }
            }
            typingCoachName = null
        } else if (messages.size < revealedMessageCount) {
            // Messages were cleared or reduced
            revealedMessageCount = messages.size
        } else if (!isCouncilMode) {
            // Not council mode - show all messages immediately
            revealedMessageCount = messages.size
        }
    }

    // Scroll to bottom when revealed count changes
    LaunchedEffect(revealedMessageCount) {
        if (revealedMessageCount > 0) {
            listState.animateScrollToItem(revealedMessageCount - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            // Welcome message
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Psychology,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Text(
                        text = "Hi! I'm Luna",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    Text(
                        text = "I'm here to help you stay motivated, overcome obstacles, and achieve your goals. What's on your mind today?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    // Suggestion chips
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip("How do I stay motivated?") { onSendMessage(it) }
                        SuggestionChip("Help me break down my goals") { onSendMessage(it) }
                        SuggestionChip("I'm feeling stuck") { onSendMessage(it) }
                    }
                }
            }
        } else {
            // Messages list - in council mode, only show revealed messages
            val visibleMessages = if (isCouncilMode) {
                messages.take(revealedMessageCount)
            } else {
                messages
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visibleMessages) { message ->
                    // Get suggestions from metadata if this is an assistant message
                    val suggestions = remember(message.metadata) {
                        if (message.role == MessageRole.ASSISTANT) {
                            message.metadata?.coachSuggestions ?: emptyList()
                        } else {
                            emptyList()
                        }
                    }

                    MessageBubble(
                        message = message,
                        suggestions = suggestions,
                        onExecuteSuggestion = onExecuteSuggestion,
                        isExecutingAction = isExecutingAction,
                        executedSuggestionIds = executedSuggestionIds
                    )
                }

                // Show typing indicator with coach name in council mode
                if (isSending || typingCoachName != null) {
                    item {
                        CoachTypingIndicator(coachName = typingCoachName)
                    }
                }
            }
        }

        // Offline banner above input
        OfflineBanner(isOffline = isOffline)

        // Input field
        ChatInputField(
            value = inputText,
            onValueChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    // Include attachment context in message
                    val messageWithContext = buildString {
                        if (attachedGoal != null) {
                            append("[Discussing Goal: ${attachedGoal?.title}] ")
                        } else if (attachedHabit != null) {
                            append("[Discussing Habit: ${attachedHabit?.title}] ")
                        }
                        append(inputText)
                    }
                    onSendMessage(messageWithContext)
                    inputText = ""
                    // Clear attachment after sending
                    attachedGoal = null
                    attachedHabit = null
                }
            },
            isSending = isSending,
            isOffline = isOffline,
            attachedGoal = attachedGoal,
            attachedHabit = attachedHabit,
            onAttachClick = { showAttachmentSheet = true },
            onClearAttachment = {
                attachedGoal = null
                attachedHabit = null
            }
        )
    }

    // Attachment selection bottom sheet
    if (showAttachmentSheet) {
        AttachmentSelectionSheet(
            goals = goals,
            habits = habits,
            onGoalSelected = { goal ->
                attachedGoal = goal
                attachedHabit = null
                showAttachmentSheet = false
            },
            onHabitSelected = { habit ->
                attachedHabit = habit
                attachedGoal = null
                showAttachmentSheet = false
            },
            onDismiss = { showAttachmentSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentSelectionSheet(
    goals: List<Goal>,
    habits: List<Habit>,
    onGoalSelected: (Goal) -> Unit,
    onHabitSelected: (Habit) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Discuss with Luna",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (goals.isNotEmpty()) {
                Text(
                    text = "Goals",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                // Use LazyRow for multiple goals, regular list for single
                if (goals.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(goals.take(8)) { goal ->
                            AttachmentChip(
                                title = goal.title,
                                subtitle = goal.category.name.lowercase().replaceFirstChar { it.uppercase() },
                                icon = Icons.Rounded.Flag,
                                iconTint = MaterialTheme.colorScheme.primary,
                                onClick = { onGoalSelected(goal) }
                            )
                        }
                    }
                } else {
                    goals.first().let { goal ->
                        Surface(
                            onClick = { onGoalSelected(goal) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Flag,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = goal.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = goal.category.name.lowercase().replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (habits.isNotEmpty()) {
                Text(
                    text = "Habits",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                // Use LazyRow for multiple habits, regular list for single
                if (habits.size > 1) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(habits.take(8)) { habit ->
                            AttachmentChip(
                                title = habit.title,
                                subtitle = habit.frequency.displayName,
                                icon = Icons.Rounded.Loop,
                                iconTint = MaterialTheme.colorScheme.secondary,
                                onClick = { onHabitSelected(habit) }
                            )
                        }
                    }
                } else {
                    habits.first().let { habit ->
                        Surface(
                            onClick = { onHabitSelected(habit) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Loop,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = habit.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = habit.frequency.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (goals.isEmpty() && habits.isEmpty()) {
                Text(
                    text = "No goals or habits to discuss yet. Create some first!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AttachmentChip(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .width(160.dp)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun SuggestionChip(
    text: String,
    onClick: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick(text) },
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    suggestions: List<CoachSuggestion> = emptyList(),
    onExecuteSuggestion: (CoachSuggestion) -> Unit = {},
    isExecutingAction: Boolean = false,
    executedSuggestionIds: Set<String> = emptySet()
) {
    val isUser = message.role == MessageRole.USER

    // Parse coach name from council messages (format: "CoachName: message")
    val (coachName, messageText) = remember(message.content) {
        val content = message.content
        if (!isUser && content.contains(": ")) {
            val colonIndex = content.indexOf(": ")
            val potentialCoach = content.substring(0, colonIndex)
            // Check if it looks like a coach name (single capitalized word)
            if (potentialCoach.length <= 10 && potentialCoach.first().isUpperCase() && !potentialCoach.contains(" ")) {
                Pair(potentialCoach, content.substring(colonIndex + 2))
            } else {
                Pair(null, content)
            }
        } else {
            Pair(null, content)
        }
    }

    // Get coach emoji if we found a coach name
    val coachEmoji = remember(coachName) {
        when (coachName?.lowercase()) {
            "luna" -> "✨"
            "alex" -> "💼"
            "morgan" -> "💰"
            "kai" -> "💪"
            "sam" -> "🤝"
            "river" -> "🧘"
            "jamie" -> "👨‍👩‍👧‍👦"
            else -> null
        }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (coachEmoji != null) {
                        Text(
                            text = coachEmoji,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                // Show coach name badge in council mode
                if (coachName != null && !isUser) {
                    Text(
                        text = coachName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Surface(
                    modifier = Modifier.animateContentSize(),
                    shape = RoundedCornerShape(
                        topStart = if (isUser) 16.dp else 4.dp,
                        topEnd = if (isUser) 4.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    ),
                    color = if (isUser) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    // Strip markdown for clean display (backward compatibility)
                    val cleanContent = remember(messageText) {
                        stripMarkdown(messageText)
                    }
                    Text(
                        text = cleanContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Show suggestion buttons below assistant messages
        if (!isUser && suggestions.isNotEmpty()) {
            CoachSuggestionButtons(
                suggestions = suggestions,
                onExecute = onExecuteSuggestion,
                isExecuting = isExecutingAction,
                executedSuggestionIds = executedSuggestionIds,
                modifier = Modifier.padding(start = 40.dp) // Align with message
            )
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) { index ->
                    val alpha by animateFloatAsState(
                        targetValue = 1f,
                        label = "dot_$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.6f),
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun CoachTypingIndicator(coachName: String? = null) {
    // Get coach emoji if we have a coach name
    val coachEmoji = remember(coachName) {
        when (coachName?.lowercase()) {
            "luna" -> "✨"
            "alex" -> "💼"
            "morgan" -> "💰"
            "kai" -> "💪"
            "sam" -> "🤝"
            "river" -> "🧘"
            "jamie" -> "👨‍👩‍👧‍👦"
            else -> null
        }
    }

    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (coachEmoji != null) {
                Text(
                    text = coachEmoji,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            // Show coach name if available
            if (coachName != null) {
                Text(
                    text = "$coachName is typing...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Surface(
                shape = RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(3) { index ->
                        val alpha by animateFloatAsState(
                            targetValue = 1f,
                            label = "dot_$index"
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.6f),
                                    CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    isOffline: Boolean = false,
    attachedGoal: Goal? = null,
    attachedHabit: Habit? = null,
    onAttachClick: () -> Unit = {},
    onClearAttachment: () -> Unit = {}
) {
    val isDisabled = isSending || isOffline
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Attachment preview
            if (attachedGoal != null || attachedHabit != null) {
                AttachmentPreview(
                    goal = attachedGoal,
                    habit = attachedHabit,
                    onClear = onClearAttachment
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .imePadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Attachment button
                IconButton(
                    onClick = onAttachClick,
                    enabled = !isDisabled,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Rounded.AttachFile,
                        contentDescription = "Attach goal or habit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (isOffline) "You're offline"
                            else if (attachedGoal != null) "Ask about this goal..."
                            else if (attachedHabit != null) "Ask about this habit..."
                            else "Message Luna...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (!isDisabled) onSend() }),
                    maxLines = 4,
                    enabled = !isDisabled
                )

                IconButton(
                    onClick = onSend,
                    enabled = value.isNotBlank() && !isDisabled,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (value.isNotBlank() && !isDisabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            CircleShape
                        )
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send",
                            tint = if (value.isNotBlank()) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreview(
    goal: Goal?,
    habit: Habit?,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (goal != null) Icons.Rounded.Flag else Icons.Rounded.Loop,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (goal != null) "Discussing Goal" else "Discussing Habit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = goal?.title ?: habit?.title ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Remove attachment",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyChatState(onNewChat: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(50.dp)
                )
            }

            Text(
                text = "Your Personal Coach Awaits",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )

            Text(
                text = "Start a conversation with Luna to get personalized guidance on your goals, habits, and personal growth journey.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { onNewChat() },
                color = MaterialTheme.colorScheme.primary
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = "Start New Chat",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}


/**
 * Strip markdown formatting for clean display in chat bubbles.
 * Used for backward compatibility with old messages.
 */
private fun stripMarkdown(text: String): String {
    return text
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")  // **bold** -> bold
        .replace(Regex("\\*(.+?)\\*"), "$1")        // *italic* -> italic
        .replace(Regex("__(.+?)__"), "$1")          // __bold__ -> bold
        .replace(Regex("_(.+?)_"), "$1")            // _italic_ -> italic
        .replace(Regex("~~(.+?)~~"), "$1")          // ~~strike~~ -> strike
        .replace(Regex("`(.+?)`"), "$1")            // `code` -> code
        .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "") // # headers
        .replace(Regex("^[-*]\\s+", RegexOption.MULTILINE), "• ") // bullet points
        .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "") // numbered lists
        .replace(Regex("\\[(.+?)\\]\\(.+?\\)"), "$1") // [link](url) -> link
        .trim()
}
