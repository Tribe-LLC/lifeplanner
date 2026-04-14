package az.tribe.lifeplanner.usecases.habit

import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.repository.HabitRepository
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class GetHabitsWithTodayStatusUseCase(
    private val repository: HabitRepository
) {
    suspend operator fun invoke(
        today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    ): List<Pair<Habit, Boolean>> {
        return repository.getHabitsWithTodayStatus(today)
    }
}
