package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.domain.enum.BadgeType
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/**
 * Represents a badge earned by the user
 */
@Serializable
data class Badge(
    val id: String,
    val type: BadgeType,
    val earnedAt: LocalDateTime,
    val isNew: Boolean = true // To show celebration animation
)

/**
 * Extension to get badge progress requirements
 */
object BadgeRequirements {
    fun getRequirementValue(type: BadgeType): Int = when (type) {
        BadgeType.FIRST_STEP -> 1
        BadgeType.STREAK_3 -> 3
        BadgeType.STREAK_7 -> 7
        BadgeType.STREAK_14 -> 14
        BadgeType.STREAK_30 -> 30
        BadgeType.STREAK_100 -> 100
        BadgeType.GOAL_1 -> 1
        BadgeType.GOAL_5 -> 5
        BadgeType.GOAL_10 -> 10
        BadgeType.GOAL_25 -> 25
        BadgeType.GOAL_50 -> 50
        BadgeType.HABIT_STARTER -> 1
        BadgeType.HABIT_5 -> 5
        BadgeType.HABIT_PERFECT_WEEK -> 7
        BadgeType.HABIT_PERFECT_MONTH -> 30
        BadgeType.JOURNAL_FIRST -> 1
        BadgeType.JOURNAL_10 -> 10
        BadgeType.JOURNAL_30 -> 30
        BadgeType.BALANCED -> 8
        BadgeType.HEALTH_FOCUS -> 5
        BadgeType.CAREER_FOCUS -> 5
        BadgeType.EARLY_BIRD -> 1
        BadgeType.NIGHT_OWL -> 1
        BadgeType.COMEBACK -> 7
        BadgeType.PERFECTIONIST -> 100
    }

    /**
     * Get the category this badge applies to
     */
    fun getCategory(type: BadgeType): BadgeCategory = when (type) {
        BadgeType.STREAK_3, BadgeType.STREAK_7, BadgeType.STREAK_14,
        BadgeType.STREAK_30, BadgeType.STREAK_100 -> BadgeCategory.STREAK

        BadgeType.FIRST_STEP, BadgeType.GOAL_1, BadgeType.GOAL_5,
        BadgeType.GOAL_10, BadgeType.GOAL_25, BadgeType.GOAL_50 -> BadgeCategory.GOALS

        BadgeType.HABIT_STARTER, BadgeType.HABIT_5,
        BadgeType.HABIT_PERFECT_WEEK, BadgeType.HABIT_PERFECT_MONTH -> BadgeCategory.HABITS

        BadgeType.JOURNAL_FIRST, BadgeType.JOURNAL_10,
        BadgeType.JOURNAL_30 -> BadgeCategory.JOURNAL

        BadgeType.BALANCED, BadgeType.HEALTH_FOCUS,
        BadgeType.CAREER_FOCUS -> BadgeCategory.CATEGORY

        BadgeType.EARLY_BIRD, BadgeType.NIGHT_OWL,
        BadgeType.COMEBACK, BadgeType.PERFECTIONIST -> BadgeCategory.SPECIAL
    }
}

enum class BadgeCategory(val displayName: String) {
    STREAK("Streaks"),
    GOALS("Goals"),
    HABITS("Habits"),
    JOURNAL("Journal"),
    CATEGORY("Categories"),
    SPECIAL("Special")
}
