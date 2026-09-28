package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.domain.model.ChatMessage
import az.tribe.lifeplanner.domain.model.CoachCharacteristics
import az.tribe.lifeplanner.domain.model.CoachGroup
import az.tribe.lifeplanner.domain.model.CoachPersona
import az.tribe.lifeplanner.domain.model.CustomCoach
import az.tribe.lifeplanner.domain.model.MessageRole
import az.tribe.lifeplanner.domain.model.UserContext
import az.tribe.lifeplanner.domain.model.UserSituation

// ============================================================================
// SYSTEM PROMPT CONSTANTS (hardcoded defaults, overridden by SystemPromptStore)
// ============================================================================

internal fun getCoachPersona(): String = SystemPromptStore.getOrDefault("coach_persona", COACH_PERSONA_DEFAULT)
internal fun getCouncilPersona(): String = SystemPromptStore.getOrDefault("council", COUNCIL_PERSONA_DEFAULT)
internal fun getStreamingInstructions(coachName: String): String =
    SystemPromptStore.getOrDefault("streaming_instructions", STREAMING_INSTRUCTIONS_DEFAULT)
        .replace("{coach_name}", coachName)

private const val COACH_PERSONA_DEFAULT = """
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

Categories: CAREER, MONEY, BODY, PEOPLE, WELLBEING, PURPOSE
Timelines: SHORT_TERM, MID_TERM, LONG_TERM
Frequencies: DAILY, WEEKLY
"""

private const val COUNCIL_PERSONA_DEFAULT = """
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

Categories: CAREER, MONEY, BODY, PEOPLE, WELLBEING, PURPOSE
Timelines: SHORT_TERM, MID_TERM, LONG_TERM
Frequencies: DAILY, WEEKLY
"""

private const val STREAMING_INSTRUCTIONS_DEFAULT = """INSTRUCTIONS:
- Respond in plain text (NOT JSON). Write naturally.
- Keep responses to 1-3 sentences. Get to the point.
- Stay in character as {coach_name}.
- Give actionable advice, not cheerleading. No filler phrases like "That's great!", "I love that!", "Absolutely!".
- Don't repeat back what the user said.
- Ask at most 1 follow-up question, and only if truly needed.
- If the user already provided details in the conversation history, don't re-ask.
- NEVER claim you have created, added, or set up a goal, habit, or journal entry. You cannot do that directly. The user will see action buttons to create items themselves.
- SUGGESTION TAGS: Only append a hidden suggestion tag when the user EXPLICITLY asks to create, add, or start a goal, habit, or journal entry. Do NOT suggest on casual mentions, just have a conversation. If unsure whether they want to create something, ask first. Use at most 1 tag per response, placed at the very end:
  For a goal: [SUGGEST_GOAL:title|description|CATEGORY|TIMELINE]
  For a habit: [SUGGEST_HABIT:title|description|CATEGORY|FREQUENCY|goalId|targetCount|targetUnit]
    - goalId: the goal ID from ACTIVE GOALS this habit supports (leave empty if not linked to a goal)
    - targetCount: numeric target (e.g. 2, 8, 10), use 1 if no specific quantity
    - targetUnit: unit for the target (e.g. L, min, times, glasses, pages, steps), leave empty if no unit
    - Example: [SUGGEST_HABIT:Drink water daily|Stay hydrated|BODY|DAILY|goal-abc123|2|L]
    - Example: [SUGGEST_HABIT:Morning meditation|10 min mindfulness|WELLBEING|DAILY||10|min]
  For a journal entry: [SUGGEST_JOURNAL:title|content|MOOD]
  Categories: CAREER, MONEY, BODY, PEOPLE, WELLBEING, PURPOSE
  Timelines: SHORT_TERM, MID_TERM, LONG_TERM
  Frequencies: DAILY, WEEKLY
  Moods: HAPPY, SAD, ANXIOUS, CALM, EXCITED, GRATEFUL, ANGRY, NEUTRAL
- GOAL CLARIFICATION ANSWERS: When the user sends a message starting with "Goal clarification answers for", they have answered personalisation questions about their goal. Use those answers to IMMEDIATELY suggest a highly specific and personalised goal using [SUGGEST_GOAL:...]. Make the title and description concrete and tailored to their answers. For that kind of message only, go straight to the suggestion instead of asking anything further."""

// Always appended LAST in the streaming prompt (highest recency) so the model reliably emits the
// [SUGGEST_*] tag. gemini-2.5-flash tends to ignore the mid-prompt "hidden tag" instruction and
// reply in prose only, which produces "Parsed 0 inline suggestions" and no Add button. A forceful,
// example-led rule at the very end restores the create-from-chat flow. Independent of
// SystemPromptStore so it holds even when streaming_instructions is overridden from the backend.
private const val TAG_ENFORCEMENT = """
CRITICAL OUTPUT RULE (do not skip):
If the user wants to create, add, start, or set up a goal or a habit, or confirms one you just offered (for example replies "yes", "sure", "do it", "provide it"), you MUST end your reply with exactly ONE tag on its own final line. This tag is what makes the app show the "Add" button; without it the user cannot create anything. Write the tag literally, with no backticks and no code block, and do not explain it.
  Goal:  [SUGGEST_GOAL:title|description|CATEGORY|TIMELINE]
  Habit: [SUGGEST_HABIT:title|description|CATEGORY|FREQUENCY]
  Categories: CAREER, MONEY, BODY, PEOPLE, WELLBEING, PURPOSE. Timelines: SHORT_TERM, MID_TERM, LONG_TERM. Frequencies: DAILY, WEEKLY.
Example reply (the tag is the final line):
Love it, let's make it official.
[SUGGEST_GOAL:Run a 5K|Build up to running 5 km without stopping|BODY|SHORT_TERM]"""

// ============================================================================
// PROMPT BUILDER FUNCTIONS
// ============================================================================

/**
 * Get coach-specific system prompt
 */
internal suspend fun getCoachSystemPrompt(coach: CoachPersona, personaOverride: String? = null): String {
    return """
You are ${coach.name}, a ${coach.title} in the LifePlanner app. Reply ONLY with valid JSON.

YOUR PERSONALITY: ${coach.personality}
YOUR SPECIALTIES: ${coach.specialties.joinToString(", ")}
YOUR GREETING STYLE: ${coach.greeting}
${if (personaOverride != null) "USER'S CUSTOMIZATION (follow this closely): $personaOverride" else ""}

FORMAT (strict):
{"messages":["msg1","msg2"],"suggestions":[]}

RULES:
- messages: 1-3 short strings (max 80 chars each)
- Stay in character as ${coach.name}
- Use your ${coach.personality} tone
- suggestions: 0-2 items, only when helpful
- NO emojis in JSON, NO markdown, NO repetition
- Keep titles under 25 chars

Categories: CAREER, MONEY, BODY, PEOPLE, WELLBEING, PURPOSE
Timelines: SHORT_TERM, MID_TERM, LONG_TERM
Frequencies: DAILY, WEEKLY
""".trimIndent()
}

/**
 * Get system prompt for a user-created custom coach
 */
internal fun getCustomCoachSystemPrompt(coach: CustomCoach): String {
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

Categories: CAREER, MONEY, BODY, PEOPLE, WELLBEING, PURPOSE
Timelines: SHORT_TERM, MID_TERM, LONG_TERM
Frequencies: DAILY, WEEKLY
""".trimIndent()
}

/**
 * Get system prompt for a custom coach group (council-style)
 */
internal fun getCustomGroupSystemPrompt(
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

Categories: CAREER, MONEY, BODY, PEOPLE, WELLBEING, PURPOSE
Timelines: SHORT_TERM, MID_TERM, LONG_TERM
Frequencies: DAILY, WEEKLY
""".trimIndent()
}

internal fun buildSystemPrompt(userContext: UserContext): String {
    return """
${getCoachPersona()}

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

internal fun buildResponseSchema(): ResponseSchema {
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

/**
 * Build user context info for prompts
 */
internal fun buildUserContextInfo(userContext: UserContext): String {
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
 * Build plain-text streaming system prompt (no JSON schema for streaming)
 */
internal fun buildStreamingSystemPrompt(
    userContext: UserContext,
    coach: CoachPersona?,
    conversationHistory: List<ChatMessage>,
    customCoach: CustomCoach?,
    personaOverride: String?,
    situation: UserSituation? = null,
    orchestrator: CoachOrchestrator? = null,
    activeGoals: List<String> = emptyList(),
    lifeDigest: String? = null,
): String {
    val coachName = coach?.name ?: "Luna"
    val coachPersonality = coach?.personality ?: "warm, encouraging, holistic thinker"

    val historyText = if (conversationHistory.isNotEmpty()) {
        conversationHistory.takeLast(10).joinToString("\n") { msg ->
            "${if (msg.role == MessageRole.USER) "User" else coachName}: ${msg.content}"
        }
    } else ""

    val coachIntro = if (customCoach != null) {
        """
You are ${customCoach.name}, a personal coach in the LifePlanner app.
YOUR PERSONALITY AND INSTRUCTIONS: ${customCoach.systemPrompt}
""".trimIndent()
    } else {
        """
You are $coachName, a friendly ${coach?.title ?: "Life Coach"} in LifePlanner app.
${if (coach != null) "YOUR PERSONALITY: $coachPersonality\nYOUR SPECIALTIES: ${coach.specialties.joinToString(", ")}" else ""}
${if (personaOverride != null) "USER'S CUSTOMIZATION (follow this closely): $personaOverride" else ""}
""".trimIndent()
    }

    // An absent situation is not "nothing to say" — it is the case where everything is missing,
    // which is exactly when the coach should be asking. Skipping the block on null dropped that
    // instruction for every user who had no stored situation yet.
    val situationBlock = if (orchestrator != null && customCoach == null) {
        orchestrator.buildSituationContext(situation ?: UserSituation(), coach)
    } else ""

    val goalsBlock = if (activeGoals.isNotEmpty()) {
        "ACTIVE GOALS (use goalId when suggesting linked habits):\n" +
            activeGoals.joinToString("\n") { "  - $it" }
    } else ""

    return """
$coachIntro

${if (situationBlock.isNotEmpty()) "$situationBlock\n" else ""}User Context:
- Name: ${userContext.userName ?: "User"}
- Level: ${userContext.level} (${userContext.totalXp} XP)
- Goals: ${userContext.activeGoals} active, ${userContext.completedGoals} completed
- Streak: ${userContext.currentStreak} days

${if (!lifeDigest.isNullOrBlank()) "THE USER'S LAST 7 DAYS IN THE APP (their own data; use it when it helps, never recite it back unasked, be kind about misses):\n$lifeDigest\n\n" else ""}${if (goalsBlock.isNotEmpty()) "$goalsBlock\n" else ""}${if (historyText.isNotEmpty()) "CONVERSATION HISTORY:\n$historyText\n" else ""}
${getStreamingInstructions(coachName)}
${if (situationBlock.isNotEmpty()) SITUATION_UPDATE_INSTRUCTION else ""}
$TAG_ENFORCEMENT
""".trimIndent()
}
