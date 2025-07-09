package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.repository.GoalRepository

class GetUpcomingDeadlinesUseCase(
    private val goalRepository: GoalRepository
) {
    suspend operator fun invoke(days: Int = 7): List<Goal> {
        return goalRepository.getUpcomingDeadlines(days)
    }
}