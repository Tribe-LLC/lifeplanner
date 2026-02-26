package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.CoachGroup
import az.tribe.lifeplanner.domain.model.CoachGroupMember
import az.tribe.lifeplanner.domain.model.CustomCoach

/**
 * Repository for managing custom coaches and coach groups
 */
interface CoachRepository {
    // Custom Coaches
    suspend fun getAllCustomCoaches(): List<CustomCoach>
    suspend fun getCustomCoachById(id: String): CustomCoach?
    suspend fun createCustomCoach(coach: CustomCoach): CustomCoach
    suspend fun updateCustomCoach(coach: CustomCoach)
    suspend fun deleteCustomCoach(id: String)
    suspend fun getCustomCoachCount(): Long

    // Coach Groups
    suspend fun getAllCoachGroups(): List<CoachGroup>
    suspend fun getCoachGroupById(id: String): CoachGroup?
    suspend fun createCoachGroup(group: CoachGroup): CoachGroup
    suspend fun updateCoachGroup(group: CoachGroup)
    suspend fun deleteCoachGroup(id: String)
    suspend fun getCoachGroupCount(): Long

    // Group Members
    suspend fun getCoachGroupMembers(groupId: String): List<CoachGroupMember>
    suspend fun addMemberToGroup(groupId: String, member: CoachGroupMember)
    suspend fun removeMemberFromGroup(memberId: String)
    suspend fun updateMemberOrder(memberId: String, newOrder: Int)
    suspend fun setGroupMembers(groupId: String, members: List<CoachGroupMember>)
}
