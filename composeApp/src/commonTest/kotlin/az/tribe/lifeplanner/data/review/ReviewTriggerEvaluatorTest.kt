package az.tribe.lifeplanner.data.review

import com.russhwolf.settings.MapSettings
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class ReviewTriggerEvaluatorTest {

    private fun createSettings(vararg pairs: Pair<String, Any>): MapSettings {
        return MapSettings().also { settings ->
            pairs.forEach { (key, value) ->
                when (value) {
                    is Int -> settings.putInt(key, value)
                    is Long -> settings.putLong(key, value)
                    is String -> settings.putString(key, value)
                }
            }
        }
    }

    @Test
    fun `shouldPrompt returns true for fresh user with no prior prompts`() {
        val settings = createSettings()
        assertTrue(ReviewTriggerEvaluator.shouldPrompt(settings))
    }

    @Test
    fun `shouldPrompt returns false within cooldown period`() {
        val now = Clock.System.now().epochSeconds
        val settings = createSettings(
            "review_last_prompt_epoch" to now,
            "review_prompt_count" to 1
        )
        assertFalse(ReviewTriggerEvaluator.shouldPrompt(settings))
    }

    @Test
    fun `shouldPrompt returns true after cooldown expires`() {
        val now = Clock.System.now().epochSeconds
        val sixtyOneDaysAgo = now - (61 * 86400)
        val settings = createSettings(
            "review_last_prompt_epoch" to sixtyOneDaysAgo,
            "review_prompt_count" to 1
        )
        assertTrue(ReviewTriggerEvaluator.shouldPrompt(settings))
    }

    @Test
    fun `shouldPrompt returns false when max prompts reached`() {
        val now = Clock.System.now().epochSeconds
        val longAgo = now - (365 * 86400)
        val settings = createSettings(
            "review_last_prompt_epoch" to longAgo,
            "review_prompt_count" to 3
        )
        assertFalse(ReviewTriggerEvaluator.shouldPrompt(settings))
    }

    @Test
    fun `recordPrompt increments count and updates timestamp`() {
        val settings = createSettings()
        assertEquals(0, settings.getInt("review_prompt_count", 0))

        ReviewTriggerEvaluator.recordPrompt(settings)

        assertEquals(1, settings.getInt("review_prompt_count", 0))
        assertTrue(settings.getLong("review_last_prompt_epoch", 0L) > 0)
    }

    @Test
    fun `recordPrompt increments existing count`() {
        val settings = createSettings("review_prompt_count" to 1)

        ReviewTriggerEvaluator.recordPrompt(settings)

        assertEquals(2, settings.getInt("review_prompt_count", 0))
    }

    @Test
    fun `full lifecycle - prompt, record, wait, prompt again`() {
        val settings = createSettings()

        // First prompt should be allowed
        assertTrue(ReviewTriggerEvaluator.shouldPrompt(settings))
        ReviewTriggerEvaluator.recordPrompt(settings)

        // Immediately after - should be blocked (cooldown)
        assertFalse(ReviewTriggerEvaluator.shouldPrompt(settings))

        // Simulate 61 days passing
        val futureTime = Clock.System.now().epochSeconds - (61 * 86400)
        settings.putLong("review_last_prompt_epoch", futureTime)

        // Should be allowed again
        assertTrue(ReviewTriggerEvaluator.shouldPrompt(settings))
        ReviewTriggerEvaluator.recordPrompt(settings)

        // Count is now 2, simulate another cooldown expiry
        settings.putLong("review_last_prompt_epoch", futureTime)
        assertTrue(ReviewTriggerEvaluator.shouldPrompt(settings))
        ReviewTriggerEvaluator.recordPrompt(settings)

        // Count is now 3 (max) - should be blocked permanently
        settings.putLong("review_last_prompt_epoch", futureTime)
        assertFalse(ReviewTriggerEvaluator.shouldPrompt(settings))
    }
}
