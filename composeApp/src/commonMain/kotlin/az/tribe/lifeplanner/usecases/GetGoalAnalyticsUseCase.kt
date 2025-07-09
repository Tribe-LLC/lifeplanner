package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.model.GoalAnalytics
import az.tribe.lifeplanner.domain.repository.GoalRepository

class GetGoalAnalyticsUseCase(
    private val repository: GoalRepository
) {
    suspend operator fun invoke(): GoalAnalytics {
        return repository.getAnalytics()
    }
}