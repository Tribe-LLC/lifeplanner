package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.repository.GoalRepository

class UpdateMilestoneUseCase(
    private val goalRepository: GoalRepository
) {
    suspend operator fun invoke(milestone: Milestone): Result<Unit> {
        return try {
            goalRepository.updateMilestone(milestone)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}