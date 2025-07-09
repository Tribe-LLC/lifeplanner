package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.domain.repository.GamificationRepository
import az.tribe.lifeplanner.domain.model.UserProgress
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class GamificationRepositoryImpl(
    private val database: SharedDatabase
) : GamificationRepository {

    init {
        CoroutineScope(Dispatchers.IO).launch {
            initializeProgress()
        }
    }

    private suspend fun initializeProgress() {
        database { db ->
            if (db.lifePlannerDBQueries.getUserProgress().executeAsOneOrNull() == null) {
                db.lifePlannerDBQueries.insertUserProgress(
                    currentStreak = 0,
                    lastCheckInDate = null
                )
            }
        }
    }

    override suspend fun getUserProgress(): Flow<UserProgress> = flow {
        val progress = database { db ->
            db.lifePlannerDBQueries.getUserProgress().executeAsOneOrNull()
                ?: run {
                    initializeProgress()
                    db.lifePlannerDBQueries.getUserProgress().executeAsOne()
                }
        }

        emit(UserProgress(
            currentStreak = progress.currentStreak.toInt(),
            lastCheckInDate = progress.lastCheckInDate?.let { LocalDate.parse(it) }
        ))
    }

    override suspend fun updateDailyStreak(): Flow<Int> = flow {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        val progress = database { db ->
            db.lifePlannerDBQueries.getUserProgress().executeAsOne()
        }

        val lastCheckIn = progress.lastCheckInDate?.let { LocalDate.parse(it) }

        val newStreak = when {
            lastCheckIn == null -> 1
            isConsecutiveDay(lastCheckIn, today) -> progress.currentStreak.toInt() + 1
            lastCheckIn == today -> progress.currentStreak.toInt()
            else -> 1
        }

        database { db ->
            db.lifePlannerDBQueries.updateUserStreak(
                currentStreak = newStreak.toLong(),
                lastCheckInDate = today.toString()
            )
        }

        emit(newStreak)
    }

    private fun isConsecutiveDay(previous: LocalDate, current: LocalDate): Boolean {
        return (current.toEpochDays() - previous.toEpochDays()).toLong() == 1L
    }


}