package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** What a Study row is. Stored in [LifeLog.category] under [key]. */
enum class StudyKind(val key: String) {
    /** Time spent studying, done. */
    SESSION("session"),
    /** A block of study planned for a day, ticked off on Today. */
    BLOCK("block"),
    EXAM("exam"),
    DEADLINE("deadline");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: SESSION
    }
}

/** Study time from anywhere: a logged session, a ticked block, or a focus timer session. */
data class StudyTime(val date: LocalDate, val minutes: Int, val subject: String?)

/**
 * The pure parts of the Study page. Rows are [LogKind.STUDY] in [PlanArea.STUDY]; the subject is
 * the title ("Biology"), the kind is the category. Exams and deadlines are dated plans that
 * count down, and study blocks can be spread over the days before one.
 */
object StudyPlanner {

    fun isStudy(l: LifeLog) = l.kind == LogKind.STUDY && l.area == PlanArea.STUDY

    fun kindOf(l: LifeLog): StudyKind = StudyKind.fromKey(l.category)

    fun isDated(l: LifeLog) = isStudy(l) && kindOf(l).let { it == StudyKind.EXAM || it == StudyKind.DEADLINE }

    /** Minutes that count: done sessions and blocks from the logs, plus focus timer sessions. */
    fun times(logs: List<LifeLog>, focus: List<StudyTime>): List<StudyTime> =
        logs.filter { isStudy(it) && it.status == LogStatus.DONE && !isDated(it) && (it.durationMin ?: 0) > 0 }
            .map { StudyTime(it.date, it.durationMin!!, it.title.takeIf { t -> t.isNotBlank() }) } + focus

    data class Week(
        val minutes: Int,
        val target: Int?,
        /** Minutes per day, oldest first, ending today. */
        val perDay: List<Pair<LocalDate, Int>>,
        /** Minutes per subject, most first. Untitled time is "Other". */
        val bySubject: List<Pair<String, Int>>,
        val streakDays: Int,
    )

    fun week(times: List<StudyTime>, today: LocalDate, targetMinutes: Int?): Week {
        val from = today.minus(DatePeriod(days = 6))
        val inWeek = times.filter { it.date in from..today }
        val perDay = (6 downTo 0).map { today.minus(DatePeriod(days = it)) }.map { d -> d to inWeek.filter { it.date == d }.sumOf { it.minutes } }
        val bySubject = inWeek.groupBy { it.subject?.trim()?.takeIf { s -> s.isNotEmpty() } ?: "Other" }
            .map { (s, rows) -> s to rows.sumOf { it.minutes } }
            .sortedByDescending { it.second }
        return Week(inWeek.sumOf { it.minutes }, targetMinutes, perDay, bySubject, streak(times, today))
    }

    /** Days in a row with any study, counting today only once something is logged today. */
    fun streak(times: List<StudyTime>, today: LocalDate): Int {
        val days = times.filter { it.minutes > 0 }.map { it.date }.toSet()
        var d = if (today in days) today else today.minus(DatePeriod(days = 1))
        var n = 0
        while (d in days) { n++; d = d.minus(DatePeriod(days = 1)) }
        return n
    }

    /** "today", "tomorrow", "in 12 days", "3 days ago". */
    fun countdown(date: LocalDate, today: LocalDate): String {
        val days = (date.toEpochDays() - today.toEpochDays()).toInt()
        return when {
            days == 0 -> "today"
            days == 1 -> "tomorrow"
            days > 1 -> "in $days days"
            days == -1 -> "yesterday"
            else -> "${-days} days ago"
        }
    }

    /** Exams and deadlines still ahead, soonest first. */
    fun upcoming(logs: List<LifeLog>, today: LocalDate): List<LifeLog> =
        logs.filter { isDated(it) && it.date >= today && it.status != LogStatus.SKIPPED }.sortedBy { it.occurredAt }

    /** The planned blocks before [exam] that share its subject, so the exam can show "3 of 5 done". */
    fun blocksFor(exam: LifeLog, logs: List<LifeLog>): List<LifeLog> =
        logs.filter {
            isStudy(it) && kindOf(it) == StudyKind.BLOCK && it.date < exam.date &&
                (it.notes == exam.id || it.title.trim().equals(exam.title.trim(), ignoreCase = true))
        }

    /**
     * Days for [count] study blocks before [due]: spread evenly from [today] to the day before,
     * leaning toward the end, skipping days that already have a block for anything, and never
     * two on one day unless there are more blocks than free days.
     */
    fun spreadBefore(due: LocalDate, today: LocalDate, count: Int, busy: Set<LocalDate> = emptySet()): List<LocalDate> {
        if (count <= 0) return emptyList()
        val last = due.minus(DatePeriod(days = 1))
        if (last < today) return emptyList()
        val all = generateSequence(today) { it.plus(DatePeriod(days = 1)) }.takeWhile { it <= last }.toList()
        val free = all.filter { it !in busy }.ifEmpty { all }
        if (count >= free.size) {
            // More blocks than days: every free day, then wrap onto the latest days again.
            return (free + generateSequence { free.asReversed() }.flatten().take(count - free.size)).sorted()
        }
        // Even steps counted back from the day before, so the last block is always close to it.
        val step = free.size.toDouble() / count
        return (0 until count).map { i -> free[free.size - 1 - (i * step).toInt()] }.distinct().sorted()
    }

    /** Planned blocks from before today that were never done. */
    fun missed(logs: List<LifeLog>, today: LocalDate): List<LifeLog> =
        logs.filter { isStudy(it) && kindOf(it) == StudyKind.BLOCK && it.status == LogStatus.PLANNED && it.date < today }
            .sortedBy { it.occurredAt }

    /**
     * New days for missed blocks, so falling behind reschedules instead of piling up. Blocks for an
     * exam still ahead are spread over the days left before it; the rest go to the next free days
     * this week. Blocks for an exam already past have nowhere useful to go and are left out.
     */
    fun rollover(missed: List<LifeLog>, logs: List<LifeLog>, today: LocalDate): Map<String, LocalDate> {
        val exams = logs.filter { isDated(it) }.associateBy { it.id }
        val busy = logs.filter { isStudy(it) && kindOf(it) == StudyKind.BLOCK && it.status == LogStatus.PLANNED && it.date >= today }
            .map { it.date }.toMutableSet()
        val out = mutableMapOf<String, LocalDate>()
        missed.groupBy { m -> m.notes?.let { exams[it] } ?: exams.values.firstOrNull { e -> e.date >= today && e.title.equals(m.title, ignoreCase = true) } }
            .forEach { (exam, blocks) ->
                val days = when {
                    exam == null -> spreadBefore(today.plus(DatePeriod(days = 7)), today, blocks.size, busy)
                    exam.date > today -> spreadBefore(exam.date, today, blocks.size, busy)
                    else -> emptyList()
                }
                blocks.zip(days).forEach { (b, d) -> out[b.id] = d; busy += d }
            }
        return out
    }

    /** Short look-backs after a topic is studied, the gaps spaced practice uses. */
    val REVIEW_DAYS = listOf(1, 3, 7)

    private val examWords = Regex("""\b(exam|exams|test|quiz|midterm|mid-term|final|finals|oral|viva)\b""", RegexOption.IGNORE_CASE)

    /** "Biology exam", but "BIO101 midterm" as it is, since it already says what it is. */
    fun dueName(l: LifeLog): String = when (kindOf(l)) {
        StudyKind.EXAM -> if (examWords.containsMatchIn(l.title)) l.title else "${l.title} exam"
        else -> l.title
    }

    /** "BIO101 midterm", "History essay due": the line for lists and chips. */
    fun dueLine(l: LifeLog): String = if (kindOf(l) == StudyKind.EXAM) dueName(l) else "${l.title} due"

    /** The calendar event's title. */
    fun dueEvent(l: LifeLog): String = if (kindOf(l) == StudyKind.EXAM) "Exam: ${l.title}" else "Due: ${l.title}"

    /** "2h 05m", "45 min". */
    fun formatMinutes(m: Int): String = if (m < 60) "$m min" else "${m / 60}h ${(m % 60).toString().padStart(2, '0')}m"
}
