package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.repository.GoalRepository

// Milestone Management Use Cases
class AddMilestoneUseCase(
    private val goalRepository: GoalRepository
) {
    suspend operator fun invoke(goalId: String, milestone: Milestone): Result<Unit> {
        return try {
            goalRepository.addMilestone(goalId, milestone)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}