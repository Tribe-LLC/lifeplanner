package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.repository.GoalRepository

class FilterGoalsByStatusUseCase(
    private val goalRepository: GoalRepository
) {
    suspend operator fun invoke(status: GoalStatus): List<Goal> {
        return goalRepository.getAllGoals().filter { it.status == status }
    }
}