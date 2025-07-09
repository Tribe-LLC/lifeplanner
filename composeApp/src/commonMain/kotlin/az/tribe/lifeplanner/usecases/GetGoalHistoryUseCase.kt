package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.repository.GoalHistoryRepository
import az.tribe.lifeplanner.domain.model.GoalChange

class GetGoalHistoryUseCase(private val repository: GoalHistoryRepository) {
    suspend operator fun invoke(goalId: String): List<GoalChange> {
        return repository.getHistoryForGoal(goalId)
    }
}