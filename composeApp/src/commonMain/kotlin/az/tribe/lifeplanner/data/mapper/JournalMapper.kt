package az.tribe.lifeplanner.data.mapper

import az.tribe.lifeplanner.database.JournalEntryEntity
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.JournalEntry
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
        tags = if (tags.isBlank()) emptyList() else tags.split(",").map { it.trim() },
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
