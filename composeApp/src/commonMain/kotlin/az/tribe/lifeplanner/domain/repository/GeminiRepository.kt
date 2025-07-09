package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.data.model.DataError.Remote
import az.tribe.lifeplanner.data.model.GoalTypeQuestions
import az.tribe.lifeplanner.data.model.Result
import az.tribe.lifeplanner.data.model.UserQuestionnaireAnswers
import az.tribe.lifeplanner.domain.model.Goal


interface GeminiRepository {
    suspend fun generateQuestionnaire(userPrompt: String): Result<List<GoalTypeQuestions>, Remote>
    suspend fun generatePersonalizedGoals(
        originalPrompt: String,
        userAnswers: UserQuestionnaireAnswers,
    ): Result<List<Goal>, Remote>
}
