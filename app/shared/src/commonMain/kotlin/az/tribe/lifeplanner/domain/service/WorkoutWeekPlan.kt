package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.data.health.WorkoutKind
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/** One day of the repeating week: "Mon: Strength, 07:00, 45 min". A null [time] is "any time". */
data class WeekSlot(val day: DayOfWeek, val title: String, val time: LocalTime?, val minutes: Int)

/**
 * The week that repeats. Kept in one budget row's category as text so it syncs with no new table.
 * [gen] goes up on every save, so rows planned from an older week can be told apart and replaced.
 */
data class WorkoutWeek(val slots: List<WeekSlot>, val gen: Int = 1, val toCalendar: Boolean = false) {

    fun slotFor(day: DayOfWeek): WeekSlot? = slots.firstOrNull { it.day == day }

    fun encode(): String = buildList {
        add("g=$gen")
        add("cal=${if (toCalendar) 1 else 0}")
        slots.sortedBy { it.day.ordinal }.forEach { add(WorkoutWeekPlan.signature(it)) }
    }.joinToString(";")

    companion object {
        fun decode(text: String?): WorkoutWeek? {
            if (text.isNullOrBlank()) return null
            val parts = text.split(';').map { it.trim() }.filter { it.isNotEmpty() }
            val gen = parts.firstOrNull { it.startsWith("g=") }?.removePrefix("g=")?.toIntOrNull() ?: 1
            val cal = parts.firstOrNull { it.startsWith("cal=") }?.removePrefix("cal=") == "1"
            val slots = parts.filter { '|' in it }.mapNotNull(WorkoutWeekPlan::parseSignature).distinctBy { it.day }
            return WorkoutWeek(slots.sortedBy { it.day.ordinal }, gen, cal)
        }
    }
}

/**
 * Turns the repeating week into planned workouts for the next seven days, and keeps them right as
 * the week changes. Each planned row carries an external id that names the week it came from and
 * exactly what it was planned as, so:
 * - the same day is never planned twice (the id is fixed per week and date, on every device),
 * - a row the user moved or removed is left alone (its id still exists, so it is not made again),
 * - rows from an older week that nobody touched are replaced when the week changes.
 */
object WorkoutWeekPlan {
    const val ROW_PREFIX = "fitweek-"
    const val EXT_PREFIX = "fitweek:"
    private const val ANY = "any"

    fun signature(s: WeekSlot): String = listOf(s.day.name.take(3), s.title.replace('|', ' ').replace(';', ' ').replace(':', ' ').trim(), s.time?.let(::hhmm) ?: ANY, s.minutes).joinToString("|")

    fun parseSignature(text: String): WeekSlot? {
        val p = text.split('|')
        if (p.size != 4) return null
        val day = DayOfWeek.entries.firstOrNull { it.name.take(3) == p[0] } ?: return null
        val title = p[1].trim().ifEmpty { return null }
        val time = if (p[2] == ANY) null else runCatching { LocalTime.parse(p[2]) }.getOrNull() ?: return null
        val minutes = p[3].toIntOrNull()?.takeIf { it in 1..600 } ?: return null
        return WeekSlot(day, title, time, minutes)
    }

    fun rowId(gen: Int, date: LocalDate) = "$ROW_PREFIX$gen-$date"

    fun externalId(gen: Int, date: LocalDate, slot: WeekSlot) = "$EXT_PREFIX$gen:$date:${signature(slot)}"

    fun isGenerated(l: LifeLog) = l.externalId?.startsWith(EXT_PREFIX) == true

    /** The week a generated row came from, or null for any other row. */
    fun genOf(l: LifeLog): Int? = l.externalId?.takeIf { it.startsWith(EXT_PREFIX) }?.removePrefix(EXT_PREFIX)?.substringBefore(':')?.toIntOrNull()

    /** What the row was planned as: the slot inside its external id. */
    fun plannedAs(l: LifeLog): WeekSlot? = l.externalId?.takeIf { it.startsWith(EXT_PREFIX) }
        ?.removePrefix(EXT_PREFIX)?.split(':', limit = 3)?.getOrNull(2)?.let(::parseSignature)

    /** Still exactly as it was planned: same day of the week, name, time and length. */
    fun untouched(l: LifeLog): Boolean {
        val was = plannedAs(l) ?: return false
        val now = WeekSlot(l.date.dayOfWeek, l.title, l.occurredAt.time.takeIf { it != MIDNIGHT }, l.durationMin ?: -1)
        return signature(was) == signature(now)
    }

    /**
     * The rows the week wants in the next [days] days starting [today]. [now] keeps a slot that
     * already ended today from being planned in the past on the first run.
     */
    fun wanted(week: WorkoutWeek, today: LocalDate, now: LocalTime, days: Int = 7): List<LifeLog> =
        (0 until days).mapNotNull { i ->
            val date = today.plus(DatePeriod(days = i))
            val slot = week.slotFor(date.dayOfWeek) ?: return@mapNotNull null
            if (i == 0 && slot.time != null && endsBefore(slot, now)) return@mapNotNull null
            LifeLog(
                id = rowId(week.gen, date), area = PlanArea.FITNESS, kind = LogKind.WORKOUT, status = LogStatus.PLANNED,
                title = slot.title, durationMin = slot.minutes, occurredAt = LocalDateTime(date, slot.time ?: MIDNIGHT),
                source = LifeLog.SOURCE_PLAN, externalId = externalId(week.gen, date, slot),
            )
        }

    /**
     * Rows planned from another week (or from a week that was switched off) that nobody changed,
     * from [today] on. These go when the week changes; moved or ticked ones stay.
     */
    fun stale(week: WorkoutWeek?, rows: List<LifeLog>, today: LocalDate): List<LifeLog> =
        rows.filter { l ->
            isGenerated(l) && l.status == LogStatus.PLANNED && l.date >= today && untouched(l) &&
                (week == null || week.slots.isEmpty() || genOf(l) != week.gen)
        }

    /** Groups the week for reading: "Mon, Wed, Fri: Strength, 07:00, 45 min". */
    fun summary(week: WorkoutWeek?): List<String> {
        if (week == null) return emptyList()
        return week.slots.sortedBy { it.day.ordinal }
            .groupBy { Triple(it.title, it.time, it.minutes) }
            .map { (k, v) ->
                val days = v.joinToString(", ") { FitnessWeek.shortDay(it.day) }
                "$days: ${k.first}, ${k.second?.let(::hhmm) ?: "any time"}, ${k.third} min"
            }
    }

    private fun endsBefore(slot: WeekSlot, now: LocalTime): Boolean {
        val t = slot.time ?: return false
        val end = t.hour * 60 + t.minute + slot.minutes
        return end <= now.hour * 60 + now.minute
    }

    private fun hhmm(t: LocalTime) = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"

    private val MIDNIGHT = LocalTime(0, 0)
}

/**
 * What a workout row carries in its notes besides the coach's line: "did: Squat 3x5 60kg" (what the
 * user did, shown as "Last time" next time), and "paused" on planned rows set aside by a break.
 * Anything else in the notes is shown as it always was.
 */
object WorkoutNotes {
    private const val DID = "did:"
    private const val PAUSED = "paused"

    private fun lines(notes: String?) = notes.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun did(notes: String?): String? = lines(notes).firstOrNull { it.startsWith(DID) }?.removePrefix(DID)?.trim()?.ifEmpty { null }

    fun withDid(notes: String?, text: String?): String? {
        val keep = lines(notes).filterNot { it.startsWith(DID) }
        val did = text?.replace('\n', ' ')?.trim()?.take(140)?.takeIf { it.isNotEmpty() }
        return (keep + listOfNotNull(did?.let { "$DID $it" })).joinToString("\n").ifEmpty { null }
    }

    fun isPaused(notes: String?) = lines(notes).any { it == PAUSED }

    fun withPaused(notes: String?, paused: Boolean): String? {
        val keep = lines(notes).filterNot { it == PAUSED }
        return (keep + listOfNotNull(if (paused) PAUSED else null)).joinToString("\n").ifEmpty { null }
    }

    /** The notes as the user should read them: without the lines kept for the app. */
    fun display(notes: String?): String? = lines(notes).filterNot { it.startsWith(DID) || it == PAUSED }.joinToString(" ").ifEmpty { null }

    /**
     * The last time this workout was done with a note of what was done: the same name first,
     * else the same kind (a "Leg day" and a "Strength" both count as strength).
     */
    fun lastTime(logs: List<LifeLog>, next: LifeLog): LifeLog? {
        val done = logs.filter { it.kind == LogKind.WORKOUT && it.id != next.id && it.status == LogStatus.DONE && it.date <= next.date && did(it.notes) != null }
            .sortedByDescending { it.occurredAt }
        done.firstOrNull { it.title.trim().equals(next.title.trim(), ignoreCase = true) }?.let { return it }
        val kind = WorkoutKind.fromTitle(next.title)
        if (kind == WorkoutKind.OTHER) return null
        return done.firstOrNull { WorkoutKind.fromTitle(it.title) == kind }
    }
}
