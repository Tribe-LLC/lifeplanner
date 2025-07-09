package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.repository.GoalRepository

class GetGoalsByCategoryUseCase(
    private val repository: GoalRepository
) {
    suspend operator fun invoke(category: GoalCategory): List<Goal> {
        return repository.getAllGoals().filter { it.category == category }
    }
}