package az.tribe.lifeplanner.domain.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/**
 * User-created custom coach with personalized settings
 */
@Serializable
data class CustomCoach(
    val id: String,
    val name: String,
    val icon: String,
    val iconBackgroundColor: String = "#6366F1",
    val iconAccentColor: String = "#818CF8",
    val systemPrompt: String,
    val characteristics: List<String> = emptyList(),
    val isFromTemplate: Boolean = false,
    val templateId: String? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime? = null
)

/**
 * A group of coaches that respond together (council-style)
 */
@Serializable
data class CoachGroup(
    val id: String,
    val name: String,
    val icon: String,
    val description: String = "",
    val members: List<CoachGroupMember> = emptyList(),
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime? = null
)

/**
 * A member of a coach group - can be either a built-in or custom coach
 */
@Serializable
data class CoachGroupMember(
    val id: String,
    val coachType: CoachType,
    val coachId: String,
    val displayOrder: Int = 0
)

/**
 * Type of coach in a group
 */
@Serializable
enum class CoachType {
    BUILTIN,
    CUSTOM
}
