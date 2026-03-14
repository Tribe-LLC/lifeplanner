package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.FocusSession
import kotlinx.coroutines.flow.Flow

interface FocusRepository {
    fun observeAllSessions(): Flow<List<FocusSession>>
    suspend fun insertSession(session: FocusSession)
    suspend fun updateSession(session: FocusSession)
    suspend fun getSessionById(id: String): FocusSession?
    suspend fun getSessionsByGoalId(goalId: String): List<FocusSession>
    suspend fun getSessionsByMilestoneId(milestoneId: String): List<FocusSession>
    suspend fun getCompletedSessions(): List<FocusSession>
    suspend fun getTotalFocusMinutes(): Int
    suspend fun getTotalFocusSeconds(): Long
    suspend fun getTotalSessionCount(): Int
    suspend fun getTodaySessions(): List<FocusSession>
}
