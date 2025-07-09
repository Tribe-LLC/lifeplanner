package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.repository.GoalRepository

class UpdateGoalProgressUseCase(private val repository: GoalRepository) {
    suspend operator fun invoke(id: String, progress: Int) {
        repository.updateProgress(id, progress)
    }
}