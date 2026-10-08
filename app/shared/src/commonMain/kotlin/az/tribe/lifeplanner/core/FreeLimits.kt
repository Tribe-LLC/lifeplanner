package az.tribe.lifeplanner.core

import com.russhwolf.settings.Settings
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** What the free plan includes. Plus lifts both. With billing off ([PremiumGate.isPremium] true) nothing is limited. */
object FreeLimits {
    const val COACH_MESSAGES_PER_DAY = 20

    /** Areas a free user can have on, not counting Habits, which is always on. */
    const val AREAS = 3
}

/**
 * Counts the messages the user has sent to the coach today (their side only, replies never count).
 * The day follows the phone's local calendar, so the allowance resets at midnight.
 */
class CoachQuota(
    private val settings: Settings,
    private val gate: PremiumGate,
) {
    private fun today(): String = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()

    private fun usedToday(): Int =
        if (settings.getString(KEY_DAY, "") == today()) settings.getInt(KEY_COUNT, 0) else 0

    /** True when the user may send another message. Premium users always can. */
    suspend fun canSend(): Boolean = gate.isPremium() || usedToday() < FreeLimits.COACH_MESSAGES_PER_DAY

    /** Call once per message actually sent. */
    fun record() {
        val day = today()
        val n = if (settings.getString(KEY_DAY, "") == day) settings.getInt(KEY_COUNT, 0) else 0
        settings.putString(KEY_DAY, day)
        settings.putInt(KEY_COUNT, n + 1)
    }

    private companion object {
        const val KEY_DAY = "coach_quota_day"
        const val KEY_COUNT = "coach_quota_count"
    }
}
