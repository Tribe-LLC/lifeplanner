package az.tribe.lifeplanner.domain.model

data class GoalStatistics(
    val totalGoals: Int,
    val activeGoals: Int,
    val completedGoals: Int,
    val upcomingDeadlines: Int,
    val completionRate: Float
)