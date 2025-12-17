package az.tribe.lifeplanner.usecases.journal

import az.tribe.lifeplanner.domain.model.JournalEntry
import az.tribe.lifeplanner.domain.repository.JournalRepository

class GetRecentJournalEntriesUseCase(
    private val repository: JournalRepository
) {
    suspend operator fun invoke(limit: Int = 10): List<JournalEntry> {
        return repository.getRecentEntries(limit)
    }
}
