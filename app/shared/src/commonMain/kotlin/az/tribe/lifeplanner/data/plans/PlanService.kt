package az.tribe.lifeplanner.data.plans

import az.tribe.lifeplanner.data.calendar.CalendarWriter
import az.tribe.lifeplanner.data.fitness.WorkoutService
import az.tribe.lifeplanner.data.integrations.DataFlow
import az.tribe.lifeplanner.data.integrations.IntegrationPrefs
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import com.russhwolf.settings.Settings
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

/**
 * Planned rows that are not workouts (dinners, study blocks, exams): saving them, ticking them off,
 * moving and removing them, and their calendar event when the user wants one. Uses the same event
 * key as [WorkoutService], so a plan's event follows it whichever page moves it.
 */
class PlanService(
    private val logs: LifeLogRepository,
    private val calendar: CalendarWriter,
    private val prefs: IntegrationPrefs,
    private val settings: Settings,
) {
    private val tz = TimeZone.currentSystemDefault()

    private fun calendarOut(): Boolean = prefs.state.value.let { it.calendar && it.isOn(DataFlow.EVENTS_OUT) }

    suspend fun canAddToCalendar(): Boolean = calendarOut() && runCatching { calendar.canWrite() }.getOrDefault(false)

    fun hasEvent(log: LifeLog): Boolean = settings.hasKey(eventKey(log.id))

    /**
     * Saves [log] as a plan. With [addToCalendar], a timed plan becomes an event and an untimed one
     * an all-day one. [leadMinutes] makes the event end at the plan's time instead of starting
     * there, for things that take time beforehand, like cooking dinner.
     */
    suspend fun plan(log: LifeLog, addToCalendar: Boolean, eventTitle: String = log.title, leadMinutes: Int? = null) {
        val planned = log.copy(status = LogStatus.PLANNED, source = LifeLog.SOURCE_PLAN)
        logs.save(planned)
        if (addToCalendar && calendarOut()) addEvent(planned, eventTitle, leadMinutes)
    }

    suspend fun planAll(rows: List<LifeLog>, addToCalendar: Boolean, eventTitle: (LifeLog) -> String = { it.title }) {
        val planned = rows.map { it.copy(status = LogStatus.PLANNED, source = LifeLog.SOURCE_PLAN) }
        logs.saveAll(planned)
        if (addToCalendar && calendarOut()) planned.forEach { addEvent(it, eventTitle(it)) }
    }

    /** Ticks a plan off, or back to planned. The row stays, so the plan is not lost by an undo. */
    suspend fun setDone(log: LifeLog, done: Boolean) {
        logs.save(log.copy(status = if (done) LogStatus.DONE else LogStatus.PLANNED))
    }

    suspend fun remove(log: LifeLog) {
        logs.delete(log.id)
        settings.getStringOrNull(eventKey(log.id))?.let { runCatching { calendar.deleteEvent(it) }; settings.remove(eventKey(log.id)) }
    }

    suspend fun moveTo(log: LifeLog, at: LocalDateTime): LifeLog {
        val moved = log.copy(occurredAt = at)
        logs.save(moved)
        settings.getStringOrNull(eventKey(log.id))?.let { id ->
            val (start, end) = span(moved)
            runCatching { calendar.moveEvent(id, start, end) }
        }
        return moved
    }

    /** Puts an existing plan on the calendar, or takes it off. */
    suspend fun toggleCalendar(log: LifeLog, eventTitle: String = log.title) {
        val existing = settings.getStringOrNull(eventKey(log.id))
        if (existing != null) {
            runCatching { calendar.deleteEvent(existing) }
            settings.remove(eventKey(log.id))
        } else if (calendarOut()) addEvent(log, eventTitle)
    }

    private suspend fun addEvent(log: LifeLog, title: String, leadMinutes: Int? = null) {
        val (start, end) = if (leadMinutes != null && WorkoutService.hasTime(log)) {
            val at = log.occurredAt.toInstant(tz).toEpochMilliseconds()
            at - leadMinutes * 60_000L to at
        } else span(log)
        runCatching { calendar.addEvent(title, start, end, "Planned in LifePlanner") }.getOrNull()?.let { settings.putString(eventKey(log.id), it) }
    }

    /** A timed plan lasts its minutes (half an hour by default); an untimed one is the whole day. */
    private fun span(log: LifeLog): Pair<Long, Long> =
        if (WorkoutService.hasTime(log)) {
            val start = log.occurredAt.toInstant(tz).toEpochMilliseconds()
            start to start + (log.durationMin ?: 30) * 60_000L
        } else {
            log.date.atStartOfDayIn(tz).toEpochMilliseconds() to log.date.plus(DatePeriod(days = 1)).atStartOfDayIn(tz).toEpochMilliseconds()
        }

    private fun eventKey(logId: String) = "v4_cal_event_$logId"
}
