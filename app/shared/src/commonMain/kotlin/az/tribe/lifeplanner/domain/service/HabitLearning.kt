package az.tribe.lifeplanner.domain.service

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * What the app learns from the user's own ticks, with no setup: the time of day a habit really
 * happens, the weekdays it really happens on, and when a habit has quietly stopped happening.
 * Everything here is pure so it can be tested against made-up histories.
 */
object HabitLearning {
    /** Ticks needed before a habit gets a usual time. */
    const val MIN_TICKS = 5
    /** Scheduled misses in a row that put a habit up for review. Skips and breaks do not count. */
    const val MISSES = 3
    /** Skips among the last [SKIP_WINDOW] scheduled days that also put it up for review. */
    const val SKIPS = 5
    const val SKIP_WINDOW = 7
    /** A reviewed habit is left alone this long. */
    const val COOLDOWN_DAYS = 14
    /** New habits get this long before they can slip. */
    const val GRACE_DAYS = 7
    /** Log category for a review decision; externalId is the habit. */
    const val REVIEW = "habit_review"

    // ── Time of day ──────────────────────────────────────────────────────────

    /**
     * The minute of the day a habit usually gets ticked: the median of its recent same-day ticks.
     * Ticks written on another day (fixing a past day) say nothing about the time, so they are left out.
     */
    fun usualMinute(ticks: List<Pair<LocalDate, LocalDateTime>>): Int? {
        val minutes = ticks.filter { (day, at) -> at.date == day }
            .sortedByDescending { it.second }
            .take(30)
            .map { it.second.hour * 60 + it.second.minute }
            .sorted()
        if (minutes.size < MIN_TICKS) return null
        val mid = minutes.size / 2
        return if (minutes.size % 2 == 1) minutes[mid] else (minutes[mid - 1] + minutes[mid]) / 2
    }

    fun slotOf(minute: Int): HabitSchedule.Slot = when {
        minute < 12 * 60 -> HabitSchedule.Slot.MORNING
        minute < 17 * 60 -> HabitSchedule.Slot.AFTERNOON
        else -> HabitSchedule.Slot.EVENING
    }

    fun clock(minute: Int): String = "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"

    /** Rounded to the quarter hour, for "usually around 21:30". */
    fun roughly(minute: Int): String = clock(((minute + 7) / 15 * 15) % (24 * 60))

    /**
     * True when the reminder is set far from when the habit really happens (3 hours or more),
     * so the coach can offer to move it.
     */
    fun reminderIsOff(reminderMinute: Int?, usual: Int?): Boolean =
        reminderMinute != null && usual != null && kotlin.math.abs(reminderMinute - usual) >= 180

    // ── Days of the week ─────────────────────────────────────────────────────

    /**
     * The weekdays a habit actually gets done on, when that is clearly fewer than it is set for.
     * Needs 4 weeks of history; a weekday counts as "yes" at 60% or more and "no" at 20% or less,
     * and every scheduled weekday must be one or the other.
     */
    fun learnedDays(s: Schedule, done: Set<LocalDate>, neutral: Set<LocalDate>, today: LocalDate, since: LocalDate): Set<DayOfWeek>? {
        val schedule = HabitSchedule.normal(s)
        if (schedule is Schedule.PerWeek) return null
        val from = maxOf(since, today.minus(DatePeriod(days = 42)))
        if (from > today.minus(DatePeriod(days = 28))) return null
        val seen = mutableMapOf<DayOfWeek, Int>()
        val kept = mutableMapOf<DayOfWeek, Int>()
        var d = from
        while (d < today) {
            if (HabitSchedule.isScheduled(schedule, d) && d !in neutral) {
                seen[d.dayOfWeek] = (seen[d.dayOfWeek] ?: 0) + 1
                if (d in done) kept[d.dayOfWeek] = (kept[d.dayOfWeek] ?: 0) + 1
            }
            d = d.plus(DatePeriod(days = 1))
        }
        if (kept.values.sum() < 4) return null
        val yes = mutableSetOf<DayOfWeek>()
        for ((day, n) in seen) {
            if (n < 3) return null
            val rate = (kept[day] ?: 0).toFloat() / n
            when {
                rate >= 0.6f -> yes += day
                rate > 0.2f -> return null
            }
        }
        return yes.takeIf { it.isNotEmpty() && it.size < seen.size }
    }

    // ── Slipping ─────────────────────────────────────────────────────────────

    data class Slip(
        /** Scheduled misses in a row (days, or weeks for times-a-week habits). */
        val missedInRow: Int,
        val skippedRecently: Int,
        val keptLast30: Int,
        val dueLast30: Int,
        val inWeeks: Boolean,
        /** Never ticked since it was added, so "missed N in a row" would just count its age. */
        val neverDone: Boolean = false,
    )

    /**
     * Whether a habit has quietly stopped: [MISSES] scheduled misses in a row (skipped and break days are
     * neutral and neither break nor extend the run), or [SKIPS] skips among the last [SKIP_WINDOW]
     * scheduled days. Today never counts, it is not over. New and recently reviewed habits are left alone.
     */
    fun slip(
        s: Schedule,
        done: Set<LocalDate>,
        skipped: Set<LocalDate>,
        neutral: Set<LocalDate>,
        today: LocalDate,
        since: LocalDate,
        lastReviewed: LocalDate?,
    ): Slip? {
        if (since > today.minus(DatePeriod(days = GRACE_DAYS))) return null
        // Done today means it is back on track, whatever came before.
        if (today in done) return null
        if (lastReviewed != null && lastReviewed > today.minus(DatePeriod(days = COOLDOWN_DAYS))) return null
        val schedule = HabitSchedule.normal(s)
        val (kept30, due30) = last30(schedule, done, neutral + skipped, today, since)
        if (schedule is Schedule.PerWeek) {
            var run = 0
            var w = HabitSchedule.weekStart(today).minus(DatePeriod(days = 7))
            repeat(8) {
                if (w.plus(DatePeriod(days = 6)) < since) return@repeat
                when (HabitSchedule.weekResult(w, schedule.times, done, neutral + skipped, today)) {
                    true -> return if (run >= MISSES) Slip(run, 0, kept30, due30, true) else null
                    false -> run++
                    null -> Unit
                }
                w = w.minus(DatePeriod(days = 7))
            }
            return if (run >= MISSES) Slip(run, 0, kept30, due30, true) else null
        }
        var run = 0
        var runOpen = true
        var scheduledSeen = 0
        var skips = 0
        var d = today.minus(DatePeriod(days = 1))
        val stop = maxOf(since, today.minus(DatePeriod(days = 90)))
        while (d >= stop && (runOpen || scheduledSeen < SKIP_WINDOW)) {
            if (HabitSchedule.isScheduled(schedule, d) && d !in neutral) {
                val isSkip = d in skipped
                if (scheduledSeen < SKIP_WINDOW) { scheduledSeen++; if (isSkip) skips++ }
                if (runOpen && !isSkip) {
                    if (d in done) runOpen = false else run++
                }
            }
            d = d.minus(DatePeriod(days = 1))
        }
        return if (run >= MISSES || skips >= SKIPS) Slip(run, skips, kept30, due30, false, neverDone = done.none { it >= since }) else null
    }

    private fun last30(s: Schedule, done: Set<LocalDate>, neutral: Set<LocalDate>, today: LocalDate, since: LocalDate): Pair<Int, Int> {
        var due = 0
        var kept = 0
        var d = maxOf(since, today.minus(DatePeriod(days = 30)))
        while (d < today) {
            if (d in done) { kept++; due++ } else if (s !is Schedule.PerWeek && HabitSchedule.isScheduled(s, d) && d !in neutral) due++
            d = d.plus(DatePeriod(days = 1))
        }
        return kept to due
    }

    /** The line on a review card, plain and without blame. */
    fun slipText(slip: Slip): String = when {
        slip.neverDone -> "Not started yet"
        slip.inWeeks -> "Missed ${slip.missedInRow} weeks in a row"
        slip.skippedRecently >= SKIPS && slip.missedInRow < MISSES -> "Skipped ${slip.skippedRecently} of the last $SKIP_WINDOW"
        slip.missedInRow > 14 -> "Missed for over 2 weeks"
        else -> "Missed ${slip.missedInRow} in a row"
    }

    fun historyText(slip: Slip): String = when {
        slip.dueLast30 == 0 -> "Nothing due in the last 30 days."
        slip.keptLast30 == 0 -> "Not kept in the last 30 days."
        else -> "Kept ${slip.keptLast30} of the last ${slip.dueLast30} days it was due."
    }
}
