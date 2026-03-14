package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.data.mapper.parseLocalDateTime
import az.tribe.lifeplanner.database.CoachGroupEntity
import az.tribe.lifeplanner.database.CoachGroupMemberEntity
import az.tribe.lifeplanner.database.CustomCoachEntity
import az.tribe.lifeplanner.domain.model.CoachGroup
import az.tribe.lifeplanner.domain.model.CoachGroupMember
import az.tribe.lifeplanner.domain.model.CoachType
import az.tribe.lifeplanner.domain.model.CustomCoach
import az.tribe.lifeplanner.domain.repository.CoachRepository
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CoachRepositoryImpl(
    private val database: SharedDatabase,
    private val syncManager: az.tribe.lifeplanner.data.sync.SyncManager
) : CoachRepository {

    // ===== Custom Coaches =====

    override suspend fun getAllCustomCoaches(): List<CustomCoach> {
        return database.getAllCustomCoaches().map { it.toDomain() }
    }

    override suspend fun getCustomCoachById(id: String): CustomCoach? {
        return database.getCustomCoachById(id)?.toDomain()
    }

    override suspend fun createCustomCoach(coach: CustomCoach): CustomCoach {
        val id = coach.id.ifEmpty { Uuid.random().toString() }
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        database.insertCustomCoach(
            id = id,
            name = coach.name,
            icon = coach.icon,
            iconBackgroundColor = coach.iconBackgroundColor,
            iconAccentColor = coach.iconAccentColor,
            systemPrompt = coach.systemPrompt,
            characteristics = coach.characteristics.joinToString(","),
            isFromTemplate = if (coach.isFromTemplate) 1L else 0L,
            templateId = coach.templateId,
            createdAt = now.toString(),
            updatedAt = null
        )

        return coach.copy(id = id, createdAt = now)
    }

    override suspend fun updateCustomCoach(coach: CustomCoach) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        database.updateCustomCoach(
            name = coach.name,
            icon = coach.icon,
            iconBackgroundColor = coach.iconBackgroundColor,
            iconAccentColor = coach.iconAccentColor,
            systemPrompt = coach.systemPrompt,
            characteristics = coach.characteristics.joinToString(","),
            updatedAt = now.toString(),
            id = coach.id
        )
    }

    override suspend fun deleteCustomCoach(id: String) {
        database.deleteCustomCoach(id)
    }

    override suspend fun getCustomCoachCount(): Long {
        return database.getCustomCoachCount()
    }

    // ===== Coach Groups =====

    override suspend fun getAllCoachGroups(): List<CoachGroup> {
        // Batch: 2 queries instead of 1+N (one for groups, one for ALL members)
        val groups = database.getAllCoachGroups()
        val allMembers = database.getAllActiveCoachGroupMembers()
        return groups.map { entity ->
            val members = allMembers[entity.id]?.map { it.toDomain() } ?: emptyList()
            entity.toDomain(members)
        }
    }

    override suspend fun getCoachGroupById(id: String): CoachGroup? {
        val entity = database.getCoachGroupById(id) ?: return null
        val members = database.getCoachGroupMembers(id).map { it.toDomain() }
        return entity.toDomain(members)
    }

    override suspend fun createCoachGroup(group: CoachGroup): CoachGroup {
        val id = group.id.ifEmpty { Uuid.random().toString() }
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        database.insertCoachGroup(
            id = id,
            name = group.name,
            icon = group.icon,
            description = group.description,
            createdAt = now.toString(),
            updatedAt = null
        )

        // Insert members
        group.members.forEachIndexed { index, member ->
            val memberId = member.id.ifEmpty { Uuid.random().toString() }
            database.insertCoachGroupMember(
                id = memberId,
                groupId = id,
                coachType = member.coachType.name,
                coachId = member.coachId,
                displayOrder = index.toLong()
            )
        }

        return group.copy(id = id, createdAt = now)
    }

    override suspend fun updateCoachGroup(group: CoachGroup) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        database.updateCoachGroup(
            name = group.name,
            icon = group.icon,
            description = group.description,
            updatedAt = now.toString(),
            id = group.id
        )
    }

    override suspend fun deleteCoachGroup(id: String) {
        // Members are deleted via CASCADE
        database.deleteCoachGroup(id)
    }

    override suspend fun getCoachGroupCount(): Long {
        return database.getCoachGroupCount()
    }

    // ===== Group Members =====

    override suspend fun getCoachGroupMembers(groupId: String): List<CoachGroupMember> {
        return database.getCoachGroupMembers(groupId).map { it.toDomain() }
    }

    override suspend fun addMemberToGroup(groupId: String, member: CoachGroupMember) {
        val id = member.id.ifEmpty { Uuid.random().toString() }
        database.insertCoachGroupMember(
            id = id,
            groupId = groupId,
            coachType = member.coachType.name,
            coachId = member.coachId,
            displayOrder = member.displayOrder.toLong()
        )
    }

    override suspend fun removeMemberFromGroup(memberId: String) {
        database.deleteCoachGroupMember(memberId)
    }

    override suspend fun updateMemberOrder(memberId: String, newOrder: Int) {
        database.updateCoachGroupMemberOrder(newOrder.toLong(), memberId)
    }

    override suspend fun setGroupMembers(groupId: String, members: List<CoachGroupMember>) {
        // Delete existing members
        database.deleteCoachGroupMembersByGroup(groupId)

        // Insert new members
        members.forEachIndexed { index, member ->
            val memberId = member.id.ifEmpty { Uuid.random().toString() }
            database.insertCoachGroupMember(
                id = memberId,
                groupId = groupId,
                coachType = member.coachType.name,
                coachId = member.coachId,
                displayOrder = index.toLong()
            )
        }
    }

    // ===== Persona Overrides =====

    override suspend fun getPersonaOverride(coachId: String): String? {
        return database.getCoachPersonaOverride(coachId)?.userPersona?.takeIf { it.isNotBlank() }
    }

    override suspend fun savePersonaOverride(coachId: String, userPersona: String) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        database.upsertCoachPersonaOverride(coachId, userPersona, now.toString())
    }

    override suspend fun deletePersonaOverride(coachId: String) {
        database.deleteCoachPersonaOverride(coachId)
    }

    // ===== Mappers =====

    private fun CustomCoachEntity.toDomain(): CustomCoach {
        return CustomCoach(
            id = id,
            name = name,
            icon = icon,
            iconBackgroundColor = iconBackgroundColor,
            iconAccentColor = iconAccentColor,
            systemPrompt = systemPrompt,
            characteristics = characteristics.split(",").filter { it.isNotBlank() },
            isFromTemplate = isFromTemplate == 1L,
            templateId = templateId,
            createdAt = parseLocalDateTime(createdAt),
            updatedAt = updatedAt?.let { parseLocalDateTime(it) }
        )
    }

    private fun CoachGroupEntity.toDomain(members: List<CoachGroupMember>): CoachGroup {
        return CoachGroup(
            id = id,
            name = name,
            icon = icon,
            description = description,
            members = members,
            createdAt = parseLocalDateTime(createdAt),
            updatedAt = updatedAt?.let { parseLocalDateTime(it) }
        )
    }

    private fun CoachGroupMemberEntity.toDomain(): CoachGroupMember {
        return CoachGroupMember(
            id = id,
            coachType = CoachType.valueOf(coachType),
            coachId = coachId,
            displayOrder = displayOrder.toInt()
        )
    }
}
