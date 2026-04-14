package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.data.mapper.toDomain
import az.tribe.lifeplanner.database.AbilityHabitLinkEntity
import az.tribe.lifeplanner.domain.model.Ability
import az.tribe.lifeplanner.domain.model.AbilityHabitLink
import az.tribe.lifeplanner.domain.repository.AbilityRepository
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.max
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class AbilityRepositoryImpl(
    private val database: SharedDatabase
) : AbilityRepository {

    override fun observeAllAbilities(): Flow<List<Ability>> {
        return database.observeAllAbilities().map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getAbilityById(id: String): Ability? {
        return database.getAbilityById(id)?.toDomain()
    }

    override suspend fun createAbility(ability: Ability) {
        database.insertAbility(
            az.tribe.lifeplanner.database.AbilityEntity(
                id = ability.id,
                title = ability.title,
                description = ability.description,
                iconEmoji = ability.iconEmoji,
                totalXp = ability.totalXp.toLong(),
                currentLevel = ability.currentLevel.toLong(),
                isActive = if (ability.isActive) 1L else 0L,
                createdAt = ability.createdAt,
                lastActivityDate = ability.lastActivityDate,
                sync_updated_at = null,
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
            )
        )
    }

    override suspend fun updateAbility(ability: Ability) {
        database.updateAbility(
            id = ability.id,
            title = ability.title,
            description = ability.description,
            iconEmoji = ability.iconEmoji
        )
    }

    override suspend fun deleteAbility(id: String) {
        database.deleteAbility(id)
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun linkHabit(abilityId: String, habitId: String, xpWeight: Float) {
        val link = AbilityHabitLinkEntity(
            id = Uuid.random().toString(),
            abilityId = abilityId,
            habitId = habitId,
            xpWeight = xpWeight.toDouble(),
            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
        )
        database.insertAbilityHabitLink(link)
    }

    override suspend fun unlinkHabit(abilityId: String, habitId: String) {
        database.deleteAbilityHabitLink(abilityId, habitId)
    }

    override suspend fun getLinksForAbility(abilityId: String): List<AbilityHabitLink> {
        return database.getLinksForAbility(abilityId).map { it.toDomain() }
    }

    override suspend fun getLinksForHabit(habitId: String): List<AbilityHabitLink> {
        return database.getLinksForHabit(habitId).map { it.toDomain() }
    }

    override suspend fun awardXpToAbilitiesForHabit(habitId: String, baseXp: Int) {
        val links = database.getLinksForHabit(habitId)
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        for (link in links) {
            val ability = database.getAbilityById(link.abilityId) ?: continue
            val earned = (baseXp * link.xpWeight).toInt()
            val newTotalXp = ability.totalXp.toInt() + earned
            val newLevel = calculateLevel(newTotalXp)
            database.updateAbilityXpAndLevel(
                id = ability.id,
                totalXp = newTotalXp.toLong(),
                currentLevel = newLevel.toLong(),
                lastActivityDate = now
            )
        }
    }

    private fun calculateLevel(totalXp: Int): Int {
        // xpForLevel = 50 * level → sum = 50*(1+2+...+n) = 25*n*(n+1)
        // Find n such that 25*n*(n+1) >= totalXp
        var level = 1
        var xpAccum = 0
        while (true) {
            xpAccum += level * 50
            if (xpAccum > totalXp) break
            level++
        }
        return max(1, level)
    }
}
