package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.repository.GoalRepository

class DeleteMilestoneUseCase(
    private val goalRepository: GoalRepository
) {
    suspend operator fun invoke(milestoneId: String): Result<Unit> {
        return try {
            goalRepository.deleteMilestone(milestoneId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}