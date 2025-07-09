package az.tribe.lifeplanner.data.model

data class GoalAnalyticsData(
    val totalGoals: Long,
    val activeGoals: Long,
    val completedGoals: Long,
    val completionRate: Double,
    val goalsByCategory: Map<String, Long>,
    val goalsByTimeline: Map<String, Long>,
    val goalsByStatus: Map<String, Long>,
    val averageProgressByCategory: Map<String, Double>
)
