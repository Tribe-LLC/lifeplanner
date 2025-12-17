package az.tribe.lifeplanner.domain.enum

import kotlinx.serialization.Serializable

/**
 * Types of challenges users can participate in
 */
@Serializable
enum class ChallengeType(
    val displayName: String,
    val description: String,
    val durationDays: Int,
    val xpReward: Int
) {
    // Daily Challenges
    DAILY_CHECK_IN("Daily Check-In", "Check in with your goals today", 1, 10),
    DAILY_JOURNAL("Daily Reflection", "Write a journal entry today", 1, 15),
    DAILY_HABIT("Habit Hero", "Complete all your habits today", 1, 20),

    // Weekly Challenges
    WEEKLY_GOALS("Goal Crusher", "Make progress on 3 goals this week", 7, 50),
    WEEKLY_HABITS("Habit Master", "Complete habits 5 days this week", 7, 75),
    WEEKLY_JOURNAL("Thoughtful Week", "Write 3 journal entries this week", 7, 50),
    WEEKLY_MILESTONE("Milestone Hunter", "Complete 2 milestones this week", 7, 60),

    // Monthly Challenges
    MONTHLY_COMPLETION("Goal Finisher", "Complete 1 goal this month", 30, 200),
    MONTHLY_STREAK("Streak Legend", "Maintain a 30-day streak", 30, 300),
    MONTHLY_BALANCED("Life Balance", "Track all 8 life categories", 30, 250),

    // Special Challenges
    PERFECT_DAY("Perfect Day", "Complete all tasks in a single day", 1, 100),
    CATEGORY_FOCUS("Category Master", "Complete 3 goals in one category", 30, 150),
    EARLY_RISER("Morning Momentum", "Check in before 8 AM for 7 days", 7, 100)
}
