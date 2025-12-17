package az.tribe.lifeplanner.usecases.journal

import az.tribe.lifeplanner.domain.model.JournalEntry
import az.tribe.lifeplanner.domain.repository.JournalRepository

class GetAllJournalEntriesUseCase(
    private val repository: JournalRepository
) {
    suspend operator fun invoke(): List<JournalEntry> {
        return repository.getAllEntries()
    }
}
