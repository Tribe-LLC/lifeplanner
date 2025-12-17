package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.ChatMessage
import az.tribe.lifeplanner.domain.model.ChatSession
import az.tribe.lifeplanner.domain.model.UserContext
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for AI Coach chat functionality
 */
interface ChatRepository {
    /**
     * Get all chat sessions
     */
    suspend fun getAllSessions(): List<ChatSession>

    /**
     * Get a specific chat session by ID
     */
    suspend fun getSessionById(sessionId: String): ChatSession?

    /**
     * Create a new chat session
     */
    suspend fun createSession(title: String): ChatSession

    /**
     * Delete a chat session and all its messages
     */
    suspend fun deleteSession(sessionId: String)

    /**
     * Get messages for a session
     */
    suspend fun getMessages(sessionId: String): List<ChatMessage>

    /**
     * Get recent messages for context (last N messages)
     */
    suspend fun getRecentMessages(sessionId: String, limit: Int): List<ChatMessage>

    /**
     * Add a user message to a session
     */
    suspend fun addUserMessage(sessionId: String, content: String, relatedGoalId: String? = null): ChatMessage

    /**
     * Add an assistant message to a session
     */
    suspend fun addAssistantMessage(sessionId: String, content: String, metadata: String? = null): ChatMessage

    /**
     * Send a message to the AI coach and get a response
     */
    suspend fun sendMessage(
        sessionId: String,
        userMessage: String,
        userContext: UserContext,
        relatedGoalId: String? = null
    ): ChatMessage

    /**
     * Get the current user context for personalization
     */
    suspend fun getUserContext(): UserContext

    /**
     * Update session summary
     */
    suspend fun updateSessionSummary(sessionId: String, summary: String)

    /**
     * Delete old sessions (cleanup)
     */
    suspend fun deleteOldSessions(beforeDate: String)

    /**
     * Get session count
     */
    suspend fun getSessionCount(): Long
}
