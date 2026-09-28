package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.enum.HabitFrequency
import az.tribe.lifeplanner.domain.model.Budget
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.PlanArea
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** When a habit is meant to happen. */
sealed interface Schedule {
    data object Daily : Schedule
    data class Days(val days: Set<DayOfWeek>) : Schedule
    /** Any days, [times] of them each week (Monday to Sunday). */
    data class PerWeek(val times: Int) : Schedule
}

/** What a streak needs to know about one habit: its schedule and the days that count for nothing. */
data class HabitRules(val schedule: Schedule, val neutral: Set<LocalDate>)

/**
 * Looks up [HabitRules] for a habit, so the repository's stored streak follows the same schedule,
 * skips and trip days as the Habits page.
 */
fun interface HabitStreakRules {
    suspend fun rulesFor(habit: Habit): HabitRules

    companion object {
        val Daily = HabitStreakRules { HabitRules(Schedule.Daily, emptySet()) }
    }
}

/**
 * Schedules, streaks and scores for habits. The schedule rides on the synced budgets table as a
 * per-habit target (no new columns): [METRIC_DAYS] holds a day mask, [METRIC_WEEK] a weekly count.
 * The habit's own [HabitFrequency] stays set too, so the v3 screens still read sensibly.
 *
 * Streaks forgive: days off the schedule, skipped days and trip days are neutral, and today not
 * being done yet never breaks anything. The score (share of due days kept in the last 30) sits
 * next to the streak so one miss does not undo months of work.
 */
object HabitSchedule {
    const val METRIC_DAYS = "habit_days"
    const val METRIC_WEEK = "habit_week"

    /** Life log categories under Habits: a skipped day, and a note on a day. */
    const val SKIP = "habit_skip"
    const val NOTE = "habit_note"

    val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val WEEKENDS = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    fun of(habit: Habit, budgets: List<Budget>): Schedule {
        val mine = budgets.filter { it.area == PlanArea.HABITS && it.category == habit.id }
        mine.firstOrNull { it.metric == METRIC_WEEK }?.let { return Schedule.PerWeek(it.amount.toInt().coerceIn(1, 7)) }
        mine.firstOrNull { it.metric == METRIC_DAYS }?.let { b -> daysOf(b.amount.toInt()).takeIf { it.isNotEmpty() }?.let { return normal(Schedule.Days(it)) } }
        return when (habit.frequency) {
            HabitFrequency.DAILY, HabitFrequency.CUSTOM -> Schedule.Daily
            HabitFrequency.WEEKDAYS -> Schedule.Days(WEEKDAYS)
            HabitFrequency.WEEKENDS -> Schedule.Days(WEEKENDS)
            HabitFrequency.WEEKLY -> Schedule.PerWeek(1)
        }
    }

    /** Seven picked days is every day; seven a week is every day too. */
    fun normal(s: Schedule): Schedule = when {
        s is Schedule.Days && s.days.size == 7 -> Schedule.Daily
        s is Schedule.PerWeek && s.times >= 7 -> Schedule.Daily
        else -> s
    }

    /** The v3 frequency that best matches, so older screens show something true. */
    fun frequencyFor(s: Schedule): HabitFrequency = when (val n = normal(s)) {
        Schedule.Daily -> HabitFrequency.DAILY
        is Schedule.Days -> when (n.days) { WEEKDAYS -> HabitFrequency.WEEKDAYS; WEEKENDS -> HabitFrequency.WEEKENDS; else -> HabitFrequency.CUSTOM }
        is Schedule.PerWeek -> HabitFrequency.WEEKLY
    }

    fun mask(days: Set<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or (1 shl d.ordinal) }
    fun daysOf(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { mask and (1 shl it.ordinal) != 0 }.toSet()

    fun describe(s: Schedule): String = when (val n = normal(s)) {
        Schedule.Daily -> "Every day"
        is Schedule.Days -> when (n.days) {
            WEEKDAYS -> "Weekdays"
            WEEKENDS -> "Weekends"
            else -> n.days.sortedBy { it.ordinal }.joinToString(", ") { FitnessWeek.shortDay(it) }
        }
        is Schedule.PerWeek -> if (n.times == 1) "Once a week" else "${n.times} times a week"
    }

    fun weekStart(d: LocalDate): LocalDate = d.minus(DatePeriod(days = d.dayOfWeek.ordinal))

    fun isScheduled(s: Schedule, date: LocalDate): Boolean = when (s) {
        Schedule.Daily, is Schedule.PerWeek -> true
        is Schedule.Days -> date.dayOfWeek in s.days
    }

    data class Stats(
        /** Days for daily and day-picked habits, weeks for times-a-week ones. */
        val streak: Int,
        val streakInWeeks: Boolean,
        val best: Int,
        /** Share of due days kept in the last 30 days, or null before there is anything to judge. */
        val score: Float?,
        val doneThisWeek: Int,
        val dueToday: Boolean,
        val skippedToday: Boolean,
        /** The next scheduled day after today, for day-picked habits. */
        val next: LocalDate?,
    )

    fun stats(s: Schedule, done: Set<LocalDate>, neutral: Set<LocalDate>, today: LocalDate, since: LocalDate): Stats {
        val schedule = normal(s)
        val weekFrom = weekStart(today)
        val doneThisWeek = done.count { it >= weekFrom && it <= today }
        val skippedToday = today in neutral
        val doneToday = today in done
        return when (schedule) {
            is Schedule.PerWeek -> {
                val (streak, best) = weekStreaks(schedule.times, done, neutral, today, since)
                Stats(
                    streak = streak, streakInWeeks = true, best = best,
                    score = weekScore(schedule.times, done, neutral, today, since),
                    doneThisWeek = doneThisWeek,
                    dueToday = !skippedToday && (doneThisWeek < schedule.times || doneToday),
                    skippedToday = skippedToday,
                    next = null,
                )
            }
            else -> Stats(
                streak = dayStreak(schedule, done, neutral, today, since),
                streakInWeeks = false,
                best = bestDayStreak(schedule, done, neutral, today, since),
                score = dayScore(schedule, done, neutral, today, since),
                doneThisWeek = doneThisWeek,
                dueToday = !skippedToday && (isScheduled(schedule, today) || doneToday),
                skippedToday = skippedToday,
                next = (1..7).map { today.plus(DatePeriod(days = it)) }.firstOrNull { isScheduled(schedule, it) && it !in neutral },
            )
        }
    }

    /** Counting back from today: done days add one, off and neutral days (and today, pending) pass. */
    fun dayStreak(s: Schedule, done: Set<LocalDate>, neutral: Set<LocalDate>, today: LocalDate, since: LocalDate): Int {
        var streak = 0
        var d = today
        while (d >= since) {
            when {
                d in done -> streak++
                d in neutral || !isScheduled(s, d) || d == today -> {}
                else -> return streak
            }
            d = d.minus(DatePeriod(days = 1))
        }
        return streak
    }

    private fun bestDayStreak(s: Schedule, done: Set<LocalDate>, neutral: Set<LocalDate>, today: LocalDate, since: LocalDate): Int {
        val first = done.minOrNull()?.let { maxOf(it, since) } ?: return 0
        var best = 0
        var run = 0
        var d = first
        while (d <= today) {
            when {
                d in done -> { run++; best = maxOf(best, run) }
                d in neutral || !isScheduled(s, d) || d == today -> {}
                else -> run = 0
            }
            d = d.plus(DatePeriod(days = 1))
        }
        return best
    }

    private fun dayScore(s: Schedule, done: Set<LocalDate>, neutral: Set<LocalDate>, today: LocalDate, since: LocalDate): Float? {
        var due = 0
        var kept = 0
        for (i in 0 until 30) {
            val d = today.minus(DatePeriod(days = i))
            if (d < since) break
            if (d in neutral || !isScheduled(s, d)) continue
            if (d == today && d !in done) continue
            due++
            if (d in done) kept++
        }
        // "100% in 30 days" on a habit made this morning says nothing; wait for a few due days.
        return if (due < MIN_SCORE_DAYS) null else kept.toFloat() / due
    }

    /** Due days before a daily score means anything. */
    const val MIN_SCORE_DAYS = 5

    /**
     * Weeks in a row that reached [times], counting back. This week counts once reached and is
     * pending until then; a short week with neutral days needs fewer.
     */
    private fun weekStreaks(times: Int, done: Set<LocalDate>, neutral: Set<LocalDate>, today: LocalDate, since: LocalDate): Pair<Int, Int> {
        val thisWeek = weekStart(today)
        val firstWeek = weekStart(since)
        val weeks = generateSequence(thisWeek) { it.minus(DatePeriod(days = 7)) }.takeWhile { it >= firstWeek }.toList().reversed()
        var run = 0
        var best = 0
        var current = 0
        weeks.forEach { w ->
            val result = weekResult(w, times, done, neutral, today)
            when (result) {
                true -> { run++; best = maxOf(best, run) }
                false -> run = 0
                null -> {}
            }
            current = run
        }
        return current to best
    }

    /** True when a week reached its target, false when it missed, null when it does not count yet. */
    internal fun weekResult(week: LocalDate, times: Int, done: Set<LocalDate>, neutral: Set<LocalDate>, today: LocalDate): Boolean? {
        val days = (0 until 7).map { week.plus(DatePeriod(days = it)) }
        val count = days.count { it in done }
        val needed = (times - days.count { it in neutral }).coerceAtLeast(0)
        return when {
            needed == 0 -> if (count > 0) true else null
            count >= needed -> true
            today <= days.last() -> null
            else -> false
        }
    }

    private fun weekScore(times: Int, done: Set<LocalDate>, neutral: Set<LocalDate>, today: LocalDate, since: LocalDate): Float? {
        var target = 0
        var kept = 0
        var w = weekStart(today)
        repeat(5) {
            val days = (0 until 7).map { w.plus(DatePeriod(days = it)) }
            if (days.last() >= since) {
                val count = days.count { it in done }
                val needed = (times - days.count { it in neutral }).coerceAtLeast(0)
                val current = today <= days.last()
                if (!current || count >= needed) {
                    target += needed
                    kept += minOf(count, needed)
                }
            }
            w = w.minus(DatePeriod(days = 7))
        }
        return if (target == 0) null else kept.toFloat() / target
    }

    enum class Slot(val label: String) { MORNING("Morning"), AFTERNOON("Afternoon"), EVENING("Evening"), ANYTIME("Any time") }

    /** Groups a habit by its reminder time, the way people think about their day. */
    fun slot(reminderTime: String?): Slot {
        val hour = reminderTime?.substringBefore(':')?.trim()?.toIntOrNull() ?: return Slot.ANYTIME
        return when {
            hour < 12 -> Slot.MORNING
            hour < 17 -> Slot.AFTERNOON
            else -> Slot.EVENING
        }
    }

    /** The short line under a habit: its progress in words, never a guilt trip. */
    fun meta(schedule: Schedule, stats: Stats, done: Boolean): String {
        val s = normal(schedule)
        val score = stats.score?.let { "${(it * 100).toInt()}% in 30 days" }
        val streak = when {
            stats.streak < 2 -> null
            stats.streakInWeeks -> "${stats.streak} weeks in a row"
            else -> "${stats.streak} day streak"
        }
        return when {
            stats.skippedToday -> "Skipped today, the streak waits"
            s is Schedule.PerWeek -> listOfNotNull("${stats.doneThisWeek} of ${s.times} this week", streak).joinToString(", ")
            else -> listOfNotNull(streak, score).joinToString(", ").ifEmpty { if (done) "Done" else describe(s) }
        }
    }
}
