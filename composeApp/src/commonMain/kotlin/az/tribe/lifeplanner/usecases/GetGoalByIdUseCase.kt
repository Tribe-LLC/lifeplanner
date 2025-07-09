package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.repository.GoalRepository

// Utility Use Cases
class GetGoalByIdUseCase(
    private val goalRepository: GoalRepository
) {
    suspend operator fun invoke(goalId: String): Goal? {
        return goalRepository.getAllGoals().find { it.id == goalId }
    }
}