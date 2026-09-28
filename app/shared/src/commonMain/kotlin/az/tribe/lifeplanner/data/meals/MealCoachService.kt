package az.tribe.lifeplanner.data.meals

import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.domain.service.CoachContext
import az.tribe.lifeplanner.domain.service.CoachDinner
import az.tribe.lifeplanner.domain.service.MealCoach
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate

/**
 * "Plan my week" through the ai-proxy (no keys on the device). Nothing is saved here: the page
 * shows the dinners, the user keeps, swaps or drops them, and only then are they planned.
 * Null means the coach could not be reached or gave nothing usable; the page says so.
 */
class MealCoachService(
    private val ai: AiProxyService,
    private val settings: Settings,
) {
    /** How many the user cooks for. Kept on this device; asked in the sheet. */
    var household: Int
        get() = settings.getInt(KEY_HOUSEHOLD, 1)
        set(v) = settings.putInt(KEY_HOUSEHOLD, v.coerceIn(1, 8))

    suspend fun planWeek(dates: List<LocalDate>, ctx: CoachContext): List<CoachDinner>? {
        if (dates.isEmpty()) return emptyList()
        return ask(dates, ctx, emptyList())?.also {
            PostHogAnalytics.capture("v4_meals_coach_week", mapOf("asked" to dates.size, "got" to it.size, "household" to (ctx.household ?: 1)))
        }
    }

    /** Another dinner for [date], unlike [avoid]. */
    suspend fun swap(date: LocalDate, ctx: CoachContext, avoid: List<String>): CoachDinner? =
        ask(listOf(date), ctx, avoid)?.firstOrNull()?.also { PostHogAnalytics.capture("v4_meals_coach_swap", mapOf("ok" to true)) }

    private suspend fun ask(dates: List<LocalDate>, ctx: CoachContext, avoid: List<String>): List<CoachDinner>? = try {
        val raw = ai.generateStructuredJson(MealCoach.prompt(dates, ctx, avoid), MealCoach.schema())
        MealCoach.parse(raw, dates).takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        Logger.w("MealCoachService") { "Plan my week failed: ${e.message}" }
        PostHogAnalytics.capture("v4_meals_coach_failed", mapOf("days" to dates.size))
        null
    }

    companion object {
        private const val KEY_HOUSEHOLD = "v4_meals_household"
    }
}
