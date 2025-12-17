package az.tribe.lifeplanner.data.network

import az.tribe.lifeplanner.data.network.dto.AuthTokenRequest
import az.tribe.lifeplanner.data.network.dto.AuthTokenResponse
import az.tribe.lifeplanner.data.network.dto.BulkSyncRequest
import az.tribe.lifeplanner.data.network.dto.BulkSyncResponse
import az.tribe.lifeplanner.data.network.dto.GoalDTO
import az.tribe.lifeplanner.data.network.dto.UserProgressDTO

/**
 * API Service interface for backend communication
 */
interface LifePlannerApiService {
    /**
     * Authenticate with Firebase token
     */
    suspend fun authenticateWithToken(request: AuthTokenRequest): AuthTokenResponse
    /**
     * Get all goals for the authenticated user
     */
    suspend fun getGoals(): List<GoalDTO>

    /**
     * Create a new goal
     */
    suspend fun createGoal(goal: GoalDTO): GoalDTO

    /**
     * Update an existing goal
     */
    suspend fun updateGoal(id: String, goal: GoalDTO): GoalDTO

    /**
     * Delete a goal
     */
    suspend fun deleteGoal(id: String)

    /**
     * Get active goals
     */
    suspend fun getActiveGoals(): List<GoalDTO>

    /**
     * Bulk sync goals with the backend
     */
    suspend fun syncGoals(request: BulkSyncRequest): BulkSyncResponse

    /**
     * Get user progress/streaks
     */
    suspend fun getUserProgress(): UserProgressDTO
}
