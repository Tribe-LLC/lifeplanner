package az.tribe.lifeplanner.data.network

import az.tribe.lifeplanner.data.model.DataError.Remote
import az.tribe.lifeplanner.data.model.GeminiResponseDto
import az.tribe.lifeplanner.data.model.Result
import az.tribe.lifeplanner.data.model.UserQuestionnaireAnswers

interface GeminiService {
    suspend fun generateQuestions(userPrompt: String): Result<GeminiResponseDto, Remote>
    suspend fun generateGoalsFromAnswers(
        originalPrompt: String,
        userAnswers: UserQuestionnaireAnswers,
    ): Result<GeminiResponseDto, Remote>
    suspend fun generateGoalsDirect(prompt: String): Result<GeminiResponseDto, Remote>
}