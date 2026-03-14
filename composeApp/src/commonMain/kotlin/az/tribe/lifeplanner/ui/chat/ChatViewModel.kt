package az.tribe.lifeplanner.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.enum.HabitFrequency
import az.tribe.lifeplanner.domain.enum.Mood
import co.touchlab.kermit.Logger
import az.tribe.lifeplanner.data.repository.ChatRepositoryImpl
import az.tribe.lifeplanner.domain.model.ChatMessage
import az.tribe.lifeplanner.domain.model.ChatSession
import az.tribe.lifeplanner.domain.model.CoachGroup
import az.tribe.lifeplanner.domain.model.CoachPersona
import az.tribe.lifeplanner.domain.model.CoachSuggestion
import az.tribe.lifeplanner.domain.model.CustomCoach
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.JournalEntry
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.model.UserContext
import az.tribe.lifeplanner.domain.repository.ChatRepository
import az.tribe.lifeplanner.domain.repository.CoachRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.JournalRepository
import az.tribe.lifeplanner.domain.repository.StreamingChatEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class ChatUiState(
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isStreaming: Boolean = false,
    val streamingText: String? = null,
    val sessions: List<ChatSession> = emptyList(),
    val sessionsByCoach: Map<String, ChatSession?> = emptyMap(),
    val currentSession: ChatSession? = null,
    val currentCoach: CoachPersona? = null,
    val currentCustomCoach: CustomCoach? = null,
    val currentCoachGroup: CoachGroup? = null,
    val isCouncilMode: Boolean = false,
    val isCustomCoachMode: Boolean = false,
    val isCustomGroupMode: Boolean = false,
    val customCoaches: List<CustomCoach> = emptyList(),
    val coachGroups: List<CoachGroup> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val userContext: UserContext? = null,
    val error: String? = null,
    val showSessionList: Boolean = true,
    val actionFeedback: String? = null,
    val executingAction: Boolean = false,
    val executedSuggestionIds: Set<String> = emptySet()
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val goalRepository: GoalRepository,
    private val habitRepository: HabitRepository,
    private val journalRepository: JournalRepository,
    private val coachRepository: CoachRepository? = null  // Optional for backward compatibility
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadSessions()
        loadUserContext()
        loadCustomCoachesAndGroups()
    }

    fun loadSessions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val sessions = chatRepository.getAllSessions()

                // Build sessions by coach map
                val sessionsByCoach = mutableMapOf<String, ChatSession?>()
                CoachPersona.ALL_COACHES.forEach { coach ->
                    sessionsByCoach[coach.id] = sessions.find { it.coachId == coach.id }
                }
                // Add council session
                sessionsByCoach[CoachPersona.COUNCIL_ID] = sessions.find { it.coachId == CoachPersona.COUNCIL_ID }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    sessions = sessions,
                    sessionsByCoach = sessionsByCoach
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    private fun loadUserContext() {
        viewModelScope.launch {
            try {
                val context = chatRepository.getUserContext()
                _uiState.value = _uiState.value.copy(userContext = context)
            } catch (e: Exception) {
                co.touchlab.kermit.Logger.e("ChatViewModel") {
                    "Failed to load user context: ${e.message}\n${e.stackTraceToString()}"
                }
                // Use default context so chat still works
                _uiState.value = _uiState.value.copy(userContext = defaultUserContext())
            }
        }
    }

    private fun defaultUserContext(): az.tribe.lifeplanner.domain.model.UserContext {
        return az.tribe.lifeplanner.domain.model.UserContext(
            userName = null,
            totalGoals = 0,
            completedGoals = 0,
            activeGoals = 0,
            currentStreak = 0,
            totalXp = 0,
            level = 1,
            recentMilestones = emptyList(),
            upcomingDeadlines = emptyList(),
            habitCompletionRate = 0f,
            journalEntryCount = 0,
            primaryCategories = emptyList()
        )
    }

    private fun loadCustomCoachesAndGroups() {
        viewModelScope.launch {
            try {
                val customCoaches = coachRepository?.getAllCustomCoaches() ?: emptyList()
                val coachGroups = coachRepository?.getAllCoachGroups() ?: emptyList()

                _uiState.value = _uiState.value.copy(
                    customCoaches = customCoaches,
                    coachGroups = coachGroups
                )
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }

    /**
     * Refresh custom coaches and groups (call after creating/editing)
     */
    fun refreshCustomCoaches() {
        loadCustomCoachesAndGroups()
        loadSessions()
    }

    fun createNewSession() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val session = chatRepository.createSession("New Chat")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentSession = session,
                    messages = emptyList(),
                    showSessionList = false,
                    executedSuggestionIds = emptySet()
                )
                // Refresh sessions list
                loadSessions()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * Select a specific coach to chat with (one session per coach)
     */
    fun selectCoach(coach: CoachPersona) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val session = chatRepository.getOrCreateSessionForCoach(coach.id)
                val messages = chatRepository.getMessages(session.id)

                // Collect executed suggestion IDs from message metadata
                val executedIds = messages
                    .mapNotNull { it.metadata?.executedSuggestionIds }
                    .flatten()
                    .toSet()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentSession = session,
                    currentCoach = coach,
                    isCouncilMode = false,
                    messages = messages,
                    showSessionList = false,
                    executedSuggestionIds = executedIds
                )

                // Refresh sessions list
                loadSessions()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * Select The Council group chat where all coaches can participate
     */
    fun selectCouncil() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val session = chatRepository.getOrCreateSessionForCoach(CoachPersona.COUNCIL_ID)
                val messages = chatRepository.getMessages(session.id)

                // Collect executed suggestion IDs from message metadata
                val executedIds = messages
                    .mapNotNull { it.metadata?.executedSuggestionIds }
                    .flatten()
                    .toSet()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentSession = session,
                    currentCoach = null,
                    isCouncilMode = true,
                    messages = messages,
                    showSessionList = false,
                    executedSuggestionIds = executedIds
                )

                // Refresh sessions list
                loadSessions()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * Select a user-created custom coach
     */
    fun selectCustomCoach(customCoach: CustomCoach) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val coachId = ChatRepositoryImpl.makeCustomCoachId(customCoach.id)
                val session = chatRepository.getOrCreateSessionForCoach(coachId)
                val messages = chatRepository.getMessages(session.id)

                val executedIds = messages
                    .mapNotNull { it.metadata?.executedSuggestionIds }
                    .flatten()
                    .toSet()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentSession = session,
                    currentCoach = null,
                    currentCustomCoach = customCoach,
                    currentCoachGroup = null,
                    isCouncilMode = false,
                    isCustomCoachMode = true,
                    isCustomGroupMode = false,
                    messages = messages,
                    showSessionList = false,
                    executedSuggestionIds = executedIds
                )

                loadSessions()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * Select a user-created coach group
     */
    fun selectCoachGroup(coachGroup: CoachGroup) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val groupId = ChatRepositoryImpl.makeGroupId(coachGroup.id)
                val session = chatRepository.getOrCreateSessionForCoach(groupId)
                val messages = chatRepository.getMessages(session.id)

                val executedIds = messages
                    .mapNotNull { it.metadata?.executedSuggestionIds }
                    .flatten()
                    .toSet()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentSession = session,
                    currentCoach = null,
                    currentCustomCoach = null,
                    currentCoachGroup = coachGroup,
                    isCouncilMode = false,
                    isCustomCoachMode = false,
                    isCustomGroupMode = true,
                    messages = messages,
                    showSessionList = false,
                    executedSuggestionIds = executedIds
                )

                loadSessions()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * Select coach by ID (used for navigation)
     * Handles built-in coaches, custom coaches, and groups
     */
    fun selectCoachById(coachId: String) {
        viewModelScope.launch {
            when {
                coachId == CoachPersona.COUNCIL_ID -> {
                    selectCouncil()
                }
                ChatRepositoryImpl.isCustomCoachId(coachId) -> {
                    val customId = ChatRepositoryImpl.extractCustomCoachId(coachId)
                    val customCoach = coachRepository?.getCustomCoachById(customId)
                    if (customCoach != null) {
                        selectCustomCoach(customCoach)
                    }
                }
                ChatRepositoryImpl.isGroupId(coachId) -> {
                    val groupId = ChatRepositoryImpl.extractGroupId(coachId)
                    val group = coachRepository?.getCoachGroupById(groupId)
                    if (group != null) {
                        selectCoachGroup(group)
                    }
                }
                else -> {
                    val coach = CoachPersona.getById(coachId)
                    selectCoach(coach)
                }
            }
        }
    }

    fun selectSession(session: ChatSession) {
        selectSessionById(session.id)
    }

    fun selectSessionById(sessionId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val fullSession = chatRepository.getSessionById(sessionId)
                val messages = chatRepository.getMessages(sessionId)

                // Collect all executed suggestion IDs from message metadata
                val executedIds = messages
                    .mapNotNull { it.metadata?.executedSuggestionIds }
                    .flatten()
                    .toSet()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentSession = fullSession,
                    messages = messages,
                    showSessionList = false,
                    executedSuggestionIds = executedIds
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * Returns true if the current chat mode supports streaming.
     * Council mode, custom groups use structured JSON (non-streaming).
     */
    private fun isStreamable(): Boolean {
        val state = _uiState.value
        return !state.isCouncilMode && !state.isCustomGroupMode
    }

    fun sendMessage(content: String, relatedGoalId: String? = null) {
        val session = _uiState.value.currentSession ?: run {
            co.touchlab.kermit.Logger.w("ChatViewModel") { "sendMessage: no currentSession, ignoring" }
            return
        }
        val userContext = _uiState.value.userContext ?: run {
            co.touchlab.kermit.Logger.w("ChatViewModel") { "sendMessage: userContext is null, using default" }
            defaultUserContext()
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)

            if (isStreamable()) {
                sendMessageStreaming(session.id, content, userContext, relatedGoalId)
            } else {
                sendMessageNonStreaming(session.id, content, userContext, relatedGoalId)
            }
        }
    }

    private suspend fun sendMessageStreaming(
        sessionId: String,
        content: String,
        userContext: UserContext,
        relatedGoalId: String?
    ) {
        var receivedCompletion = false

        try {
            chatRepository.sendMessageStreaming(
                sessionId = sessionId,
                userMessage = content,
                userContext = userContext,
                relatedGoalId = relatedGoalId
            ).collect { event ->
                when (event) {
                    is StreamingChatEvent.UserMessageSaved -> {
                        // User message is now in DB — add it to the UI with its real ID
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages + event.message,
                            isStreaming = true,
                            streamingText = ""
                        )
                    }
                    is StreamingChatEvent.PartialText -> {
                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            streamingText = event.accumulatedText
                        )
                    }
                    is StreamingChatEvent.Completed -> {
                        receivedCompletion = true
                        // Load final messages from DB — correct IDs and order guaranteed
                        val dbMessages = chatRepository.getMessages(sessionId)
                        val executedIds = dbMessages
                            .mapNotNull { it.metadata?.executedSuggestionIds }
                            .flatten()
                            .toSet()

                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            isStreaming = false,
                            streamingText = null,
                            messages = dbMessages,
                            executedSuggestionIds = executedIds
                        )
                        loadSessions()
                    }
                    is StreamingChatEvent.Error -> {
                        receivedCompletion = true
                        // On error, reload from DB (user msg was already saved)
                        val dbMessages = try { chatRepository.getMessages(sessionId) } catch (_: Exception) { null }
                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            isStreaming = false,
                            streamingText = null,
                            messages = dbMessages ?: _uiState.value.messages,
                            error = event.message
                        )
                    }
                }
            }

            // Flow completed without Done/Error — reset state and reload messages
            if (!receivedCompletion) {
                Logger.w("ChatViewModel") { "Streaming flow completed without terminal event" }
                val dbMessages = chatRepository.getMessages(sessionId)
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    isStreaming = false,
                    streamingText = null,
                    messages = dbMessages,
                    error = "Response failed. Please try again."
                )
            }
        } catch (e: Exception) {
            Logger.e("ChatViewModel") { "Streaming failed: ${e.message}" }
            // Reload messages from DB (user message may have been saved)
            val dbMessages = try { chatRepository.getMessages(sessionId) } catch (_: Exception) { null }
            _uiState.value = _uiState.value.copy(
                isSending = false,
                isStreaming = false,
                streamingText = null,
                messages = dbMessages ?: _uiState.value.messages,
                error = e.message ?: "Failed to send message"
            )
        }
    }

    private suspend fun sendMessageNonStreaming(
        sessionId: String,
        content: String,
        userContext: UserContext,
        relatedGoalId: String?
    ) {
        try {
            chatRepository.sendMessage(
                sessionId = sessionId,
                userMessage = content,
                userContext = userContext,
                relatedGoalId = relatedGoalId
            )

            // Reload all messages to get proper IDs and correct order
            val dbMessages = chatRepository.getMessages(sessionId)

            // Collect executed suggestion IDs from message metadata
            val executedIds = dbMessages
                .mapNotNull { it.metadata?.executedSuggestionIds }
                .flatten()
                .toSet()

            _uiState.value = _uiState.value.copy(
                isSending = false,
                messages = dbMessages,
                executedSuggestionIds = executedIds
            )

            // Refresh sessions to update titles
            loadSessions()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isSending = false,
                error = e.message ?: "Failed to send message"
            )
        }
    }

    /**
     * Sends a message to the AI without showing the user prompt in the chat.
     * The prompt is stored in DB for conversation context, but only the AI response is displayed.
     */
    private fun sendHiddenFollowUp(content: String) {
        val session = _uiState.value.currentSession ?: return
        val userContext = _uiState.value.userContext ?: defaultUserContext()

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)
            try {
                chatRepository.sendMessage(
                    sessionId = session.id,
                    userMessage = content,
                    userContext = userContext,
                    relatedGoalId = null
                )

                // Reload all messages, then drop the hidden user prompt
                val allMessages = chatRepository.getMessages(session.id)
                val hiddenId = allMessages.lastOrNull {
                    it.role == az.tribe.lifeplanner.domain.model.MessageRole.USER && it.content == content
                }?.id
                val visibleMessages = allMessages.filter { it.id != hiddenId }

                val executedIds = allMessages
                    .mapNotNull { it.metadata?.executedSuggestionIds }
                    .flatten()
                    .toSet()

                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    messages = visibleMessages,
                    executedSuggestionIds = executedIds
                )

                loadSessions()
            } catch (e: Exception) {
                Logger.w("ChatViewModel") { "Council message send failed: ${e.message}" }
                _uiState.value = _uiState.value.copy(isSending = false)
            }
        }
    }

    fun deleteSession(session: ChatSession) {
        viewModelScope.launch {
            try {
                chatRepository.deleteSession(session.id)
                if (_uiState.value.currentSession?.id == session.id) {
                    _uiState.value = _uiState.value.copy(
                        currentSession = null,
                        messages = emptyList(),
                        showSessionList = true
                    )
                }
                loadSessions()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun navigateBack() {
        _uiState.value = _uiState.value.copy(
            showSessionList = true,
            currentSession = null,
            messages = emptyList()
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun refreshUserContext() {
        loadUserContext()
    }

    /**
     * Execute a coach suggestion (create goal, habit, journal entry, or check-in habit)
     */
    @OptIn(ExperimentalUuidApi::class)
    fun executeCoachSuggestion(suggestion: CoachSuggestion) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(executingAction = true, error = null)

            // Find the message containing this suggestion to persist executed state
            val messageWithSuggestion = _uiState.value.messages.find { message ->
                message.metadata?.coachSuggestions?.any { it.id == suggestion.id } == true
            }

            try {
                when (suggestion) {
                    is CoachSuggestion.CreateGoal -> {
                        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                        val dueDate = when (suggestion.timeline) {
                            "SHORT_TERM" -> now.date.plus(30, DateTimeUnit.DAY)
                            "MID_TERM" -> now.date.plus(90, DateTimeUnit.DAY)
                            "LONG_TERM" -> now.date.plus(365, DateTimeUnit.DAY)
                            else -> now.date.plus(90, DateTimeUnit.DAY)
                        }

                        // Convert suggested milestones to Milestone objects
                        val milestones = suggestion.milestones.mapIndexed { index, suggested ->
                            val milestoneDueDate = now.date.plus(
                                (suggested.weekOffset.coerceAtLeast(1) * 7).toLong(),
                                DateTimeUnit.DAY
                            )
                            Milestone(
                                id = Uuid.random().toString(),
                                title = suggested.title,
                                isCompleted = false,
                                dueDate = milestoneDueDate
                            )
                        }

                        val goal = Goal(
                            id = Uuid.random().toString(),
                            category = GoalCategory.entries.find { it.name == suggestion.category }
                                ?: GoalCategory.CAREER,
                            title = suggestion.title,
                            description = suggestion.description,
                            status = GoalStatus.NOT_STARTED,
                            timeline = GoalTimeline.entries.find { it.name == suggestion.timeline }
                                ?: GoalTimeline.MID_TERM,
                            dueDate = dueDate,
                            progress = 0,
                            milestones = milestones,
                            createdAt = now
                        )
                        goalRepository.insertGoal(goal)

                        val milestoneCount = milestones.size
                        val feedback = if (milestoneCount > 0) {
                            "Goal '${suggestion.title}' created with $milestoneCount milestones!"
                        } else {
                            "Goal '${suggestion.title}' created!"
                        }
                        _uiState.value = _uiState.value.copy(
                            executingAction = false,
                            actionFeedback = feedback,
                            executedSuggestionIds = _uiState.value.executedSuggestionIds + suggestion.id
                        )
                    }

                    is CoachSuggestion.CreateHabit -> {
                        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                        val habit = Habit(
                            id = Uuid.random().toString(),
                            title = suggestion.title,
                            description = suggestion.description,
                            category = GoalCategory.entries.find { it.name == suggestion.category }
                                ?: GoalCategory.EMOTIONAL,
                            frequency = if (suggestion.frequency == "WEEKLY") HabitFrequency.WEEKLY else HabitFrequency.DAILY,
                            createdAt = now
                        )
                        habitRepository.insertHabit(habit)
                        _uiState.value = _uiState.value.copy(
                            executingAction = false,
                            actionFeedback = "Habit '${suggestion.title}' created!",
                            executedSuggestionIds = _uiState.value.executedSuggestionIds + suggestion.id
                        )
                    }

                    is CoachSuggestion.CreateJournalEntry -> {
                        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                        val entry = JournalEntry(
                            id = Uuid.random().toString(),
                            title = suggestion.title,
                            content = suggestion.content,
                            mood = suggestion.mood?.let { moodStr ->
                                Mood.entries.find { it.name == moodStr }
                            } ?: Mood.NEUTRAL,
                            date = now.date,
                            createdAt = now
                        )
                        journalRepository.insertEntry(entry)
                        _uiState.value = _uiState.value.copy(
                            executingAction = false,
                            actionFeedback = "Journal entry created!",
                            executedSuggestionIds = _uiState.value.executedSuggestionIds + suggestion.id
                        )
                    }

                    is CoachSuggestion.CheckInHabit -> {
                        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                        habitRepository.checkIn(suggestion.habitId, today)
                        _uiState.value = _uiState.value.copy(
                            executingAction = false,
                            actionFeedback = "Checked in: ${suggestion.habitTitle}",
                            executedSuggestionIds = _uiState.value.executedSuggestionIds + suggestion.id
                        )
                    }

                    is CoachSuggestion.AskQuestion -> {
                        // Mark the question as answered — the selected option is sent
                        // as a regular message via the onAnswerQuestion callback in the UI
                        _uiState.value = _uiState.value.copy(
                            executingAction = false,
                            executedSuggestionIds = _uiState.value.executedSuggestionIds + suggestion.id
                        )
                    }
                }

                // Persist executed suggestion to database
                messageWithSuggestion?.let { message ->
                    chatRepository.markSuggestionExecuted(message.id, suggestion.id)
                }

                // Refresh user context after action
                loadUserContext()

                // Auto-send hidden follow-up so the coach continues the conversation
                val followUp = buildFollowUpMessage(suggestion)
                if (followUp != null) {
                    sendHiddenFollowUp(followUp)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    executingAction = false,
                    error = e.message ?: "Failed to execute action"
                )
            }
        }
    }

    private fun buildFollowUpMessage(suggestion: CoachSuggestion): String? {
        return when (suggestion) {
            is CoachSuggestion.CreateGoal ->
                "I just added the goal \"${suggestion.title}\" to my list. What should I focus on first to get started?"
            is CoachSuggestion.CreateHabit ->
                "I just created the habit \"${suggestion.title}\". Any tips on how to stay consistent with it?"
            is CoachSuggestion.CreateJournalEntry ->
                "I just saved that journal entry. What else should I reflect on?"
            is CoachSuggestion.CheckInHabit ->
                "Done! I checked in for \"${suggestion.habitTitle}\". How am I doing overall?"
            is CoachSuggestion.AskQuestion -> null
        }
    }

    fun clearActionFeedback() {
        _uiState.value = _uiState.value.copy(actionFeedback = null)
    }
}
