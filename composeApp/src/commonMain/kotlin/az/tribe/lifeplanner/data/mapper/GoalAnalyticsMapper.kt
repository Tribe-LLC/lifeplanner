package az.tribe.lifeplanner.data.mapper

import az.tribe.lifeplanner.data.model.GoalAnalyticsData
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.model.GoalAnalytics
import az.tribe.lifeplanner.domain.enum.GoalTimeline

fun GoalAnalyticsData.toDomainAnalytics(upcomingDeadlines: Int = 0): GoalAnalytics {
    return GoalAnalytics(
        totalGoals = totalGoals.toInt(),
        activeGoals = activeGoals.toInt(),
        completedGoals = completedGoals.toInt(),
        completionRate = (completionRate / 100.0).toFloat(),
        upcomingDeadlines = upcomingDeadlines,
        goalsByCategory = goalsByCategory.mapKeys { GoalCategory.valueOf(it.key) }
            .mapValues { it.value.toInt() },
        goalsByTimeline = goalsByTimeline.mapKeys { GoalTimeline.valueOf(it.key) }
            .mapValues { it.value.toInt() },
        averageProgressPerCategory = averageProgressByCategory.mapKeys { GoalCategory.valueOf(it.key) }
            .mapValues { it.value.toFloat() },
        goalsCompletedThisWeek = completedGoals.toInt(), // Placeholder
        goalsCompletedThisMonth = completedGoals.toInt() // Placeholder
    )
}