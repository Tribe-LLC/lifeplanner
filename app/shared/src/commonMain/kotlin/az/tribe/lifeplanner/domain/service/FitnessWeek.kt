package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

enum class RingState { DONE, REST, MISSED, TODAY }

data class DayRing(val date: LocalDate, val label: String, val state: RingState)

/** The pure parts of the Fitness page, kept apart so they can be tested without a device. */
object FitnessWeek {

    fun isWorkout(l: LifeLog) = l.kind == LogKind.WORKOUT

    /** The last seven days ending today, oldest first, one ring each. */
    fun rings(logs: List<LifeLog>, today: LocalDate): List<DayRing> = (6 downTo 0).map { back ->
        val d = today.minus(DatePeriod(days = back))
        val day = logs.filter { isWorkout(it) && it.date == d }
        val state = when {
            day.any { it.status == LogStatus.DONE } -> RingState.DONE
            d == today -> RingState.TODAY
            day.any { it.status == LogStatus.PLANNED } -> RingState.MISSED
            else -> RingState.REST
        }
        DayRing(d, dayLetter(d.dayOfWeek), state)
    }

    fun doneInLastWeek(logs: List<LifeLog>, today: LocalDate): Int {
        val from = today.minus(DatePeriod(days = 6))
        return logs.count { isWorkout(it) && it.status == LogStatus.DONE && it.date >= from && it.date <= today }
    }

    /**
     * Where a workout goes when the coach lightens today: the first of the next [within] days with
     * no workout planned or done, else tomorrow.
     */
    fun nextFreeDay(logs: List<LifeLog>, today: LocalDate, within: Int = 6): LocalDate {
        val busy = logs.filter { isWorkout(it) && it.status != LogStatus.SKIPPED }.map { it.date }.toSet()
        return (1..within).map { today.plus(DatePeriod(days = it)) }.firstOrNull { it !in busy }
            ?: today.plus(DatePeriod(days = 1))
    }

    fun dayLetter(d: DayOfWeek) = when (d) {
        DayOfWeek.MONDAY -> "M"; DayOfWeek.TUESDAY -> "T"; DayOfWeek.WEDNESDAY -> "W"
        DayOfWeek.THURSDAY -> "T"; DayOfWeek.FRIDAY -> "F"; DayOfWeek.SATURDAY -> "S"; else -> "S"
    }

    fun dayName(d: DayOfWeek) = d.name.lowercase().replaceFirstChar { it.uppercase() }

    fun shortDay(d: DayOfWeek) = dayName(d).take(3)
}
