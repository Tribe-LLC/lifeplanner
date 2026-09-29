package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.data.plans.PlanStepSuggester
import az.tribe.lifeplanner.data.plans.SuggestedStep
import az.tribe.lifeplanner.data.plans.Suggestion
import az.tribe.lifeplanner.domain.enum.AiProvider
import az.tribe.lifeplanner.domain.model.PlanArea
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlanStepSuggesterTest {

    /** Never the real coach: counts calls and answers with [reply]. */
    private class CountingAi(var reply: String) : AiProxyService {
        var calls = 0
        override suspend fun generateText(prompt: String, systemPrompt: String?, provider: AiProvider?) = error("not used")
        override suspend fun generateStructuredJson(prompt: String, responseSchema: JsonObject, systemPrompt: String?, provider: AiProvider?): String {
            calls++
            return reply
        }
        override suspend fun chat(messages: List<AiProxyService.ChatMessage>, systemPrompt: String?, responseSchema: JsonObject?, provider: AiProvider?) = error("not used")
        override fun chatStream(messages: List<AiProxyService.ChatMessage>, systemPrompt: String?, provider: AiProvider?) = flow<AiProxyService.StreamEvent> {}
    }

    private val shed = """{"steps":[{"title":"Buy wood, screws and a saw","week":2},{"title":"1. Pick the spot \u2014 and measure it.","week":1},{"title":"Build the base","week":3},{"title":"Paint it","week":5}]}"""

    @Test
    fun parsesCleansAndOrders() {
        val steps = PlanStepSuggester.parse(shed)
        assertEquals(listOf("Pick the spot, and measure it", "Buy wood, screws and a saw", "Build the base", "Paint it"), steps.map { it.title })
        assertEquals(listOf(1, 2, 3, 5), steps.map { it.week })
        assertTrue(PlanStepSuggester.parse("not json").isEmpty())
        assertTrue(steps.none { '\u2014' in it.title })
    }

    @Test
    fun oneCallThenTheCacheForTheSameTitle() = runTest {
        val ai = CountingAi(shed)
        val s = PlanStepSuggester(ai, MapSettings())
        val first = assertIs<Suggestion.Steps>(s.suggest("Build a garden shed", PlanArea.HABITS, null))
        assertEquals(false, first.cached)
        val again = assertIs<Suggestion.Steps>(s.suggest("build the Garden Shed!", PlanArea.HABITS, 8))
        assertEquals(true, again.cached)
        assertEquals(1, ai.calls)
    }

    @Test
    fun aDailyCapAndNothingSavedOnFailure() = runTest {
        val ai = CountingAi("{}")
        val s = PlanStepSuggester(ai, MapSettings())
        repeat(PlanStepSuggester.DAILY_CAP) { i -> assertIs<Suggestion.Failed>(s.suggest("Plan $i", PlanArea.HABITS, null)) }
        assertIs<Suggestion.Limit>(s.suggest("One more", PlanArea.HABITS, null))
        assertEquals(PlanStepSuggester.DAILY_CAP, ai.calls)
    }

    @Test
    fun cacheRoundTrip() {
        val steps = listOf(SuggestedStep("A | b", 1), SuggestedStep("C", 4))
        assertEquals(steps, PlanStepSuggester.decode(PlanStepSuggester.encode(steps)))
        assertEquals("build garden shed", PlanStepSuggester.normalise("Build a Garden-Shed!"))
    }
}
