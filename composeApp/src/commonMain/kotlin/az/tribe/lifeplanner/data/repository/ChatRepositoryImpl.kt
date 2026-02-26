package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.BuildKonfig
import az.tribe.lifeplanner.data.mapper.createChatMessage
import az.tribe.lifeplanner.data.mapper.createChatSession
import az.tribe.lifeplanner.data.mapper.toDomain
import az.tribe.lifeplanner.data.mapper.toDomainMessages
import az.tribe.lifeplanner.data.mapper.toDomainSessions
import az.tribe.lifeplanner.di.GEMINI_PRO
import az.tribe.lifeplanner.domain.model.ChatMessage
import az.tribe.lifeplanner.domain.model.ChatMessageMetadata
import az.tribe.lifeplanner.domain.model.ChatSession
import az.tribe.lifeplanner.domain.model.CoachCharacteristics
import az.tribe.lifeplanner.domain.model.CoachGroup
import az.tribe.lifeplanner.domain.model.CoachPersona
import az.tribe.lifeplanner.domain.model.CoachResponse
import az.tribe.lifeplanner.domain.model.CoachSuggestion
import az.tribe.lifeplanner.domain.model.CoachType
import az.tribe.lifeplanner.domain.model.CustomCoach
import az.tribe.lifeplanner.domain.model.MessageRole
import az.tribe.lifeplanner.domain.model.SuggestedMilestone
import az.tribe.lifeplanner.domain.model.UserContext
import az.tribe.lifeplanner.domain.repository.ChatRepository
import az.tribe.lifeplanner.domain.repository.CoachRepository
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

// ============================================================================
// REQUEST DATA CLASSES - What we send to Gemini
// ============================================================================

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double = 0.7,
    val maxOutputTokens: Int = 2048,
    val topP: Double = 0.9,
    val topK: Int = 40,
    val responseMimeType: String = "application/json",
    val responseSchema: ResponseSchema = ResponseSchema()
)

@Serializable
data class ResponseSchema(
    val type: String = "object",
    val properties: Map<String, SchemaProperty> = emptyMap(),
    val required: List<String> = listOf("messages", "suggestions")
)

@Serializable
data class SchemaProperty(
    val type: String,
    val description: String = "",
    val items: SchemaProperty? = null,
    val properties: Map<String, SchemaProperty>? = null,
    val enum: List<String>? = null,
    val maxItems: Int? = null
)

// ============================================================================
// RESPONSE DATA CLASSES - What Gemini returns
// ============================================================================

@Serializable
data class CoachResponseData(
    val messages: List<String> = emptyList(),
    val suggestions: List<SuggestionData> = emptyList()
)

// Council-specific response format where each message has a coach
@Serializable
data class CouncilResponseData(
    val messages: List<CouncilMessage> = emptyList(),
    val suggestions: List<SuggestionData> = emptyList()
)

@Serializable
data class CouncilMessage(
    val coach: String = "",
    val text: String = ""
)

@Serializable
data class SuggestionData(
    val type: String = "",
    val label: String = "",
    val data: SuggestionPayload = SuggestionPayload()
)

@Serializable
data class SuggestionPayload(
    val title: String? = null,
    val description: String? = null,
    val category: String? = null,
    val timeline: String? = null,
    val frequency: String? = null,
    val content: String? = null,
    val mood: String? = null,
    val habitId: String? = null,
    val habitTitle: String? = null,
    // Milestones for goals
    val milestones: List<MilestoneData> = emptyList(),
    // Question fields
    val question: String? = null,
    val options: List<OptionData> = emptyList(),
    val questionType: String? = null
)

@Serializable
data class MilestoneData(
    val title: String = "",
    val weekOffset: Int = 0
)

@Serializable
data class OptionData(
    val id: String = "",
    val label: String = "",
    val value: String = "",
    val description: String? = null
)

@Serializable
data class GeminiApiResponse(
    val candidates: List<Candidate> = emptyList()
)

@Serializable
data class Candidate(
    val content: Content = Content()
)

@Serializable
data class Content(
    val parts: List<Part> = emptyList()
)

@Serializable
data class Part(
    val text: String = ""
)

// ============================================================================
// REPOSITORY IMPLEMENTATION
// ============================================================================

class ChatRepositoryImpl(
    private val database: SharedDatabase,
    private val httpClient: HttpClient,
    private val coachRepository: CoachRepository? = null  // Optional for backward compatibility
) : ChatRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    companion object {
        // Custom coach/group ID prefixes
        const val CUSTOM_COACH_PREFIX = "custom_"
        const val GROUP_PREFIX = "group_"

        fun isCustomCoachId(coachId: String): Boolean = coachId.startsWith(CUSTOM_COACH_PREFIX)
        fun isGroupId(coachId: String): Boolean = coachId.startsWith(GROUP_PREFIX)
        fun extractCustomCoachId(coachId: String): String = coachId.removePrefix(CUSTOM_COACH_PREFIX)
        fun extractGroupId(coachId: String): String = coachId.removePrefix(GROUP_PREFIX)
        fun makeCustomCoachId(id: String): String = "$CUSTOM_COACH_PREFIX$id"
        fun makeGroupId(id: String): String = "$GROUP_PREFIX$id"

        private const val COACH_PERSONA = """
You are Luna, a Personal Coach. Reply ONLY with valid JSON, nothing else.

FORMAT (strict):
{"messages":["msg1","msg2"],"suggestions":[]}

RULES:
- messages: 1-3 short strings (max 80 chars each)
- suggestions: 0-2 items, only when helpful
- NO emojis, NO markdown, NO repetition
- Keep titles under 25 chars

SUGGESTION FORMAT:
{"type":"CREATE_GOAL","label":"Add Goal","data":{"title":"...","description":"...","category":"CAREER","timeline":"MID_TERM"}}
{"type":"CREATE_HABIT","label":"Add Habit","data":{"title":"...","description":"...","category":"HEALTH","frequency":"DAILY"}}

Categories: CAREER, FINANCIAL, PHYSICAL, SOCIAL, EMOTIONAL, SPIRITUAL, FAMILY
Timelines: SHORT_TERM, MID_TERM, LONG_TERM
Frequencies: DAILY, WEEKLY
"""

        private const val COUNCIL_PERSONA = """
You are "The Council" - a group of specialized coaches in a meeting room. Each coach has a unique personality:

COACHES:
1. Luna (Life Coach) - warm, big-picture thinker, moderates the discussion
2. Alex (Career Coach) - professional, strategic, business-minded
3. Morgan (Finance Coach) - analytical, numbers-focused, practical
4. Kai (Fitness Coach) - energetic, action-oriented, motivating
5. Sam (Social Coach) - friendly, empathetic, relationship-focused
6. River (Wellness Coach) - calm, mindful, thoughtful
7. Jamie (Family Coach) - nurturing, patient, family-oriented

RESPONSE FORMAT:
{"messages":[{"coach":"luna","text":"msg"},{"coach":"alex","text":"msg"}],"suggestions":[]}

RULES:
- 2-4 coaches respond per message (pick most relevant ones)
- Each message: max 100 chars
- Coaches build on each other's points like a real meeting
- Mix serious advice with occasional light humor
- Luna often opens or summarizes, but not always
- Only relevant coaches speak (career question = Alex + maybe Morgan)
- Coaches can agree, add perspective, or playfully disagree
- Keep it natural like a supportive team discussion
- suggestions: 0-2 items total

Example flow:
User: "I want to get a promotion"
Luna: "Exciting goal! Let's hear from our experts."
Alex: "Focus on visible projects and document your wins."
Morgan: "Promotion usually means salary bump - have a number in mind!"
Kai: "Don't forget: confident posture in meetings makes a difference!"

Categories: CAREER, FINANCIAL, PHYSICAL, SOCIAL, EMOTIONAL, SPIRITUAL, FAMILY
Timelines: SHORT_TERM, MID_TERM, LONG_TERM
Frequencies: DAILY, WEEKLY
"""

        /**
         * Get coach-specific system prompt
         */
        private fun getCoachSystemPrompt(coach: CoachPersona): String {
            return """
You are ${coach.name}, a ${coach.title} in the LifePlanner app. Reply ONLY with valid JSON.

YOUR PERSONALITY: ${coach.personality}
YOUR SPECIALTIES: ${coach.specialties.joinToString(", ")}
YOUR GREETING STYLE: ${coach.greeting}

FORMAT (strict):
{"messages":["msg1","msg2"],"suggestions":[]}

RULES:
- messages: 1-3 short strings (max 80 chars each)
- Stay in character as ${coach.name}
- Use your ${coach.personality} tone
- suggestions: 0-2 items, only when helpful
- NO emojis in JSON, NO markdown, NO repetition
- Keep titles under 25 chars

Categories: CAREER, FINANCIAL, PHYSICAL, SOCIAL, EMOTIONAL, SPIRITUAL, FAMILY
Timelines: SHORT_TERM, MID_TERM, LONG_TERM
Frequencies: DAILY, WEEKLY
""".trimIndent()
        }

        /**
         * Get system prompt for a user-created custom coach
         */
        private fun getCustomCoachSystemPrompt(coach: CustomCoach): String {
            val characteristicsPrompt = if (coach.characteristics.isNotEmpty()) {
                CoachCharacteristics.getPromptForCharacteristics(coach.characteristics)
            } else ""

            return """
You are ${coach.name}, a personal coach in the LifePlanner app. Reply ONLY with valid JSON.

YOUR PERSONALITY AND INSTRUCTIONS:
${coach.systemPrompt}

${if (characteristicsPrompt.isNotEmpty()) "YOUR CHARACTERISTICS:\n$characteristicsPrompt" else ""}

FORMAT (strict):
{"messages":["msg1","msg2"],"suggestions":[]}

RULES:
- messages: 1-3 short strings (max 80 chars each)
- Stay in character as ${coach.name}
- suggestions: 0-2 items, only when helpful
- NO emojis in JSON, NO markdown, NO repetition
- Keep titles under 25 chars

Categories: CAREER, FINANCIAL, PHYSICAL, SOCIAL, EMOTIONAL, SPIRITUAL, FAMILY
Timelines: SHORT_TERM, MID_TERM, LONG_TERM
Frequencies: DAILY, WEEKLY
""".trimIndent()
        }

        /**
         * Get system prompt for a custom coach group (council-style)
         */
        private fun getCustomGroupSystemPrompt(
            group: CoachGroup,
            customCoaches: List<CustomCoach>,
            builtinCoaches: List<CoachPersona>
        ): String {
            val coachDescriptions = buildList {
                builtinCoaches.forEach { coach ->
                    add("${coach.name} (${coach.title}) - ${coach.personality}")
                }
                customCoaches.forEach { coach ->
                    val traits = coach.characteristics.take(2).joinToString(", ")
                    add("${coach.name} - ${traits.ifEmpty { "Custom Coach" }}")
                }
            }.mapIndexed { index, desc -> "${index + 1}. $desc" }.joinToString("\n")

            val coachNames = (builtinCoaches.map { it.name } + customCoaches.map { it.name })
                .joinToString(", ") { it.lowercase() }

            return """
You are "${group.name}" - a group of coaches having a discussion.

${if (group.description.isNotEmpty()) "GROUP PURPOSE: ${group.description}\n" else ""}
COACHES IN THIS GROUP:
$coachDescriptions

RESPONSE FORMAT (strict JSON):
{"messages":[{"coach":"coachname","text":"message"}],"suggestions":[]}

RULES:
- 2-4 coaches respond per message (pick most relevant ones)
- Use EXACT coach names (lowercase): $coachNames
- Each message: max 100 chars
- Coaches build on each other's points like a real meeting
- messages array contains objects with "coach" (lowercase name) and "text"
- suggestions: 0-2 items when genuinely helpful
- NO emojis, NO markdown

Categories: CAREER, FINANCIAL, PHYSICAL, SOCIAL, EMOTIONAL, SPIRITUAL, FAMILY
Timelines: SHORT_TERM, MID_TERM, LONG_TERM
Frequencies: DAILY, WEEKLY
""".trimIndent()
        }

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

        private fun buildResponseSchema(): ResponseSchema {
            return ResponseSchema(
                properties = mapOf(
                    "messages" to SchemaProperty(
                        type = "array",
                        description = "1-3 short messages, each under 150 chars",
                        maxItems = 3,
                        items = SchemaProperty(type = "string")
                    ),
                    "suggestions" to SchemaProperty(
                        type = "array",
                        description = "0-2 action suggestions",
                        maxItems = 2,
                        items = SchemaProperty(
                            type = "object",
                            properties = mapOf(
                                "type" to SchemaProperty(
                                    type = "string",
                                    enum = listOf("CREATE_GOAL", "CREATE_HABIT", "CREATE_JOURNAL", "CHECK_IN_HABIT")
                                ),
                                "label" to SchemaProperty(type = "string"),
                                "data" to SchemaProperty(
                                    type = "object",
                                    properties = mapOf(
                                        "title" to SchemaProperty(type = "string"),
                                        "description" to SchemaProperty(type = "string"),
                                        "category" to SchemaProperty(type = "string"),
                                        "timeline" to SchemaProperty(type = "string"),
                                        "frequency" to SchemaProperty(type = "string"),
                                        "content" to SchemaProperty(type = "string"),
                                        "mood" to SchemaProperty(type = "string"),
                                        "habitId" to SchemaProperty(type = "string"),
                                        "habitTitle" to SchemaProperty(type = "string")
                                    )
                                )
                            )
                        )
                    )
                )
            )
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

    override suspend fun createSession(title: String, coachId: String): ChatSession {
        val session = createChatSession(title = title, coachId = coachId)
        database.insertChatSession(
            id = session.id,
            title = session.title,
            createdAt = session.createdAt.toString(),
            lastMessageAt = session.lastMessageAt.toString(),
            summary = session.summary,
            coachId = session.coachId
        )
        return session
    }

    override suspend fun getSessionByCoachId(coachId: String): ChatSession? {
        val sessionEntity = database.getChatSessionByCoachId(coachId) ?: return null
        val messages = database.getMessagesBySessionId(sessionEntity.id).toDomainMessages()
        return sessionEntity.toDomain(messages)
    }

    override suspend fun getOrCreateSessionForCoach(coachId: String): ChatSession {
        // Check if a session already exists for this coach
        val existingSession = getSessionByCoachId(coachId)
        if (existingSession != null) {
            return existingSession
        }

        // Determine session title based on coach type
        val sessionTitle = when {
            isCustomCoachId(coachId) -> {
                val customId = extractCustomCoachId(coachId)
                val customCoach = coachRepository?.getCustomCoachById(customId)
                "Chat with ${customCoach?.name ?: "Custom Coach"}"
            }
            isGroupId(coachId) -> {
                val groupId = extractGroupId(coachId)
                val group = coachRepository?.getCoachGroupById(groupId)
                "Chat with ${group?.name ?: "Coach Group"}"
            }
            coachId == CoachPersona.COUNCIL_ID -> {
                "Chat with The Council"
            }
            else -> {
                val coachPersona = CoachPersona.getById(coachId)
                "Chat with ${coachPersona.name}"
            }
        }

        return createSession(title = sessionTitle, coachId = coachId)
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
        // Get session to determine if it's council mode or individual coach
        val session = getSessionById(sessionId)
        val coachId = session?.coachId ?: "luna_general"
        val isCouncilMode = coachId == CoachPersona.COUNCIL_ID
        val isCustomCoach = isCustomCoachId(coachId)
        val isCustomGroup = isGroupId(coachId)

        // Get conversation history BEFORE adding current message to avoid duplication
        val recentMessages = getRecentMessages(sessionId, 10)

        // Now add the user message to the database
        addUserMessage(sessionId, userMessage, relatedGoalId)

        val coachResponse = when {
            isCouncilMode -> {
                callGeminiCouncilChat(userMessage, recentMessages, userContext)
            }
            isCustomCoach && coachRepository != null -> {
                val customCoachId = extractCustomCoachId(coachId)
                val customCoach = coachRepository.getCustomCoachById(customCoachId)
                if (customCoach != null) {
                    callGeminiCustomCoachChat(userMessage, recentMessages, userContext, customCoach)
                } else {
                    callGeminiChat(userMessage, recentMessages, userContext, null)
                }
            }
            isCustomGroup && coachRepository != null -> {
                val groupId = extractGroupId(coachId)
                val group = coachRepository.getCoachGroupById(groupId)
                if (group != null) {
                    callGeminiCustomGroupChat(userMessage, recentMessages, userContext, group)
                } else {
                    callGeminiCouncilChat(userMessage, recentMessages, userContext)
                }
            }
            else -> {
                val coach = if (coachId == "luna_general") null else CoachPersona.getById(coachId)
                callGeminiChat(userMessage, recentMessages, userContext, coach)
            }
        }

        val metadataJson = if (coachResponse.suggestions.isNotEmpty()) {
            try {
                val metadata = ChatMessageMetadata(
                    coachSuggestions = coachResponse.suggestions
                )
                json.encodeToString(ChatMessageMetadata.serializer(), metadata)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        var lastMessage: ChatMessage? = null
        coachResponse.messages.forEachIndexed { index, messageText ->
            val isLastMessage = index == coachResponse.messages.lastIndex
            lastMessage = addAssistantMessage(
                sessionId = sessionId,
                content = messageText,
                metadata = if (isLastMessage) metadataJson else null
            )
        }

        return lastMessage ?: addAssistantMessage(sessionId, "I'm here to help!", null)
    }

    private suspend fun callGeminiChat(
        userMessage: String,
        conversationHistory: List<ChatMessage>,
        userContext: UserContext,
        coach: CoachPersona? = null
    ): CoachResponse {
        val coachName = coach?.name ?: "Luna"
        val coachPersonality = coach?.personality ?: "warm, encouraging, holistic thinker"

        // Build conversation history with last 10 messages for better context
        val historyText = if (conversationHistory.isNotEmpty()) {
            conversationHistory.takeLast(10).joinToString("\n") { msg ->
                "${if (msg.role == MessageRole.USER) "User" else coachName}: ${msg.content}"
            }
        } else ""

        // Log conversation history for debugging
        co.touchlab.kermit.Logger.d("ChatRepository") {
            "Sending message to $coachName with ${conversationHistory.size} history messages"
        }
        if (historyText.isNotEmpty()) {
            co.touchlab.kermit.Logger.d("ChatRepository") {
                "History:\n$historyText"
            }
        }

        val prompt = """
You are $coachName, a friendly ${coach?.title ?: "Life Coach"} in LifePlanner app.
${if (coach != null) "YOUR PERSONALITY: $coachPersonality\nYOUR SPECIALTIES: ${coach.specialties.joinToString(", ")}" else ""}

User Context:
- Level: ${userContext.level} (${userContext.totalXp} XP)
- Goals: ${userContext.activeGoals} active, ${userContext.completedGoals} completed
- Streak: ${userContext.currentStreak} days

${if (historyText.isNotEmpty()) "CONVERSATION HISTORY (use this to understand context):\n$historyText\n" else ""}
User's current message: $userMessage

INSTRUCTIONS:
1. Keep messages short (under 100 chars each), friendly and encouraging
2. Stay in character as $coachName with your ${coachPersonality} personality
3. READ THE CONVERSATION HISTORY - if user already provided details or answered questions, DO NOT ask again
4. Be HELPFUL - suggest goals/habits when:
   - User explicitly asks to create something
   - User has given enough context (what they want to achieve)
   - You've already asked a question in history and user responded
5. Only ask ONE quick clarifying question if the request is truly vague (like just "help me")
6. When creating a goal, include 3-5 meaningful milestones with weekOffset (1=week 1, 2=week 2, etc.)
7. If discussing an attached goal/habit, provide relevant advice

SUGGESTION FORMAT - Add to "suggestions" array when appropriate:
- CREATE_GOAL: {"type":"CREATE_GOAL","label":"Add Goal","data":{"title":"Goal title","description":"Description","category":"CAREER","timeline":"MID_TERM","milestones":[{"title":"Step 1","weekOffset":1}]}}
- CREATE_HABIT: {"type":"CREATE_HABIT","label":"Add Habit","data":{"title":"Habit title","description":"Description","category":"HEALTH","frequency":"DAILY"}}

Categories: CAREER, FINANCIAL, PHYSICAL, SOCIAL, EMOTIONAL, SPIRITUAL, FAMILY
Timelines: SHORT_TERM (30 days), MID_TERM (90 days), LONG_TERM (1 year)
Frequencies: DAILY, WEEKLY

IMPORTANT: If user explained what they want to achieve, CREATE the goal/habit immediately with suggestions! Don't just talk about it.
        """.trimIndent()

        // Log the full prompt for debugging
        co.touchlab.kermit.Logger.d("ChatRepository") {
            "Full prompt being sent:\n$prompt"
        }

        val requestBody = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", prompt)
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                putJsonObject("responseSchema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("messages") {
                            put("type", "array")
                            putJsonObject("items") {
                                put("type", "string")
                            }
                        }
                        putJsonObject("suggestions") {
                            put("type", "array")
                            putJsonObject("items") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("type") {
                                        put("type", "string")
                                        putJsonArray("enum") {
                                            add("CREATE_GOAL")
                                            add("CREATE_HABIT")
                                        }
                                    }
                                    putJsonObject("label") {
                                        put("type", "string")
                                    }
                                    putJsonObject("data") {
                                        put("type", "object")
                                        putJsonObject("properties") {
                                            putJsonObject("title") { put("type", "string") }
                                            putJsonObject("description") { put("type", "string") }
                                            putJsonObject("category") {
                                                put("type", "string")
                                                putJsonArray("enum") {
                                                    add("CAREER")
                                                    add("FINANCIAL")
                                                    add("PHYSICAL")
                                                    add("SOCIAL")
                                                    add("EMOTIONAL")
                                                    add("SPIRITUAL")
                                                    add("FAMILY")
                                                    add("HEALTH")
                                                    add("PRODUCTIVITY")
                                                    add("MINDFULNESS")
                                                    add("LEARNING")
                                                    add("PERSONAL")
                                                }
                                            }
                                            putJsonObject("timeline") {
                                                put("type", "string")
                                                putJsonArray("enum") {
                                                    add("SHORT_TERM")
                                                    add("MID_TERM")
                                                    add("LONG_TERM")
                                                }
                                            }
                                            putJsonObject("frequency") {
                                                put("type", "string")
                                                putJsonArray("enum") {
                                                    add("DAILY")
                                                    add("WEEKLY")
                                                }
                                            }
                                            // Milestones for goals
                                            putJsonObject("milestones") {
                                                put("type", "array")
                                                putJsonObject("items") {
                                                    put("type", "object")
                                                    putJsonObject("properties") {
                                                        putJsonObject("title") { put("type", "string") }
                                                        putJsonObject("weekOffset") { put("type", "integer") }
                                                    }
                                                    putJsonArray("required") { add("title") }
                                                }
                                            }
                                        }
                                        putJsonArray("required") {
                                            add("title")
                                        }
                                    }
                                }
                                putJsonArray("required") {
                                    add("type")
                                    add("label")
                                    add("data")
                                }
                            }
                        }
                    }
                    putJsonArray("required") {
                        add("messages")
                        add("suggestions")
                    }
                }
            }
        }

        return try {
            val response = httpClient.post {
                url("v1beta/models/$GEMINI_PRO:generateContent")
                parameter("key", BuildKonfig.GEMINI_API_KEY)
                setBody(requestBody)
            }

            val responseBody: GeminiApiResponse = response.body()
            val rawText = extractResponseText(responseBody)
            parseCoachResponse(rawText)
        } catch (e: Exception) {
            e.printStackTrace()
            CoachResponse(
                messages = listOf("I'm having trouble connecting right now. Could you try again in a moment?"),
                suggestions = emptyList()
            )
        }
    }

    /**
     * Custom coach chat: Uses user-defined personality and prompt
     */
    private suspend fun callGeminiCustomCoachChat(
        userMessage: String,
        conversationHistory: List<ChatMessage>,
        userContext: UserContext,
        customCoach: CustomCoach
    ): CoachResponse {
        val historyText = if (conversationHistory.isNotEmpty()) {
            conversationHistory.takeLast(10).joinToString("\n") { msg ->
                "${if (msg.role == MessageRole.USER) "User" else customCoach.name}: ${msg.content}"
            }
        } else ""

        val systemPrompt = getCustomCoachSystemPrompt(customCoach)
        val userContextInfo = buildUserContextInfo(userContext)

        val fullPrompt = """
$systemPrompt

$userContextInfo

${if (historyText.isNotEmpty()) "Recent conversation:\n$historyText\n" else ""}
User's message: $userMessage

Respond as ${customCoach.name} in the required JSON format.
""".trimIndent()

        return makeGeminiRequest(fullPrompt, customCoach.name)
    }

    /**
     * Custom group chat: Multiple coaches (custom and/or built-in) respond council-style
     */
    private suspend fun callGeminiCustomGroupChat(
        userMessage: String,
        conversationHistory: List<ChatMessage>,
        userContext: UserContext,
        group: CoachGroup
    ): CoachResponse {
        // Separate built-in and custom coaches from group members
        val builtinCoaches = mutableListOf<CoachPersona>()
        val customCoaches = mutableListOf<CustomCoach>()

        for (member in group.members) {
            when (member.coachType) {
                CoachType.BUILTIN -> {
                    CoachPersona.getById(member.coachId)?.let { builtinCoaches.add(it) }
                }
                CoachType.CUSTOM -> {
                    coachRepository?.getCustomCoachById(member.coachId)?.let { customCoaches.add(it) }
                }
            }
        }

        val allCoachNames = (builtinCoaches.map { it.name } + customCoaches.map { it.name })

        val historyText = if (conversationHistory.isNotEmpty()) {
            conversationHistory.takeLast(10).joinToString("\n") { msg ->
                if (msg.role == MessageRole.USER) {
                    "User: ${msg.content}"
                } else {
                    msg.content
                }
            }
        } else ""

        val systemPrompt = getCustomGroupSystemPrompt(group, customCoaches, builtinCoaches)
        val userContextInfo = buildUserContextInfo(userContext)

        val fullPrompt = """
$systemPrompt

$userContextInfo

${if (historyText.isNotEmpty()) "Recent conversation:\n$historyText\n" else ""}
User's message: $userMessage

Have 2-4 relevant coaches respond in the required JSON format.
""".trimIndent()

        return makeGeminiCouncilRequest(fullPrompt, allCoachNames)
    }

    /**
     * Build user context info for prompts
     */
    private fun buildUserContextInfo(userContext: UserContext): String {
        return """
Current User Context:
- Name: ${userContext.userName ?: "User"}
- Level: ${userContext.level} (${userContext.totalXp} XP)
- Current Streak: ${userContext.currentStreak} days
- Goals: ${userContext.activeGoals} active, ${userContext.completedGoals} completed
- Habit completion rate: ${(userContext.habitCompletionRate * 100).toInt()}%
""".trimIndent()
    }

    /**
     * Make a Gemini API request and parse the response (for individual coach)
     */
    private suspend fun makeGeminiRequest(prompt: String, coachName: String): CoachResponse {
        val requestBody = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.7)
                put("maxOutputTokens", 1024)
                put("responseMimeType", "application/json")
            }
        }

        return try {
            val response = httpClient.post {
                url("v1beta/models/$GEMINI_PRO:generateContent")
                parameter("key", BuildKonfig.GEMINI_API_KEY)
                setBody(requestBody)
            }

            val responseBody: GeminiApiResponse = response.body()
            val rawText = extractResponseText(responseBody)
            parseCoachResponse(rawText)
        } catch (e: Exception) {
            e.printStackTrace()
            CoachResponse(
                messages = listOf("I'm having trouble connecting right now. Could you try again?"),
                suggestions = emptyList()
            )
        }
    }

    /**
     * Make a Gemini API request for council-style responses
     */
    private suspend fun makeGeminiCouncilRequest(prompt: String, coachNames: List<String>): CoachResponse {
        val requestBody = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.7)
                put("maxOutputTokens", 1500)
                put("responseMimeType", "application/json")
            }
        }

        return try {
            val response = httpClient.post {
                url("v1beta/models/$GEMINI_PRO:generateContent")
                parameter("key", BuildKonfig.GEMINI_API_KEY)
                setBody(requestBody)
            }

            val responseBody: GeminiApiResponse = response.body()
            val rawText = extractResponseText(responseBody)
            parseCouncilResponse(rawText, coachNames)
        } catch (e: Exception) {
            e.printStackTrace()
            CoachResponse(
                messages = listOf("We're having trouble connecting right now. Please try again."),
                suggestions = emptyList()
            )
        }
    }

    /**
     * Council mode: Multiple coaches respond in a meeting-style discussion
     */
    private suspend fun callGeminiCouncilChat(
        userMessage: String,
        conversationHistory: List<ChatMessage>,
        userContext: UserContext
    ): CoachResponse {
        // Build conversation history - in council mode, messages may have coach prefixes
        val historyText = if (conversationHistory.isNotEmpty()) {
            conversationHistory.takeLast(10).joinToString("\n") { msg ->
                if (msg.role == MessageRole.USER) {
                    "User: ${msg.content}"
                } else {
                    // Assistant messages in council mode might have coach names embedded
                    msg.content
                }
            }
        } else ""

        co.touchlab.kermit.Logger.d("ChatRepository") {
            "Sending council message with ${conversationHistory.size} history messages"
        }

        val prompt = """
$COUNCIL_PERSONA

User Context:
- Level: ${userContext.level} (${userContext.totalXp} XP)
- Goals: ${userContext.activeGoals} active, ${userContext.completedGoals} completed
- Streak: ${userContext.currentStreak} days

${if (historyText.isNotEmpty()) "CONVERSATION HISTORY:\n$historyText\n" else ""}
User's current message: $userMessage

Remember:
- Pick 2-4 most relevant coaches to respond
- Each coach should add unique value, not repeat others
- Build on each other's points like a real meeting
- Mix serious and light-hearted tones based on coach personality

SUGGESTION FORMAT - Add to "suggestions" array when user wants to create something:
- CREATE_GOAL: {"type":"CREATE_GOAL","label":"Add Goal","data":{"title":"Goal title","description":"Description","category":"CAREER","timeline":"MID_TERM","milestones":[{"title":"Step 1","weekOffset":1}]}}
- CREATE_HABIT: {"type":"CREATE_HABIT","label":"Add Habit","data":{"title":"Habit title","description":"Description","category":"HEALTH","frequency":"DAILY"}}

Categories: CAREER, FINANCIAL, PHYSICAL, SOCIAL, EMOTIONAL, SPIRITUAL, FAMILY
If user explained their goal clearly, one coach should propose a goal/habit suggestion immediately!
        """.trimIndent()

        co.touchlab.kermit.Logger.d("ChatRepository") {
            "Council prompt being sent:\n$prompt"
        }

        val requestBody = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", prompt)
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                putJsonObject("responseSchema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("messages") {
                            put("type", "array")
                            putJsonObject("items") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("coach") {
                                        put("type", "string")
                                        putJsonArray("enum") {
                                            add("luna")
                                            add("alex")
                                            add("morgan")
                                            add("kai")
                                            add("sam")
                                            add("river")
                                            add("jamie")
                                        }
                                    }
                                    putJsonObject("text") {
                                        put("type", "string")
                                    }
                                }
                                putJsonArray("required") {
                                    add("coach")
                                    add("text")
                                }
                            }
                        }
                        putJsonObject("suggestions") {
                            put("type", "array")
                            putJsonObject("items") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("type") {
                                        put("type", "string")
                                        putJsonArray("enum") {
                                            add("CREATE_GOAL")
                                            add("CREATE_HABIT")
                                        }
                                    }
                                    putJsonObject("label") {
                                        put("type", "string")
                                    }
                                    putJsonObject("data") {
                                        put("type", "object")
                                        putJsonObject("properties") {
                                            putJsonObject("title") { put("type", "string") }
                                            putJsonObject("description") { put("type", "string") }
                                            putJsonObject("category") { put("type", "string") }
                                            putJsonObject("timeline") { put("type", "string") }
                                            putJsonObject("frequency") { put("type", "string") }
                                            putJsonObject("milestones") {
                                                put("type", "array")
                                                putJsonObject("items") {
                                                    put("type", "object")
                                                    putJsonObject("properties") {
                                                        putJsonObject("title") { put("type", "string") }
                                                        putJsonObject("weekOffset") { put("type", "integer") }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    putJsonArray("required") {
                        add("messages")
                        add("suggestions")
                    }
                }
            }
        }

        return try {
            val response = httpClient.post {
                url("v1beta/models/$GEMINI_PRO:generateContent")
                parameter("key", BuildKonfig.GEMINI_API_KEY)
                setBody(requestBody)
            }

            val responseBody: GeminiApiResponse = response.body()
            val rawText = extractResponseText(responseBody)
            parseCouncilResponse(rawText)
        } catch (e: Exception) {
            e.printStackTrace()
            CoachResponse(
                messages = listOf("Luna: The council is having a moment. Let me help you directly!"),
                suggestions = emptyList()
            )
        }
    }

    /**
     * Parse council response where messages have coach + text structure
     */
    @OptIn(ExperimentalUuidApi::class)
    private fun parseCouncilResponse(rawText: String): CoachResponse {
        return parseCouncilResponse(rawText, null)
    }

    /**
     * Parse council response with optional custom coach names
     * Used for custom groups where coach names are dynamic
     */
    @OptIn(ExperimentalUuidApi::class)
    private fun parseCouncilResponse(rawText: String, coachNames: List<String>?): CoachResponse {
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
            e.printStackTrace()
            CoachResponse(
                messages = listOf("$fallbackCoach: Let me try again... What would you like help with?"),
                suggestions = emptyList()
            )
        }
    }

    private fun extractResponseText(response: GeminiApiResponse): String {
        return try {
            response.candidates
                .firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?: "I couldn't generate a response. Please try again."
        } catch (e: Exception) {
            "Something went wrong parsing the response. Let's try again!"
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun parseCoachResponse(rawText: String): CoachResponse {
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
            e.printStackTrace()
            CoachResponse(
                messages = listOf("Let me try that again... What would you like help with?"),
                suggestions = emptyList()
            )
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun convertToCoachSuggestion(suggestionData: SuggestionData): CoachSuggestion? {
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
                frequency = payload.frequency ?: "DAILY"
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

    override suspend fun getUserContext(): UserContext {
        val userProgress = database.getUserProgressEntity()
        val activeGoals = database.getActiveGoals()
        val completedGoals = database.getCompletedGoals()
        val allGoals = database.getAllGoals()

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val weekFromNow = today.toString()

        val categoryCount = database.getGoalCountByCategory()
        val topCategories = categoryCount.entries
            .sortedByDescending { it.value }
            .take(3)
            .map { it.key }

        val recentMilestones = mutableListOf<String>()

        return UserContext(
            userName = null,
            totalGoals = allGoals.size,
            completedGoals = completedGoals.size,
            activeGoals = activeGoals.size,
            currentStreak = userProgress?.currentStreak?.toInt() ?: 0,
            totalXp = userProgress?.totalXp?.toInt() ?: 0,
            level = userProgress?.currentLevel?.toInt() ?: 1,
            recentMilestones = recentMilestones,
            upcomingDeadlines = emptyList(),
            habitCompletionRate = 0f,
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

    override suspend fun markSuggestionExecuted(messageId: String, suggestionId: String) {
        val message = database.getMessageById(messageId) ?: return

        val currentMetadata = message.metadata?.let { metadataString ->
            try {
                json.decodeFromString<ChatMessageMetadata>(metadataString)
            } catch (e: Exception) {
                null
            }
        }

        val updatedMetadata = currentMetadata?.copy(
            executedSuggestionIds = currentMetadata.executedSuggestionIds + suggestionId
        ) ?: ChatMessageMetadata(executedSuggestionIds = setOf(suggestionId))

        val updatedMetadataJson = json.encodeToString(ChatMessageMetadata.serializer(), updatedMetadata)
        database.updateChatMessageMetadata(messageId, updatedMetadataJson)
    }
}