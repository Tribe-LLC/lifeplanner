package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.domain.enum.Mood
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

data class JournalEntry(
    val id: String,
    val title: String,
    val content: String,
    val mood: Mood,
    val linkedGoalId: String? = null,
    val linkedHabitId: String? = null,
    val promptUsed: String? = null,
    val tags: List<String> = emptyList(),
    val date: LocalDate,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime? = null
)

object JournalPrompts {
    val dailyReflection = listOf(
        "What are you grateful for today?",
        "What was the highlight of your day?",
        "What did you learn today?",
        "How did you take care of yourself today?",
        "What's one thing you could have done better?"
    )

    val goalReflection = listOf(
        "What progress did you make on your goals today?",
        "What obstacles are you facing with your goals?",
        "What motivates you to achieve this goal?",
        "How will you feel when you accomplish this goal?",
        "What's the next small step you can take?"
    )

    val moodExploration = listOf(
        "What's causing you to feel this way?",
        "What would make today better?",
        "What are you looking forward to?",
        "Describe your current emotional state in detail.",
        "What do you need right now?"
    )

    val weeklyReview = listOf(
        "What were your biggest wins this week?",
        "What challenges did you overcome?",
        "What would you do differently?",
        "How did you grow this week?",
        "What are your priorities for next week?"
    )

    fun getRandomPrompt(): String {
        val allPrompts = dailyReflection + goalReflection + moodExploration
        return allPrompts.random()
    }

    fun getPromptsForMood(mood: Mood): List<String> {
        return when (mood) {
            Mood.VERY_HAPPY, Mood.HAPPY -> dailyReflection.take(3)
            Mood.NEUTRAL -> goalReflection.take(3)
            Mood.SAD, Mood.VERY_SAD -> moodExploration.take(3)
        }
    }
}
