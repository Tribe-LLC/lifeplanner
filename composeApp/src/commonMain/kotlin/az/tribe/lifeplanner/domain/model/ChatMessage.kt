package az.tribe.lifeplanner.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/**
 * Represents a message in the AI Coach chat
 */
@Serializable
data class ChatMessage(
    val id: String,
    val content: String,
    val role: MessageRole,
    val timestamp: LocalDateTime,
    val relatedGoalId: String? = null,
    val metadata: ChatMessageMetadata? = null
)

/**
 * Role of the message sender
 */
@Serializable
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * Additional metadata for chat messages
 */
@Serializable
data class ChatMessageMetadata(
    val suggestedActions: List<SuggestedAction> = emptyList(),
    val coachSuggestions: List<CoachSuggestion> = emptyList(),
    val referencedGoals: List<String> = emptyList(),
    val mood: String? = null,
    val isMotivational: Boolean = false,
    val executedSuggestionIds: Set<String> = emptySet()
)

/**
 * Action suggested by the AI coach
 */
@Serializable
data class SuggestedAction(
    val type: ActionType,
    val label: String,
    val targetId: String? = null
)

/**
 * Types of actions the AI can suggest
 */
@Serializable
enum class ActionType {
    VIEW_GOAL,
    CREATE_GOAL,
    UPDATE_PROGRESS,
    CHECK_IN_HABIT,
    WRITE_JOURNAL,
    VIEW_ACHIEVEMENTS,
    SET_REMINDER
}

/**
 * Chat session containing multiple messages
 */
@Serializable
data class ChatSession(
    val id: String,
    val title: String,
    val messages: List<ChatMessage>,
    val createdAt: LocalDateTime,
    val lastMessageAt: LocalDateTime,
    val summary: String? = null,
    val coachId: String = "luna_general"
)

/**
 * User context for personalized AI responses
 */
data class UserContext(
    val userName: String?,
    val totalGoals: Int,
    val completedGoals: Int,
    val activeGoals: Int,
    val currentStreak: Int,
    val totalXp: Int,
    val level: Int,
    val recentMilestones: List<String>,
    val upcomingDeadlines: List<Goal>,
    val habitCompletionRate: Float,
    val journalEntryCount: Int,
    val primaryCategories: List<String>
)
