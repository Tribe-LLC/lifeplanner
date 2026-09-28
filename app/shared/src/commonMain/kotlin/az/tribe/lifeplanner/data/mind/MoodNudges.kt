package az.tribe.lifeplanner.data.mind

import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.habits.NudgePlan
import az.tribe.lifeplanner.data.habits.NudgePrefs
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.service.MindCheckIns
import az.tribe.lifeplanner.notification.NudgeAlarms
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The daily mood check-in reminder: off by default, at a set time or a surprise time, a week
 * ahead. Replanned when the app opens, when a setting changes, and when a reminder fires or is
 * answered from the shade, so it keeps going for someone who only ever answers from there.
 */
class MoodNudges(private val prefs: NudgePrefs, private val logs: LifeLogRepository) {
    private val tz = TimeZone.currentSystemDefault()

    val state get() = prefs.state

    /** Whether a mood is already down for today, so the reminder can stay quiet. */
    suspend fun recordedToday(): Boolean {
        val today = Clock.System.now().toLocalDateTime(tz).date
        return runCatching { logs.getInRange(today, today).any { MindCheckIns.isCheckIn(it) } }.getOrDefault(false)
    }

    suspend fun replan() {
        runCatching {
            NudgePlan.moodIds().forEach(NudgeAlarms::cancel)
            val p = prefs.state.value
            if (!p.mood) return
            val now = Clock.System.now().toLocalDateTime(tz)
            NudgePlan.moods(now, p.moodMinute, prefs.moodSeed, recordedToday()).forEach(NudgeAlarms::schedule)
        }
    }

    suspend fun setOn(on: Boolean) {
        prefs.setMood(on)
        PostHogAnalytics.capture("v4_mind_reminder_set", mapOf("on" to on, "time" to timeKey(prefs.state.value.moodMinute)))
        replan()
    }

    /** Null is "Surprise me". */
    suspend fun setMinute(minute: Int?) {
        prefs.setMoodMinute(minute)
        PostHogAnalytics.capture("v4_mind_reminder_set", mapOf("on" to prefs.state.value.mood, "time" to timeKey(minute)))
        replan()
    }

    private fun timeKey(minute: Int?) = minute?.let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" } ?: "surprise"
}
