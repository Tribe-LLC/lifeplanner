package az.tribe.lifeplanner.data.mapper

import az.tribe.lifeplanner.database.ChatMessageEntity
import az.tribe.lifeplanner.database.ChatSessionEntity
import az.tribe.lifeplanner.domain.model.ChatMessage
import az.tribe.lifeplanner.domain.model.ChatMessageMetadata
import az.tribe.lifeplanner.domain.model.ChatSession
import az.tribe.lifeplanner.domain.model.MessageRole
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val json = Json { ignoreUnknownKeys = true }

/**
 * ChatMessageEntity to Domain mapper
 */
fun ChatMessageEntity.toDomain(): ChatMessage = ChatMessage(
    id = id,
    content = content,
    role = MessageRole.valueOf(role),
    timestamp = LocalDateTime.parse(timestamp),
    relatedGoalId = relatedGoalId,
    metadata = metadata?.let {
        try {
            json.decodeFromString<ChatMessageMetadata>(it)
        } catch (e: Exception) {
            null
        }
    }
)

/**
 * ChatMessage Domain to Entity mapper
 */
fun ChatMessage.toEntity(sessionId: String): ChatMessageEntity = ChatMessageEntity(
    id = id,
    sessionId = sessionId,
    content = content,
    role = role.name,
    timestamp = timestamp.toString(),
    relatedGoalId = relatedGoalId,
    metadata = metadata?.let { json.encodeToString(ChatMessageMetadata.serializer(), it) }
)

/**
 * ChatSessionEntity to Domain mapper (without messages)
 */
fun ChatSessionEntity.toDomain(messages: List<ChatMessage> = emptyList()): ChatSession = ChatSession(
    id = id,
    title = title,
    messages = messages,
    createdAt = LocalDateTime.parse(createdAt),
    lastMessageAt = LocalDateTime.parse(lastMessageAt),
    summary = summary
)

/**
 * List mappers
 */
fun List<ChatMessageEntity>.toDomainMessages(): List<ChatMessage> = map { it.toDomain() }

fun List<ChatSessionEntity>.toDomainSessions(): List<ChatSession> = map { it.toDomain() }

/**
 * Create a new ChatMessage
 */
@OptIn(ExperimentalUuidApi::class)
fun createChatMessage(
    content: String,
    role: MessageRole,
    relatedGoalId: String? = null,
    metadata: ChatMessageMetadata? = null
): ChatMessage = ChatMessage(
    id = Uuid.random().toString(),
    content = content,
    role = role,
    timestamp = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    relatedGoalId = relatedGoalId,
    metadata = metadata
)

/**
 * Create a new ChatSession
 */
@OptIn(ExperimentalUuidApi::class)
fun createChatSession(
    title: String = "New Chat"
): ChatSession {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return ChatSession(
        id = Uuid.random().toString(),
        title = title,
        messages = emptyList(),
        createdAt = now,
        lastMessageAt = now,
        summary = null
    )
}
