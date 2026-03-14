package az.tribe.lifeplanner.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import az.tribe.lifeplanner.data.review.ReviewMessageBuilder
import az.tribe.lifeplanner.domain.model.ReviewType
import az.tribe.lifeplanner.ui.balance.InsightMessageHolder
import az.tribe.lifeplanner.ui.chat.ChatViewModel
import kotlinx.coroutines.launch
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
    onNavigateToCreateGroup: () -> Unit = {},
    onNavigateToCoachProfile: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val connectivityObserver: NetworkConnectivityObserver = koinInject()
    val isConnected by connectivityObserver.isConnected.collectAsState()
    val isOffline = !isConnected
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope = rememberCoroutineScope()

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
        if (!isOffline && !uiState.showSessionList && !uiState.isLoading) {
            val pending = InsightMessageHolder.pendingMessage
            if (pending != null) {
                InsightMessageHolder.pendingMessage = null
                viewModel.sendMessage(pending)
            }
        }
    }


    // Show error snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // Determine the title based on current state
    val screenTitle = when {
        uiState.showSessionList -> "Personal Coach"
        uiState.isCouncilMode -> "The Council"
        uiState.isCustomCoachMode && uiState.currentCustomCoach != null -> uiState.currentCustomCoach?.name ?: ""
        uiState.isCustomGroupMode && uiState.currentCoachGroup != null -> uiState.currentCoachGroup?.name ?: ""
        uiState.currentCoach != null -> uiState.currentCoach?.name ?: ""
        else -> "Luna"
    }

    val screenSubtitle = when {
        uiState.showSessionList -> null
        uiState.isCouncilMode -> "All coaches united"
        uiState.isCustomCoachMode && uiState.currentCustomCoach != null -> "Custom Coach"
        uiState.isCustomGroupMode && uiState.currentCoachGroup != null -> "${uiState.currentCoachGroup?.members?.size ?: 0} coaches"
        uiState.currentCoach != null -> uiState.currentCoach?.title ?: ""
        else -> "Life Coach"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        CircleShape
                                    )
                                    .clickable {
                                        uiState.currentCoach?.let { onNavigateToCoachProfile(it.id) }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = uiState.currentCoach?.emoji ?: "",
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
                    modifier = Modifier.align(Alignment.TopCenter),
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
                    isStreaming = uiState.isStreaming,
                    streamingText = uiState.streamingText,
                    isExecutingAction = uiState.executingAction,
                    onSendMessage = { viewModel.sendMessage(it) },
                    onExecuteSuggestion = { viewModel.executeCoachSuggestion(it) },
                    executedSuggestionIds = uiState.executedSuggestionIds,
                    isCouncilMode = uiState.isCouncilMode,
                    isOffline = isOffline,
                    coach = uiState.currentCoach,
                    onCopyMessage = { text ->
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(text))
                        scope.launch {
                            snackbarHostState.showSnackbar("Copied to clipboard")
                        }
                    }
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
    isStreaming: Boolean = false,
    streamingText: String? = null,
    isExecutingAction: Boolean,
    onSendMessage: (String) -> Unit,
    onExecuteSuggestion: (CoachSuggestion) -> Unit,
    executedSuggestionIds: Set<String> = emptySet(),
    goals: List<Goal> = emptyList(),
    habits: List<Habit> = emptyList(),
    isCouncilMode: Boolean = false,
    isOffline: Boolean = false,
    coach: CoachPersona? = null,
    onCopyMessage: (String) -> Unit = {}
) {
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

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

    // Scroll to bottom (index 0 in reversed layout) when revealed count changes
    LaunchedEffect(revealedMessageCount) {
        if (revealedMessageCount > 0) {
            listState.animateScrollToItem(0)
        }
    }


    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val keyboardDismissConnection = remember(keyboardController) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
                return Offset.Zero
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            // Welcome message — personalized per coach
            val coachName = coach?.name ?: "Luna"
            val coachEmoji = coach?.emoji ?: "✨"
            val coachGreeting = coach?.greeting ?: "Hey! I'm Luna, your personal life coach. What's on your mind today?"
            val coachSpecialties = coach?.specialties ?: listOf("Goal setting", "Motivation", "Life balance")
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
                        Text(
                            text = coachEmoji,
                            style = MaterialTheme.typography.headlineLarge
                        )
                    }

                    Text(
                        text = "$coachEmoji Hi! I'm $coachName",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )

                    Text(
                        text = coachGreeting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    // Suggestion chips based on coach specialties
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        coachSpecialties.take(3).forEach { specialty ->
                            SuggestionChip("Help me with ${specialty.lowercase()}") { onSendMessage(it) }
                        }
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

            // Reversed list: index 0 = newest (at bottom, near input)
            val reversedMessages = remember(visibleMessages) { visibleMessages.reversed() }

            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(keyboardDismissConnection),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Streaming bubble at index 0 (bottom) when active
                if (isStreaming && !streamingText.isNullOrEmpty()) {
                    item(key = "streaming_bubble") {
                        StreamingMessageBubble(text = streamingText)
                    }
                }

                items(reversedMessages, key = { it.id }) { message ->
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
                        executedSuggestionIds = executedSuggestionIds,
                        onAnswerQuestion = onSendMessage,
                        onCopyMessage = onCopyMessage
                    )
                }
            }

            // Auto-scroll to bottom (index 0 in reversed layout) on new messages
            LaunchedEffect(messages.lastOrNull()?.id, isStreaming, streamingText) {
                listState.animateScrollToItem(0)
            }

            // Dismiss keyboard when AI responds with action suggestions
            val lastMessage = messages.lastOrNull()
            LaunchedEffect(lastMessage?.id) {
                if (lastMessage != null &&
                    lastMessage.role == MessageRole.ASSISTANT &&
                    !lastMessage.metadata?.coachSuggestions.isNullOrEmpty()
                ) {
                    keyboardController?.hide()
                    focusManager.clearFocus()
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
                    onSendMessage(inputText)
                    inputText = ""
                    // Keep keyboard open after sending
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
            },
            isSending = isSending,
            isStreaming = isStreaming,
            isOffline = isOffline,
            coachName = coach?.name ?: "Luna",
            focusRequester = focusRequester
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentSelectionSheet(
    goals: List<Goal>,
    habits: List<Habit>,
    coachName: String = "Luna",
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
                text = "Discuss with $coachName",
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
fun StreamingMessageBubble(text: String) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val styledText = parseMarkdownToAnnotatedString(text, textColor)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
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
            modifier = Modifier
                .widthIn(max = 280.dp)
                .animateContentSize(),
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = styledText,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    suggestions: List<CoachSuggestion> = emptyList(),
    onExecuteSuggestion: (CoachSuggestion) -> Unit = {},
    isExecutingAction: Boolean = false,
    executedSuggestionIds: Set<String> = emptySet(),
    onAnswerQuestion: (String) -> Unit = {},
    onCopyMessage: (String) -> Unit = {}
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

                val bubbleTextColor = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                val styledContent = parseMarkdownToAnnotatedString(messageText, bubbleTextColor)

                var showCopyButton by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier
                        .animateContentSize()
                        .then(
                            if (!isUser) Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = { showCopyButton = !showCopyButton }
                            ) else Modifier
                        ),
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
                    Text(
                        text = styledContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = bubbleTextColor,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                // Copy button — only shows on long-press, auto-hides
                if (!isUser) {
                    val cleanContent = remember(messageText) {
                        stripMarkdown(messageText)
                    }

                    // Auto-hide after 3 seconds
                    if (showCopyButton) {
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(3000)
                            showCopyButton = false
                        }
                    }

                    AnimatedVisibility(
                        visible = showCopyButton,
                        enter = fadeIn() + slideInVertically { -it / 2 },
                        exit = fadeOut() + slideOutVertically { -it / 2 }
                    ) {
                        Surface(
                            onClick = {
                                onCopyMessage(cleanContent)
                                showCopyButton = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Copy",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
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
                onAnswerQuestion = onAnswerQuestion,
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
    val thinkingPhrases = remember {
        listOf(
            "Thinking",
            "Reflecting",
            "Considering your goals",
            "Finding the best approach",
            "Crafting a response"
        )
    }

    var phraseIndex by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2200)
            phraseIndex = (phraseIndex + 1) % thinkingPhrases.size
        }
    }

    // Pulsing glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Row(
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pulsing AI avatar
        Box(
            modifier = Modifier
                .size(32.dp)
                .graphicsLayer { alpha = 0.6f + pulseAlpha * 0.4f }
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
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Animated dots with staggered pulse
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { index ->
                        val dotDelay = index * 0.33f
                        val dotPhase = (shimmerOffset + dotDelay) % 1f
                        val dotScale = if (dotPhase < 0.5f) {
                            lerp(0.6f, 1.2f, dotPhase * 2f)
                        } else {
                            lerp(1.2f, 0.6f, (dotPhase - 0.5f) * 2f)
                        }

                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .graphicsLayer {
                                    scaleX = dotScale
                                    scaleY = dotScale
                                }
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f + dotScale * 0.3f),
                                    CircleShape
                                )
                        )
                    }
                }

                // Rotating phrase
                androidx.compose.animation.AnimatedContent(
                    targetState = thinkingPhrases[phraseIndex],
                    transitionSpec = {
                        (fadeIn(tween(300)) + slideInVertically { it / 2 }) togetherWith
                        (fadeOut(tween(200)) + slideOutVertically { -it / 2 })
                    },
                    label = "phrase"
                ) { phrase ->
                    Text(
                        text = phrase,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewPromptChips(
    onSelect: (ReviewType) -> Unit,
    isLoading: Boolean,
    loadingType: ReviewType?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Choose a review type",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                ReviewType.WEEKLY to "Weekly",
                ReviewType.MONTHLY to "Monthly",
                ReviewType.QUARTERLY to "Quarterly"
            ).forEach { (type, label) ->
                val isThisLoading = isLoading && loadingType == type
                Surface(
                    onClick = { if (!isLoading) onSelect(type) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isThisLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
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
    isStreaming: Boolean = false,
    isOffline: Boolean = false,
    coachName: String = "Luna",
    focusRequester: FocusRequester? = null
) {
    val isDisabled = isSending || isOffline
    val isThinking = isSending || isStreaming
    val hasText = value.isNotBlank()

    // Animated thinking phrases for the placeholder
    val thinkingPhrases = remember {
        listOf(
            "Thinking...",
            "Reflecting...",
            "Considering your goals...",
            "Finding the best approach...",
            "Crafting a response..."
        )
    }
    var phraseIndex by remember { mutableStateOf(0) }
    LaunchedEffect(isThinking) {
        if (isThinking) {
            phraseIndex = 0
            while (true) {
                kotlinx.coroutines.delay(2200)
                phraseIndex = (phraseIndex + 1) % thinkingPhrases.size
            }
        }
    }

    // Re-request focus after sending completes to keep keyboard open
    val keyboardController = LocalSoftwareKeyboardController.current
    var wasSending by remember { mutableStateOf(false) }
    LaunchedEffect(isSending) {
        if (isSending) {
            wasSending = true
        } else if (wasSending && focusRequester != null) {
            wasSending = false
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Box(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.background,
                RoundedCornerShape(28.dp)
            )
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .imePadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    if (isThinking) {
                        // Animated thinking placeholder
                        AnimatedContent(
                            targetState = thinkingPhrases[phraseIndex],
                            transitionSpec = {
                                (fadeIn(tween(300)) + slideInVertically { it / 2 }) togetherWith
                                (fadeOut(tween(200)) + slideOutVertically { -it / 2 })
                            },
                            label = "placeholder_phrase"
                        ) { phrase ->
                            Text(
                                text = phrase,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        Text(
                            text = if (isOffline) "You're offline"
                                   else "Message $coachName...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (focusRequester != null) Modifier.focusRequester(focusRequester)
                            else Modifier
                        ),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (!isDisabled) onSend() }),
                    maxLines = 4,
                    enabled = !isOffline
                )
            }

            // Plus icon when empty, Send icon when typing
            IconButton(
                onClick = {
                    if (hasText && !isDisabled) onSend()
                },
                enabled = !isOffline,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (hasText && !isDisabled) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                        CircleShape
                    )
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    androidx.compose.animation.Crossfade(targetState = hasText) { showSend ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (showSend) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = "More",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
fun EmptyChatState(
    onNewChat: () -> Unit,
    coach: CoachPersona? = null
) {
    val coachName = coach?.name ?: "Luna"
    val coachEmoji = coach?.emoji ?: "✨"
    val coachTitle = coach?.title ?: "Life Coach"
    val specialtiesText = coach?.specialties?.joinToString(", ")?.lowercase()
        ?: "goals, habits, and personal growth"

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
                Text(
                    text = coachEmoji,
                    style = MaterialTheme.typography.displaySmall
                )
            }

            Text(
                text = "$coachName — $coachTitle",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )

            Text(
                text = "Start a conversation with $coachName to get personalized guidance on $specialtiesText.",
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
                        text = "Chat with $coachName",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}


/**
 * Strip markdown formatting for plain-text contexts (copy, etc.).
 */
private fun stripMarkdown(text: String): String {
    return text
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("\\*(.+?)\\*"), "$1")
        .replace(Regex("__(.+?)__"), "$1")
        .replace(Regex("_(.+?)_"), "$1")
        .replace(Regex("~~(.+?)~~"), "$1")
        .replace(Regex("`(.+?)`"), "$1")
        .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("^[-*]\\s+", RegexOption.MULTILINE), "• ")
        .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("\\[(.+?)\\]\\(.+?\\)"), "$1")
        .trim()
}

/**
 * Parse markdown into AnnotatedString with proper styling.
 * Supports bold, italic, inline code, headers, bullet points, numbered lists, and links.
 */
@Composable
private fun parseMarkdownToAnnotatedString(
    text: String,
    baseColor: Color
): androidx.compose.ui.text.AnnotatedString {
    val boldStyle = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)
    val italicStyle = androidx.compose.ui.text.SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    val codeStyle = androidx.compose.ui.text.SpanStyle(
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        background = baseColor.copy(alpha = 0.08f)
    )
    val headerStyle = androidx.compose.ui.text.SpanStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp
    )

    return androidx.compose.ui.text.buildAnnotatedString {
        // Pre-process: clean up lines
        val cleaned = text
            .replace(Regex("\\[(.+?)\\]\\(.+?\\)"), "$1") // links → text only
            .trim()

        val lines = cleaned.split("\n")
        lines.forEachIndexed { lineIndex, rawLine ->
            var line = rawLine

            // Headers: strip # prefix, apply bold+larger
            val headerMatch = Regex("^(#{1,3})\\s+(.*)").find(line)
            if (headerMatch != null) {
                line = headerMatch.groupValues[2]
                pushStyle(headerStyle)
                appendInlineMarkdown(line, boldStyle, italicStyle, codeStyle)
                pop()
            }
            // Bullet points
            else if (line.matches(Regex("^\\s*[-*]\\s+.*"))) {
                val content = line.replace(Regex("^\\s*[-*]\\s+"), "")
                append("• ")
                appendInlineMarkdown(content, boldStyle, italicStyle, codeStyle)
            }
            // Numbered lists
            else if (line.matches(Regex("^\\s*\\d+\\.\\s+.*"))) {
                val match = Regex("^\\s*(\\d+\\.)\\s+(.*)").find(line)
                if (match != null) {
                    append("${match.groupValues[1]} ")
                    appendInlineMarkdown(match.groupValues[2], boldStyle, italicStyle, codeStyle)
                } else {
                    appendInlineMarkdown(line, boldStyle, italicStyle, codeStyle)
                }
            }
            // Regular line
            else {
                appendInlineMarkdown(line, boldStyle, italicStyle, codeStyle)
            }

            if (lineIndex < lines.size - 1) append("\n")
        }
    }
}

/**
 * Append a single line of text, parsing inline bold, italic, and code spans.
 */
private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    boldStyle: androidx.compose.ui.text.SpanStyle,
    italicStyle: androidx.compose.ui.text.SpanStyle,
    codeStyle: androidx.compose.ui.text.SpanStyle
) {
    // Pattern: **bold**, *italic*, `code`
    val pattern = Regex("(\\*\\*(.+?)\\*\\*|\\*(.+?)\\*|`(.+?)`)")
    var lastIndex = 0

    pattern.findAll(text).forEach { match ->
        // Append text before the match
        if (match.range.first > lastIndex) {
            append(text.substring(lastIndex, match.range.first))
        }
        when {
            match.groupValues[2].isNotEmpty() -> {
                // **bold**
                pushStyle(boldStyle)
                append(match.groupValues[2])
                pop()
            }
            match.groupValues[3].isNotEmpty() -> {
                // *italic*
                pushStyle(italicStyle)
                append(match.groupValues[3])
                pop()
            }
            match.groupValues[4].isNotEmpty() -> {
                // `code`
                pushStyle(codeStyle)
                append(match.groupValues[4])
                pop()
            }
        }
        lastIndex = match.range.last + 1
    }
    // Append remaining text
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}
