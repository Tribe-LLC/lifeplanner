package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.model.LifeLog
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.roundToInt

/**
 * "A year in pixels": one row per month, one cell per day, coloured by that day's mood. Starts
 * at the first month with a check-in (at most a year back, at least a few months so it never
 * looks broken), and ends with this month. Days still ahead are left out of the colouring.
 */
object MoodYear {
    const val MAX_MONTHS = 12
    const val MIN_MONTHS = 3

    /** A day with nothing recorded. */
    const val EMPTY = 0
    /** A day still ahead. */
    const val FUTURE = -1

    /** [levels] has one entry per day of the month: 1 to 5, [EMPTY] or [FUTURE]. */
    data class Row(val first: LocalDate, val label: String, val levels: List<Int>) {
        fun date(day: Int): LocalDate = LocalDate(first.year, first.month, day)
        val recorded: Int get() = levels.count { it > 0 }
    }

    fun level(avg: Double): Int = avg.roundToInt().coerceIn(1, 5)

    fun rows(today: LocalDate, daily: Map<LocalDate, Double>, max: Int = MAX_MONTHS, min: Int = MIN_MONTHS): List<Row> {
        val thisMonth = firstOfMonth(today)
        val earliest = thisMonth.minus(DatePeriod(months = max - 1))
        val latestStart = thisMonth.minus(DatePeriod(months = min - 1))
        val firstData = daily.keys.filter { it <= today }.minOrNull()?.let(::firstOfMonth) ?: thisMonth
        val start = minOf(maxOf(firstData, earliest), latestStart)

        val rows = mutableListOf<Row>()
        var m = start
        while (m <= thisMonth) {
            val next = m.plus(DatePeriod(months = 1))
            val length = next.minus(DatePeriod(days = 1)).day
            val levels = (1..length).map { d ->
                val date = LocalDate(m.year, m.month, d)
                when {
                    date > today -> FUTURE
                    else -> daily[date]?.let(::level) ?: EMPTY
                }
            }
            rows += Row(m, monthLabel(m), levels)
            m = next
        }
        return rows
    }

    /** "Sep". */
    fun monthLabel(first: LocalDate): String = first.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)

    /** For screen readers: "September: 18 days, mostly good". */
    fun describe(row: Row): String {
        val name = row.first.month.name.lowercase().replaceFirstChar { it.uppercase() }
        val levels = row.levels.filter { it > 0 }
        if (levels.isEmpty()) return "$name: no check-ins"
        return "$name: ${levels.size} ${if (levels.size == 1) "day" else "days"}, ${MindInsights.summary(levels.average()).lowercase()}"
    }

    /**
     * The words that go with a day: the latest check-in's note, else its feelings and what was part
     * of it, else the title of that day's journal entry. Null when there is nothing to say.
     */
    fun dayNote(checkIns: List<LifeLog>, journalTitles: List<String>): String? {
        val latest = checkIns.sortedByDescending { it.occurredAt }
        latest.firstNotNullOfOrNull { MindCheckIns.note(it) }?.let { return it }
        latest.firstOrNull { MindCheckIns.feelings(it).isNotEmpty() || MindCheckIns.tags(it).isNotEmpty() }?.let { l ->
            val feelings = MindCheckIns.feelings(l)
            val tags = MindCheckIns.tags(l)
            return listOfNotNull(
                feelings.takeIf { it.isNotEmpty() }?.joinToString(", ")?.lowercase()?.replaceFirstChar { it.uppercase() },
                tags.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.lowercase() }?.let { "Part of it: $it" },
            ).joinToString(". ")
        }
        return journalTitles.firstOrNull { it.isNotBlank() }?.let { "Journal: ${it.trim()}" }
    }

    private fun firstOfMonth(d: LocalDate) = LocalDate(d.year, d.month, 1)
}
