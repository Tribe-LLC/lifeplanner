package az.tribe.lifeplanner.usecases.habit

import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.repository.HabitRepository

class GetHabitsByGoalUseCase(
    private val repository: HabitRepository
) {
    suspend operator fun invoke(goalId: String): List<Habit> {
        return repository.getHabitsByGoalId(goalId)
    }
}
