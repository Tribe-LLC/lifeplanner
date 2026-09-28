package az.tribe.lifeplanner.data.repository

import app.cash.sqldelight.Query
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.database.BudgetEntity
import az.tribe.lifeplanner.database.LifeLogEntity
import az.tribe.lifeplanner.database.LifePlannerDBQueries
import az.tribe.lifeplanner.database.TripEntity
import az.tribe.lifeplanner.database.TripItemEntity
import az.tribe.lifeplanner.domain.model.Budget
import az.tribe.lifeplanner.domain.model.BudgetPeriod
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.model.Trip
import az.tribe.lifeplanner.domain.model.TripItem
import az.tribe.lifeplanner.domain.model.TripItemKind
import az.tribe.lifeplanner.domain.repository.BudgetRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.repository.TripRepository
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Clock

/** Observes a query as a list, opening the database first like the other SharedDatabase flows. */
private fun <T : Any> SharedDatabase.observe(query: (LifePlannerDBQueries) -> Query<T>): Flow<List<T>> = flow {
    initDatabase()
    emitAll(query(database!!.lifePlannerDBQueries).asFlow().mapToList(Dispatchers.IO))
}

private fun now() = Clock.System.now().toString()

// ── Logs ───────────────────────────────────────────────────────────────────

internal fun LifeLogEntity.toDomain() = LifeLog(
    id = id,
    area = PlanArea.fromKey(area) ?: PlanArea.HABITS,
    kind = LogKind.fromKey(kind),
    status = LogStatus.fromKey(status),
    title = title,
    amount = amount,
    currency = currency,
    category = category,
    quantity = quantity,
    unit = unit,
    durationMin = durationMin?.toInt(),
    occurredAt = LocalDateTime.parse(occurredAt),
    source = source,
    externalId = externalId,
    tripId = tripId,
    notes = notes,
)

class LifeLogRepositoryImpl(
    private val db: SharedDatabase,
    private val syncManager: SyncManager,
) : LifeLogRepository {

    override fun observeInRange(from: LocalDate, to: LocalDate): Flow<List<LifeLog>> =
        db.observe { it.selectLifeLogsInRange(from.toString(), to.toString()) }.map { rows -> rows.mapNotNull { runCatching { it.toDomain() }.getOrNull() } }

    override fun observeRecent(limit: Int): Flow<List<LifeLog>> =
        db.observe { it.selectRecentLifeLogs(limit.toLong()) }.map { rows -> rows.mapNotNull { runCatching { it.toDomain() }.getOrNull() } }

    override fun observeForTrip(tripId: String): Flow<List<LifeLog>> =
        db.observe { it.selectLifeLogsByTrip(tripId) }.map { rows -> rows.mapNotNull { runCatching { it.toDomain() }.getOrNull() } }

    override suspend fun getInRange(from: LocalDate, to: LocalDate): List<LifeLog> =
        db { it.lifePlannerDBQueries.selectLifeLogsInRange(from.toString(), to.toString()).executeAsList() }
            .mapNotNull { runCatching { it.toDomain() }.getOrNull() }

    override suspend fun getById(id: String): LifeLog? =
        db { it.lifePlannerDBQueries.selectLifeLogById(id).executeAsOneOrNull() }?.takeIf { it.is_deleted == 0L }?.toDomain()

    override suspend fun save(log: LifeLog) {
        write(log)
        syncManager.requestSync()
    }

    override suspend fun saveAll(logs: List<LifeLog>) {
        if (logs.isEmpty()) return
        logs.forEach { write(it) }
        syncManager.requestSync()
    }

    private suspend fun write(l: LifeLog) {
        val created = db { it.lifePlannerDBQueries.selectLifeLogById(l.id).executeAsOneOrNull() }?.createdAt ?: now()
        db {
            it.lifePlannerDBQueries.saveLifeLog(
                id = l.id, area = l.area.key, kind = l.kind.key, status = l.status.key, title = l.title,
                amount = l.amount, currency = l.currency, category = l.category, quantity = l.quantity, unit = l.unit,
                durationMin = l.durationMin?.toLong(), occurredAt = l.occurredAt.toString(), date = l.date.toString(),
                source = l.source, externalId = l.externalId, tripId = l.tripId, notes = l.notes, createdAt = created,
                id_ = l.id, id__ = l.id,
            )
        }
    }

    override suspend fun delete(id: String) {
        db { it.lifePlannerDBQueries.softDeleteLifeLog(id) }
        syncManager.requestSync()
    }
}

// ── Budgets ────────────────────────────────────────────────────────────────

internal fun BudgetEntity.toDomain() = Budget(
    id = id,
    area = PlanArea.fromKey(area) ?: PlanArea.MONEY,
    metric = metric,
    category = category,
    amount = amount,
    currency = currency,
    period = BudgetPeriod.fromKey(period),
    tripId = tripId,
)

class BudgetRepositoryImpl(
    private val db: SharedDatabase,
    private val syncManager: SyncManager,
) : BudgetRepository {
    override fun observeAll(): Flow<List<Budget>> = db.observe { it.selectAllBudgets() }.map { rows -> rows.map { it.toDomain() } }

    override suspend fun getAll(): List<Budget> = db { it.lifePlannerDBQueries.selectAllBudgets().executeAsList() }.map { it.toDomain() }

    override suspend fun save(budget: Budget) {
        val created = db { it.lifePlannerDBQueries.selectBudgetById(budget.id).executeAsOneOrNull() }?.createdAt ?: now()
        db {
            it.lifePlannerDBQueries.saveBudget(
                id = budget.id, area = budget.area.key, metric = budget.metric, category = budget.category,
                amount = budget.amount, currency = budget.currency, period = budget.period.key, tripId = budget.tripId,
                createdAt = created, id_ = budget.id, id__ = budget.id,
            )
        }
        syncManager.requestSync()
    }

    override suspend fun delete(id: String) {
        db { it.lifePlannerDBQueries.softDeleteBudget(id) }
        syncManager.requestSync()
    }
}

// ── Trips ──────────────────────────────────────────────────────────────────

internal fun TripEntity.toDomain() = Trip(
    id = id,
    destination = destination,
    latitude = latitude,
    longitude = longitude,
    startDate = LocalDate.parse(startDate),
    endDate = LocalDate.parse(endDate),
    budget = budget,
    currency = currency,
    travelMode = travelMode != 0L,
    notes = notes,
)

internal fun TripItemEntity.toDomain() = TripItem(
    id = id,
    tripId = tripId,
    kind = TripItemKind.fromKey(kind),
    title = title,
    notes = notes,
    date = date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    isDone = isDone != 0L,
    sortOrder = sortOrder.toInt(),
)

class TripRepositoryImpl(
    private val db: SharedDatabase,
    private val syncManager: SyncManager,
) : TripRepository {
    override fun observeAll(): Flow<List<Trip>> = db.observe { it.selectAllTrips() }.map { rows -> rows.mapNotNull { runCatching { it.toDomain() }.getOrNull() } }

    override fun observeItems(tripId: String): Flow<List<TripItem>> = db.observe { it.selectTripItems(tripId) }.map { rows -> rows.map { it.toDomain() } }

    override suspend fun getAll(): List<Trip> = db { it.lifePlannerDBQueries.selectAllTrips().executeAsList() }.mapNotNull { runCatching { it.toDomain() }.getOrNull() }

    override suspend fun getById(id: String): Trip? =
        db { it.lifePlannerDBQueries.selectTripById(id).executeAsOneOrNull() }?.takeIf { it.is_deleted == 0L }?.toDomain()

    override suspend fun save(trip: Trip) {
        val created = db { it.lifePlannerDBQueries.selectTripById(trip.id).executeAsOneOrNull() }?.createdAt ?: now()
        db {
            it.lifePlannerDBQueries.saveTrip(
                id = trip.id, destination = trip.destination, latitude = trip.latitude, longitude = trip.longitude,
                startDate = trip.startDate.toString(), endDate = trip.endDate.toString(), budget = trip.budget,
                currency = trip.currency, travelMode = if (trip.travelMode) 1L else 0L, notes = trip.notes,
                createdAt = created, id_ = trip.id, id__ = trip.id,
            )
        }
        syncManager.requestSync()
    }

    override suspend fun delete(id: String) {
        db { it.lifePlannerDBQueries.softDeleteTrip(id) }
        syncManager.requestSync()
    }

    override suspend fun saveItem(item: TripItem) {
        val created = db { it.lifePlannerDBQueries.selectTripItemById(item.id).executeAsOneOrNull() }?.createdAt ?: now()
        db {
            it.lifePlannerDBQueries.saveTripItem(
                id = item.id, tripId = item.tripId, kind = item.kind.key, title = item.title, notes = item.notes,
                date = item.date?.toString(), isDone = if (item.isDone) 1L else 0L, sortOrder = item.sortOrder.toLong(),
                createdAt = created, id_ = item.id, id__ = item.id,
            )
        }
        syncManager.requestSync()
    }

    override suspend fun deleteItem(id: String) {
        db { it.lifePlannerDBQueries.softDeleteTripItem(id) }
        syncManager.requestSync()
    }
}
