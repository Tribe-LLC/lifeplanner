package az.tribe.lifeplanner.data.mapper

import az.tribe.lifeplanner.database.AbilityEntity
import az.tribe.lifeplanner.database.AbilityHabitLinkEntity
import az.tribe.lifeplanner.domain.model.Ability
import az.tribe.lifeplanner.domain.model.AbilityHabitLink
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun AbilityEntity.toDomain(): Ability = Ability(
    id = id,
    title = title,
    description = description,
    iconEmoji = iconEmoji,
    totalXp = totalXp.toInt(),
    currentLevel = currentLevel.toInt(),
    isActive = isActive == 1L,
    createdAt = createdAt,
    lastActivityDate = lastActivityDate
)

fun AbilityHabitLinkEntity.toDomain(): AbilityHabitLink = AbilityHabitLink(
    id = id,
    abilityId = abilityId,
    habitId = habitId,
    xpWeight = xpWeight.toFloat(),
    createdAt = createdAt
)

@OptIn(ExperimentalUuidApi::class)
fun createNewAbility(
    title: String,
    description: String = "",
    iconEmoji: String = "⚡"
): Ability = Ability(
    id = Uuid.random().toString(),
    title = title,
    description = description,
    iconEmoji = iconEmoji,
    totalXp = 0,
    currentLevel = 1,
    isActive = true,
    createdAt = Clock.System.now().toString(),
    lastActivityDate = null
)
