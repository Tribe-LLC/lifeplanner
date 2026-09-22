package az.tribe.lifeplanner.domain.enum

import kotlinx.serialization.Serializable

/**
 * Types of badges users can earn
 */
@Serializable
enum class BadgeType(
    val displayName: String,
    val description: String,
    val color: Long
) {
    // Streak Badges
    FIRST_STEP("First Step", "Complete your first goal", 0xFFFFD700),
    STREAK_3("On Fire", "Maintain a 3-day streak", 0xFFFF6B35),
    STREAK_7("Week Warrior", "Maintain a 7-day streak", 0xFFFF4500),
    STREAK_14("Fortnight Force", "Maintain a 14-day streak", 0xFFDC143C),
    STREAK_30("Monthly Master", "Maintain a 30-day streak", 0xFF8B0000),
    STREAK_100("Centurion", "Maintain a 100-day streak", 0xFFB8860B),

    // Goal Completion Badges
    GOAL_1("Goal Getter", "Complete 1 goal", 0xFF4CAF50),
    GOAL_5("High Achiever", "Complete 5 goals", 0xFF2196F3),
    GOAL_10("Dream Chaser", "Complete 10 goals", 0xFF9C27B0),
    GOAL_25("Goal Master", "Complete 25 goals", 0xFFE91E63),
    GOAL_50("Legend", "Complete 50 goals", 0xFFFFD700),

    // Habit Badges
    HABIT_STARTER("Habit Starter", "Create your first habit", 0xFF00BCD4),
    HABIT_5("Habit Builder", "Track 5 habits", 0xFF009688),
    HABIT_PERFECT_WEEK("Perfect Week", "Complete all habits for a week", 0xFF4CAF50),
    HABIT_PERFECT_MONTH("Perfect Month", "Complete all habits for a month", 0xFFFFD700),

    // Journal Badges
    JOURNAL_FIRST("Reflection Starter", "Write your first journal entry", 0xFF795548),
    JOURNAL_10("Thoughtful Soul", "Write 10 journal entries", 0xFF607D8B),
    JOURNAL_30("Deep Thinker", "Write 30 journal entries", 0xFF3F51B5),

    // Category Badges
    BALANCED("Balanced Life", "Have goals in all 8 categories", 0xFF9E9E9E),
    HEALTH_FOCUS("Health Champion", "Complete 5 health goals", 0xFFE91E63),
    CAREER_FOCUS("Career Climber", "Complete 5 career goals", 0xFF3F51B5),

    // Onboarding Badge
    GETTING_STARTED("Explorer", "Complete all Getting Started objectives", 0xFFFF6B35),

    // Special Badges
    EARLY_BIRD("Early Bird", "Check in before 7 AM", 0xFFFFC107),
    NIGHT_OWL("Night Owl", "Check in after 10 PM", 0xFF673AB7),
    COMEBACK("Comeback King", "Return after a 7+ day break", 0xFF00BCD4),
    PERFECTIONIST("Perfectionist", "Complete a goal at 100% progress", 0xFF4CAF50),

    // Learning Badges, one per Learn zone (a path is only cleared once every lesson in it is read)
    LEARN_HABITS("Habit Scholar", "Clear the Building habits that stick path", 0xFF7A5AF8),
    LEARN_MOTIVATION("Fire Keeper", "Clear the Staying motivated path", 0xFFFF6B35),
    LEARN_MIND("Clear Mind", "Clear the Focus and the mind path", 0xFF26A69A),
    LEARN_REST("Well Rested", "Clear the Rest and recovery path", 0xFF5C6BC0),

    // Decision Badges. Deliberately not volume-based, unlike the journal tier above: these reward
    // answering for a call and thinking soundly, never logging more of them.
    DECISION_FIRST("First Call", "Log your first decision", 0xFF4A6FFF),
    DECISION_REVIEW_FIRST("Answered For It", "Come back and review a decision", 0xFF3B5BE5),
    DECISION_REVIEW_10("Hindsight", "Review 10 decisions", 0xFF2C42B0),
    DECISION_SOUND_10("Sound Judgment", "Reach 10 decisions graded as sound process", 0xFF7A5AF8),
    DECISION_CALIBRATED("Well Calibrated", "Land within 10 points of your own confidence across 10 reviews", 0xFF26A69A),

    // Focus Badges
    FOCUS_FIRST("First Focus", "Complete your first focus session", 0xFFFF6B35),
    FOCUS_HOUR("Hour Power", "Complete a 60-minute focus session", 0xFFE65100),
    FOCUS_10("Focus Pro", "Complete 10 focus sessions", 0xFFFF9800),
    FOCUS_50("Deep Worker", "Complete 50 focus sessions", 0xFFEF6C00)
}
