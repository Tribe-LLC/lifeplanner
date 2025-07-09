package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.model.GoalStatistics
import az.tribe.lifeplanner.domain.repository.GoalRepository

// Enhanced Analytics Use Cases
class GetGoalStatisticsUseCase(
    private val goalRepository: GoalRepository
) {
    suspend operator fun invoke(): GoalStatistics {
        val allGoals = goalRepository.getAllGoals()
        val activeGoals = goalRepository.getActiveGoals()
        val completedGoals = goalRepository.getCompletedGoals()
        val upcomingDeadlines = goalRepository.getUpcomingDeadlines()

        return GoalStatistics(
            totalGoals = allGoals.size,
            activeGoals = activeGoals.size,
            completedGoals = completedGoals.size,
            upcomingDeadlines = upcomingDeadlines.size,
            completionRate = if (allGoals.isNotEmpty()) {
                completedGoals.size.toFloat() / allGoals.size
            } else 0f
        )
    }
}