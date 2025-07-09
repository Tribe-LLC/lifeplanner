package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.repository.GoalRepository

class UpdateGoalNotesUseCase(
    private val goalRepository: GoalRepository
) {
    suspend operator fun invoke(goalId: String, notes: String): Result<Unit> {
        return try {
            goalRepository.updateGoalNotes(goalId, notes)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}