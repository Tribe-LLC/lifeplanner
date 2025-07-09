package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.repository.GoalRepository

class UpdateGoalUseCase(
    private val repository: GoalRepository
) {
    suspend operator fun invoke(goal: Goal) {
        repository.updateGoal(goal)
    }
}