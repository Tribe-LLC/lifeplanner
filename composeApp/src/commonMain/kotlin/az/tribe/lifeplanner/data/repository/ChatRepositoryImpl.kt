package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.BuildKonfig
import az.tribe.lifeplanner.data.mapper.createChatMessage
import az.tribe.lifeplanner.data.mapper.createChatSession
import az.tribe.lifeplanner.data.mapper.toDomain
import az.tribe.lifeplanner.data.mapper.toDomainMessages
import az.tribe.lifeplanner.data.mapper.toDomainSessions
import az.tribe.lifeplanner.di.GEMINI_PRO
import az.tribe.lifeplanner.domain.model.ChatMessage
import az.tribe.lifeplanner.domain.model.ChatSession
import az.tribe.lifeplanner.domain.model.MessageRole
import az.tribe.lifeplanner.domain.model.UserContext
import az.tribe.lifeplanner.domain.repository.ChatRepository
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*

class ChatRepositoryImpl(
    private val database: SharedDatabase,
    private val httpClient: HttpClient
) : ChatRepository {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val COACH_PERSONA = """
You are Luna, a warm, supportive AI life coach inside the LifePlanner app. Your personality traits:

- **Encouraging & Positive**: Always find something to celebrate, even small wins
- **Empathetic**: Acknowledge feelings and validate struggles before offering solutions
- **Practical**: Give actionable, specific advice that can be implemented immediately
- **Personalized**: Reference the user's actual goals, habits, and progress when relevant
- **Conversational**: Use a friendly, approachable tone (not robotic or overly formal)
- **Motivational**: End messages with encouragement or a thought-provoking question

Guidelines:
1. Keep responses concise but meaningful (2-4 paragraphs max)
2. When discussing goals, be specific about next steps
3. Celebrate progress, no matter how small
4. If the user seems stuck, suggest breaking things into smaller steps
5. Use occasional emojis sparingly for warmth (1-2 per message max)
6. Never be preachy or condescending
7. If you don't have enough context, ask clarifying questions

Remember: You're a coach, not a therapist. For serious mental health concerns, gently suggest professional help.
"""

        private fun buildSystemPrompt(userContext: UserContext): String {
            return """
$COACH_PERSONA

Current User Context:
- Name: ${userContext.userName ?: "User"}
- Level: ${userContext.level} (${userContext.totalXp} XP)
- Current Streak: ${userContext.currentStreak} days
- Goals: ${userContext.activeGoals} active, ${userContext.completedGoals} completed out of ${userContext.totalGoals} total
- Recent milestones completed: ${userContext.recentMilestones.take(3).joinToString(", ").ifEmpty { "None recently" }}
- Habit completion rate: ${(userContext.habitCompletionRate * 100).toInt()}%
- Journal entries: ${userContext.journalEntryCount}
- Primary focus areas: ${userContext.primaryCategories.joinToString(", ").ifEmpty { "Not set" }}
${if (userContext.upcomingDeadlines.isNotEmpty()) "- Upcoming deadlines: ${userContext.upcomingDeadlines.take(3).joinToString(", ") { "${it.title} (${it.dueDate})" }}" else ""}

Use this context to personalize your responses and make relevant suggestions.
""".trimIndent()
        }
    }

    override suspend fun getAllSessions(): List<ChatSession> {
        return database.getAllChatSessions().toDomainSessions()
    }

    override suspend fun getSessionById(sessionId: String): ChatSession? {
        val sessionEntity = database.getChatSessionById(sessionId) ?: return null
        val messages = database.getMessagesBySessionId(sessionId).toDomainMessages()
        return sessionEntity.toDomain(messages)
    }

    override suspend fun createSession(title: String): ChatSession {
        val session = createChatSession(title)
        database.insertChatSession(
            id = session.id,
            title = session.title,
            createdAt = session.createdAt.toString(),
            lastMessageAt = session.lastMessageAt.toString(),
            summary = session.summary
        )
        return session
    }

    override suspend fun deleteSession(sessionId: String) {
        database.deleteMessagesBySession(sessionId)
        database.deleteChatSession(sessionId)
    }

    override suspend fun getMessages(sessionId: String): List<ChatMessage> {
        return database.getMessagesBySessionId(sessionId).toDomainMessages()
    }

    override suspend fun getRecentMessages(sessionId: String, limit: Int): List<ChatMessage> {
        return database.getRecentMessages(sessionId, limit.toLong()).toDomainMessages().reversed()
    }

    override suspend fun addUserMessage(
        sessionId: String,
        content: String,
        relatedGoalId: String?
    ): ChatMessage {
        val message = createChatMessage(
            content = content,
            role = MessageRole.USER,
            relatedGoalId = relatedGoalId
        )

        database.insertChatMessage(
            id = message.id,
            sessionId = sessionId,
            content = message.content,
            role = message.role.name,
            timestamp = message.timestamp.toString(),
            relatedGoalId = message.relatedGoalId,
            metadata = null
        )

        // Update session timestamp
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val newTitle = if (content.length > 30) content.take(30) + "..." else content
        database.updateChatSessionLastMessage(sessionId, now.toString(), newTitle)

        return message
    }

    override suspend fun addAssistantMessage(
        sessionId: String,
        content: String,
        metadata: String?
    ): ChatMessage {
        val message = createChatMessage(
            content = content,
            role = MessageRole.ASSISTANT
        )

        database.insertChatMessage(
            id = message.id,
            sessionId = sessionId,
            content = message.content,
            role = message.role.name,
            timestamp = message.timestamp.toString(),
            relatedGoalId = null,
            metadata = metadata
        )

        return message
    }

    override suspend fun sendMessage(
        sessionId: String,
        userMessage: String,
        userContext: UserContext,
        relatedGoalId: String?
    ): ChatMessage {
        // Save user message
        addUserMessage(sessionId, userMessage, relatedGoalId)

        // Get conversation history for context
        val recentMessages = getRecentMessages(sessionId, 10)

        // Build the request with conversation history
        val response = callGeminiChat(userMessage, recentMessages, userContext)

        // Save and return assistant response
        return addAssistantMessage(sessionId, response)
    }

    private suspend fun callGeminiChat(
        userMessage: String,
        conversationHistory: List<ChatMessage>,
        userContext: UserContext
    ): String {
        val systemPrompt = buildSystemPrompt(userContext)

        val requestBody = buildJsonObject {
            putJsonArray("contents") {
                // System instruction as first message
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", "System: $systemPrompt")
                        }
                    }
                }
                addJsonObject {
                    put("role", "model")
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", "I understand. I'm Luna, your AI life coach. I'll be supportive, practical, and personalized in my responses. How can I help you today?")
                        }
                    }
                }

                // Add conversation history
                conversationHistory.forEach { message ->
                    addJsonObject {
                        put("role", if (message.role == MessageRole.USER) "user" else "model")
                        putJsonArray("parts") {
                            addJsonObject {
                                put("text", message.content)
                            }
                        }
                    }
                }

                // Add current user message
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", userMessage)
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.8)
                put("maxOutputTokens", 1024)
                put("topP", 0.95)
                put("topK", 40)
            }
            putJsonArray("safetySettings") {
                addJsonObject {
                    put("category", "HARM_CATEGORY_HARASSMENT")
                    put("threshold", "BLOCK_MEDIUM_AND_ABOVE")
                }
                addJsonObject {
                    put("category", "HARM_CATEGORY_HATE_SPEECH")
                    put("threshold", "BLOCK_MEDIUM_AND_ABOVE")
                }
            }
        }

        return try {
            val response = httpClient.post {
                url("v1beta/models/$GEMINI_PRO:generateContent")
                parameter("key", BuildKonfig.GEMINI_API_KEY)
                setBody(requestBody)
            }

            val responseBody: JsonObject = response.body()
            extractResponseText(responseBody)
        } catch (e: Exception) {
            "I'm having trouble connecting right now. Could you try again in a moment? 🙏"
        }
    }

    private fun extractResponseText(response: JsonObject): String {
        return try {
            response["candidates"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("content")
                ?.jsonObject
                ?.get("parts")
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("text")
                ?.jsonPrimitive
                ?.content
                ?: "I couldn't generate a response. Please try again."
        } catch (e: Exception) {
            "Something went wrong parsing the response. Let's try again!"
        }
    }

    override suspend fun getUserContext(): UserContext {
        val userProgress = database.getUserProgressEntity()
        val activeGoals = database.getActiveGoals()
        val completedGoals = database.getCompletedGoals()
        val allGoals = database.getAllGoals()

        // Get upcoming deadlines (next 7 days)
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val weekFromNow = today.toString() // Simplified - would need proper date math

        // Get category distribution
        val categoryCount = database.getGoalCountByCategory()
        val topCategories = categoryCount.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        // Get recent completed milestones - simplified
        val recentMilestones = mutableListOf<String>()

        return UserContext(
            userName = null, // Would come from user profile
            totalGoals = allGoals.size,
            completedGoals = completedGoals.size,
            activeGoals = activeGoals.size,
            currentStreak = userProgress?.currentStreak?.toInt() ?: 0,
            totalXp = userProgress?.totalXp?.toInt() ?: 0,
            level = userProgress?.currentLevel?.toInt() ?: 1,
            recentMilestones = recentMilestones,
            upcomingDeadlines = emptyList(), // Would need proper implementation
            habitCompletionRate = 0f, // Would calculate from habit data
            journalEntryCount = userProgress?.journalEntriesCount?.toInt() ?: 0,
            primaryCategories = topCategories
        )
    }

    override suspend fun updateSessionSummary(sessionId: String, summary: String) {
        database.updateChatSessionSummary(sessionId, summary)
    }

    override suspend fun deleteOldSessions(beforeDate: String) {
        database.deleteOldChatSessions(beforeDate)
    }

    override suspend fun getSessionCount(): Long {
        return database.getChatSessionCount()
    }
}
