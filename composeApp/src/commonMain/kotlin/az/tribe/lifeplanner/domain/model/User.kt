package az.tribe.lifeplanner.domain.model

import kotlin.time.Instant

/**
 * User domain model representing a user profile
 */
data class User(
    val id: String,
    val firebaseUid: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val isGuest: Boolean = false,
    val selectedSymbol: String? = null,
    val priorities: List<String> = emptyList(),
    val ageRange: String? = null,
    val profession: String? = null,
    val relationshipStatus: String? = null,
    val mindset: String? = null,
    val hasCompletedOnboarding: Boolean = false,
    val createdAt: Instant,
    val lastSyncedAt: Instant? = null
)

/**
 * Data class for saving onboarding data
 */
data class OnboardingData(
    val selectedSymbol: String,
    val priorities: List<String>,
    val ageRange: String,
    val profession: String,
    val relationshipStatus: String,
    val mindset: String
)
