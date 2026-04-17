package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.domain.model.ChatMessage
import az.tribe.lifeplanner.domain.model.CoachGroup
import az.tribe.lifeplanner.domain.model.CoachPersona
import az.tribe.lifeplanner.domain.model.CoachResponse
import az.tribe.lifeplanner.domain.model.CoachType
import az.tribe.lifeplanner.domain.model.CustomCoach
import az.tribe.lifeplanner.domain.model.MessageRole
import az.tribe.lifeplanner.domain.model.UserContext
import co.touchlab.kermit.Logger
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// ============================================================================
// AI CALL HELPERS (extension functions on ChatRepositoryImpl)
// ============================================================================

internal suspend fun ChatRepositoryImpl.callGeminiChat(
    userMessage: String,
    conversationHistory: List<ChatMessage>,
    userContext: UserContext,
    coach: CoachPersona? = null
): CoachResponse {
    val coachName = coach?.name ?: "Luna"
    val coachPersonality = coach?.personality ?: "warm, encouraging, holistic thinker"
    val personaOverride = coach?.let { coachRepository?.getPersonaOverride(it.id) }

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
${if (personaOverride != null) "USER'S CUSTOMIZATION (follow this closely): $personaOverride" else ""}

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
- CREATE_GOAL: {"type":"CREATE_GOAL","label":"Add Goal","data":{"title":"<specific goal name derived from user's message>","description":"<1-2 sentence description of what user wants to achieve>","category":"CAREER","timeline":"MID_TERM","milestones":[{"title":"Step 1","weekOffset":1}]}}
- CREATE_HABIT: {"type":"CREATE_HABIT","label":"Add Habit","data":{"title":"<specific habit name derived from user's message>","description":"<why this habit helps the user>","category":"HEALTH","frequency":"DAILY"}}

Categories: CAREER, MONEY, BODY, PEOPLE, WELLBEING, PURPOSE
Timelines: SHORT_TERM (30 days), MID_TERM (90 days), LONG_TERM (1 year)
Frequencies: DAILY, WEEKLY

CRITICAL: title and description MUST contain real content derived from the user's message. Never use placeholder text like "Goal title" or "Description". If you don't have enough context, ask a clarifying question first instead of suggesting.
IMPORTANT: If user explained what they want to achieve, include the goal/habit in the suggestions array immediately. In your messages, say you're suggesting it — never claim you created it. The user will see action buttons to create items themselves.
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
                                                add("MONEY")
                                                add("BODY")
                                                add("PEOPLE")
                                                add("WELLBEING")
                                                add("PURPOSE")
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
                                        add("description")
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
        val proxyMessages = buildAiProxyMessages(requestBody)
        val systemPrompt = extractSystemPrompt(requestBody)
        val responseSchema = extractResponseSchema(requestBody)
        Logger.d("ChatRepositoryImpl") {
            "Calling aiProxy.chat: ${proxyMessages.size} messages, " +
                "systemPrompt=${systemPrompt != null}, schema=${responseSchema != null}"
        }
        val rawText = aiProxy.chat(
            messages = proxyMessages,
            systemPrompt = systemPrompt,
            responseSchema = responseSchema
        )
        Logger.d("ChatRepositoryImpl") { "AI response received (${rawText.length} chars)" }
        parseCoachResponse(rawText, json)
    } catch (e: Exception) {
        Logger.e("ChatRepositoryImpl") { "Coach chat request failed: ${e.message}\n${e.stackTraceToString()}" }
        CoachResponse(
            messages = listOf("I'm having trouble connecting right now. Could you try again in a moment?"),
            suggestions = emptyList()
        )
    }
}

/**
 * Custom coach chat: Uses user-defined personality and prompt
 */
internal suspend fun ChatRepositoryImpl.callGeminiCustomCoachChat(
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
internal suspend fun ChatRepositoryImpl.callGeminiCustomGroupChat(
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
 * Make a Gemini API request and parse the response (for individual coach)
 */
internal suspend fun ChatRepositoryImpl.makeGeminiRequest(prompt: String, coachName: String): CoachResponse {
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
        val rawText = aiProxy.chat(
            messages = buildAiProxyMessages(requestBody),
            systemPrompt = extractSystemPrompt(requestBody),
            responseSchema = extractResponseSchema(requestBody)
        )
        parseCoachResponse(rawText, json)
    } catch (e: Exception) {
        Logger.e("ChatRepositoryImpl") { "Custom coach chat request failed: ${e.message}\n${e.stackTraceToString()}" }
        CoachResponse(
            messages = listOf("I'm having trouble connecting right now. Could you try again?"),
            suggestions = emptyList()
        )
    }
}

/**
 * Make a Gemini API request for council-style responses
 */
internal suspend fun ChatRepositoryImpl.makeGeminiCouncilRequest(prompt: String, coachNames: List<String>): CoachResponse {
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
        val rawText = aiProxy.chat(
            messages = buildAiProxyMessages(requestBody),
            systemPrompt = extractSystemPrompt(requestBody),
            responseSchema = extractResponseSchema(requestBody)
        )
        parseCouncilResponse(rawText, coachNames, json)
    } catch (e: Exception) {
        Logger.e("ChatRepositoryImpl") { "Council chat request failed: ${e.message}\n${e.stackTraceToString()}" }
        CoachResponse(
            messages = listOf("We're having trouble connecting right now. Please try again."),
            suggestions = emptyList()
        )
    }
}

/**
 * Council mode: Multiple coaches respond in a meeting-style discussion
 */
internal suspend fun ChatRepositoryImpl.callGeminiCouncilChat(
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
${getCouncilPersona()}

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

Categories: CAREER, MONEY, BODY, PEOPLE, WELLBEING, PURPOSE
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
        val rawText = aiProxy.chat(
            messages = buildAiProxyMessages(requestBody),
            systemPrompt = extractSystemPrompt(requestBody),
            responseSchema = extractResponseSchema(requestBody)
        )
        parseCouncilResponse(rawText, json)
    } catch (e: Exception) {
        Logger.e("ChatRepositoryImpl") { "Council meeting request failed: ${e.message}\n${e.stackTraceToString()}" }
        CoachResponse(
            messages = listOf("Luna: The council is having a moment. Let me help you directly!"),
            suggestions = emptyList()
        )
    }
}
