package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.domain.model.CoachResponse
import az.tribe.lifeplanner.domain.model.CoachSuggestion
import az.tribe.lifeplanner.domain.model.SuggestedMilestone
import co.touchlab.kermit.Logger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ============================================================================
// RESPONSE PARSERS
// ============================================================================

/**
 * Parse inline suggestion tags from streaming response text.
 * Tags: [SUGGEST_GOAL:title|desc|category|timeline]
 *        [SUGGEST_HABIT:title|desc|category|frequency]
 *        [SUGGEST_JOURNAL:title|content|mood]
 * Returns the cleaned text (tags removed) and parsed suggestions.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun parseInlineSuggestions(rawText: String): Pair<String, List<CoachSuggestion>> {
    val suggestions = mutableListOf<CoachSuggestion>()
    val tagPattern = Regex("""\[SUGGEST_(GOAL|HABIT|JOURNAL):([^\]]+)\]""")
    val matches = tagPattern.findAll(rawText)

    for (match in matches) {
        try {
            val type = match.groupValues[1]
            val parts = match.groupValues[2].split("|").map { it.trim() }
            val id = Uuid.random().toString()

            val suggestion: CoachSuggestion? = when (type) {
                "GOAL" -> {
                    if (parts.size >= 2) {
                        CoachSuggestion.CreateGoal(
                            id = id,
                            label = "Add Goal",
                            title = parts[0],
                            description = parts.getOrElse(1) { "" },
                            category = parts.getOrElse(2) { "CAREER" },
                            timeline = parts.getOrElse(3) { "MID_TERM" }
                        )
                    } else null
                }
                "HABIT" -> {
                    if (parts.size >= 2) {
                        val rawCount = parts.getOrElse(5) { "" }.trim().toIntOrNull()
                        val rawUnit = parts.getOrElse(6) { "" }.trim().takeIf { it.isNotEmpty() }
                        CoachSuggestion.CreateHabit(
                            id = id,
                            label = "Add Habit",
                            title = parts[0],
                            description = parts.getOrElse(1) { "" },
                            category = parts.getOrElse(2) { "PERSONAL" },
                            frequency = parts.getOrElse(3) { "DAILY" },
                            goalId = parts.getOrElse(4) { "" }.trim().takeIf { it.isNotEmpty() },
                            targetCount = rawCount ?: 1,
                            targetUnit = rawUnit
                        )
                    } else null
                }
                "JOURNAL" -> {
                    if (parts.size >= 2) {
                        CoachSuggestion.CreateJournalEntry(
                            id = id,
                            label = "Add Entry",
                            title = parts[0],
                            content = parts.getOrElse(1) { "" },
                            mood = parts.getOrElse(2) { null }
                        )
                    } else null
                }
                else -> null
            }

            if (suggestion != null) {
                suggestions.add(suggestion)
                Logger.d("ChatRepositoryImpl") { "Parsed suggestion: $type - ${parts[0]}" }
            }
        } catch (e: Exception) {
            Logger.w("ChatRepositoryImpl") { "Failed to parse suggestion tag: ${match.value}, error: ${e.message}" }
        }
    }

    // Also handle legacy [SUGGEST_ACTION] tag (just remove it)
    val cleanedText = rawText.replace(tagPattern, "").replace("[SUGGEST_ACTION]", "").trimEnd()
    return Pair(cleanedText, suggestions)
}

/**
 * Parse council response where messages have coach + text structure
 */
@OptIn(ExperimentalUuidApi::class)
internal fun parseCouncilResponse(rawText: String, json: Json): CoachResponse {
    return parseCouncilResponse(rawText, null, json)
}

/**
 * Parse council response with optional custom coach names
 * Used for custom groups where coach names are dynamic
 */
@OptIn(ExperimentalUuidApi::class)
internal fun parseCouncilResponse(rawText: String, coachNames: List<String>?, json: Json): CoachResponse {
    val trimmed = rawText.trim()
    val fallbackCoach = coachNames?.firstOrNull() ?: "Luna"

    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
        return CoachResponse(
            messages = listOf("$fallbackCoach: Let me gather the team's thoughts on this..."),
            suggestions = emptyList()
        )
    }

    return try {
        val responseData = json.decodeFromString<CouncilResponseData>(trimmed)

        // Format messages with coach names (e.g., "Luna: message")
        val messages = responseData.messages
            .filter { it.text.isNotBlank() }
            .map { msg ->
                val coachName = msg.coach.replaceFirstChar { it.uppercase() }
                "$coachName: ${msg.text}"
            }
            .ifEmpty { listOf("$fallbackCoach: I'm here to help!") }

        val suggestions = responseData.suggestions.mapNotNull { suggestionData ->
            convertToCoachSuggestion(suggestionData)
        }

        CoachResponse(messages = messages, suggestions = suggestions)
    } catch (e: Exception) {
        Logger.e("ChatRepositoryImpl") { "Council response parsing failed: ${e.message}" }
        CoachResponse(
            messages = listOf("$fallbackCoach: Let me try again... What would you like help with?"),
            suggestions = emptyList()
        )
    }
}

@OptIn(ExperimentalUuidApi::class)
internal fun parseCoachResponse(rawText: String, json: Json): CoachResponse {
    val trimmed = rawText.trim()

    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
        return CoachResponse(
            messages = listOf("I'm thinking about that... Could you try asking again?"),
            suggestions = emptyList()
        )
    }

    return try {
        val responseData = json.decodeFromString<CoachResponseData>(trimmed)

        val messages = responseData.messages
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("I'm here to help!") }

        val suggestions = responseData.suggestions.mapNotNull { suggestionData ->
            convertToCoachSuggestion(suggestionData)
        }

        CoachResponse(messages = messages, suggestions = suggestions)
    } catch (e: Exception) {
        Logger.e("ChatRepositoryImpl") { "Coach response parsing failed: ${e.message}" }
        CoachResponse(
            messages = listOf("Let me try that again... What would you like help with?"),
            suggestions = emptyList()
        )
    }
}

@OptIn(ExperimentalUuidApi::class)
internal fun convertToCoachSuggestion(suggestionData: SuggestionData): CoachSuggestion? {
    val id = Uuid.random().toString()
    val payload = suggestionData.data

    return when (suggestionData.type) {
        "CREATE_GOAL" -> CoachSuggestion.CreateGoal(
            id = id,
            label = suggestionData.label,
            title = payload.title ?: return null,
            description = payload.description ?: "",
            category = payload.category ?: "CAREER",
            timeline = payload.timeline ?: "MID_TERM",
            milestones = payload.milestones.map { m ->
                SuggestedMilestone(title = m.title, weekOffset = m.weekOffset)
            }
        )
        "CREATE_HABIT" -> CoachSuggestion.CreateHabit(
            id = id,
            label = suggestionData.label,
            title = payload.title ?: return null,
            description = payload.description ?: "",
            category = payload.category ?: "PERSONAL",
            frequency = payload.frequency ?: "DAILY",
            goalId = payload.goalId,
            targetCount = payload.targetCount ?: 1,
            targetUnit = payload.targetUnit
        )
        "CREATE_JOURNAL" -> CoachSuggestion.CreateJournalEntry(
            id = id,
            label = suggestionData.label,
            title = payload.title ?: return null,
            content = payload.content ?: "",
            mood = payload.mood
        )
        "CHECK_IN_HABIT" -> CoachSuggestion.CheckInHabit(
            id = id,
            label = suggestionData.label,
            habitId = payload.habitId ?: return null,
            habitTitle = payload.habitTitle ?: ""
        )
        else -> null
    }
}

// ============================================================================
// AI PROXY MESSAGE HELPERS
// ============================================================================

internal fun buildAiProxyMessages(requestBody: JsonObject): List<AiProxyService.ChatMessage> {
    val contents = requestBody["contents"]?.jsonArray ?: return emptyList()
    return contents.mapNotNull { content ->
        val obj = content.jsonObject
        val role = obj["role"]?.jsonPrimitive?.content ?: "user"
        val text = obj["parts"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")?.jsonPrimitive?.content ?: return@mapNotNull null
        AiProxyService.ChatMessage(role = role, content = text)
    }
}

internal fun extractSystemPrompt(requestBody: JsonObject): String? {
    val systemInstruction = requestBody["systemInstruction"]?.jsonObject ?: return null
    return systemInstruction["parts"]?.jsonArray?.firstOrNull()
        ?.jsonObject?.get("text")?.jsonPrimitive?.content
}

internal fun extractResponseSchema(requestBody: JsonObject): JsonObject? {
    val config = requestBody["generationConfig"]?.jsonObject ?: return null
    return config["responseSchema"]?.jsonObject
}

/**
 * Takes the coach's [FOLLOWUPS:a|b|c] line off a reply: the replies the user can tap next.
 * Keeps at most three, drops empty or overlong ones, and tolerates a missing tag.
 */
internal fun splitFollowUps(rawText: String): Pair<String, List<String>> {
    val tag = Regex("""\[FOLLOWUPS:([^\]]*)\]""")
    val found = tag.findAll(rawText).lastOrNull() ?: return rawText.trim() to emptyList()
    val options = found.groupValues[1].split("|")
        .map { it.trim().trim('"').trimEnd('.') }
        .filter { it.isNotEmpty() && it.length <= 60 }
        .distinct()
        .take(3)
    return rawText.replace(tag, "").trim() to options
}
