package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.repository.GoalRepository

class GetAllGoalsUseCase(
    private val repository: GoalRepository
) {
    suspend operator fun invoke(): List<Goal> {
        return repository.getAllGoals()
    }
}