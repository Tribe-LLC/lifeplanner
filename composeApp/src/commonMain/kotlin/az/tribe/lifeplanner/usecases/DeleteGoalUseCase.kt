package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.repository.GoalRepository

class DeleteGoalUseCase(
    private val repository: GoalRepository
) {
    suspend operator fun invoke(id: String) {
        repository.deleteGoalById(id)
    }
    suspend operator fun invoke() {
        repository.deleteAllGoals()
    }
}