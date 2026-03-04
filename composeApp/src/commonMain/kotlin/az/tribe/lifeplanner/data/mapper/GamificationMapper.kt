package az.tribe.lifeplanner.data.mapper

import az.tribe.lifeplanner.database.BadgeEntity
import az.tribe.lifeplanner.database.ChallengeEntity
import az.tribe.lifeplanner.database.UserProgressEntity
import az.tribe.lifeplanner.domain.enum.BadgeType
import az.tribe.lifeplanner.domain.enum.ChallengeType
import az.tribe.lifeplanner.domain.model.Badge
import az.tribe.lifeplanner.domain.model.Challenge
import az.tribe.lifeplanner.domain.model.ChallengeTargets
import az.tribe.lifeplanner.domain.model.UserProgress
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Badge Entity to Domain mapper
 */
fun BadgeEntity.toDomain(): Badge = Badge(
    id = id,
    type = BadgeType.valueOf(badgeType),
    earnedAt = parseLocalDateTime(earnedAt),
    isNew = isNew == 1L
)

/**
 * Badge Domain to Entity mapper
 */
fun Badge.toEntity(): BadgeEntity = BadgeEntity(
    id = id,
    badgeType = type.name,
    earnedAt = earnedAt.toString(),
    isNew = if (isNew) 1L else 0L,
    sync_updated_at = Clock.System.now().toString(),
    is_deleted = 0L,
    sync_version = 0L,
    last_synced_at = null
)

/**
 * Challenge Entity to Domain mapper
 */
fun ChallengeEntity.toDomain(): Challenge = Challenge(
    id = id,
    type = ChallengeType.valueOf(challengeType),
    startDate = LocalDate.parse(startDate),
    endDate = LocalDate.parse(endDate),
    currentProgress = currentProgress.toInt(),
    targetProgress = targetProgress.toInt(),
    isCompleted = isCompleted == 1L,
    completedAt = completedAt?.let { parseLocalDateTime(it) },
    xpEarned = xpEarned.toInt()
)

/**
 * Challenge Domain to Entity mapper
 */
fun Challenge.toEntity(): ChallengeEntity = ChallengeEntity(
    id = id,
    challengeType = type.name,
    startDate = startDate.toString(),
    endDate = endDate.toString(),
    currentProgress = currentProgress.toLong(),
    targetProgress = targetProgress.toLong(),
    isCompleted = if (isCompleted) 1L else 0L,
    completedAt = completedAt?.toString(),
    xpEarned = xpEarned.toLong(),
    sync_updated_at = Clock.System.now().toString(),
    is_deleted = 0L,
    sync_version = 0L,
    last_synced_at = null
)

/**
 * Extended UserProgressEntity to Domain mapper
 */
fun UserProgressEntity.toDomain(): UserProgress = UserProgress(
    currentStreak = currentStreak.toInt(),
    lastCheckInDate = lastCheckInDate?.let { LocalDate.parse(it) },
    totalXp = totalXp.toInt(),
    currentLevel = currentLevel.toInt(),
    goalsCompleted = goalsCompleted.toInt(),
    habitsCompleted = habitsCompleted.toInt(),
    journalEntriesCount = journalEntriesCount.toInt(),
    longestStreak = longestStreak.toInt()
)

/**
 * Create a new Badge for a badge type
 */
@OptIn(ExperimentalUuidApi::class)
fun createNewBadge(type: BadgeType): Badge = Badge(
    id = Uuid.random().toString(),
    type = type,
    earnedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
    isNew = true
)

/**
 * Create a new Challenge from a challenge type
 */
@OptIn(ExperimentalUuidApi::class)
fun createNewChallenge(type: ChallengeType): Challenge {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val endDate = LocalDate.fromEpochDays(today.toEpochDays() + type.durationDays)

    return Challenge(
        id = Uuid.random().toString(),
        type = type,
        startDate = today,
        endDate = endDate,
        currentProgress = 0,
        targetProgress = ChallengeTargets.getTargetForType(type),
        isCompleted = false,
        completedAt = null,
        xpEarned = 0
    )
}
