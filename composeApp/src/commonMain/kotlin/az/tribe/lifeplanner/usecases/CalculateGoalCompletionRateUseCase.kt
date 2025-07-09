package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.model.Milestone

class CalculateGoalCompletionRateUseCase {
    operator fun invoke(milestones: List<Milestone>): Float {
        if (milestones.isEmpty()) return 0f
        val completedCount = milestones.count { it.isCompleted }
        return (completedCount.toFloat() / milestones.size) * 100f
    }
}