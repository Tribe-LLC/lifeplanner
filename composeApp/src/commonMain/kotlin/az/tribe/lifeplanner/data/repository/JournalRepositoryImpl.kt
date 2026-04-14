package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.data.mapper.toDomain
import az.tribe.lifeplanner.data.mapper.toDomainJournalEntries
import az.tribe.lifeplanner.data.mapper.toEntity
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.JournalEntry
import az.tribe.lifeplanner.domain.repository.JournalRepository
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class JournalRepositoryImpl(
    private val database: SharedDatabase,
    private val syncManager: SyncManager
) : JournalRepository {

    override fun observeAllEntries(): Flow<List<JournalEntry>> {
        return database.observeAllJournalEntries().map { it.toDomainJournalEntries() }
    }

    override suspend fun getAllEntries(): List<JournalEntry> {
        return database.getAllJournalEntries().toDomainJournalEntries()
    }

    override suspend fun getEntryById(id: String): JournalEntry? {
        return database.getJournalEntryById(id)?.toDomain()
    }

    override suspend fun getEntriesByDate(date: LocalDate): List<JournalEntry> {
        return database.getJournalEntriesByDate(date.toString()).toDomainJournalEntries()
    }

    override suspend fun getEntriesByGoalId(goalId: String): List<JournalEntry> {
        return database.getJournalEntriesByGoalId(goalId).toDomainJournalEntries()
    }

    override suspend fun getEntriesByHabitId(habitId: String): List<JournalEntry> {
        return database.getJournalEntriesByHabitId(habitId).toDomainJournalEntries()
    }

    override suspend fun getEntriesByMood(mood: Mood): List<JournalEntry> {
        return database.getJournalEntriesByMood(mood.name).toDomainJournalEntries()
    }

    override suspend fun getEntriesInRange(startDate: LocalDate, endDate: LocalDate): List<JournalEntry> {
        return database.getJournalEntriesInRange(startDate.toString(), endDate.toString())
            .toDomainJournalEntries()
    }

    override suspend fun getRecentEntries(limit: Int): List<JournalEntry> {
        return database.getRecentJournalEntries(limit.toLong()).toDomainJournalEntries()
    }

    override suspend fun insertEntry(entry: JournalEntry) {
        database.insertJournalEntry(entry.toEntity())
        syncManager.requestSync()
    }

    override suspend fun updateEntry(entry: JournalEntry) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val updatedEntry = entry.copy(updatedAt = now)
        database.updateJournalEntry(updatedEntry.toEntity())
        syncManager.requestSync()
    }

    override suspend fun deleteEntry(id: String) {
        database.deleteJournalEntry(id)
        syncManager.requestSync()
    }

    override suspend fun searchEntries(query: String): List<JournalEntry> {
        return database.searchJournalEntries(query).toDomainJournalEntries()
    }

    override suspend fun getMoodStats(startDate: LocalDate, endDate: LocalDate): Map<Mood, Int> {
        return database.getMoodCountInRange(startDate.toString(), endDate.toString())
            .mapKeys { Mood.fromString(it.key) }
            .mapValues { it.value.toInt() }
    }
}
