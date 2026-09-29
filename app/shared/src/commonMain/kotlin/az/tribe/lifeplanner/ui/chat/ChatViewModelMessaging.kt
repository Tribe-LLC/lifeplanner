package az.tribe.lifeplanner.ui.chat

import az.tribe.lifeplanner.domain.model.UserContext
import az.tribe.lifeplanner.domain.repository.StreamingChatEvent
import co.touchlab.kermit.Logger

internal suspend fun ChatViewModel.sendMessageStreaming(
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
                    // User message is now in DB, add it to the UI with its real ID
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
                    // Load final messages from DB, correct IDs and order guaranteed
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
                    // Auto-trigger goal questionnaire for first unprocessed CreateGoal suggestion
                    maybeStartGoalQuestionnaire(dbMessages)
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

        // Flow completed without Done/Error, reset state and reload messages
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
    } catch (e: kotlinx.coroutines.CancellationException) {
        // Stopped by the user: stopReply keeps what was written and sets the state.
        throw e
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

internal suspend fun ChatViewModel.sendMessageNonStreaming(
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
        // Auto-trigger goal questionnaire for first unprocessed CreateGoal suggestion
        maybeStartGoalQuestionnaire(dbMessages)
    } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(
            isSending = false,
            error = e.message ?: "Failed to send message"
        )
    }
}

/**
 * A reply cut short can end inside one of the coach's hidden tags ("[SUGGEST_HABIT: ...") or
 * carry whole ones. Keep only the words.
 */
internal fun cleanPartial(text: String): String =
    text.replace(Regex("""\[(SUGGEST_[A-Z]+|UPDATE_SITUATION)[^\]]*\]"""), "")
        .replace(Regex("""\[[A-Z_]*[^\]]*$"""), "")
        .trim()
