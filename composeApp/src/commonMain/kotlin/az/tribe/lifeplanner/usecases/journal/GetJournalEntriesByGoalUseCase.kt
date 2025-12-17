package az.tribe.lifeplanner.usecases.journal

import az.tribe.lifeplanner.domain.model.JournalEntry
import az.tribe.lifeplanner.domain.repository.JournalRepository

class GetJournalEntriesByGoalUseCase(
    private val repository: JournalRepository
) {
    suspend operator fun invoke(goalId: String): List<JournalEntry> {
        return repository.getEntriesByGoalId(goalId)
    }
}
