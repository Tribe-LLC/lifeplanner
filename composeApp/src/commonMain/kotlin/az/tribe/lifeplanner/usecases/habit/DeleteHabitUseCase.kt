package az.tribe.lifeplanner.usecases.habit

import az.tribe.lifeplanner.domain.repository.HabitRepository

class DeleteHabitUseCase(
    private val repository: HabitRepository
) {
    suspend operator fun invoke(id: String) {
        repository.deleteHabit(id)
    }
}
