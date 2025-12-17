package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalTimeline

data class GoalTemplate(
    val id: String,
    val category: GoalCategory,
    val title: String,
    val description: String,
    val suggestedTimeline: GoalTimeline,
    val suggestedMilestones: List<String>,
    val icon: String = "",
    val difficulty: TemplateDifficulty = TemplateDifficulty.MEDIUM,
    val tags: List<String> = emptyList()
)

enum class TemplateDifficulty {
    EASY,
    MEDIUM,
    HARD
}

data class MilestoneTemplate(
    val title: String,
    val relativeWeeks: Int = 0 // Weeks from goal start
)
