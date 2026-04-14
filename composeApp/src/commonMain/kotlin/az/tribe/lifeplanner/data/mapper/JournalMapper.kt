package az.tribe.lifeplanner.data.mapper

import az.tribe.lifeplanner.database.JournalEntryEntity
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.JournalEntry
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun JournalEntryEntity.toDomain(): JournalEntry {
    return JournalEntry(
        id = id,
        title = title,
        content = content,
        mood = Mood.fromString(mood),
        linkedGoalId = linkedGoalId,
        linkedHabitId = linkedHabitId,
        promptUsed = promptUsed,
        tags = parseTags(tags),
        date = LocalDate.parse(date),
        createdAt = parseLocalDateTime(createdAt),
        updatedAt = updatedAt?.let { parseLocalDateTime(it) }
    )
}

fun JournalEntry.toEntity(): JournalEntryEntity {
    return JournalEntryEntity(
        id = id,
        title = title,
        content = content,
        mood = mood.name,
        linkedGoalId = linkedGoalId,
        linkedHabitId = linkedHabitId,
        promptUsed = promptUsed,
        tags = tags.joinToString(","),
        date = date.toString(),
        createdAt = createdAt.toString(),
        updatedAt = updatedAt?.toString(),
        sync_updated_at = Clock.System.now().toString(),
        is_deleted = 0L,
        sync_version = 0L,
        last_synced_at = null
    )
}

fun List<JournalEntryEntity>.toDomainJournalEntries(): List<JournalEntry> {
    return map { it.toDomain() }
}

/**
 * Parse tags from SQLite TEXT column.
 * Handles both formats:
 * - JSON array from sync: `["tag1","tag2"]`
 * - Comma-separated from local: `tag1,tag2`
 */
private fun parseTags(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    // Try JSON array first (handles synced data that was stored as raw JSON)
    if (raw.startsWith("[")) {
        try {
            return Json.parseToJsonElement(raw)
                .jsonArray
                .mapNotNull { it.jsonPrimitive.content.takeIf { s -> s.isNotBlank() } }
        } catch (_: Exception) { /* fall through to comma split */ }
    }
    return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
}

@OptIn(ExperimentalUuidApi::class)
fun createNewJournalEntry(
    title: String,
    content: String,
    mood: Mood,
    linkedGoalId: String? = null,
    linkedHabitId: String? = null,
    promptUsed: String? = null,
    tags: List<String> = emptyList(),
    date: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
): JournalEntry {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return JournalEntry(
        id = Uuid.random().toString(),
        title = title,
        content = content,
        mood = mood,
        linkedGoalId = linkedGoalId,
        linkedHabitId = linkedHabitId,
        promptUsed = promptUsed,
        tags = tags,
        date = date,
        createdAt = now,
        updatedAt = null
    )
}
