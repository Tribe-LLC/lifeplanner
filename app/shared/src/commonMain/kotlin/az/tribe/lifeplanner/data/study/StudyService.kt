package az.tribe.lifeplanner.data.study

import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.fitness.WorkoutService
import az.tribe.lifeplanner.data.habits.Nudge
import az.tribe.lifeplanner.data.habits.NudgePlan
import az.tribe.lifeplanner.data.plans.PlanService
import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.service.StudyKind
import az.tribe.lifeplanner.domain.service.StudyPlanner
import az.tribe.lifeplanner.notification.NudgeAlarms
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A study timer running now. Kept in settings, so it survives leaving the screen or the app.
 * While paused, [pausedAtMs] is when it stopped; [pausedTotalMs] is all the earlier pauses, so the
 * time studied never counts a break.
 */
data class ActiveStudy(
    val startEpochMs: Long,
    val subject: String,
    val blockId: String?,
    val targetMin: Int,
    val pausedAtMs: Long? = null,
    val pausedTotalMs: Long = 0,
) {
    val paused: Boolean get() = pausedAtMs != null

    fun elapsedMs(nowMs: Long): Long = ((pausedAtMs ?: nowMs) - startEpochMs - pausedTotalMs).coerceAtLeast(0)

    fun pausedAt(nowMs: Long): ActiveStudy = if (paused) this else copy(pausedAtMs = nowMs)

    fun resumedAt(nowMs: Long): ActiveStudy =
        pausedAtMs?.let { copy(pausedAtMs = null, pausedTotalMs = pausedTotalMs + (nowMs - it).coerceAtLeast(0)) } ?: this

    /** When the aimed-for minutes are reached, if the timer is running and that is still ahead. */
    fun targetAtMs(nowMs: Long): Long? = if (paused) null else (nowMs + targetMin * 60_000L - elapsedMs(nowMs)).takeIf { it > nowMs }

    /** Where a stopwatch would have started had there been no pauses: what a chronometer counts from. */
    val chronoBaseMs: Long get() = startEpochMs + pausedTotalMs
}

/** A date the AI found in a pasted syllabus, for the user to keep or drop before anything is saved. */
data class FoundDate(val title: String, val kind: StudyKind, val date: LocalDate)

/**
 * The Study timer, repeating blocks and the syllabus reader. The timer is separate from the v3
 * focus screen because that one needs a goal and a step; here all it needs is a subject. Its time
 * is saved as a session, or, when started from a planned block, ticks that block with the real
 * minutes. It can pause, here or from its notification, and paused time never counts.
 */
@OptIn(ExperimentalUuidApi::class)
class StudyService(
    private val logs: LifeLogRepository,
    private val settings: Settings,
    private val ai: AiProxyService,
    private val plans: PlanService,
) {
    private val tz = TimeZone.currentSystemDefault()
    private fun nowMs() = Clock.System.now().toEpochMilliseconds()

    private val _active = MutableStateFlow(readActive())
    val active: StateFlow<ActiveStudy?> = _active.asStateFlow()

    init {
        // A timer left running shows its notification again (after an update or a restart).
        _active.value?.let { runCatching { StudyTimerNotice.show(it) } }
    }

    fun start(subject: String, blockId: String? = null, targetMin: Int = 25) {
        val a = ActiveStudy(nowMs(), subject.trim().ifEmpty { "Study" }, blockId, targetMin)
        write(a)
        PostHogAnalytics.capture("v4_study_timer_started", mapOf("from_block" to (blockId != null), "target" to targetMin))
    }

    fun pause() {
        val a = _active.value ?: return
        if (a.paused) return
        write(a.pausedAt(nowMs()))
        PostHogAnalytics.capture("v4_study_timer_paused", mapOf("minutes" to a.elapsedMs(nowMs()) / 60_000))
    }

    fun resume() {
        val a = _active.value ?: return
        if (!a.paused) return
        write(a.resumedAt(nowMs()))
        PostHogAnalytics.capture("v4_study_timer_resumed")
    }

    fun cancel() {
        settings.remove(KEY_ACTIVE)
        settings.remove(KEY_PAUSE)
        _active.value = null
        runCatching { StudyTimerNotice.clear() }
        runCatching { NudgeAlarms.cancel(TARGET_ID) }
    }

    /**
     * Stops and saves. Returns the minutes saved, or 0 if it ran under a minute and was dropped.
     * Called from the page and from the notification's Stop, so both save the same way.
     */
    suspend fun stop(fromNotification: Boolean = false): Int {
        val a = _active.value ?: return 0
        val minutes = (a.elapsedMs(nowMs()) / 60_000L).toInt()
        cancel()
        if (minutes < 1) return 0
        val block = a.blockId?.let { logs.getById(it) }
        if (block != null) {
            logs.save(block.copy(status = LogStatus.DONE, durationMin = minutes))
        } else {
            logs.save(
                LifeLog(
                    id = Uuid.random().toString(), area = PlanArea.STUDY, kind = LogKind.STUDY, title = a.subject,
                    category = StudyKind.SESSION.key, durationMin = minutes,
                    occurredAt = Instant.fromEpochMilliseconds(a.startEpochMs).toLocalDateTime(tz), source = LifeLog.SOURCE_TIMER,
                ),
            )
        }
        if (fromNotification) runCatching { StudyTimerNotice.saved(a.subject, minutes) }
        PostHogAnalytics.capture("v4_study_timer_saved", mapOf("minutes" to minutes, "from_block" to (block != null), "from_notification" to fromNotification))
        return minutes
    }

    private fun write(a: ActiveStudy) {
        settings.putString(KEY_ACTIVE, listOf(a.startEpochMs, a.targetMin, a.blockId ?: "", a.subject).joinToString("|"))
        if (a.pausedAtMs != null || a.pausedTotalMs > 0) settings.putString(KEY_PAUSE, "${a.pausedAtMs ?: ""}|${a.pausedTotalMs}")
        else settings.remove(KEY_PAUSE)
        _active.value = a
        runCatching { StudyTimerNotice.show(a) }
        // A heads-up when the aimed-for minutes are up; moved by a pause, gone once stopped.
        runCatching {
            NudgeAlarms.cancel(TARGET_ID)
            a.targetAtMs(nowMs())?.let { at ->
                NudgeAlarms.schedule(
                    Nudge(
                        TARGET_ID, "That is your ${a.targetMin} minutes", "Keep going, or stop and save ${a.subject}.",
                        Instant.fromEpochMilliseconds(at).toLocalDateTime(tz), NudgePlan.STUDY,
                    ),
                )
            }
        }
    }

    private fun readActive(): ActiveStudy? = settings.getStringOrNull(KEY_ACTIVE)?.split('|', limit = 4)?.takeIf { it.size == 4 }?.let {
        val start = it[0].toLongOrNull() ?: return null
        val pause = settings.getStringOrNull(KEY_PAUSE)?.split('|')
        ActiveStudy(
            start, it[3], it[2].ifEmpty { null }, it[1].toIntOrNull() ?: 25,
            pausedAtMs = pause?.getOrNull(0)?.toLongOrNull(),
            pausedTotalMs = pause?.getOrNull(1)?.toLongOrNull() ?: 0,
        )
    }

    // ── Repeating blocks ─────────────────────────────────────────────────────

    /**
     * Saves "[subject] every [days], [time], [minutes] min" and plans its blocks for the next days.
     * The repeat row itself is never a plan: Today, rollover and the calendar only ever see blocks.
     */
    suspend fun addRepeat(subject: String, days: Set<DayOfWeek>, time: LocalTime?, minutes: Int, toCalendar: Boolean) {
        val name = subject.trim().ifEmpty { return }
        if (days.isEmpty()) return
        val now = Clock.System.now().toLocalDateTime(tz)
        val through = StudyPlanner.firstFilledThrough(now.date, now.time, time)
        val repeat = LifeLog(
            id = Uuid.random().toString(), area = PlanArea.STUDY, kind = LogKind.STUDY, status = LogStatus.PLANNED,
            title = name, category = StudyKind.ROUTINE.key, durationMin = minutes,
            occurredAt = LocalDateTime(through, time ?: WorkoutService.ANY_TIME),
            notes = StudyPlanner.daysNote(days) + if (toCalendar) "\n$CALENDAR_NOTE" else "",
        )
        logs.save(repeat)
        fill(repeat, now.date)
        PostHogAnalytics.capture("v4_study_repeat_created", mapOf("days" to days.size, "minutes" to minutes, "timed" to (time != null), "calendar" to toCalendar))
    }

    /** Stops a repeat: its blocks from today on go, the ones already done stay. */
    suspend fun stopRepeat(repeat: LifeLog) {
        val today = Clock.System.todayIn(tz)
        logs.getInRange(today, today.plus(DatePeriod(days = StudyPlanner.REPEAT_DAYS + 7)))
            .filter { it.externalId == repeat.id && it.status == LogStatus.PLANNED }
            .forEach { plans.remove(it) }
        logs.delete(repeat.id)
        PostHogAnalytics.capture("v4_study_repeat_stopped")
    }

    /**
     * Keeps every repeat's next [StudyPlanner.REPEAT_DAYS] days planned. Safe to call as often as
     * wanted: each day is made once, so a moved or removed block stays moved or removed.
     */
    suspend fun fillRepeats() {
        val today = Clock.System.todayIn(tz)
        val repeats = runCatching { logs.getInRange(today.minus(DatePeriod(days = 3650)), today.plus(DatePeriod(days = 60))) }
            .getOrDefault(emptyList()).filter { StudyPlanner.isRepeat(it) }
        repeats.forEach { runCatching { fill(it, today) } }
    }

    private suspend fun fill(repeat: LifeLog, today: LocalDate) {
        val days = StudyPlanner.datesToFill(StudyPlanner.repeatDays(repeat), repeat.date, today)
        if (days.isEmpty()) return
        val time = repeat.occurredAt.time
        val blocks = days.map { d ->
            LifeLog(
                id = StudyPlanner.repeatBlockId(repeat.id, d), area = PlanArea.STUDY, kind = LogKind.STUDY, title = repeat.title,
                category = StudyKind.BLOCK.key, durationMin = repeat.durationMin ?: 45, occurredAt = LocalDateTime(d, time), externalId = repeat.id,
            )
        }.filter { logs.getById(it.id) == null }
        val toCalendar = repeat.notes?.lines()?.any { it == CALENDAR_NOTE } == true && time != WorkoutService.ANY_TIME
        if (blocks.isNotEmpty()) plans.planAll(blocks, toCalendar) { "Study: ${it.title}" }
        logs.save(repeat.copy(occurredAt = LocalDateTime(days.last(), time)))
    }

    /**
     * Reads exam and deadline dates out of pasted syllabus or timetable text. Nothing is saved
     * here: the page shows what was found and the user keeps what is right.
     */
    suspend fun readSyllabus(text: String): List<FoundDate>? {
        if (text.isBlank()) return emptyList()
        val today = Clock.System.todayIn(tz)
        val prompt = """
            Below is text a student pasted from a course syllabus, a timetable, or an email. List every
            exam, test, quiz, assignment, essay, project or other deadline in it that has a date.
            Today is $today. Resolve dates without a year to the next time that date comes, on or after
            today. Skip anything with no date. Keep each title short, with the subject, like
            "Biology midterm" or "History essay". Use kind "exam" for exams, tests and quizzes, and
            "deadline" for everything that is handed in. Do not invent anything that is not in the text.

            Text:
            ${text.take(12_000)}
        """.trimIndent()
        return try {
            val raw = ai.generateStructuredJson(prompt, schema())
            val items = Json.parseToJsonElement(raw).jsonObject["items"]?.jsonArray ?: return emptyList()
            items.mapNotNull { e ->
                val o = e.jsonObject
                val title = o["title"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val date = o["date"]?.jsonPrimitive?.contentOrNull?.let { runCatching { LocalDate.parse(it.trim().take(10)) }.getOrNull() } ?: return@mapNotNull null
                val kind = if (o["kind"]?.jsonPrimitive?.contentOrNull == "exam") StudyKind.EXAM else StudyKind.DEADLINE
                FoundDate(title, kind, date)
            }.filter { it.date >= today }.distinctBy { it.title.lowercase() to it.date }.sortedBy { it.date }
                .also { PostHogAnalytics.capture("v4_syllabus_read", mapOf("found" to it.size)) }
        } catch (e: Exception) {
            Logger.w("StudyService") { "Syllabus read failed: ${e.message}" }
            null
        }
    }

    private fun schema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("items") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("title") { put("type", "string") }
                        putJsonObject("kind") {
                            put("type", "string")
                            putJsonArray("enum") { add(kotlinx.serialization.json.JsonPrimitive("exam")); add(kotlinx.serialization.json.JsonPrimitive("deadline")) }
                        }
                        putJsonObject("date") { put("type", "string"); put("description", "YYYY-MM-DD") }
                    }
                    putJsonArray("required") {
                        add(kotlinx.serialization.json.JsonPrimitive("title")); add(kotlinx.serialization.json.JsonPrimitive("kind")); add(kotlinx.serialization.json.JsonPrimitive("date"))
                    }
                }
            }
        }
        putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("items")) }
    }

    companion object {
        private const val KEY_ACTIVE = "v4_study_active"
        private const val KEY_PAUSE = "v4_study_active_pause"
        private const val TARGET_ID = "v4_study_target"
        private const val CALENDAR_NOTE = "calendar: yes"
    }
}
