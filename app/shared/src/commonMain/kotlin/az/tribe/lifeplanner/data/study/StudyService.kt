package az.tribe.lifeplanner.data.study

import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.service.StudyKind
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
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

/** A study timer running now. Kept in settings, so it survives leaving the screen or the app. */
data class ActiveStudy(val startEpochMs: Long, val subject: String, val blockId: String?, val targetMin: Int)

/** A date the AI found in a pasted syllabus, for the user to keep or drop before anything is saved. */
data class FoundDate(val title: String, val kind: StudyKind, val date: LocalDate)

/**
 * The Study timer and the syllabus reader. The timer is separate from the v3 focus screen because
 * that one needs a goal and a step; here all it needs is a subject. Its time is saved as a
 * session, or, when started from a planned block, ticks that block with the real minutes.
 */
@OptIn(ExperimentalUuidApi::class)
class StudyService(
    private val logs: LifeLogRepository,
    private val settings: Settings,
    private val ai: AiProxyService,
) {
    private val tz = TimeZone.currentSystemDefault()

    private val _active = MutableStateFlow(readActive())
    val active: StateFlow<ActiveStudy?> = _active.asStateFlow()

    fun start(subject: String, blockId: String? = null, targetMin: Int = 25) {
        val a = ActiveStudy(Clock.System.now().toEpochMilliseconds(), subject.trim().ifEmpty { "Study" }, blockId, targetMin)
        settings.putString(KEY_ACTIVE, listOf(a.startEpochMs, a.targetMin, a.blockId ?: "", a.subject).joinToString("|"))
        _active.value = a
        PostHogAnalytics.capture("v4_study_timer_started", mapOf("from_block" to (blockId != null), "target" to targetMin))
    }

    fun cancel() {
        settings.remove(KEY_ACTIVE)
        _active.value = null
    }

    /** Stops and saves. Returns the minutes saved, or 0 if it ran under a minute and was dropped. */
    suspend fun stop(): Int {
        val a = _active.value ?: return 0
        cancel()
        val minutes = ((Clock.System.now().toEpochMilliseconds() - a.startEpochMs) / 60_000L).toInt()
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
        PostHogAnalytics.capture("v4_study_timer_saved", mapOf("minutes" to minutes, "from_block" to (block != null)))
        return minutes
    }

    private fun readActive(): ActiveStudy? = settings.getStringOrNull(KEY_ACTIVE)?.split('|', limit = 4)?.takeIf { it.size == 4 }?.let {
        val start = it[0].toLongOrNull() ?: return null
        ActiveStudy(start, it[3], it[2].ifEmpty { null }, it[1].toIntOrNull() ?: 25)
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
    }
}
