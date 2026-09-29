package az.tribe.lifeplanner.domain.service

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.math.roundToInt

enum class PaceKind { ON_TRACK, AHEAD, BEHIND, CATCHING_UP, PAUSED, DONE, LAST_DAY, PAST }

/**
 * How a plan is doing against an even pace from its start to its date. [expected] is where an even
 * pace would be today (0 to 1), the marker on the progress bar. [behindDays] is how long the
 * oldest missed step has waited.
 */
data class Pace(val kind: PaceKind, val label: String, val expected: Float, val behindDays: Int = 0) {
    val good: Boolean get() = kind == PaceKind.ON_TRACK || kind == PaceKind.AHEAD || kind == PaceKind.DONE
}

/** What a behind plan is offered: one more week (or a few), keep the date with steps closer, or leave it. */
data class CatchUp(val behindDays: Int, val pushWeeks: Int, val pushTo: LocalDate, val canKeep: Boolean)

enum class CatchUpChoice { PUSH, KEEP, LEAVE }

/**
 * Dates for a plan's steps, and everything that moves them later: the pace, catching up, a new
 * date and a pause. All of it is dates in, dates out, so the plan's page, Today and the tests share
 * one set of rules.
 */
object PlanScheduler {

    /** A step waits this many days before the plan counts as behind; carry-over covers the rest. */
    const val BEHIND_AFTER_DAYS = 5

    /** How long "leave it as it is" keeps the catch-up from asking again. */
    const val ASK_AGAIN_DAYS = 7

    /**
     * Dates for [steps] between [start] and [target]. A step at 0 lands in the first days, one at 1
     * on the target, the rest in between, moved to [weekday] where that is close. Done steps are
     * dated [start]. Dates never go backwards along the list.
     */
    fun date(steps: List<StepDraft>, start: LocalDate, target: LocalDate, weekday: DayOfWeek? = null): List<LocalDate> {
        val span = days(start, target).coerceAtLeast(1)
        val first = start.plus(DatePeriod(days = minOf(2, span)))
        var prev = start
        return steps.map { s ->
            if (s.done) return@map start
            val d = when {
                s.at <= 0.0 -> first
                s.at >= 1.0 -> target
                else -> snap(start.plus(DatePeriod(days = (s.at * span).roundToInt())), weekday, first, target)
            }
            maxOf(d, prev).also { prev = it }
        }
    }

    /** One step a week on [weekday], from the first one after the next two days. For plans with no date. */
    fun weekly(count: Int, start: LocalDate, weekday: DayOfWeek = DayOfWeek.SATURDAY): List<LocalDate> {
        if (count <= 0) return emptyList()
        var d = start.plus(DatePeriod(days = 2))
        while (d.dayOfWeek != weekday) d = d.plus(DatePeriod(days = 1))
        return (0 until count).map { d.plus(DatePeriod(days = 7 * it)) }
    }

    private fun snap(d: LocalDate, weekday: DayOfWeek?, after: LocalDate, before: LocalDate): LocalDate {
        if (weekday == null) return d
        val forward = (weekday.ordinal - d.dayOfWeek.ordinal + 7) % 7
        if (forward == 0) return d
        val back = 7 - forward
        val candidates = listOf(d.plus(DatePeriod(days = -back)), d.plus(DatePeriod(days = forward))).sortedBy { if (it < d) back else forward }
        return candidates.firstOrNull { it > after && it < before } ?: d
    }

    // ── Pace ─────────────────────────────────────────────────────────────────

    /**
     * [fraction] is the progress, [overdueSince] the date of the oldest step not done in time.
     * Behind only counts missed steps, never a number that has not moved yet, so a new plan with
     * nothing done is on track, not behind.
     */
    fun pace(
        start: LocalDate,
        target: LocalDate,
        today: LocalDate,
        fraction: Float,
        overdueSince: LocalDate?,
        paused: Boolean = false,
        done: Boolean = false,
        keptOn: LocalDate? = null,
        lastDayLabel: String = "Last day",
    ): Pace {
        val expected = (days(start, today).toFloat() / days(start, target).coerceAtLeast(1)).coerceIn(0f, 1f)
        val behind = overdueSince?.let { days(it, today) }?.coerceAtLeast(0) ?: 0
        return when {
            done -> Pace(PaceKind.DONE, "Done", 1f)
            paused -> Pace(PaceKind.PAUSED, "Paused", expected)
            today > target -> Pace(PaceKind.PAST, "Past its date", 1f, maxOf(behind, days(target, today)))
            behind >= BEHIND_AFTER_DAYS && keptOn != null && days(keptOn, today) <= 14 -> Pace(PaceKind.CATCHING_UP, "Catching up", expected, behind)
            behind >= BEHIND_AFTER_DAYS -> Pace(PaceKind.BEHIND, behindLabel(behind), expected, behind)
            today == target -> Pace(PaceKind.LAST_DAY, lastDayLabel, expected)
            fraction > 0f && fraction >= expected + 0.2f -> Pace(PaceKind.AHEAD, "Ahead", expected)
            else -> Pace(PaceKind.ON_TRACK, "On track", expected)
        }
    }

    fun behindLabel(days: Int): String = if (days <= 10) "A week behind" else "${(days + 3) / 7} weeks behind"

    /** "about a week", "about 3 weeks": for the catch-up card and the coach line. */
    fun behindWords(days: Int): String = if (days <= 10) "about a week" else "about ${(days + 3) / 7} weeks"

    // ── Catching up ──────────────────────────────────────────────────────────

    /** What to offer a plan that slipped, or null when it has not, or when "leave it" was said this week. */
    fun catchUp(pace: Pace, target: LocalDate, today: LocalDate, asked: LocalDate?): CatchUp? {
        if (pace.kind != PaceKind.BEHIND && pace.kind != PaceKind.PAST) return null
        if (asked != null && days(asked, today) < ASK_AGAIN_DAYS) return null
        val weeks = ((pace.behindDays + 3) / 7).coerceAtLeast(1)
        var to = target.plus(DatePeriod(days = 7 * weeks))
        while (to < today.plus(DatePeriod(days = 7))) to = to.plus(DatePeriod(days = 7))
        val pushWeeks = (days(target, to) + 6) / 7
        return CatchUp(pace.behindDays, pushWeeks, to, canKeep = days(today, target) >= 7)
    }

    /**
     * New dates for the [count] steps left, spread from tomorrow to [target] with the last one on
     * it. Uses the study planner's spreading, so steps lean toward the end rather than piling up
     * this week.
     */
    fun redate(count: Int, today: LocalDate, target: LocalDate): List<LocalDate> {
        if (count <= 0) return emptyList()
        val from = today.plus(DatePeriod(days = 1))
        if (target < from) return List(count) { target.coerceAtLeast(from) }
        return StudyPlanner.spreadBefore(target.plus(DatePeriod(days = 1)), from, count)
            .let { if (it.size < count) (it + List(count - it.size) { target }).sorted() else it }
    }

    /** Moves every date by [by] days, never before [notBefore]. For a pause and for coming back from one. */
    fun shift(dates: List<LocalDate?>, by: Int, notBefore: LocalDate): List<LocalDate?> =
        dates.map { d -> d?.plus(DatePeriod(days = by))?.coerceAtLeast(notBefore) }

    /** Days to move a plan's dates back by when it is resumed before a fixed pause ran out. */
    fun unusedPause(pausedUntil: LocalDate, today: LocalDate): Int = (days(today, pausedUntil) + 1).coerceAtLeast(0)

    // ── Words ────────────────────────────────────────────────────────────────

    /** "Tue 1 Dec". */
    fun dayLabel(d: LocalDate): String = "${FitnessWeek.shortDay(d.dayOfWeek)} ${d.day} ${TripPlanner.monthName(d.month).take(3)}"

    /** "9 weeks", "5 months", "a year". */
    fun spanLabel(start: LocalDate, target: LocalDate): String {
        val d = days(start, target)
        return when {
            d < 14 -> if (d == 1) "a day" else "$d days"
            d < 70 -> "${(d / 7.0).roundToInt()} weeks"
            d < 350 -> "${(d / 30.44).roundToInt()} months"
            d < 548 -> "a year"
            else -> "${(d / 365.25).roundToInt()} years"
        }
    }

    /** "a month left", "5 weeks left", "that is today". */
    fun leftLabel(target: LocalDate, today: LocalDate): String {
        val d = days(today, target)
        return when {
            d < 0 -> if (d == -1) "a day past it" else "${-d} days past it"
            d == 0 -> "that is today"
            d == 1 -> "that is tomorrow"
            d < 14 -> "$d days left"
            d in 28..35 -> "a month left"
            d < 60 -> "${(d / 7.0).roundToInt()} weeks left"
            else -> "${(d / 30.44).roundToInt()} months left"
        }
    }

    fun days(a: LocalDate, b: LocalDate): Int = (b.toEpochDays() - a.toEpochDays()).toInt()
}
