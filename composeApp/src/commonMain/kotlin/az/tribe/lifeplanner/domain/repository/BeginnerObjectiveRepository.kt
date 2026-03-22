package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.BeginnerObjective
import az.tribe.lifeplanner.domain.model.ObjectiveType
import kotlinx.coroutines.flow.Flow

interface BeginnerObjectiveRepository {
    fun observeObjectives(): Flow<List<BeginnerObjective>>
    suspend fun initializeObjectives()
    suspend fun completeObjective(type: ObjectiveType)
    suspend fun uncompleteObjective(type: ObjectiveType)
    suspend fun isObjectiveCompleted(type: ObjectiveType): Boolean
    suspend fun getCompletedCount(): Int
    suspend fun getAllObjectives(): List<BeginnerObjective>
}
