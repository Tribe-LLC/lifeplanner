package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.repository.GoalHistoryRepository
import kotlinx.datetime.Clock

class LogGoalChangeUseCase(private val repository: GoalHistoryRepository) {
    suspend operator fun invoke(
        goalId: String,
        field: String,
        oldValue: String?,
        newValue: String
    ) {
        repository.insertChange(
            id = generateChangeId(),
            goalId = goalId,
            field = field,
            oldValue = oldValue,
            newValue = newValue,
            changedAt = Clock.System.now().toString()
        )
    }

    private fun generateChangeId(): String {
        return "change_" + Clock.System.now().toEpochMilliseconds().toString()
    }
}