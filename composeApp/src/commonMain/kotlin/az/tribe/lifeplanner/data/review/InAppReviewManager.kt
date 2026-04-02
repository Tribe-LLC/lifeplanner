package az.tribe.lifeplanner.data.review

import com.russhwolf.settings.Settings

/**
 * Cross-platform in-app review manager.
 * Uses Google Play In-App Review on Android and StoreKit on iOS.
 *
 * Tracks engagement milestones to prompt at the right moment:
 * - First goal completed
 * - 3-day streak achieved
 * - 5 habits checked in
 * - 10 journal entries created
 */
expect class InAppReviewManager() {
    fun requestReview(trigger: String)
}

/**
 * Determines whether the user should be prompted for a review based on
 * engagement signals and cooldown periods.
 */
object ReviewTriggerEvaluator {
    private const val KEY_LAST_REVIEW_PROMPT = "review_last_prompt_epoch"
    private const val KEY_REVIEW_PROMPT_COUNT = "review_prompt_count"
    private const val COOLDOWN_DAYS = 60
    private const val MAX_PROMPTS = 3

    fun shouldPrompt(settings: Settings): Boolean {
        val promptCount = settings.getInt(KEY_REVIEW_PROMPT_COUNT, 0)
        if (promptCount >= MAX_PROMPTS) return false

        val lastPrompt = settings.getLong(KEY_LAST_REVIEW_PROMPT, 0L)
        val now = kotlinx.datetime.Clock.System.now().epochSeconds
        val daysSinceLastPrompt = (now - lastPrompt) / 86400

        return daysSinceLastPrompt >= COOLDOWN_DAYS
    }

    fun recordPrompt(settings: Settings) {
        val now = kotlinx.datetime.Clock.System.now().epochSeconds
        settings.putLong(KEY_LAST_REVIEW_PROMPT, now)
        settings.putInt(KEY_REVIEW_PROMPT_COUNT, settings.getInt(KEY_REVIEW_PROMPT_COUNT, 0) + 1)
    }
}
