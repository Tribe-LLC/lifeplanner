package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.enum.Mood
import kotlinx.serialization.Serializable

/**
 * Sealed class representing actionable suggestions from the Personal Coach.
 * These suggestions can be executed directly from the chat interface.
 */
@Serializable
sealed class CoachSuggestion {
    abstract val label: String
    abstract val id: String

    /**
     * Suggestion to create a new goal with milestones
     */
    @Serializable
    data class CreateGoal(
        override val id: String,
        override val label: String,
        val title: String,
        val description: String,
        val category: String, // GoalCategory name
        val timeline: String, // GoalTimeline name
        val milestones: List<SuggestedMilestone> = emptyList()
    ) : CoachSuggestion()

    /**
     * Suggestion to create a new habit
     */
    @Serializable
    data class CreateHabit(
        override val id: String,
        override val label: String,
        val title: String,
        val description: String,
        val category: String,
        val frequency: String // DAILY or WEEKLY
    ) : CoachSuggestion()

    /**
     * Suggestion to create a journal entry
     */
    @Serializable
    data class CreateJournalEntry(
        override val id: String,
        override val label: String,
        val title: String,
        val content: String,
        val mood: String? = null // Mood name
    ) : CoachSuggestion()

    /**
     * Suggestion to check in on a habit
     */
    @Serializable
    data class CheckInHabit(
        override val id: String,
        override val label: String,
        val habitId: String,
        val habitTitle: String
    ) : CoachSuggestion()

    /**
     * Interactive question for gathering user input before creating a goal/habit
     */
    @Serializable
    data class AskQuestion(
        override val id: String,
        override val label: String,
        val question: String,
        val options: List<QuestionOption>,
        val questionType: String, // CATEGORY, TIMELINE, TITLE, DESCRIPTION, CONFIRM
        val context: QuestionContext? = null // Accumulated answers for multi-step flow
    ) : CoachSuggestion()
}

/**
 * A milestone suggested by the coach
 */
@Serializable
data class SuggestedMilestone(
    val title: String,
    val weekOffset: Int = 0 // Weeks from goal start
)

/**
 * An option for a coach question
 */
@Serializable
data class QuestionOption(
    val id: String,
    val label: String,
    val value: String,
    val description: String? = null
)

/**
 * Context accumulated during multi-step questioning
 */
@Serializable
data class QuestionContext(
    val goalType: String? = null, // "GOAL" or "HABIT"
    val category: String? = null,
    val timeline: String? = null,
    val frequency: String? = null,
    val title: String? = null,
    val description: String? = null,
    val milestones: List<SuggestedMilestone> = emptyList()
)

/**
 * Result of parsing coach response - contains messages and optional suggestions
 */
data class CoachResponse(
    val messages: List<String>,
    val suggestions: List<CoachSuggestion> = emptyList()
) {
    /** Single message for backward compatibility */
    val message: String get() = messages.joinToString("\n\n")
}

/**
 * Attachment that can be added to a chat message
 */
@Serializable
sealed class ChatAttachment {
    @Serializable
    data class GoalAttachment(
        val goalId: String,
        val goalTitle: String,
        val category: String
    ) : ChatAttachment()

    @Serializable
    data class HabitAttachment(
        val habitId: String,
        val habitTitle: String,
        val category: String
    ) : ChatAttachment()
}
