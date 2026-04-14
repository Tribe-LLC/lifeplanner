package az.tribe.lifeplanner.usecases.habit

import az.tribe.lifeplanner.domain.repository.HabitRepository
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class UncheckHabitUseCase(
    private val repository: HabitRepository
) {
    suspend operator fun invoke(
        habitId: String,
        date: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    ): Boolean {
        val checkIn = repository.getCheckInByHabitAndDate(habitId, date)
        return if (checkIn != null) {
            repository.deleteCheckIn(checkIn.id)
            true
        } else {
            false
        }
    }
}
