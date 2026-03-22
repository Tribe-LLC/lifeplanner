package az.tribe.lifeplanner.data.model

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeminiResponseDto(
    val candidates: List<CandidateDto>,
)

@Serializable
data class CandidateDto(
    val content: ContentDto,
)

@Serializable
data class ContentDto(
    val parts: List<PartDto>,
    val role: String
)

@Serializable
data class PartDto(
    val text: String
)

// Goal-specific response models (DTOs from API)
@Serializable
data class GoalGenerationResponse(
    val goals: List<GoalDto>
)

@Serializable
data class GoalDto(
    val title: String,
    val description: String,
    val notes: String = "",
    val category: GoalCategory,
    val timeline: GoalTimeline,
    val dueDate: String? = null, // Will be parsed to LocalDate, optional from AI
    val milestones: List<MilestoneDto> = emptyList(),
    val reasoning: String? = null // AI explanation for why this goal was suggested
)

@Serializable
data class MilestoneDto(
    val title: String
)

// NEW: Question Generation Models
@Serializable
data class QuestionGenerationResponse(
    val goals: List<GoalTypeQuestions>
)

@Serializable
data class GoalTypeQuestions(
    @SerialName("goal_type") val goalType: String,
    val questions: List<Question>
)

@Serializable
data class Question(
    val title: String,
    val options: List<String>
)

// NEW: User's answers to the questions
@Serializable
data class UserQuestionnaireAnswers(
    val answers: List<QuestionAnswer>
)

@Serializable
data class QuestionAnswer(
    val questionTitle: String,
    val selectedOption: String
)