package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.data.mapper.createChatMessage
import az.tribe.lifeplanner.data.mapper.createChatSession
import az.tribe.lifeplanner.data.mapper.toDomain
import az.tribe.lifeplanner.data.mapper.toDomainMessages
import az.tribe.lifeplanner.data.mapper.toDomainSessions
import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.domain.model.ChatMessage
import az.tribe.lifeplanner.domain.model.ChatMessageMetadata
import az.tribe.lifeplanner.domain.model.ChatSession
import az.tribe.lifeplanner.domain.model.CoachPersona
import az.tribe.lifeplanner.domain.model.CoachResponse
import az.tribe.lifeplanner.domain.model.MessageRole
import az.tribe.lifeplanner.domain.model.UserContext
import az.tribe.lifeplanner.domain.repository.ChatRepository
import az.tribe.lifeplanner.domain.repository.CoachRepository
import az.tribe.lifeplanner.domain.repository.StreamingChatEvent
import az.tribe.lifeplanner.domain.repository.UserSituationRepository
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import az.tribe.lifeplanner.infrastructure.*
import az.tribe.lifeplanner.infrastructure.toUserSituationDomain
import co.touchlab.kermit.Logger
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

// ============================================================================
// REPOSITORY IMPLEMENTATION
// ============================================================================

class ChatRepositoryImpl(
    internal val database: SharedDatabase,
    internal val aiProxy: AiProxyService,
    internal val coachRepository: CoachRepository? = null,
    private val syncManager: SyncManager? = null,
    private val userSituationRepository: UserSituationRepository? = null,
    internal val orchestrator: CoachOrchestrator = CoachOrchestrator(),
    /** A short summary of the user's last week across the areas, read when a message is sent. */
    private val lifeDigest: (suspend () -> String?)? = null,
) : ChatRepository {

    internal val json = Json {
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
        syncManager?.requestSync()
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
        syncManager?.requestSync()
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
        syncManager?.requestSync()

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
        syncManager?.requestSync()

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

        // Load user situation for personalized prompts.
        //
        // Falls back to an empty one rather than null. A user with no stored situation is not a
        // user we have nothing to say about — it is the case where every slot is missing, which is
        // exactly when the coach should be asking. Returning null skipped the whole situation block
        // including that instruction, so the asking behaviour was dead for every new user: the only
        // ones it existed for.
        val situation = userSituationRepository?.let {
            try {
                database.getUserSituation()?.toUserSituationDomain()
                    ?: az.tribe.lifeplanner.domain.model.UserSituation()
            } catch (_: Exception) {
                az.tribe.lifeplanner.domain.model.UserSituation()
            }
        }

        // Load active goals for habit linking
        val goalSummaries = try {
            database.getActiveGoals().map { g -> "${g.id}: ${g.title} (${g.category})" }
        } catch (_: Exception) { emptyList() }

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
                    callGeminiChat(userMessage, recentMessages, userContext, null, situation, goalSummaries)
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
                callGeminiChat(userMessage, recentMessages, userContext, coach, situation, goalSummaries)
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

    override fun sendMessageStreaming(
        sessionId: String,
        userMessage: String,
        userContext: UserContext,
        relatedGoalId: String?
    ): Flow<StreamingChatEvent> = flow {
        // Get session to determine coach
        val session = getSessionById(sessionId)
        val coachId = session?.coachId ?: "luna_general"
        val coach = if (coachId == "luna_general") null else {
            try { CoachPersona.getById(coachId) } catch (_: Exception) { null }
        }

        // Get conversation history BEFORE adding current message
        val recentMessages = getRecentMessages(sessionId, 10)

        // Load user situation for personalized prompts.
        //
        // Falls back to an empty one rather than null. A user with no stored situation is not a
        // user we have nothing to say about — it is the case where every slot is missing, which is
        // exactly when the coach should be asking. Returning null skipped the whole situation block
        // including that instruction, so the asking behaviour was dead for every new user: the only
        // ones it existed for.
        val situation = userSituationRepository?.let {
            try {
                database.getUserSituation()?.toUserSituationDomain()
                    ?: az.tribe.lifeplanner.domain.model.UserSituation()
            } catch (_: Exception) {
                az.tribe.lifeplanner.domain.model.UserSituation()
            }
        }

        // Load active goals for habit linking
        val goalSummaries = try {
            database.getActiveGoals().map { g -> "${g.id}: ${g.title} (${g.category})" }
        } catch (_: Exception) { emptyList() }

        // Save user message to DB and notify the ViewModel immediately
        val savedUserMessage = addUserMessage(sessionId, userMessage, relatedGoalId)
        emit(StreamingChatEvent.UserMessageSaved(savedUserMessage))

        // Resolve custom coach and persona override before building the prompt
        val customCoach = if (isCustomCoachId(coachId) && coachRepository != null) {
            val customId = extractCustomCoachId(coachId)
            coachRepository.getCustomCoachById(customId)
        } else null

        val personaOverride = if (customCoach == null && coach != null) {
            coachRepository?.getPersonaOverride(coach.id)
        } else null

        // Build plain-text system prompt (no JSON schema for streaming)
        val systemPrompt = buildStreamingSystemPrompt(
            userContext = userContext,
            coach = coach,
            conversationHistory = recentMessages,
            customCoach = customCoach,
            personaOverride = personaOverride,
            situation = situation,
            orchestrator = orchestrator,
            activeGoals = goalSummaries,
            lifeDigest = runCatching { lifeDigest?.invoke() }.getOrNull()
        )

        // Build messages for the proxy
        val proxyMessages = listOf(
            AiProxyService.ChatMessage(role = "user", content = userMessage)
        )

        val accumulated = StringBuilder()

        try {
            aiProxy.chatStream(
                messages = proxyMessages,
                systemPrompt = systemPrompt
            ).collect { event ->
                when (event) {
                    is AiProxyService.StreamEvent.TextChunk -> {
                        accumulated.append(event.text)
                        // Strip suggestion tags from display during streaming
                        val tagPattern = Regex("""\[(SUGGEST_(GOAL|HABIT|JOURNAL|ACTION)|FOLLOWUPS|UPDATE_SITUATION)[^\]]*\]""")
                        // A tag still arriving ("[FOLLOWUPS:Make it") is hidden until it closes.
                        val displayText = accumulated.toString().replace(tagPattern, "")
                            .replace(Regex("""\[[A-Z_]*(:[^\]]*)?$"""), "").trimEnd()
                        val chunkText = event.text.replace(tagPattern, "")
                        emit(StreamingChatEvent.PartialText(chunkText, displayText))
                    }
                    is AiProxyService.StreamEvent.Done -> {
                        val rawText = accumulated.toString().ifEmpty { event.fullText }

                        // Write back any profile updates the AI extracted
                        persistMemoryUpdate(rawText, situation)

                        // Parse inline suggestion tags directly, no second API call needed
                        val (withoutFollowUps, followUps) = splitFollowUps(
                            rawText.replace(Regex("""\[UPDATE_SITUATION:[^\]]+\]"""), "")
                        )
                        val (cleanedText, suggestions) = parseInlineSuggestions(withoutFollowUps)
                        Logger.d("ChatRepositoryImpl") {
                            "Parsed ${suggestions.size} inline suggestions from response"
                        }

                        val metadataJson = if (suggestions.isNotEmpty() || followUps.isNotEmpty()) {
                            try {
                                val metadata = ChatMessageMetadata(coachSuggestions = suggestions, followUps = followUps)
                                json.encodeToString(ChatMessageMetadata.serializer(), metadata)
                            } catch (e: Exception) {
                                Logger.w("ChatRepositoryImpl") { "Metadata encoding failed: ${e.message}" }
                                null
                            }
                        } else null

                        val savedMessage = addAssistantMessage(sessionId, cleanedText, metadataJson)
                        emit(StreamingChatEvent.Completed(savedMessage))
                    }
                    is AiProxyService.StreamEvent.Error -> {
                        // Save partial text if we have any
                        if (accumulated.isNotEmpty()) {
                            addAssistantMessage(sessionId, accumulated.toString(), null)
                        }
                        emit(StreamingChatEvent.Error(event.message))
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("ChatRepositoryImpl") { "Streaming failed: ${e.message}" }
            // Save partial text if we have any
            if (accumulated.isNotEmpty()) {
                val saved = addAssistantMessage(sessionId, accumulated.toString(), null)
                emit(StreamingChatEvent.Completed(saved))
            } else {
                emit(StreamingChatEvent.Error(e.message ?: "Streaming failed"))
            }
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

        val userEntity = database { db -> db.lifePlannerDBQueries.getCurrentUser().executeAsOneOrNull() }
        val userName = userEntity?.displayName?.takeIf { it.isNotBlank() }

        return UserContext(
            userName = userName,
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
        syncManager?.requestSync()
    }

    override suspend fun deleteOldSessions(beforeDate: String) {
        database.deleteOldChatSessions(beforeDate)
        syncManager?.requestSync()
    }

    override suspend fun getSessionCount(): Long {
        return database.getChatSessionCount()
    }

    // ===== UserSituation helpers =====

    internal suspend fun persistMemoryUpdate(
        rawText: String,
        currentSituation: az.tribe.lifeplanner.domain.model.UserSituation?
    ) {
        val repo = userSituationRepository ?: return
        val tag = orchestrator.parseMemoryUpdateTag(rawText) ?: return
        val userId = database.getCurrentUserId() ?: return
        val base = currentSituation ?: repo.getOrCreate(userId)
        val updated = orchestrator.applyUpdate(tag, base)
        try {
            repo.upsert(userId, updated)
        } catch (e: Exception) {
            Logger.w("ChatRepositoryImpl") { "Memory update write failed: ${e.message}" }
        }
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
        syncManager?.requestSync()
    }
}
