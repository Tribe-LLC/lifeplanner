package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.data.mapper.toDomain
import az.tribe.lifeplanner.domain.model.FocusSession
import az.tribe.lifeplanner.domain.repository.FocusRepository
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class FocusRepositoryImpl(
    private val database: SharedDatabase,
    private val syncManager: SyncManager
) : FocusRepository {

    override fun observeAllSessions(): Flow<List<FocusSession>> {
        return database.observeAllFocusSessions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertSession(session: FocusSession) {
        database.insertFocusSession(
            id = session.id,
            goalId = session.goalId,
            milestoneId = session.milestoneId,
            plannedDurationMinutes = session.plannedDurationMinutes.toLong(),
            actualDurationSeconds = session.actualDurationSeconds.toLong(),
            wasCompleted = if (session.wasCompleted) 1L else 0L,
            xpEarned = session.xpEarned.toLong(),
            startedAt = session.startedAt.toString(),
            completedAt = session.completedAt?.toString(),
            createdAt = session.createdAt.toString(),
            mood = session.mood?.name,
            ambientSound = session.ambientSound?.name,
            focusTheme = session.focusTheme?.name
        )
        syncManager.requestSync()
    }

    override suspend fun updateSession(session: FocusSession) {
        database.updateFocusSession(
            id = session.id,
            actualDurationSeconds = session.actualDurationSeconds.toLong(),
            wasCompleted = if (session.wasCompleted) 1L else 0L,
            xpEarned = session.xpEarned.toLong(),
            completedAt = session.completedAt?.toString(),
            mood = session.mood?.name,
            ambientSound = session.ambientSound?.name,
            focusTheme = session.focusTheme?.name
        )
        syncManager.requestSync()
    }

    override suspend fun getSessionById(id: String): FocusSession? {
        return database.getFocusSessionById(id)?.toDomain()
    }

    override suspend fun getSessionsByGoalId(goalId: String): List<FocusSession> {
        return database.getFocusSessionsByGoalId(goalId).map { it.toDomain() }
    }

    override suspend fun getSessionsByMilestoneId(milestoneId: String): List<FocusSession> {
        return database.getFocusSessionsByMilestoneId(milestoneId).map { it.toDomain() }
    }

    override suspend fun getCompletedSessions(): List<FocusSession> {
        return database.getCompletedFocusSessions().map { it.toDomain() }
    }

    override suspend fun getTotalFocusMinutes(): Int {
        return (database.getTotalFocusSeconds() / 60).toInt()
    }

    override suspend fun getTotalFocusSeconds(): Long {
        return database.getTotalFocusSeconds()
    }

    override suspend fun getTotalSessionCount(): Int {
        return database.getTotalFocusSessionCount().toInt()
    }

    override suspend fun getTodaySessions(): List<FocusSession> {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        return database.getTodayFocusSessions(today).map { it.toDomain() }
    }
}
