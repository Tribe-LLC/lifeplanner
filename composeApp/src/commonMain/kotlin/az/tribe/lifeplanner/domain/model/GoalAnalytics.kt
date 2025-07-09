package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalTimeline

data class GoalAnalytics(
    val totalGoals: Int,
    val activeGoals: Int,
    val completedGoals: Int,
    val completionRate: Float, // 0.0 to 1.0
    val upcomingDeadlines: Int,
    val goalsByCategory: Map<GoalCategory, Int>,
    val goalsByTimeline: Map<GoalTimeline, Int>,
    val averageProgressPerCategory: Map<GoalCategory, Float>,
    val goalsCompletedThisWeek: Int,
    val goalsCompletedThisMonth: Int
)
