package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.repository.GoalRepository

// Goal Status and Notes Use Cases
class UpdateGoalStatusUseCase(
    private val goalRepository: GoalRepository
) {
    suspend operator fun invoke(goalId: String, newStatus: GoalStatus): Result<Unit> {
        return try {
            val goal = goalRepository.getAllGoals().find { it.id == goalId }
            if (goal != null) {
                val updatedGoal = goal.copy(status = newStatus)
                goalRepository.updateGoal(updatedGoal)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Goal not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}