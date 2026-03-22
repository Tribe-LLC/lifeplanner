package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.domain.model.BeginnerObjective
import az.tribe.lifeplanner.domain.model.ObjectiveType
import az.tribe.lifeplanner.domain.repository.BeginnerObjectiveRepository
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class BeginnerObjectiveRepositoryImpl(
    private val database: SharedDatabase,
    private val syncManager: SyncManager
) : BeginnerObjectiveRepository {

    private val log = Logger.withTag("BeginnerObjectiveRepo")

    override fun observeObjectives(): Flow<List<BeginnerObjective>> {
        return database.observeAllBeginnerObjectives().catch { e ->
            log.w { "observeObjectives failed (table may not exist): ${e.message}" }
            emit(emptyList())
        }.map { entities ->
            val existing = entities.map { it.objectiveType }
            val objectives = entities.map { entity ->
                BeginnerObjective(
                    id = entity.id,
                    type = ObjectiveType.valueOf(entity.objectiveType),
                    isCompleted = entity.isCompleted != 0L,
                    completedAt = entity.completedAt,
                    xpAwarded = entity.xpAwarded.toInt()
                )
            }.toMutableList()

            // Add missing objective types (for new objectives added in updates)
            ObjectiveType.entries.forEach { type ->
                if (type.name !in existing) {
                    objectives.add(
                        BeginnerObjective(
                            id = "",
                            type = type,
                            isCompleted = false,
                            completedAt = null,
                            xpAwarded = 0
                        )
                    )
                }
            }

            objectives.sortedWith(compareBy({ it.isCompleted }, { it.type.sortOrder }))
        }
    }

    override suspend fun initializeObjectives() {
        try {
            // Clean up any duplicate objective rows (e.g. from sync re-inserts)
            try { database.deduplicateBeginnerObjectives() } catch (_: Exception) { }
            val existing = database.getAllBeginnerObjectives()
            val existingTypes = existing.map { it.objectiveType }.toSet()
            val now = Clock.System.now().toString()

            ObjectiveType.entries.forEach { type ->
                if (type.name !in existingTypes) {
                    database.insertBeginnerObjective(
                        id = Uuid.random().toString(),
                        objectiveType = type.name,
                        isCompleted = 0L,
                        completedAt = null,
                        xpAwarded = 0L,
                        createdAt = now
                    )
                }
            }
        } catch (e: Exception) {
            co.touchlab.kermit.Logger.w("BeginnerObjectiveRepo") { "initializeObjectives failed (table may not exist): ${e.message}" }
        }
    }

    override suspend fun completeObjective(type: ObjectiveType) {
        try {
            val existing = database.getBeginnerObjectiveByType(type.name)
            if (existing != null && existing.isCompleted != 0L) return // already done

            if (existing == null) {
                val now = Clock.System.now().toString()
                database.insertBeginnerObjective(
                    id = Uuid.random().toString(),
                    objectiveType = type.name,
                    isCompleted = 1L,
                    completedAt = now,
                    xpAwarded = type.xpReward.toLong(),
                    createdAt = now
                )
            } else {
                database.completeBeginnerObjective(
                    completedAt = Clock.System.now().toString(),
                    xpAwarded = type.xpReward.toLong(),
                    objectiveType = type.name
                )
            }

            log.d { "Completed beginner objective: ${type.name} (+${type.xpReward} XP)" }
            syncManager.requestSync()
        } catch (e: Exception) {
            log.w { "completeObjective(${type.name}) failed: ${e.message}" }
        }
    }

    override suspend fun uncompleteObjective(type: ObjectiveType) {
        try {
            database.uncompleteBeginnerObjective(type.name)
            log.d { "Uncompleted beginner objective: ${type.name}" }
            syncManager.requestSync()
        } catch (e: Exception) {
            log.w { "uncompleteObjective(${type.name}) failed: ${e.message}" }
        }
    }

    override suspend fun isObjectiveCompleted(type: ObjectiveType): Boolean {
        return try {
            val entity = database.getBeginnerObjectiveByType(type.name)
            entity?.isCompleted == 1L
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getCompletedCount(): Int {
        return try {
            database.getCompletedBeginnerObjectivesCount().toInt()
        } catch (e: Exception) {
            0
        }
    }

    override suspend fun getAllObjectives(): List<BeginnerObjective> {
        return try {
            database.getAllBeginnerObjectives().map { entity ->
                BeginnerObjective(
                    id = entity.id,
                    type = ObjectiveType.valueOf(entity.objectiveType),
                    isCompleted = entity.isCompleted != 0L,
                    completedAt = entity.completedAt,
                    xpAwarded = entity.xpAwarded.toInt()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
