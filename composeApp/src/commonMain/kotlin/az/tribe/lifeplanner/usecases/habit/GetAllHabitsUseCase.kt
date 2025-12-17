package az.tribe.lifeplanner.usecases.habit

import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.repository.HabitRepository

class GetAllHabitsUseCase(
    private val repository: HabitRepository
) {
    suspend operator fun invoke(): List<Habit> {
        return repository.getAllHabits()
    }
}
