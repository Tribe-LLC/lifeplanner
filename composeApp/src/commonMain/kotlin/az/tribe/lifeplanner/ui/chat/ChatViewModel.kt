package az.tribe.lifeplanner.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.domain.model.ChatMessage
import az.tribe.lifeplanner.domain.model.ChatSession
import az.tribe.lifeplanner.domain.model.UserContext
import az.tribe.lifeplanner.domain.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class ChatUiState(
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val sessions: List<ChatSession> = emptyList(),
    val currentSession: ChatSession? = null,
    val messages: List<ChatMessage> = emptyList(),
    val userContext: UserContext? = null,
    val error: String? = null,
    val showSessionList: Boolean = true
)

class ChatViewModel(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadSessions()
        loadUserContext()
    }

    fun loadSessions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val sessions = chatRepository.getAllSessions()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    sessions = sessions
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
                // Silently fail - will use default context
            }
        }
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
                    showSessionList = false
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

    fun selectSession(session: ChatSession) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val fullSession = chatRepository.getSessionById(session.id)
                val messages = chatRepository.getMessages(session.id)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentSession = fullSession,
                    messages = messages,
                    showSessionList = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun sendMessage(content: String, relatedGoalId: String? = null) {
        val session = _uiState.value.currentSession ?: return
        val userContext = _uiState.value.userContext ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true, error = null)

            // Optimistically add user message to UI
            val tempUserMessage = ChatMessage(
                id = "temp_${Clock.System.now().toEpochMilliseconds()}",
                content = content,
                role = az.tribe.lifeplanner.domain.model.MessageRole.USER,
                timestamp = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                relatedGoalId = relatedGoalId
            )
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + tempUserMessage
            )

            try {
                val response = chatRepository.sendMessage(
                    sessionId = session.id,
                    userMessage = content,
                    userContext = userContext,
                    relatedGoalId = relatedGoalId
                )

                // Reload all messages to get proper IDs
                val messages = chatRepository.getMessages(session.id)
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    messages = messages
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
}
