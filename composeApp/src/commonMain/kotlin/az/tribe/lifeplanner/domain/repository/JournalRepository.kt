package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.JournalEntry
import kotlinx.datetime.LocalDate

interface JournalRepository {
    suspend fun getAllEntries(): List<JournalEntry>
    suspend fun getEntryById(id: String): JournalEntry?
    suspend fun getEntriesByDate(date: LocalDate): List<JournalEntry>
    suspend fun getEntriesByGoalId(goalId: String): List<JournalEntry>
    suspend fun getEntriesByMood(mood: Mood): List<JournalEntry>
    suspend fun getEntriesInRange(startDate: LocalDate, endDate: LocalDate): List<JournalEntry>
    suspend fun getRecentEntries(limit: Int = 10): List<JournalEntry>

    suspend fun insertEntry(entry: JournalEntry)
    suspend fun updateEntry(entry: JournalEntry)
    suspend fun deleteEntry(id: String)

    suspend fun searchEntries(query: String): List<JournalEntry>
    suspend fun getMoodStats(startDate: LocalDate, endDate: LocalDate): Map<Mood, Int>
}
