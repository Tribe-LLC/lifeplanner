package az.tribe.lifeplanner.data.fitness

import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.calendar.CalendarWriter
import az.tribe.lifeplanner.data.health.HealthDataManager
import az.tribe.lifeplanner.data.health.WorkoutKind
import az.tribe.lifeplanner.data.integrations.DataFlow
import az.tribe.lifeplanner.data.integrations.IntegrationPrefs
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.service.FitnessWeek
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A workout running now. Kept in settings, so it survives leaving the screen or the app. */
data class ActiveWorkout(val startEpochMs: Long, val kind: WorkoutKind, val title: String, val plannedId: String?)

/** What a coach swap changed, so "Put it back" can undo exactly that. */
data class WorkoutSwap(val originalId: String, val originalDate: LocalDate, val movedTo: LocalDate, val walkId: String)

/**
 * Everything Fitness does to workouts, in one place so Today, the Fitness page and quick add agree:
 * the timer, planned workouts, the coach's lighter-day swap, and both directions of Health.
 * Health and calendar writes only happen when the user switched that flow on; each one is best
 * effort, and the workout is saved here either way.
 */
@OptIn(ExperimentalUuidApi::class)
class WorkoutService(
    private val logs: LifeLogRepository,
    private val health: HealthDataManager,
    private val calendar: CalendarWriter,
    private val prefs: IntegrationPrefs,
    private val settings: Settings,
) {
    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)
    private fun nowMs() = Clock.System.now().toEpochMilliseconds()

    private val _active = MutableStateFlow(readActive())
    val active: StateFlow<ActiveWorkout?> = _active.asStateFlow()

    private var lastImportMs = 0L

    // ── Timer ────────────────────────────────────────────────────────────────

    fun start(kind: WorkoutKind, title: String, plannedId: String? = null) {
        val a = ActiveWorkout(nowMs(), kind, title, plannedId)
        settings.putString(KEY_ACTIVE, listOf(a.startEpochMs, a.kind.name, a.plannedId ?: "", a.title).joinToString("|"))
        _active.value = a
        PostHogAnalytics.capture("v4_workout_started", mapOf("kind" to kind.name.lowercase(), "planned" to (plannedId != null)))
    }

    fun cancel() {
        settings.remove(KEY_ACTIVE)
        _active.value = null
    }

    /** Stops the timer and saves the workout. Returns whether it also reached Health. */
    suspend fun stop(): Boolean {
        val a = _active.value ?: return false
        cancel()
        val end = nowMs()
        val minutes = max(1, ((end - a.startEpochMs) / 60_000L).toInt())
        val startAt = Instant.fromEpochMilliseconds(a.startEpochMs).toLocalDateTime(tz)
        val planned = a.plannedId?.let { logs.getById(it) }
        val log = planned?.copy(status = LogStatus.DONE, durationMin = minutes, occurredAt = startAt)
            ?: LifeLog(
                id = newId(), area = PlanArea.FITNESS, kind = LogKind.WORKOUT, title = a.title,
                durationMin = minutes, occurredAt = startAt, source = LifeLog.SOURCE_TIMER,
            )
        logs.save(log)
        val toHealth = writeToHealth(log, a.kind, a.startEpochMs, end)
        PostHogAnalytics.capture("v4_workout_finished", mapOf("minutes" to minutes, "to_health" to toHealth))
        return toHealth
    }

    // ── Planned workouts ─────────────────────────────────────────────────────

    suspend fun plan(title: String, date: LocalDate, time: LocalTime?, durationMin: Int, addToCalendar: Boolean): LifeLog {
        val log = LifeLog(
            id = newId(), area = PlanArea.FITNESS, kind = LogKind.WORKOUT, status = LogStatus.PLANNED,
            title = title, durationMin = durationMin, occurredAt = LocalDateTime(date, time ?: ANY_TIME),
            source = LifeLog.SOURCE_PLAN,
        )
        logs.save(log)
        if (addToCalendar && calendarOut()) {
            val start = log.occurredAt.toInstant(tz).toEpochMilliseconds()
            calendar.addEvent(title, start, start + durationMin * 60_000L, "Planned in LifePlanner")?.let {
                settings.putString(eventKey(log.id), it)
            }
        }
        PostHogAnalytics.capture("v4_workout_planned", mapOf("in_days" to (date.toEpochDays() - today().toEpochDays()), "calendar" to addToCalendar))
        return log
    }

    /** Ticks a planned workout off without the timer: it happened as planned. */
    suspend fun markDone(log: LifeLog) {
        val minutes = log.durationMin ?: 30
        // Planned for later today but done already: it happened just now, not in the future.
        val plannedStart = log.occurredAt.toInstant(tz).toEpochMilliseconds()
        val start = if (plannedStart > nowMs() || !hasTime(log)) nowMs() - minutes * 60_000L else plannedStart
        val done = log.copy(status = LogStatus.DONE, occurredAt = Instant.fromEpochMilliseconds(start).toLocalDateTime(tz))
        logs.save(done)
        writeToHealth(done, WorkoutKind.fromTitle(log.title), start, start + minutes * 60_000L)
    }

    /** Un-ticks: a planned workout goes back to planned, anything else is removed. */
    suspend fun undoDone(log: LifeLog) {
        if (log.source == LifeLog.SOURCE_PLAN) logs.save(log.copy(status = LogStatus.PLANNED)) else logs.delete(log.id)
    }

    suspend fun remove(log: LifeLog) {
        logs.delete(log.id)
        settings.getStringOrNull(eventKey(log.id))?.let { calendar.deleteEvent(it); settings.remove(eventKey(log.id)) }
    }

    suspend fun moveTo(log: LifeLog, date: LocalDate, note: String? = log.notes): LifeLog {
        val moved = log.copy(occurredAt = LocalDateTime(date, log.occurredAt.time), notes = note)
        logs.save(moved)
        settings.getStringOrNull(eventKey(log.id))?.let { id ->
            val start = moved.occurredAt.toInstant(tz).toEpochMilliseconds()
            calendar.moveEvent(id, start, start + (log.durationMin ?: 30) * 60_000L)
        }
        return moved
    }

    // ── The coach's lighter day ──────────────────────────────────────────────

    /** Moves today's hard workout to the next free day and puts a 20 minute walk in its place. */
    suspend fun swapForWalk(planned: LifeLog, sleepText: String): WorkoutSwap {
        val today = today()
        val week = logs.getInRange(today, today.plus(DatePeriod(days = 7)))
        val target = FitnessWeek.nextFreeDay(week, today)
        moveTo(planned, target, "Moved from ${FitnessWeek.dayName(today.dayOfWeek).lowercase()} by your coach")
        val walk = LifeLog(
            id = newId(), area = PlanArea.FITNESS, kind = LogKind.WORKOUT, status = LogStatus.PLANNED,
            title = "Walk", durationMin = 20, occurredAt = LocalDateTime(today, planned.occurredAt.time),
            source = LifeLog.SOURCE_PLAN, notes = "Lighter today because you slept $sleepText",
        )
        logs.save(walk)
        val swap = WorkoutSwap(planned.id, planned.date, target, walk.id)
        settings.putString(swapKey(today), listOf(swap.originalId, swap.originalDate, swap.movedTo, swap.walkId).joinToString("|"))
        PostHogAnalytics.capture("v4_coach_swap", mapOf("action" to "swap"))
        return swap
    }

    suspend fun undoSwap(swap: WorkoutSwap) {
        logs.getById(swap.originalId)?.let { moveTo(it, swap.originalDate, null) }
        logs.delete(swap.walkId)
        settings.putString(swapKey(today()), "undone")
        PostHogAnalytics.capture("v4_coach_swap", mapOf("action" to "undo"))
    }

    /** Today's swap if one happened, "undone" as null. */
    fun swapToday(): WorkoutSwap? = settings.getStringOrNull(swapKey(today()))?.split('|')?.takeIf { it.size == 4 }?.let {
        runCatching { WorkoutSwap(it[0], LocalDate.parse(it[1]), LocalDate.parse(it[2]), it[3]) }.getOrNull()
    }

    fun swapDecidedToday(): Boolean = settings.hasKey(swapKey(today()))

    fun keepToday() {
        settings.putString(swapKey(today()), "kept")
        PostHogAnalytics.capture("v4_coach_swap", mapOf("action" to "keep"))
    }

    // ── Health ───────────────────────────────────────────────────────────────

    /**
     * Brings watch workouts in as logs so every area counts them. Skips ours, ones already
     * imported (even if the user deleted them since), and ones that match a workout logged here.
     */
    suspend fun importFromHealth(force: Boolean = false): Int {
        val p = prefs.state.value
        if (!p.health || !p.isOn(DataFlow.WORKOUTS)) return 0
        if (!force && nowMs() - lastImportMs < 5 * 60_000L) return 0
        lastImportMs = nowMs()
        val found = runCatching { health.readWorkouts(14) }.getOrDefault(emptyList())
        if (found.isEmpty()) return 0
        val mine = logs.getInRange(today().minus(DatePeriod(days = 15)), today())
            .filter { FitnessWeek.isWorkout(it) && it.status == LogStatus.DONE }
            .map { it.occurredAt.toInstant(tz).toEpochMilliseconds() }
        val fresh = found.filter { w ->
            !w.fromThisApp &&
                mine.none { kotlin.math.abs(it - w.startEpochMs) < 3 * 60_000L } &&
                !logs.hasExternalId(healthId(w.id))
        }.map { w ->
            LifeLog(
                id = newId(), area = PlanArea.FITNESS, kind = LogKind.WORKOUT,
                title = w.title?.takeIf { it.isNotBlank() } ?: w.kind.label,
                durationMin = max(1, w.minutes),
                occurredAt = Instant.fromEpochMilliseconds(w.startEpochMs).toLocalDateTime(tz),
                source = LifeLog.SOURCE_HEALTH, externalId = healthId(w.id),
            )
        }
        logs.saveAll(fresh)
        if (fresh.isNotEmpty()) Logger.d("WorkoutService") { "Imported ${fresh.size} workouts from Health" }
        return fresh.size
    }

    suspend fun canWriteHealth(): Boolean {
        val p = prefs.state.value
        return p.health && p.isOn(DataFlow.WORKOUTS) && runCatching { health.canWriteWorkouts() }.getOrDefault(false)
    }

    private suspend fun writeToHealth(log: LifeLog, kind: WorkoutKind, startMs: Long, endMs: Long): Boolean {
        if (!canWriteHealth()) return false
        return runCatching { health.writeWorkout(kind, log.title, startMs, endMs, "lp-${log.id}") }.getOrDefault(false)
    }

    private fun calendarOut(): Boolean = prefs.state.value.let { it.calendar && it.isOn(DataFlow.EVENTS_OUT) }

    suspend fun canAddToCalendar(): Boolean = calendarOut() && runCatching { calendar.canWrite() }.getOrDefault(false)

    private fun readActive(): ActiveWorkout? = settings.getStringOrNull(KEY_ACTIVE)?.split('|', limit = 4)?.takeIf { it.size == 4 }?.let {
        runCatching { ActiveWorkout(it[0].toLong(), WorkoutKind.valueOf(it[1]), it[3], it[2].ifEmpty { null }) }.getOrNull()
    }

    private fun newId() = Uuid.random().toString()
    private fun healthId(id: String) = "health:$id"
    private fun eventKey(logId: String) = "v4_cal_event_$logId"
    private fun swapKey(d: LocalDate) = "v4_swap_$d"

    companion object {
        private const val KEY_ACTIVE = "v4_workout_active"

        /** Planned with no time: stored at midnight and shown as "Any". */
        val ANY_TIME = LocalTime(0, 0)

        fun hasTime(log: LifeLog) = log.occurredAt.time != ANY_TIME
    }
}
