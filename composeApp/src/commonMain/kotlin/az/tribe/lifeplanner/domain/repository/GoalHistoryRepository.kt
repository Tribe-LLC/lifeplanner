package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.GoalChange

interface GoalHistoryRepository {
    suspend fun insertChange(
        id: String,
        goalId: String,
        field: String,
        oldValue: String?,
        newValue: String,
        changedAt: String
    )
    suspend fun getHistoryForGoal(goalId: String): List<GoalChange>
}