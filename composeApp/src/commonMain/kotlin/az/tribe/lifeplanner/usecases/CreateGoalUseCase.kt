package az.tribe.lifeplanner.usecases


import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.repository.GoalRepository

class CreateGoalUseCase(
    private val repository: GoalRepository
) {
    suspend operator fun invoke(goal: Goal) {
        repository.insertGoal(goal)
    }
    suspend operator fun invoke(goals: List<Goal>) {
        repository.insertGoals(goals)
    }

}