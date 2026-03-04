package az.tribe.lifeplanner.data.mapper

import az.tribe.lifeplanner.database.FocusSessionEntity
import az.tribe.lifeplanner.domain.enum.AmbientSound
import az.tribe.lifeplanner.domain.enum.FocusTheme
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.FocusSession
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime

fun FocusSessionEntity.toDomain(): FocusSession = FocusSession(
    id = id,
    goalId = goalId,
    milestoneId = milestoneId,
    plannedDurationMinutes = plannedDurationMinutes.toInt(),
    actualDurationSeconds = actualDurationSeconds.toInt(),
    wasCompleted = wasCompleted == 1L,
    xpEarned = xpEarned.toInt(),
    startedAt = parseLocalDateTime(startedAt),
    completedAt = completedAt?.let { parseLocalDateTime(it) },
    createdAt = parseLocalDateTime(createdAt),
    mood = mood?.let { runCatching { Mood.valueOf(it) }.getOrNull() },
    ambientSound = ambientSound?.let { runCatching { AmbientSound.valueOf(it) }.getOrNull() },
    focusTheme = focusTheme?.let { runCatching { FocusTheme.valueOf(it) }.getOrNull() }
)

fun FocusSession.toEntity(): FocusSessionEntity = FocusSessionEntity(
    id = id,
    goalId = goalId,
    milestoneId = milestoneId,
    plannedDurationMinutes = plannedDurationMinutes.toLong(),
    actualDurationSeconds = actualDurationSeconds.toLong(),
    wasCompleted = if (wasCompleted) 1L else 0L,
    xpEarned = xpEarned.toLong(),
    startedAt = startedAt.toString(),
    completedAt = completedAt?.toString(),
    createdAt = createdAt.toString(),
    mood = mood?.name,
    ambientSound = ambientSound?.name,
    focusTheme = focusTheme?.name,
    sync_updated_at = Clock.System.now().toString(),
    is_deleted = 0L,
    sync_version = 0L,
    last_synced_at = null
)
