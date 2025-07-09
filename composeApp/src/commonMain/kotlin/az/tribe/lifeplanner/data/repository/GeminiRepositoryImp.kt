package az.tribe.lifeplanner.data.repository


import az.tribe.lifeplanner.data.mapper.getQuestionGenerationResponse
import az.tribe.lifeplanner.data.network.GeminiService
import az.tribe.lifeplanner.data.mapper.toDomain
import az.tribe.lifeplanner.data.model.DataError.Remote
import az.tribe.lifeplanner.data.model.GoalTypeQuestions
import az.tribe.lifeplanner.data.model.Result
import az.tribe.lifeplanner.data.model.UserQuestionnaireAnswers
import az.tribe.lifeplanner.data.model.map
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.repository.GeminiRepository


class GeminiRepositoryImp(
    private val geminiService: GeminiService
) : GeminiRepository {


    // New implementation - questionnaire generation
    override suspend fun generateQuestionnaire(
        userPrompt: String
    ): Result<List<GoalTypeQuestions>, Remote> {
        return geminiService.generateQuestions(userPrompt).map { response ->
            response.getQuestionGenerationResponse()?.goals ?: emptyList()
        }
    }

    // New implementation - personalized goals from answers
    override suspend fun generatePersonalizedGoals(
        originalPrompt: String,
        userAnswers: UserQuestionnaireAnswers
    ): Result<List<Goal>, Remote> {
        return geminiService.generateGoalsFromAnswers(originalPrompt, userAnswers)
            .map { it.toDomain() }
    }

}