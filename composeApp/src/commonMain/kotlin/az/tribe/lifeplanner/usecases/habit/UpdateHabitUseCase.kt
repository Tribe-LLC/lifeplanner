package az.tribe.lifeplanner.usecases.habit

import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.repository.HabitRepository

class UpdateHabitUseCase(
    private val repository: HabitRepository
) {
    suspend operator fun invoke(habit: Habit) {
        repository.updateHabit(habit)
    }
}
