package az.tribe.lifeplanner.data.plans

import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.domain.model.PlanArea
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock

/** A step the coach suggested: its title and the week from the start it belongs to (1 is the first). */
data class SuggestedStep(val title: String, val week: Int)

sealed interface Suggestion {
    data class Steps(val steps: List<SuggestedStep>, val cached: Boolean) : Suggestion
    /** Today's suggestions are used up; the user can still add their own. */
    data object Limit : Suggestion
    data object Failed : Suggestion
}

/**
 * Steps for a plan no template fits, from the coach. Only ever called when the user taps "Suggest
 * steps", once per plan: the answer is kept by the plan's normalised title, so the next plan like
 * it is instant and free, and there is a small daily cap on new calls.
 */
class PlanStepSuggester(private val ai: AiProxyService, private val settings: Settings) {

    private fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    suspend fun suggest(title: String, area: PlanArea, weeks: Int?): Suggestion {
        val key = CACHE_PREFIX + normalise(title)
        settings.getStringOrNull(key)?.let(::decode)?.takeIf { it.isNotEmpty() }?.let {
            PostHogAnalytics.capture("v4_plan_ai_suggest", mapOf("result" to "cached"))
            return Suggestion.Steps(it, cached = true)
        }
        if (usedToday() >= DAILY_CAP) {
            PostHogAnalytics.capture("v4_plan_ai_suggest", mapOf("result" to "limit"))
            return Suggestion.Limit
        }
        countCall()
        return try {
            val steps = parse(ai.generateStructuredJson(prompt(title, area, weeks), schema()))
            if (steps.isEmpty()) return Suggestion.Failed.also { PostHogAnalytics.capture("v4_plan_ai_suggest", mapOf("result" to "empty")) }
            settings.putString(key, encode(steps))
            PostHogAnalytics.capture("v4_plan_ai_suggest", mapOf("result" to "ai", "steps" to steps.size))
            Suggestion.Steps(steps, cached = false)
        } catch (e: Exception) {
            Logger.w("PlanStepSuggester") { "Suggest failed: ${e.message}" }
            PostHogAnalytics.capture("v4_plan_ai_suggest", mapOf("result" to "failed"))
            Suggestion.Failed
        }
    }

    private fun usedToday(): Int = settings.getStringOrNull(KEY_DAY)?.split('|')?.takeIf { it.firstOrNull() == today().toString() }?.getOrNull(1)?.toIntOrNull() ?: 0

    private fun countCall() = settings.putString(KEY_DAY, "${today()}|${usedToday() + 1}")

    private fun prompt(title: String, area: PlanArea, weeks: Int?) = """
        Someone is starting a personal plan: "$title" (area: ${area.key}).
        ${if (weeks != null) "It should be done in about $weeks weeks." else "There is no fixed date; about one step a week suits them."}
        Break it into 4 to 6 concrete steps, in order, that a busy person can actually do.
        Each step: a short title of at most 8 plain words, no numbering, no emoji, no dashes, and the week
        from the start (1 is the first week) by which it should be done. The last step is the plan itself done.
    """.trimIndent()

    private fun schema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("steps") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("title") { put("type", "string") }
                        putJsonObject("week") { put("type", "integer") }
                    }
                    putJsonArray("required") { add(JsonPrimitive("title")); add(JsonPrimitive("week")) }
                }
            }
        }
        putJsonArray("required") { add(JsonPrimitive("steps")) }
    }

    companion object {
        const val DAILY_CAP = 5
        private const val CACHE_PREFIX = "v4_plan_ai_"
        private const val KEY_DAY = "v4_plan_ai_day"

        /** "Build a Garden Shed!" and "build the garden shed" share one cached answer. */
        fun normalise(title: String): String = title.lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .split(Regex("""\s+""")).filter { it.isNotEmpty() && it !in setOf("a", "an", "the", "my", "our", "to") }
            .joinToString(" ")

        /** The coach's answer, cleaned: 4 to 6 steps at most, in week order, no dashes or numbering. */
        fun parse(raw: String): List<SuggestedStep> {
            val items = runCatching { Json.parseToJsonElement(raw).jsonObject["steps"]?.jsonArray }.getOrNull() ?: return emptyList()
            return items.mapNotNull { e ->
                val o = runCatching { e.jsonObject }.getOrNull() ?: return@mapNotNull null
                val title = o["title"]?.jsonPrimitive?.contentOrNull
                    ?.replace(Regex("""\s*[\u2014\u2013]\s*"""), ", ")
                    ?.replace(Regex("""^\s*(\d+[.)]|[-*•])\s*"""), "")
                    ?.trim()?.trimEnd('.')?.takeIf { it.isNotEmpty() }?.take(80) ?: return@mapNotNull null
                SuggestedStep(title.replaceFirstChar { it.uppercase() }, (o["week"]?.jsonPrimitive?.intOrNull ?: 1).coerceIn(1, 104))
            }.take(6).sortedBy { it.week }
        }

        fun encode(steps: List<SuggestedStep>) = steps.joinToString("\n") { "${it.week}|${it.title.replace('\n', ' ')}" }

        fun decode(text: String): List<SuggestedStep> = text.lines().mapNotNull { line ->
            val i = line.indexOf('|')
            if (i <= 0) null else line.substring(0, i).toIntOrNull()?.let { SuggestedStep(line.substring(i + 1), it) }
        }
    }
}
