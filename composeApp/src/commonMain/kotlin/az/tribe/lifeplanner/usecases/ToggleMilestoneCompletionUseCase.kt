package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.repository.GoalRepository

class ToggleMilestoneCompletionUseCase(
    private val goalRepository: GoalRepository
) {
    suspend operator fun invoke(milestoneId: String, isCompleted: Boolean): Result<Unit> {
        return try {
            goalRepository.toggleMilestoneCompletion(milestoneId, isCompleted)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}