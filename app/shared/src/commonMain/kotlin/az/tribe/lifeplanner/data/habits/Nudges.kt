package az.tribe.lifeplanner.data.habits

import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.domain.service.HabitLearning
import az.tribe.lifeplanner.domain.service.HabitSchedule
import az.tribe.lifeplanner.notification.NudgeAlarms
import az.tribe.lifeplanner.ui.v4.shell.V4Routes
import az.tribe.lifeplanner.util.DeepLinkNavigator
import com.russhwolf.settings.Settings
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** A notification the app sends by itself, at one moment, opening one place. */
data class Nudge(val id: String, val title: String, val body: String, val at: LocalDateTime, val open: String)

/**
 * The only two nudges the app sends by itself, and they stay quiet when there is nothing to do:
 *
 * - Evening check-in, at the time the user usually wraps up (learned), only while habits are open.
 *   Planned a week ahead, so it goes quiet by itself when the app stops being opened.
 * - Slipped habits, Saturday 10:00, at most once a week, only when something slipped.
 *
 * Replanned whenever habits change, so today's count is current whenever the app has been used.
 *
 * A third one is opt-in, on the Sleep and mind page: a daily mood check-in, at a set time or a
 * surprise time, planned by MoodNudges.
 */
object NudgePlan {
    const val CHECK_IN = "checkin"
    const val REVIEW = "review"
    /** The Study timer's "your minutes are up" notice, and its running notification. Opens Study. */
    const val STUDY = "study"
    /** The daily mood check-in reminder. Opens Sleep and mind, and answers Low / Okay / Good from the notification on both platforms. */
    const val MOOD = "mood"
    const val DAYS_AHEAD = 7
    private const val CHECK_IN_ID = "v4_nudge_checkin_"
    const val SLIP_ID = "v4_nudge_slip"
    private val SLIP_TIME = LocalTime(10, 0)

    fun checkInIds(): List<String> = (0 until DAYS_AHEAD).map { "$CHECK_IN_ID$it" }

    /** The habits the check-in deck would show on [date]. */
    private fun openOn(rows: List<HabitRow>, date: LocalDate, today: LocalDate): List<HabitRow> =
        if (date == today) rows.filter { it.stats.dueToday && !it.doneToday && !it.stats.skippedToday && it.habit.healthMetricType == null }
        else rows.filter { it.habit.healthMetricType == null && date !in it.skipped && HabitSchedule.isScheduled(it.schedule, date) }

    fun checkIns(rows: List<HabitRow>, now: LocalDateTime, minute: Int): List<Nudge> {
        val today = now.date
        val time = LocalTime(minute / 60, minute % 60)
        return (0 until DAYS_AHEAD).mapNotNull { d ->
            val date = today.plus(DatePeriod(days = d))
            if (d == 0 && now.time >= time) return@mapNotNull null
            val open = openOn(rows, date, today)
            if (open.isEmpty()) return@mapNotNull null
            val body = if (d == 0) {
                val n = open.size
                "$n habit${if (n == 1) "" else "s"} still open. Swipe through ${if (n == 1) "it" else "them"} in a minute."
            } else "Anything left today? Swipe through it in a minute."
            Nudge("$CHECK_IN_ID$d", "Evening check-in", body, LocalDateTime(date, time), CHECK_IN)
        }
    }

    /** The next Saturday 10:00 from [now], today included when it is still ahead. */
    fun nextSaturday(now: LocalDateTime): LocalDateTime {
        var d = now.date
        while (d.dayOfWeek != DayOfWeek.SATURDAY || (d == now.date && now.time >= SLIP_TIME)) d = d.plus(DatePeriod(days = 1))
        return LocalDateTime(d, SLIP_TIME)
    }

    /**
     * The weekly slip nudge, or null. [lastSent] is the day the last one was planned for: a new one
     * is planned only 7 days after it, but a planned one still ahead is kept, with a current text.
     */
    fun slip(rows: List<HabitRow>, now: LocalDateTime, lastSent: LocalDate?): Nudge? {
        val slipped = rows.filter { it.slip != null }
        if (slipped.isEmpty()) return null
        val at = if (lastSent != null && lastSent >= now.date) LocalDateTime(lastSent, SLIP_TIME) else nextSaturday(now)
        if (lastSent != null && lastSent < now.date && at.date < lastSent.plus(DatePeriod(days = 7))) return null
        if (at <= now) return null
        val names = slipped.take(2).joinToString(" and ") { it.habit.title }
        val title = if (slipped.size == 1) "${slipped[0].habit.title} has slipped" else "${slipped.size} habits have slipped"
        val body = if (slipped.size == 1) "Keep it, make it easier, or let it go. It takes a minute."
            else "$names${if (slipped.size > 2) " and more" else ""}. Keep them, make them easier, or let them go."
        return Nudge(SLIP_ID, title, body, at, REVIEW)
    }

    // ── Mood check-in reminder ─────────────────────────────────────────────

    private const val MOOD_ID = "v4_nudge_mood_"
    /** "Surprise me" picks a time in this window, a new one each day. */
    val SURPRISE_FROM = LocalTime(10, 0)
    val SURPRISE_TO = LocalTime(20, 0)
    const val MOOD_TITLE = "How are you today?"
    const val MOOD_BODY = "Low, okay or good? One tap is enough."

    /** One id per weekday: a week ahead fits exactly, and today's is always easy to find. */
    fun moodId(date: LocalDate): String = "$MOOD_ID${date.dayOfWeek.name.lowercase()}"
    fun moodIds(): List<String> = DayOfWeek.entries.map { "$MOOD_ID${it.name.lowercase()}" }

    /**
     * The surprise minute of the day for [date]: somewhere in the window, on a 5-minute step, the
     * same every time it is asked for the same day and [seed], so replanning never moves it.
     */
    fun surpriseMinute(date: LocalDate, seed: Int): Int {
        val from = SURPRISE_FROM.hour * 60 + SURPRISE_FROM.minute
        val slots = ((SURPRISE_TO.hour * 60 + SURPRISE_TO.minute) - from) / 5 + 1
        var x = date.toEpochDays().toLong() * 6364136223846793005L + seed.toLong() * 1442695040888963407L
        x = x xor (x ushr 33)
        x *= -0x61c8864680b583ebL
        x = x xor (x ushr 29)
        return from + x.mod(slots.toLong()).toInt() * 5
    }

    /**
     * The mood reminders for the next [DAYS_AHEAD] days. [minute] is the fixed time of day, or null
     * for a surprise time. Today is left out once it has passed or a mood is already recorded.
     */
    fun moods(now: LocalDateTime, minute: Int?, seed: Int, recordedToday: Boolean): List<Nudge> =
        (0 until DAYS_AHEAD).mapNotNull { d ->
            val date = now.date.plus(DatePeriod(days = d))
            val m = minute ?: surpriseMinute(date, seed)
            val at = LocalDateTime(date, LocalTime(m / 60, m % 60))
            if (d == 0 && (recordedToday || at <= now)) return@mapNotNull null
            Nudge(moodId(date), MOOD_TITLE, MOOD_BODY, at, MOOD)
        }

    fun routeFor(open: String): String? = when (open) {
        CHECK_IN -> V4Routes.CHECK_IN
        REVIEW -> V4Routes.REVIEW
        STUDY -> V4Routes.area(az.tribe.lifeplanner.domain.model.PlanArea.STUDY)
        MOOD -> V4Routes.area(az.tribe.lifeplanner.domain.model.PlanArea.MIND)
        else -> null
    }

    /** A tapped nudge: opens its place and counts it. */
    fun opened(open: String) {
        val route = routeFor(open) ?: return
        PostHogAnalytics.capture("v4_nudge_opened", mapOf("kind" to open))
        DeepLinkNavigator.navigate(route)
    }
}

/** What the user chose for the two nudges. */
class NudgePrefs(private val settings: Settings) {
    data class Snapshot(
        val evening: Boolean,
        val eveningMinute: Int?,
        val slipped: Boolean,
        /** The daily mood check-in reminder, off unless the user turns it on. */
        val mood: Boolean = false,
        /** Minute of the day, or null for "Surprise me". */
        val moodMinute: Int? = DEFAULT_MOOD_MINUTE,
    )

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun setEvening(on: Boolean) = write { settings.putBoolean(KEY_EVENING, on) }
    /** Null follows the learned time. */
    fun setEveningMinute(minute: Int?) = write { settings.putInt(KEY_EVENING_MINUTE, minute ?: -1) }
    fun setSlipped(on: Boolean) = write { settings.putBoolean(KEY_SLIPPED, on) }
    fun setMood(on: Boolean) = write { settings.putBoolean(KEY_MOOD, on) }
    /** Null is "Surprise me". */
    fun setMoodMinute(minute: Int?) = write { settings.putInt(KEY_MOOD_MINUTE, minute ?: -1) }

    /** Fixed per install, so each day's surprise time stays put across replans. */
    val moodSeed: Int
        get() = settings.getInt(KEY_MOOD_SEED, 0).takeIf { it != 0 }
            ?: kotlin.random.Random.nextInt(1, Int.MAX_VALUE).also { settings.putInt(KEY_MOOD_SEED, it) }

    var lastSlipDate: LocalDate?
        get() = settings.getStringOrNull(KEY_SLIP_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        set(v) { if (v == null) settings.remove(KEY_SLIP_DATE) else settings.putString(KEY_SLIP_DATE, v.toString()) }

    private fun write(block: () -> Unit) { block(); _state.value = read() }

    private fun read() = Snapshot(
        evening = settings.getBoolean(KEY_EVENING, true),
        eveningMinute = settings.getInt(KEY_EVENING_MINUTE, -1).takeIf { it >= 0 },
        slipped = settings.getBoolean(KEY_SLIPPED, true),
        mood = settings.getBoolean(KEY_MOOD, false),
        moodMinute = settings.getInt(KEY_MOOD_MINUTE, DEFAULT_MOOD_MINUTE).takeIf { it >= 0 },
    )

    companion object {
        const val KEY_EVENING = "v4_nudge_evening"
        const val KEY_EVENING_MINUTE = "v4_nudge_evening_minute"
        const val KEY_SLIPPED = "v4_nudge_slipped"
        const val KEY_SLIP_DATE = "v4_nudge_slip_date"
        const val KEY_MOOD = "v4_nudge_mood"
        const val KEY_MOOD_MINUTE = "v4_nudge_mood_minute"
        const val KEY_MOOD_SEED = "v4_nudge_mood_seed"
        const val DEFAULT_MOOD_MINUTE = 20 * 60
    }
}

/** Keeps the planned nudges in step with the habits while the app runs. */
class NudgeService(private val service: HabitService, private val prefs: NudgePrefs) {
    private val tz = TimeZone.currentSystemDefault()
    private var lastRows: List<HabitRow>? = null

    private val _learned = MutableStateFlow<Int?>(null)
    /** The learned evening check-in minute, for the settings row. */
    val learned: StateFlow<Int?> = _learned.asStateFlow()

    @OptIn(FlowPreview::class)
    suspend fun run() {
        service.rows.debounce(1_500).collect { rows ->
            lastRows = rows
            plan(rows)
        }
    }

    /** After a settings change. */
    fun replan() { lastRows?.let(::plan) }

    fun eveningMinute(rows: List<HabitRow>): Int =
        prefs.state.value.eveningMinute ?: HabitLearning.checkInMinute(rows.flatMap { it.ticks }) ?: HabitLearning.DEFAULT_CHECK_IN

    private fun plan(rows: List<HabitRow>) {
        runCatching {
            val now = Clock.System.now().toLocalDateTime(tz)
            val p = prefs.state.value
            _learned.value = HabitLearning.checkInMinute(rows.flatMap { it.ticks })
            NudgePlan.checkInIds().forEach(NudgeAlarms::cancel)
            if (p.evening) NudgePlan.checkIns(rows, now, eveningMinute(rows)).forEach(NudgeAlarms::schedule)

            val slip = if (p.slipped) NudgePlan.slip(rows, now, prefs.lastSlipDate) else null
            if (slip == null) {
                NudgeAlarms.cancel(NudgePlan.SLIP_ID)
                // A planned one that is no longer needed frees the week up again.
                prefs.lastSlipDate?.let { if (it >= now.date) prefs.lastSlipDate = now.date.minus(DatePeriod(days = 7)) }
            } else {
                NudgeAlarms.schedule(slip)
                prefs.lastSlipDate = slip.at.date
            }
        }
    }
}
