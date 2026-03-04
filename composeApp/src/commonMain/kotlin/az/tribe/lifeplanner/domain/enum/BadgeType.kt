package az.tribe.lifeplanner.domain.enum

import kotlinx.serialization.Serializable

/**
 * Types of badges users can earn
 */
@Serializable
enum class BadgeType(
    val displayName: String,
    val description: String,
    val icon: String,
    val color: Long
) {
    // Streak Badges
    FIRST_STEP("First Step", "Complete your first goal", "star", 0xFFFFD700),
    STREAK_3("On Fire", "Maintain a 3-day streak", "fire", 0xFFFF6B35),
    STREAK_7("Week Warrior", "Maintain a 7-day streak", "fire", 0xFFFF4500),
    STREAK_14("Fortnight Force", "Maintain a 14-day streak", "fire", 0xFFDC143C),
    STREAK_30("Monthly Master", "Maintain a 30-day streak", "fire", 0xFF8B0000),
    STREAK_100("Centurion", "Maintain a 100-day streak", "fire", 0xFFB8860B),

    // Goal Completion Badges
    GOAL_1("Goal Getter", "Complete 1 goal", "target", 0xFF4CAF50),
    GOAL_5("High Achiever", "Complete 5 goals", "target", 0xFF2196F3),
    GOAL_10("Dream Chaser", "Complete 10 goals", "target", 0xFF9C27B0),
    GOAL_25("Goal Master", "Complete 25 goals", "target", 0xFFE91E63),
    GOAL_50("Legend", "Complete 50 goals", "trophy", 0xFFFFD700),

    // Habit Badges
    HABIT_STARTER("Habit Starter", "Create your first habit", "repeat", 0xFF00BCD4),
    HABIT_5("Habit Builder", "Track 5 habits", "repeat", 0xFF009688),
    HABIT_PERFECT_WEEK("Perfect Week", "Complete all habits for a week", "calendar", 0xFF4CAF50),
    HABIT_PERFECT_MONTH("Perfect Month", "Complete all habits for a month", "calendar", 0xFFFFD700),

    // Journal Badges
    JOURNAL_FIRST("Reflection Starter", "Write your first journal entry", "book", 0xFF795548),
    JOURNAL_10("Thoughtful Soul", "Write 10 journal entries", "book", 0xFF607D8B),
    JOURNAL_30("Deep Thinker", "Write 30 journal entries", "book", 0xFF3F51B5),

    // Category Badges
    BALANCED("Balanced Life", "Have goals in all 8 categories", "balance", 0xFF9E9E9E),
    HEALTH_FOCUS("Health Champion", "Complete 5 health goals", "heart", 0xFFE91E63),
    CAREER_FOCUS("Career Climber", "Complete 5 career goals", "briefcase", 0xFF3F51B5),

    // Special Badges
    EARLY_BIRD("Early Bird", "Check in before 7 AM", "sun", 0xFFFFC107),
    NIGHT_OWL("Night Owl", "Check in after 10 PM", "moon", 0xFF673AB7),
    COMEBACK("Comeback King", "Return after a 7+ day break", "refresh", 0xFF00BCD4),
    PERFECTIONIST("Perfectionist", "Complete a goal at 100% progress", "check-circle", 0xFF4CAF50),

    // Focus Badges
    FOCUS_FIRST("First Focus", "Complete your first focus session", "timer", 0xFFFF6B35),
    FOCUS_HOUR("Hour Power", "Complete a 60-minute focus session", "timer", 0xFFE65100),
    FOCUS_10("Focus Pro", "Complete 10 focus sessions", "timer", 0xFFFF9800),
    FOCUS_50("Deep Worker", "Complete 50 focus sessions", "timer", 0xFFEF6C00)
}
