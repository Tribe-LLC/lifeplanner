package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.UserProgress
import kotlinx.coroutines.flow.Flow


interface GamificationRepository {
    suspend fun getUserProgress(): Flow<UserProgress>
    suspend fun updateDailyStreak(): Flow<Int>
}