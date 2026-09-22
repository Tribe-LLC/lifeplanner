package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.domain.enum.BadgeType
import az.tribe.lifeplanner.domain.service.KnowledgeLibrary
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
        BadgeType.DECISION_FIRST -> 1
        BadgeType.DECISION_REVIEW_FIRST -> 1
        BadgeType.DECISION_REVIEW_10 -> 10
        BadgeType.DECISION_SOUND_10 -> 10
        BadgeType.DECISION_CALIBRATED -> 10

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
        BadgeType.FOCUS_FIRST -> 1
        BadgeType.FOCUS_HOUR -> 1
        BadgeType.FOCUS_10 -> 10
        BadgeType.FOCUS_50 -> 50
        BadgeType.GETTING_STARTED -> 10
        // Derived from the library so adding lessons to a path moves its bar automatically.
        BadgeType.LEARN_HABITS, BadgeType.LEARN_MOTIVATION,
        BadgeType.LEARN_MIND, BadgeType.LEARN_REST -> KnowledgeLibrary.lessonCountFor(type)
    }

    /**
     * How rare/hard-won a badge is, 1 (starter) to 5 (legendary). Used to pick the single badge
     * worth celebrating when several are earned at once, so a batch leads with its best moment
     * instead of burying it under starter badges.
     */
    fun getSignificance(type: BadgeType): Int = when (type) {
        // Calibration is the hardest thing here: it cannot be farmed, only earned by knowing
        // your own confidence. Logging a first call is the easiest.
        BadgeType.DECISION_CALIBRATED -> 5
        BadgeType.DECISION_SOUND_10 -> 4
        BadgeType.DECISION_REVIEW_10 -> 3
        BadgeType.DECISION_REVIEW_FIRST -> 2
        BadgeType.DECISION_FIRST -> 1

        BadgeType.STREAK_100, BadgeType.GOAL_50,
        BadgeType.HABIT_PERFECT_MONTH, BadgeType.FOCUS_50 -> 5

        BadgeType.STREAK_30, BadgeType.GOAL_25, BadgeType.JOURNAL_30,
        BadgeType.HABIT_PERFECT_WEEK, BadgeType.FOCUS_10, BadgeType.BALANCED -> 4

        BadgeType.STREAK_14, BadgeType.GOAL_10, BadgeType.JOURNAL_10, BadgeType.HABIT_5,
        BadgeType.FOCUS_HOUR, BadgeType.HEALTH_FOCUS, BadgeType.CAREER_FOCUS,
        BadgeType.COMEBACK,
        BadgeType.LEARN_HABITS, BadgeType.LEARN_MOTIVATION,
        BadgeType.LEARN_MIND, BadgeType.LEARN_REST -> 3

        BadgeType.STREAK_7, BadgeType.GOAL_5,
        BadgeType.PERFECTIONIST, BadgeType.GETTING_STARTED -> 2

        BadgeType.FIRST_STEP, BadgeType.STREAK_3, BadgeType.GOAL_1, BadgeType.HABIT_STARTER,
        BadgeType.JOURNAL_FIRST, BadgeType.FOCUS_FIRST,
        BadgeType.EARLY_BIRD, BadgeType.NIGHT_OWL -> 1
    }

    /**
     * Get the category this badge applies to
     */
    fun getCategory(type: BadgeType): BadgeCategory = when (type) {
        BadgeType.DECISION_FIRST, BadgeType.DECISION_REVIEW_FIRST, BadgeType.DECISION_REVIEW_10,
        BadgeType.DECISION_SOUND_10, BadgeType.DECISION_CALIBRATED -> BadgeCategory.DECISIONS

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

        BadgeType.FOCUS_FIRST, BadgeType.FOCUS_HOUR,
        BadgeType.FOCUS_10, BadgeType.FOCUS_50 -> BadgeCategory.SPECIAL

        BadgeType.GETTING_STARTED -> BadgeCategory.SPECIAL

        BadgeType.LEARN_HABITS, BadgeType.LEARN_MOTIVATION,
        BadgeType.LEARN_MIND, BadgeType.LEARN_REST -> BadgeCategory.LEARNING
    }
}

enum class BadgeCategory(val displayName: String) {
    STREAK("Streaks"),
    GOALS("Goals"),
    HABITS("Habits"),
    JOURNAL("Journal"),
    LEARNING("Learning"),
    CATEGORY("Categories"),
    DECISIONS("Decisions"),
    SPECIAL("Special")
}
