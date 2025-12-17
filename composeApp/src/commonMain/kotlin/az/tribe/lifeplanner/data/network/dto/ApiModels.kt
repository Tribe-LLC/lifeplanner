package az.tribe.lifeplanner.data.network.dto

import kotlinx.serialization.Serializable

/**
 * DTOs for API communication with backend
 */

@Serializable
data class GoalDTO(
    val id: String? = null,
    val title: String,
    val description: String,
    val category: String,
    val timeline: String,
    val due_date: String,
    val status: String,
    val progress: Int = 0,
    val notes: String = "",
    val created_at: String? = null,
    val updated_at: String? = null,
    val is_archived: Boolean = false
)

@Serializable
data class MilestoneDTO(
    val id: String? = null,
    val goal_id: String,
    val title: String,
    val is_completed: Boolean = false,
    val due_date: String? = null,
    val created_at: String? = null
)

@Serializable
data class UserProgressDTO(
    val current_streak: Int,
    val last_check_in_date: String?
)

@Serializable
data class BulkSyncRequest(
    val goals: List<GoalDTO>
)

@Serializable
data class BulkSyncResponse(
    val created: List<GoalDTO>,
    val updated: List<GoalDTO>,
    val failed: List<String> = emptyList()
)

@Serializable
data class ApiError(
    val detail: String
)

@Serializable
data class AuthTokenRequest(
    val firebaseToken: String,
    val deviceInfo: String? = null
)

@Serializable
data class AuthTokenResponse(
    val success: Boolean,
    val message: String? = null,
    val user: UserDTO? = null
)

@Serializable
data class UserDTO(
    val id: String,
    val firebaseUid: String,
    val email: String? = null,
    val displayName: String? = null,
    val isGuest: Boolean = false,
    val createdAt: String? = null
)
