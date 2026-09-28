package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogStatus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** A stretch of days off: sick, travelling, resting. Stored as "from..to" in a budget row. */
data class FitnessBreak(val from: LocalDate, val to: LocalDate) {
    operator fun contains(d: LocalDate) = d in from..to

    fun overlaps(start: LocalDate, end: LocalDate) = from <= end && to >= start

    fun encode() = "$from..$to"

    companion object {
        fun decode(text: String?): FitnessBreak? {
            val p = text?.split("..")?.takeIf { it.size == 2 } ?: return null
            val from = runCatching { LocalDate.parse(p[0].trim()) }.getOrNull() ?: return null
            val to = runCatching { LocalDate.parse(p[1].trim()) }.getOrNull() ?: return null
            return if (to >= from) FitnessBreak(from, to) else null
        }
    }
}

/** Weeks in a row at the weekly goal, and where this week stands. */
data class FitnessStreakState(val weeks: Int, val thisWeek: Int, val target: Int, val onBreak: FitnessBreak?)

/**
 * Weeks in a row (Monday to Sunday) with at least the weekly goal of workouts. This week counts
 * once the goal is reached and never breaks the run while it is still going. A week that fell
 * short but had a break in it is frozen: it does not add to the run, and it does not end it.
 */
object FitnessStreak {

    fun weekStart(d: LocalDate): LocalDate = d.minus(DatePeriod(days = d.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal))

    fun of(logs: List<LifeLog>, target: Int, today: LocalDate, breaks: List<FitnessBreak>, maxWeeks: Int = 104): FitnessStreakState {
        val goal = target.coerceAtLeast(1)
        val doneDays = logs.filter { FitnessWeek.isWorkout(it) && it.status == LogStatus.DONE && it.date <= today }.map { it.date }
        fun countIn(start: LocalDate): Int {
            val end = start.plus(DatePeriod(days = 6))
            return doneDays.count { it in start..end }
        }
        val thisStart = weekStart(today)
        val thisWeek = countIn(thisStart)
        var weeks = if (thisWeek >= goal) 1 else 0
        var start = thisStart
        for (i in 1..maxWeeks) {
            start = start.minus(DatePeriod(days = 7))
            val end = start.plus(DatePeriod(days = 6))
            when {
                countIn(start) >= goal -> weeks++
                breaks.any { it.overlaps(start, end) } -> Unit
                else -> break
            }
        }
        return FitnessStreakState(weeks, thisWeek, goal, breaks.firstOrNull { today in it })
    }

    /** The words next to the rings. Kind in every case, never a count of what was missed. */
    fun words(s: FitnessStreakState): Pair<String, String> = when {
        s.onBreak != null -> "Paused" to "your streak waits"
        s.weeks > 0 -> "${s.weeks} ${if (s.weeks == 1) "week" else "weeks"}" to "in a row at ${s.target}+"
        else -> {
            val left = (s.target - s.thisWeek).coerceAtLeast(1)
            "$left more" to "this week starts a streak"
        }
    }
}
