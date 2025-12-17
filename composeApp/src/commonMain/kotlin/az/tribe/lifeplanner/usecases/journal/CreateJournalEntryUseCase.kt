package az.tribe.lifeplanner.usecases.journal

import az.tribe.lifeplanner.domain.model.JournalEntry
import az.tribe.lifeplanner.domain.repository.JournalRepository

class CreateJournalEntryUseCase(
    private val repository: JournalRepository
) {
    suspend operator fun invoke(entry: JournalEntry) {
        repository.insertEntry(entry)
    }
}
