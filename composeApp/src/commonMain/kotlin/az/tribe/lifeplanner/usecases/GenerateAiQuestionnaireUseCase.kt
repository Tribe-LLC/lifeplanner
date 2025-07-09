package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.data.model.DataError.Remote
import az.tribe.lifeplanner.data.model.GoalTypeQuestions
import az.tribe.lifeplanner.data.model.Result
import az.tribe.lifeplanner.domain.repository.GeminiRepository

/**
 * Use case for generating questionnaire based on user's initial prompt
 */
class GenerateAiQuestionnaireUseCase(
    private val repository: GeminiRepository
) {
    suspend operator fun invoke(userPrompt: String): Result<List<GoalTypeQuestions>, Remote> {
        return repository.generateQuestionnaire(userPrompt)
    }
}

