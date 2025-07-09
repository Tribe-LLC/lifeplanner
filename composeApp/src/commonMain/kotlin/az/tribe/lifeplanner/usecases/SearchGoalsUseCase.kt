package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.model.Goal

class SearchGoalsUseCase(
    private val goalRepository: GoalRepository
) {
    suspend operator fun invoke(query: String): List<Goal> {
        return goalRepository.searchGoals(query)
    }
}

