package az.tribe.lifeplanner.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CoachPost(
    val id: String,
    @SerialName("coach_id") val coachId: String,
    val title: String,
    val content: String,
    val category: String = "story",
    val emoji: String = "📝",
    @SerialName("read_time_minutes") val readTimeMinutes: Int = 2,
    @SerialName("created_at") val createdAt: String? = null
)
