package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.data.model.DataError
import az.tribe.lifeplanner.data.model.Result
import az.tribe.lifeplanner.data.model.UserQuestionnaireAnswers
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.repository.GeminiRepository

/**
 * Use case for generating personalized goals from questionnaire answers
 */
class GenerateAiGoalsUseCase(
    private val repository: GeminiRepository
) {
    suspend operator fun invoke(
        originalPrompt: String,
        userAnswers: UserQuestionnaireAnswers,
    ): Result<List<Goal>, DataError.Remote> {
        return repository.generatePersonalizedGoals(originalPrompt, userAnswers)
    }
}