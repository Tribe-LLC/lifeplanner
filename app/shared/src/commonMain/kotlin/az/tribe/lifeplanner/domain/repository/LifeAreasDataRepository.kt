package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.Budget
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.Trip
import az.tribe.lifeplanner.domain.model.TripItem
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface LifeLogRepository {
    fun observeInRange(from: LocalDate, to: LocalDate): Flow<List<LifeLog>>
    fun observeRecent(limit: Int): Flow<List<LifeLog>>
    fun observeForTrip(tripId: String): Flow<List<LifeLog>>
    suspend fun getInRange(from: LocalDate, to: LocalDate): List<LifeLog>
    suspend fun getById(id: String): LifeLog?
    /** Whether any row, deleted ones included, already carries [externalId]. Keeps imports from repeating. */
    suspend fun hasExternalId(externalId: String): Boolean
    suspend fun save(log: LifeLog)
    suspend fun saveAll(logs: List<LifeLog>)
    suspend fun delete(id: String)
}

interface BudgetRepository {
    fun observeAll(): Flow<List<Budget>>
    suspend fun getAll(): List<Budget>
    suspend fun save(budget: Budget)
    suspend fun delete(id: String)
}

interface TripRepository {
    fun observeAll(): Flow<List<Trip>>
    fun observeItems(tripId: String): Flow<List<TripItem>>
    suspend fun getAll(): List<Trip>
    suspend fun getById(id: String): Trip?
    suspend fun save(trip: Trip)
    suspend fun delete(id: String)
    suspend fun saveItem(item: TripItem)
    suspend fun deleteItem(id: String)
}
