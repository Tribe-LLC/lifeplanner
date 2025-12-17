package az.tribe.lifeplanner.usecases.journal

import az.tribe.lifeplanner.domain.repository.JournalRepository

class DeleteJournalEntryUseCase(
    private val repository: JournalRepository
) {
    suspend operator fun invoke(id: String) {
        repository.deleteEntry(id)
    }
}
