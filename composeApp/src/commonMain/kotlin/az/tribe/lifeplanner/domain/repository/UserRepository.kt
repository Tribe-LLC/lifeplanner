package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.OnboardingData
import az.tribe.lifeplanner.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for user management
 */
interface UserRepository {
    /**
     * Get the current user as a Flow
     */
    fun getCurrentUserFlow(): Flow<User?>

    /**
     * Get the current user (one-shot)
     */
    suspend fun getCurrentUser(): User?

    /**
     * Create a new user
     */
    suspend fun createUser(user: User)

    /**
     * Update user information
     */
    suspend fun updateUser(user: User)

    /**
     * Save onboarding data for the current user
     */
    suspend fun saveOnboardingData(userId: String, onboardingData: OnboardingData)

    /**
     * Check if user has completed onboarding
     */
    suspend fun hasCompletedOnboarding(): Boolean

    /**
     * Delete user (for testing/logout)
     */
    suspend fun deleteUser(userId: String)
}
