package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/** The kinds of Career rows, kept in [LifeLog.category]. */
enum class CareerKind(val key: String) {
    WIN("win"), APPLICATION("application"), INTERVIEW("interview"), CONTACT("contact"), SKILL("skill");

    companion object {
        fun of(l: LifeLog): CareerKind? = if (l.area != PlanArea.CAREER) null else entries.firstOrNull { it.key == l.category }
    }
}

enum class Stage(val label: String) {
    SAVED("Saved"), APPLIED("Applied"), INTERVIEW("Interview"), OFFER("Offer"), CLOSED("Closed");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: SAVED
    }
}

/**
 * Career rows, all in life logs so they sync with nothing new:
 * - a win: [LifeLog.title] what happened, [LifeLog.notes] what it changed;
 * - an application: title the role, notes the company, link, stage and dates, and
 *   [LifeLog.occurredAt] the day of its next action, so it lands on Today when due;
 * - an interview: a timed plan, [LifeLog.externalId] its application;
 * - a person: title the name, [LifeLog.quantity] how many days between catch-ups, occurredAt the
 *   next one;
 * - a skill: title the skill, quantity the level now (1 to 5), notes the level wanted.
 */
object CareerPlanner {
    const val FOLLOW_UP_DAYS = 7

    val LEVELS = listOf("Beginner", "Basic", "Working", "Strong", "Expert")

    // ── Notes codec: "key: value" lines ──

    fun field(l: LifeLog, key: String): String? =
        l.notes?.lineSequence()?.firstOrNull { it.startsWith("$key: ") }?.removePrefix("$key: ")?.trim()?.ifEmpty { null }

    fun withField(notes: String?, key: String, value: String?): String? {
        val lines = notes?.lines().orEmpty().filterNot { it.startsWith("$key: ") }.filter { it.isNotBlank() }
        val out = if (value.isNullOrBlank()) lines else lines + "$key: ${value.trim()}"
        return out.joinToString("\n").ifEmpty { null }
    }

    fun company(l: LifeLog) = field(l, "company")
    fun link(l: LifeLog) = field(l, "link")
    fun stage(l: LifeLog) = Stage.of(field(l, "stage"))
    fun applied(l: LifeLog) = field(l, "applied")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun saved(l: LifeLog) = field(l, "saved")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun about(l: LifeLog) = field(l, "about")
    fun lastTalked(l: LifeLog) = field(l, "last")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    fun wantLevel(l: LifeLog) = field(l, "want")?.toIntOrNull()

    fun roleLine(l: LifeLog) = listOfNotNull(l.title.takeIf { it.isNotBlank() }, company(l)).joinToString(", ")

    fun isActive(l: LifeLog) = CareerKind.of(l) == CareerKind.APPLICATION && stage(l) != Stage.CLOSED

    /** One thing to do next, with the words for it. */
    data class Action(val log: LifeLog, val title: String, val meta: String, val due: LocalDate, val timed: Boolean)

    /** What to do about each open application, interview and person, soonest first. */
    fun actions(rows: List<LifeLog>, today: LocalDate, horizonDays: Int = 7): List<Action> {
        val horizon = today.plus(DatePeriod(days = horizonDays))
        val apps = rows.filter { CareerKind.of(it) == CareerKind.APPLICATION }.associateBy { it.id }
        val upcomingInterview = rows.filter { CareerKind.of(it) == CareerKind.INTERVIEW && it.status == LogStatus.PLANNED }.mapNotNull { it.externalId }.toSet()
        val out = mutableListOf<Action>()
        rows.forEach { l ->
            when (CareerKind.of(l)) {
                CareerKind.APPLICATION -> {
                    val st = stage(l)
                    if (st == Stage.CLOSED || l.date > horizon) return@forEach
                    if (st == Stage.INTERVIEW && l.id in upcomingInterview) return@forEach
                    val who = company(l) ?: l.title
                    val (title, meta) = when (st) {
                        Stage.SAVED -> "Apply: $who" to (saved(l)?.let { "${l.title}. Saved ${ago(it, today)}" } ?: l.title)
                        Stage.APPLIED -> "Follow up: $who" to (applied(l)?.let { "Applied ${ago(it, today)}, no reply yet" } ?: "No reply yet")
                        Stage.INTERVIEW -> "Update: $who" to "How did the interview go?"
                        Stage.OFFER -> "Reply to the offer: $who" to l.title
                        Stage.CLOSED -> return@forEach
                    }
                    out += Action(l, title, meta, l.date, false)
                }
                CareerKind.INTERVIEW -> if (l.status == LogStatus.PLANNED && l.date >= today && l.date <= horizon) {
                    val app = l.externalId?.let { apps[it] }
                    out += Action(l, l.title, listOfNotNull(app?.title, timeOf(l)).joinToString(", "), l.date, true)
                }
                CareerKind.CONTACT -> if (l.status == LogStatus.PLANNED && l.date <= horizon) {
                    out += Action(l, "Catch up with ${l.title}", lastTalked(l)?.let { "Last talked ${ago(it, today)}" } ?: (about(l) ?: "Keep in touch"), l.date, false)
                }
                else -> {}
            }
        }
        return out.sortedWith(compareBy({ it.due }, { it.log.occurredAt }))
    }

    private fun timeOf(l: LifeLog): String? =
        l.occurredAt.time.takeIf { it.hour != 0 || it.minute != 0 }?.let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" }

    fun ago(d: LocalDate, today: LocalDate): String {
        val n = (today.toEpochDays() - d.toEpochDays()).toInt()
        return when {
            n <= 0 -> "today"
            n == 1 -> "yesterday"
            n < 14 -> "$n days ago"
            n < 60 -> "${n / 7} weeks ago"
            else -> "${n / 30} months ago"
        }
    }

    // ── Wins ──

    fun quarterStart(d: LocalDate): LocalDate = LocalDate(d.year, Month.entries[(d.month.ordinal / 3) * 3], 1)

    fun winsInQuarter(rows: List<LifeLog>, today: LocalDate): List<LifeLog> {
        val from = quarterStart(today)
        return rows.filter { CareerKind.of(it) == CareerKind.WIN && it.date >= from && it.date <= today }.sortedByDescending { it.occurredAt }
    }

    /** The quarter's wins by month, newest month first, ready to paste into a review. */
    fun reviewText(wins: List<LifeLog>, today: LocalDate): String {
        val from = quarterStart(today)
        val to = Month.entries[(from.month.ordinal + 2).coerceAtMost(11)]
        val head = "Wins, ${monthName(from.month)} to ${monthName(to)} ${today.year}"
        if (wins.isEmpty()) return head
        val body = wins.groupBy { it.date.month }.toList().sortedByDescending { it.first.ordinal }.joinToString("\n\n") { (m, list) ->
            monthName(m) + "\n" + list.sortedBy { it.occurredAt }.joinToString("\n") { w ->
                "- " + w.title.trim().trimEnd('.') + (w.notes?.trim()?.takeIf { it.isNotEmpty() }?.let { ". $it" } ?: "")
            }
        }
        return "$head\n\n$body"
    }

    fun monthName(m: Month) = m.name.lowercase().replaceFirstChar { it.uppercase() }

    // ── Skills ──

    fun levelName(level: Int) = LEVELS[(level - 1).coerceIn(0, 4)]

    /** Minutes of study in the last 30 days whose subject is the skill's name. */
    fun practiceMinutes(skill: String, times: List<StudyTime>, today: LocalDate): Int {
        val from = today.plus(DatePeriod(days = -30))
        val name = skill.trim().lowercase()
        return times.filter { it.date >= from && it.subject?.trim()?.lowercase() == name }.sumOf { it.minutes }
    }
}
