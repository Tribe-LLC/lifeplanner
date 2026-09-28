package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.enum.HealthMetricType
import az.tribe.lifeplanner.domain.model.Habit
import kotlinx.datetime.LocalDate

/**
 * Days that do not count against a streak. A day inside a trip with travel mode on is neither kept
 * nor missed: the streak walks over it. Kept separate from trips so habits do not depend on them.
 */
fun interface StreakPauses {
    suspend fun pausedDays(): Set<LocalDate>

    companion object {
        val None = StreakPauses { emptySet() }
    }
}

object TravelMode {
    private val keepWords = listOf("walk", "water", "drink", "steps", "hydrat")

    /** Habits that still make sense away from home and stay on Today during a trip. */
    fun keeps(habit: Habit): Boolean =
        habit.healthMetricType == HealthMetricType.STEPS || keepWords.any { it in habit.title.lowercase() }

    /**
     * Streak length counting back from [today]: done days count, paused days are skipped over,
     * and the first other day ends it.
     */
    fun streak(done: Set<LocalDate>, paused: Set<LocalDate>, today: LocalDate): Int {
        var streak = 0
        var d = today
        var guard = 0
        while (guard++ < 3660) {
            when {
                d in done -> streak++
                d in paused -> {}
                else -> break
            }
            d = LocalDate.fromEpochDays(d.toEpochDays() - 1)
        }
        return streak
    }
}
