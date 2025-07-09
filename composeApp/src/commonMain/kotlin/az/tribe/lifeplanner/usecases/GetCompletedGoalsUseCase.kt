package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.repository.GoalRepository

class GetCompletedGoalsUseCase(
    private val goalRepository: GoalRepository
) {
    suspend operator fun invoke(): List<Goal> {
        return goalRepository.getCompletedGoals()
    }
}