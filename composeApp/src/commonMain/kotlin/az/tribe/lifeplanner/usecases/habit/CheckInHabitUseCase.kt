package az.tribe.lifeplanner.usecases.habit

import az.tribe.lifeplanner.domain.model.HabitCheckIn
import az.tribe.lifeplanner.domain.repository.HabitRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class CheckInHabitUseCase(
    private val repository: HabitRepository
) {
    suspend operator fun invoke(
        habitId: String,
        date: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
        notes: String = ""
    ): HabitCheckIn {
        return repository.checkIn(habitId, date, notes)
    }
}
