package az.tribe.lifeplanner.domain.service

import kotlin.math.roundToInt

/**
 * How far behind (or not) the user's sleep is over the last nights, against their goal. Only the
 * nights Health actually reported count, so a missing night never reads as zero sleep, and a long
 * night pays back a short one.
 */
object SleepDebt {
    /** Fewer nights than this and there is nothing fair to say. */
    const val MIN_NIGHTS = 3
    /** This close to the goal over the whole stretch counts as on target. */
    const val ON_TARGET_MIN = 20
    /** Used when the user has not set a goal: the low end of what most adults need. */
    const val DEFAULT_GOAL = 7.0

    /** [behindMin] is positive when behind, zero or negative when on target or ahead. */
    data class Result(val behindMin: Int, val nights: Int) {
        val onTarget: Boolean get() = behindMin <= ON_TARGET_MIN
        val ahead: Boolean get() = behindMin < -ON_TARGET_MIN
    }

    fun of(nightsHours: List<Double>, goalHours: Double): Result? {
        val nights = nightsHours.filter { it > 0.0 }
        if (nights.size < MIN_NIGHTS || goalHours <= 0.0) return null
        val behind = nights.sumOf { goalHours - it } * 60
        return Result(roundTo5(behind), nights.size)
    }

    /** "3h 20m behind over 7 nights", "On target over 5 nights", "Ahead of your goal over 7 nights". */
    fun line(r: Result): String = when {
        r.ahead -> "Ahead of your goal over ${r.nights} nights"
        r.onTarget -> "On target over ${r.nights} nights"
        else -> "${duration(r.behindMin)} behind over ${r.nights} nights"
    }

    /** "3h 20m", "2h", "45m". */
    fun duration(minutes: Int): String {
        val m = kotlin.math.abs(minutes)
        val h = m / 60
        val rest = m % 60
        return when {
            h == 0 -> "${rest}m"
            rest == 0 -> "${h}h"
            else -> "${h}h ${rest.toString().padStart(2, '0')}m"
        }
    }

    private fun roundTo5(min: Double): Int = (min / 5.0).roundToInt() * 5
}
