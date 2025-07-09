package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalTimeline

data class Quest(
    val id: String,
    val title: String,
    val description: String,
    val category: GoalCategory,
    val timeline: GoalTimeline,
    val difficulty: Int, // 1-3 for beginner/intermediate/advanced
    val points: Int,
    val parentQuestId: String? = null,
    val isLocked: Boolean = true,
    val requiredPoints: Int = 0
)