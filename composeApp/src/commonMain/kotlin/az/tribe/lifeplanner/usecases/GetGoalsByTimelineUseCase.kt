package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.enum.GoalTimeline

class GetGoalsByTimelineUseCase(
    private val repository: GoalRepository
) {
    suspend operator fun invoke(timeline: GoalTimeline): List<Goal> {
        return repository.getGoalsByTimeline(timeline)
    }
}