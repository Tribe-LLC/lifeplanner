package az.tribe.lifeplanner.ui.journal

import az.tribe.lifeplanner.BuildKonfig
import az.tribe.lifeplanner.di.GEMINI_PRO
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Habit
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

data class AiJournalResult(
    val title: String,
    val content: String,
    val tags: List<String>
)

/**
 * Generates a journal entry title, content, and tags using AI.
 * Used by both the wizard flow (with userNote) and the legacy bottom sheet (with userTitle).
 */
suspend fun generateAiJournalEntry(
    httpClient: HttpClient,
    mood: Mood,
    prompt: String,
    userNote: String,
    linkedGoal: Goal?,
    linkedHabit: Habit?
): AiJournalResult? = withContext(Dispatchers.IO) {
    try {
        val apiKey = BuildKonfig.GEMINI_API_KEY
        if (apiKey.isBlank()) return@withContext null

        val goalContext = linkedGoal?.let {
            """
            Linked Goal: "${it.title}"
            - Description: ${it.description.ifBlank { "No description" }}
            - Progress: ${(it.progress ?: 0).toInt()}%
            - Status: ${it.status.name}
            - Category: ${it.category.name.lowercase().replaceFirstChar { c -> c.uppercase() }}
            """.trimIndent()
        } ?: ""

        val habitContext = linkedHabit?.let {
            """
            Linked Habit: "${it.title}"
            - Description: ${it.description.ifBlank { "No description" }}
            - Current streak: ${it.currentStreak} days
            - Total completions: ${it.totalCompletions}
            - Frequency: ${it.frequency.displayName}
            """.trimIndent()
        } ?: ""

        val noteContext = if (userNote.isNotBlank()) {
            "Additional context from user: \"$userNote\""
        } else ""

        val aiPrompt = """
You are a personal journaling assistant helping someone write a journal entry.

User's current mood: ${mood.displayName} (${mood.emoji})
${if (prompt.isNotBlank()) "The user wants to reflect on: \"$prompt\"" else ""}
$goalContext
$habitContext
$noteContext

Generate a thoughtful journal entry with:
1. A concise, meaningful title (3-6 words) that captures the essence of the reflection
2. A personal, first-person journal entry (2-3 paragraphs) that:
   - Acknowledges the user's current mood authentically
   - If a prompt is given, uses it as the basis for reflection
   - If a goal is linked, connects the reflection to their progress or feelings about it
   - If a habit is linked, mentions how it relates to their journey
   - Is warm, honest, and introspective
   - Feels personal and genuine, not generic
3. 2-4 relevant tags (single words, no hashtags) that categorize this entry
""".trimIndent()

        val requestBody = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", aiPrompt)
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                putJsonObject("responseSchema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("title") {
                            put("type", "string")
                        }
                        putJsonObject("content") {
                            put("type", "string")
                        }
                        putJsonObject("tags") {
                            put("type", "array")
                            putJsonObject("items") {
                                put("type", "string")
                            }
                        }
                    }
                    putJsonArray("required") {
                        add(JsonPrimitive("title"))
                        add(JsonPrimitive("content"))
                        add(JsonPrimitive("tags"))
                    }
                }
            }
        }

        val response = httpClient.post {
            url("v1beta/models/$GEMINI_PRO:generateContent")
            parameter("key", apiKey)
            setBody(requestBody)
        }

        val responseText = response.bodyAsText()
        val json = Json { ignoreUnknownKeys = true }
        val responseJson = json.parseToJsonElement(responseText).jsonObject
        val candidates = responseJson["candidates"]?.jsonArray
        val textContent = candidates?.firstOrNull()
            ?.jsonObject?.get("content")
            ?.jsonObject?.get("parts")
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("text")
            ?.jsonPrimitive?.contentOrNull

        if (textContent != null) {
            try {
                val entryJson = json.parseToJsonElement(textContent).jsonObject
                val generatedTitle = entryJson["title"]?.jsonPrimitive?.contentOrNull ?: return@withContext null
                val generatedContent = entryJson["content"]?.jsonPrimitive?.contentOrNull ?: return@withContext null
                val generatedTags = entryJson["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                return@withContext AiJournalResult(generatedTitle, generatedContent, generatedTags)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
