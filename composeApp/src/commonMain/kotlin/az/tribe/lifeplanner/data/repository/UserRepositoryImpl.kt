package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.database.UserEntity
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import az.tribe.lifeplanner.domain.model.OnboardingData
import az.tribe.lifeplanner.domain.model.User
import az.tribe.lifeplanner.domain.repository.UserRepository
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Implementation of UserRepository
 */
class UserRepositoryImpl(
    private val sharedDatabase: SharedDatabase
) : UserRepository {

    override fun getCurrentUserFlow(): Flow<User?> {
        return flow {
            sharedDatabase { db ->
                emitAll(
                    db.lifePlannerDBQueries.getCurrentUser()
                        .asFlow()
                        .mapToOneOrNull(Dispatchers.Default)
                        .map { it?.toDomainModel() }
                )
            }
        }
    }

    override suspend fun getCurrentUser(): User? {
        return sharedDatabase { db ->
            db.lifePlannerDBQueries.getCurrentUser()
                .executeAsOneOrNull()
                ?.toDomainModel()
        }
    }

    override suspend fun createUser(user: User) {
        sharedDatabase { db ->
            db.lifePlannerDBQueries.insertUser(
                id = user.id,
                firebaseUid = user.firebaseUid,
                email = user.email,
                displayName = user.displayName,
                isGuest = if (user.isGuest) 1L else 0L,
                selectedSymbol = user.selectedSymbol,
                priorities = user.priorities.joinToString(","),
                ageRange = user.ageRange,
                profession = user.profession,
                relationshipStatus = user.relationshipStatus,
                mindset = user.mindset,
                hasCompletedOnboarding = if (user.hasCompletedOnboarding) 1L else 0L,
                createdAt = user.createdAt.toString(),
                lastSyncedAt = user.lastSyncedAt?.toString()
            )
        }
    }

    override suspend fun updateUser(user: User) {
        sharedDatabase { db ->
            db.lifePlannerDBQueries.updateUser(
                firebaseUid = user.firebaseUid,
                email = user.email,
                displayName = user.displayName,
                isGuest = if (user.isGuest) 1L else 0L,
                selectedSymbol = user.selectedSymbol,
                priorities = user.priorities.joinToString(","),
                ageRange = user.ageRange,
                profession = user.profession,
                relationshipStatus = user.relationshipStatus,
                mindset = user.mindset,
                hasCompletedOnboarding = if (user.hasCompletedOnboarding) 1L else 0L,
                lastSyncedAt = Clock.System.now().toString(),
                id = user.id
            )
        }
    }

    override suspend fun saveOnboardingData(userId: String, onboardingData: OnboardingData) {
        sharedDatabase { db ->
            db.lifePlannerDBQueries.updateOnboardingData(
                selectedSymbol = onboardingData.selectedSymbol,
                priorities = onboardingData.priorities.joinToString(","),
                ageRange = onboardingData.ageRange,
                profession = onboardingData.profession,
                relationshipStatus = onboardingData.relationshipStatus,
                mindset = onboardingData.mindset,
                id = userId
            )
        }
    }

    override suspend fun hasCompletedOnboarding(): Boolean {
        return sharedDatabase { db ->
            db.lifePlannerDBQueries.getCurrentUser()
                .executeAsOneOrNull()
                ?.hasCompletedOnboarding == 1L
        }
    }

    override suspend fun deleteUser(userId: String) {
        sharedDatabase { db ->
            db.lifePlannerDBQueries.deleteUser(userId)
        }
    }

    /**
     * Extension function to convert UserEntity to User domain model
     */
    private fun UserEntity.toDomainModel(): User {
        return User(
            id = id,
            firebaseUid = firebaseUid,
            email = email,
            displayName = displayName,
            isGuest = isGuest == 1L,
            selectedSymbol = selectedSymbol,
            priorities = priorities?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            ageRange = ageRange,
            profession = profession,
            relationshipStatus = relationshipStatus,
            mindset = mindset,
            hasCompletedOnboarding = hasCompletedOnboarding == 1L,
            createdAt = Instant.parse(createdAt),
            lastSyncedAt = lastSyncedAt?.let { Instant.parse(it) }
        )
    }
}
