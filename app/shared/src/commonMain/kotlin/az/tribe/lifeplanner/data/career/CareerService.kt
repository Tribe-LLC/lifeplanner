package az.tribe.lifeplanner.data.career

import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.data.plans.PlanService
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.service.CareerKind
import az.tribe.lifeplanner.domain.service.CareerPlanner
import az.tribe.lifeplanner.domain.service.Stage
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Career's writes: wins, applications and their stages, interviews (as plans, so they can go on
 * the calendar), people to keep in touch with, and skills. Today ticks next actions through
 * [complete], so an application moves the same way from either page.
 */
@OptIn(ExperimentalUuidApi::class)
class CareerService(
    private val logs: LifeLogRepository,
    private val plans: PlanService,
    private val settings: Settings,
    private val ai: AiProxyService,
) {
    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)
    private fun now() = Clock.System.now().toLocalDateTime(tz)
    private fun day(d: LocalDate) = LocalDateTime(d, LocalTime(0, 0))

    private val _searching = MutableStateFlow(settings.getBoolean(KEY_SEARCHING, false))
    /** Looking for a job (applications first) or growing where they are (wins and skills first). */
    val searching: StateFlow<Boolean> = _searching

    fun setSearching(on: Boolean) {
        settings.putBoolean(KEY_SEARCHING, on)
        _searching.value = on
    }

    private fun row(kind: CareerKind, title: String, at: LocalDateTime, status: LogStatus = LogStatus.PLANNED, notes: String? = null, quantity: Double? = null) =
        LifeLog(
            id = Uuid.random().toString(), area = PlanArea.CAREER, kind = LogKind.NOTE, status = status,
            title = title.trim(), category = kind.key, occurredAt = at, notes = notes, quantity = quantity,
        )

    // ── Wins ──

    suspend fun logWin(what: String, impact: String?, date: LocalDate = today()) {
        val at = if (date == today()) now() else LocalDateTime(date, LocalTime(12, 0))
        logs.save(row(CareerKind.WIN, what, at, LogStatus.DONE, impact?.trim()?.ifEmpty { null }))
    }

    suspend fun remove(log: LifeLog) = plans.remove(log)

    // ── Applications ──

    suspend fun addApplication(role: String, company: String, link: String?, stage: Stage, location: String? = null, closes: LocalDate? = null) {
        var notes = CareerPlanner.withField(null, "company", company)
        notes = CareerPlanner.withField(notes, "link", link)
        notes = CareerPlanner.withField(notes, "location", location)
        notes = CareerPlanner.withField(notes, "closes", closes?.toString())
        notes = CareerPlanner.withField(notes, "stage", stage.name)
        if (stage != Stage.SAVED) notes = CareerPlanner.withField(notes, "applied", today().toString())
        else notes = CareerPlanner.withField(notes, "saved", today().toString())
        logs.save(row(CareerKind.APPLICATION, role, day(nextFor(stage))).copy(notes = notes))
    }

    suspend fun updateApplication(app: LifeLog, role: String, company: String, link: String?, location: String? = null, closes: LocalDate? = null) {
        var notes = CareerPlanner.withField(app.notes, "company", company)
        notes = CareerPlanner.withField(notes, "link", link)
        notes = CareerPlanner.withField(notes, "location", location)
        notes = CareerPlanner.withField(notes, "closes", closes?.toString())
        logs.save(app.copy(title = role.trim().ifEmpty { app.title }, notes = notes))
    }

    /** Moves an application on. Its next action date follows the stage. */
    suspend fun setStage(app: LifeLog, stage: Stage, reason: String? = null) {
        var notes = CareerPlanner.withField(app.notes, "stage", stage.name)
        if (stage == Stage.APPLIED && CareerPlanner.applied(app) == null) notes = CareerPlanner.withField(notes, "applied", today().toString())
        if (stage == Stage.CLOSED) notes = CareerPlanner.withField(notes, "closed", reason)
        // What it reached, so the search funnel still counts it once it closes.
        val replied = stage == Stage.INTERVIEW || stage == Stage.OFFER || (stage == Stage.CLOSED && reason in CareerPlanner.REPLY_REASONS)
        if (replied && CareerPlanner.repliedOn(app) == null) notes = CareerPlanner.withField(notes, "replied", today().toString())
        if (stage == Stage.INTERVIEW) notes = CareerPlanner.withField(notes, "interviewed", "yes")
        if (stage == Stage.OFFER || reason == CareerPlanner.ACCEPTED) notes = CareerPlanner.withField(notes, "offer", "yes")
        logs.save(
            app.copy(
                notes = notes,
                status = if (stage == Stage.CLOSED) LogStatus.DONE else LogStatus.PLANNED,
                occurredAt = if (stage == Stage.CLOSED) app.occurredAt else day(nextFor(stage)),
            )
        )
    }

    private fun nextFor(stage: Stage): LocalDate = today().plus(
        DatePeriod(days = when (stage) { Stage.SAVED -> 0; Stage.APPLIED -> CareerPlanner.FOLLOW_UP_DAYS; Stage.INTERVIEW, Stage.OFFER -> 2; Stage.CLOSED -> 0 })
    )

    suspend fun scheduleInterview(app: LifeLog, date: LocalDate, time: LocalTime, minutes: Int, addToCalendar: Boolean) {
        val who = CareerPlanner.company(app) ?: app.title
        val interview = row(CareerKind.INTERVIEW, "Interview: $who", LocalDateTime(date, time)).copy(externalId = app.id, durationMin = minutes)
        plans.plan(interview, addToCalendar, "Interview: ${CareerPlanner.roleLine(app)}")
        var notes = CareerPlanner.withField(app.notes, "interviewed", "yes")
        if (CareerPlanner.repliedOn(app) == null) notes = CareerPlanner.withField(notes, "replied", today().toString())
        if (CareerPlanner.stage(app) != Stage.OFFER) notes = CareerPlanner.withField(notes, "stage", Stage.INTERVIEW.name)
        logs.save(app.copy(notes = notes, occurredAt = day(date.plus(DatePeriod(days = 1)))))
    }

    /** Ticking a next action, from this page or from Today. */
    suspend fun complete(log: LifeLog) {
        val today = today()
        when (CareerKind.of(log)) {
            CareerKind.APPLICATION -> when (CareerPlanner.stage(log)) {
                Stage.SAVED -> setStage(log, Stage.APPLIED)
                Stage.APPLIED -> logs.save(log.copy(occurredAt = day(today.plus(DatePeriod(days = CareerPlanner.FOLLOW_UP_DAYS))), notes = CareerPlanner.withField(log.notes, "followed", today.toString())))
                Stage.INTERVIEW, Stage.OFFER -> logs.save(log.copy(occurredAt = day(today.plus(DatePeriod(days = 3)))))
                Stage.CLOSED -> {}
            }
            CareerKind.INTERVIEW -> plans.setDone(log, log.status != LogStatus.DONE)
            CareerKind.CONTACT -> talked(log)
            else -> {}
        }
    }

    // ── People ──

    suspend fun addContact(name: String, about: String?, everyDays: Int) {
        logs.save(row(CareerKind.CONTACT, name, day(today().plus(DatePeriod(days = everyDays))), notes = CareerPlanner.withField(null, "about", about), quantity = everyDays.toDouble()))
    }

    /**
     * A catch-up happened. Moves the next one on, and keeps it in the person's history with [about],
     * what it was about, when given.
     */
    suspend fun talked(contact: LifeLog, about: String? = null) {
        val every = contact.quantity?.toInt()?.coerceAtLeast(1) ?: 30
        logs.save(contact.copy(occurredAt = day(today().plus(DatePeriod(days = every))), notes = CareerPlanner.withField(contact.notes, "last", today().toString())))
        logs.save(
            row(CareerKind.TALK, "Talked with ${contact.title.trim()}", now(), LogStatus.DONE, about?.trim()?.ifEmpty { null })
                .copy(externalId = contact.id),
        )
    }

    suspend fun setCadence(contact: LifeLog, everyDays: Int) {
        val last = CareerPlanner.lastTalked(contact) ?: today()
        logs.save(contact.copy(quantity = everyDays.toDouble(), occurredAt = day(maxOf(today(), last.plus(DatePeriod(days = everyDays))))))
    }

    // ── Skills ──

    suspend fun addSkill(name: String, level: Int, want: Int) {
        logs.save(row(CareerKind.SKILL, name, now(), quantity = level.toDouble(), notes = CareerPlanner.withField(null, "want", want.toString())))
    }

    suspend fun setSkill(skill: LifeLog, level: Int, want: Int) {
        logs.save(skill.copy(quantity = level.toDouble(), notes = CareerPlanner.withField(skill.notes, "want", want.toString())))
    }

    // ── Friday wins ──

    private val _fridayClosed = MutableStateFlow(settings.getStringOrNull(KEY_FRIDAY)?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
    /** The week (its Monday) the Friday wins prompt was saved or dismissed for. */
    val fridayClosed: StateFlow<LocalDate?> = _fridayClosed

    fun closeFriday() {
        val week = CareerPlanner.weekStart(today())
        settings.putString(KEY_FRIDAY, week.toString())
        _fridayClosed.value = week
    }

    // ── Reading a shared job ──

    /**
     * Reads role, company, link, place and closing date out of a job ad or link someone shared or
     * pasted. Nothing is saved: the sheet fills in and stays editable. Null when the coach could not
     * be reached; the link is still taken from the text then.
     */
    suspend fun readJob(text: String): CareerPlanner.JobDraft? {
        if (text.isBlank()) return null
        val today = today()
        val prompt = """
            Below is a job ad, or a link to one, that someone shared to save it as a job they might
            apply for. Find the job title (role), the company, the link to the ad, where the job is
            (city, country or "Remote"), and the closing date to apply by, if one is given.
            Today is $today. Resolve a closing date without a year to the next time that date comes.
            When only a link is given, read what you can from its words, like the company in the
            address and the title in the path. Leave anything you cannot find empty. Keep the role
            and company short, as they would appear on a list. Do not invent anything.

            Shared text:
            ${text.take(8_000)}
        """.trimIndent()
        return try {
            CareerPlanner.parseJob(ai.generateStructuredJson(prompt, jobSchema()), text, today)
        } catch (e: Exception) {
            Logger.w("CareerService") { "Job read failed: ${e.message}" }
            null
        }
    }

    private fun jobSchema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            listOf("role", "company", "link", "location").forEach { k -> putJsonObject(k) { put("type", "string") } }
            putJsonObject("closing_date") { put("type", "string"); put("description", "YYYY-MM-DD, or empty") }
        }
        putJsonArray("required") { listOf("role", "company", "link", "location", "closing_date").forEach { add(JsonPrimitive(it)) } }
    }

    companion object {
        private const val KEY_SEARCHING = "v4_career_searching"
        private const val KEY_FRIDAY = "v4_career_friday_closed"
    }
}
