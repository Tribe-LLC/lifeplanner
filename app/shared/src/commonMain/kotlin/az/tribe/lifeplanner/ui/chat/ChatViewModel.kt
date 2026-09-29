package az.tribe.lifeplanner.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.analytics.Analytics
import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.data.repository.ChatRepositoryImpl
import az.tribe.lifeplanner.domain.model.ChatMessage
import az.tribe.lifeplanner.domain.model.ChatSession
import az.tribe.lifeplanner.domain.model.CoachGroup
import az.tribe.lifeplanner.data.repository.BuiltinCoachStore
import az.tribe.lifeplanner.domain.model.CoachPersona
import az.tribe.lifeplanner.domain.model.CoachSuggestion
import az.tribe.lifeplanner.domain.model.CustomCoach
import az.tribe.lifeplanner.domain.model.MessageRole
import az.tribe.lifeplanner.domain.model.UserContext
import az.tribe.lifeplanner.domain.repository.ChatRepository
import az.tribe.lifeplanner.domain.repository.CoachRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.JournalRepository
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// ── Goal questionnaire (inline in chat) ──────────────────────────────────────

data class ChatGoalQuestion(val text: String, val options: List<String>)

data class ChatGoalQuestionnaire(
    val forSuggestionId: String,
    val intentText: String,
    val questions: List<ChatGoalQuestion> = emptyList(),
    val answers: List<List<String>> = emptyList(),   // multi-select per question
    val isLoading: Boolean = true,
    val submitted: Boolean = false,
    val loadError: Boolean = false
)

// ─────────────────────────────────────────────────────────────────────────────

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
    val executedSuggestionIds: Set<String> = emptySet(),
    val goalQuestionnaire: ChatGoalQuestionnaire? = null,
    val questionnairedSuggestionIds: Set<String> = emptySet()
)

class ChatViewModel(
    internal val chatRepository: ChatRepository,
    internal val goalRepository: GoalRepository,
    internal val habitRepository: HabitRepository,
    internal val journalRepository: JournalRepository,
    internal val aiProxy: AiProxyService,
    internal val coachRepository: CoachRepository? = null
) : ViewModel() {

    internal val _uiState = MutableStateFlow(ChatUiState())
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
                BuiltinCoachStore.getAll().forEach { coach ->
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

    internal fun loadUserContext() {
        viewModelScope.launch {
            try {
                val context = chatRepository.getUserContext()
                _uiState.value = _uiState.value.copy(userContext = context)
            } catch (e: Exception) {
                Logger.e("ChatViewModel") {
                    "Failed to load user context: ${e.message}\n${e.stackTraceToString()}"
                }
                // Use default context so chat still works
                _uiState.value = _uiState.value.copy(userContext = defaultUserContext())
            }
        }
    }

    internal fun defaultUserContext(): UserContext {
        return UserContext(
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
            Logger.w("ChatViewModel") { "sendMessage: no currentSession, ignoring" }
            return
        }
        val userContext = _uiState.value.userContext ?: run {
            Logger.w("ChatViewModel") { "sendMessage: userContext is null, using default" }
            defaultUserContext()
        }

        val coachId = session.coachId
        val isFirst = _uiState.value.messages.none { it.role == MessageRole.USER }
        Analytics.chatMessageSent(coachId, isFirst)

        replyJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)

            if (isStreamable()) {
                sendMessageStreaming(session.id, content, userContext, relatedGoalId)
            } else {
                sendMessageNonStreaming(session.id, content, userContext, relatedGoalId)
            }
        }
    }

    /** The reply being written, so Stop can end it. */
    private var replyJob: Job? = null

    /**
     * Stops the coach mid-reply, like the stop button in the big chat apps. What was written so
     * far is kept: saved as the coach's message, so it stays in the chat and in the history the
     * coach sees next time. A stop before any words arrive just ends the wait.
     */
    fun stopReply() {
        val job = replyJob?.takeIf { it.isActive } ?: return
        val session = _uiState.value.currentSession ?: return
        val partial = cleanPartial(_uiState.value.streamingText.orEmpty())
        job.cancel()
        replyJob = null
        _uiState.value = _uiState.value.copy(isSending = false, isStreaming = false, streamingText = null, error = null)
        viewModelScope.launch {
            try {
                if (partial.isNotBlank()) chatRepository.addAssistantMessage(session.id, partial)
                _uiState.value = _uiState.value.copy(messages = chatRepository.getMessages(session.id))
            } catch (e: Exception) {
                Logger.w("ChatViewModel") { "Keeping a stopped reply failed: ${e.message}" }
            }
        }
    }

    /**
     * Sends a message to the AI without showing the user prompt in the chat.
     * The prompt is stored in DB for conversation context, but only the AI response is displayed.
     */
    internal fun sendHiddenFollowUp(content: String) {
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
                    it.role == MessageRole.USER && it.content == content
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

    // ── Goal questionnaire ────────────────────────────────────────────────────

    internal fun maybeStartGoalQuestionnaire(messages: List<ChatMessage>) {
        val lastAssistant = messages.lastOrNull {
            it.role == MessageRole.ASSISTANT
        }
        val goalSuggestion = lastAssistant?.metadata?.coachSuggestions
            ?.filterIsInstance<CoachSuggestion.CreateGoal>()
            ?.firstOrNull() ?: return

        val state = _uiState.value
        // Skip if already processed or an active questionnaire is in progress
        if (goalSuggestion.id in state.questionnairedSuggestionIds) return
        if (state.goalQuestionnaire != null && !state.goalQuestionnaire.submitted) return
        // Only ONE questionnaire per conversation. After the user has answered one, the coach's
        // follow-up goal suggestions must render as their normal "Add goal" action, not spawn a
        // fresh questionnaire, otherwise answering just loops back into more questions instead of
        // producing a goal to create.
        if (state.goalQuestionnaire?.submitted == true) return

        startGoalQuestionnaire(goalSuggestion)
    }

    fun refreshUserContext() {
        loadUserContext()
    }

    fun clearActionFeedback() {
        _uiState.value = _uiState.value.copy(actionFeedback = null)
    }
}
