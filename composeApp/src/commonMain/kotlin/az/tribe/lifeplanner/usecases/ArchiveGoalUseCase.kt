package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.repository.GoalRepository

class ArchiveGoalUseCase(
    private val goalRepository: GoalRepository
) {
    suspend operator fun invoke(goalId: String): Result<Unit> {
        return try {
            goalRepository.archiveGoal(goalId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}