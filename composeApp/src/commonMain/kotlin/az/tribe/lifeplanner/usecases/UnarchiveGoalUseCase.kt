package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.repository.GoalRepository

class UnarchiveGoalUseCase(
    private val goalRepository: GoalRepository
) {
    suspend operator fun invoke(goalId: String): Result<Unit> {
        return try {
            goalRepository.unarchiveGoal(goalId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}