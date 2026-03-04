package az.tribe.lifeplanner.data.network

import az.tribe.lifeplanner.data.model.*
import az.tribe.lifeplanner.data.model.DataError.Remote
import co.touchlab.kermit.Logger
import kotlinx.serialization.json.*

class GeminiServiceImpl(
    private val aiProxy: AiProxyService
) : GeminiService {

    private val log = Logger.withTag("GeminiService")

    private fun wrapInGeminiResponseDto(text: String): GeminiResponseDto {
        return GeminiResponseDto(
            candidates = listOf(
                CandidateDto(
                    content = ContentDto(
                        parts = listOf(PartDto(text = text)),
                        role = "model"
                    )
                )
            )
        )
    }

    override suspend fun generateQuestions(userPrompt: String): Result<GeminiResponseDto, Remote> {
        val prompt = """
            Based on the user's goal statement: "$userPrompt"

            1. First, analyze their prompt and determine the most relevant goal type(s) from: FINANCIAL, CAREER, PHYSICAL, SOCIAL, EMOTIONAL, SPIRITUAL, FAMILY

            2. Then generate 5-7 focused questions that will help create personalized goals for their specific situation.

            All questions should be directly related to their stated goal and help understand:
            - Their current situation and constraints
            - Timeline preferences and urgency
            - Specific priorities within their goal area
            - Resources and limitations they have
            - Their motivation level and commitment

            Each question should have 3-4 realistic multiple choice options.

            Example: If user says "I want to get fit and save money"
            - Determine goal_type as something like "PHYSICAL_AND_FINANCIAL"
            - Ask questions about fitness level, time availability, budget constraints, savings targets, etc.

            Focus all questions on helping achieve their specific stated goal.
        """.trimIndent()

        val schema = buildQuestionGenerationSchema()

        return try {
            val text = aiProxy.generateStructuredJson(prompt, schema)
            Result.Success(wrapInGeminiResponseDto(text))
        } catch (e: Exception) {
            log.e(e) { "generateQuestions failed" }
            Result.Error(Remote.UNKNOWN)
        }
    }

    override suspend fun generateGoalsFromAnswers(
        originalPrompt: String,
        userAnswers: UserQuestionnaireAnswers
    ): Result<GeminiResponseDto, Remote> {
        val answersText = formatAnswersForPrompt(userAnswers)

        val prompt = """
            Original user goal: "$originalPrompt"

            Based on their initial goal and the following detailed responses, create personalized, specific goals:

            $answersText

            Generate realistic, actionable goals that:
            - Address their original stated goal
            - Align with their detailed preferences and situation from the questionnaire
            - Are specific and measurable
            - Include appropriate timelines based on their preferences
            - Have relevant milestones that break down the goal into manageable steps
            - Consider their current situation and constraints

            Create 3-7 goals total, focusing on what they originally wanted to achieve but now personalized based on their answers.
        """.trimIndent()

        val schema = buildGoalGenerationSchema()

        return try {
            val text = aiProxy.generateStructuredJson(prompt, schema)
            Result.Success(wrapInGeminiResponseDto(text))
        } catch (e: Exception) {
            log.e(e) { "generateGoalsFromAnswers failed" }
            Result.Error(Remote.UNKNOWN)
        }
    }

    override suspend fun generateGoalsDirect(prompt: String): Result<GeminiResponseDto, Remote> {
        val enrichedPrompt = """
            Based on the user's goal statement: "$prompt"

            Generate personalized, specific, and actionable goals that:
            - Directly address what the user wants to achieve
            - Are specific and measurable with clear success criteria
            - Include appropriate timelines (SHORT_TERM for < 3 months, MID_TERM for 3-6 months, LONG_TERM for 6+ months)
            - Have 3-5 concrete milestones each that break the goal into manageable steps
            - Consider practical constraints and realistic expectations
            - Cover different aspects of the user's stated goal

            Create 3-5 well-thought-out goals. Make each goal distinct and covering a different dimension of what the user wants to achieve. Use dueDate in YYYY-MM-DD format.
        """.trimIndent()

        val schema = buildGoalGenerationSchema()

        return try {
            val text = aiProxy.generateStructuredJson(enrichedPrompt, schema)
            Result.Success(wrapInGeminiResponseDto(text))
        } catch (e: Exception) {
            log.e(e) { "generateGoalsDirect failed" }
            Result.Error(Remote.UNKNOWN)
        }
    }

    private fun formatAnswersForPrompt(userAnswers: UserQuestionnaireAnswers): String {
        return userAnswers.answers.joinToString("\n") { answer ->
            "- ${answer.questionTitle}: ${answer.selectedOption}"
        }
    }

    private fun buildGoalGenerationSchema(): JsonObject {
        return buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("goals") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("title") { put("type", "string") }
                            putJsonObject("description") { put("type", "string") }
                            putJsonObject("notes") { put("type", "string") }
                            putJsonObject("category") {
                                put("type", "string")
                                putJsonArray("enum") {
                                    add("FINANCIAL"); add("CAREER"); add("PHYSICAL")
                                    add("SOCIAL"); add("EMOTIONAL"); add("SPIRITUAL"); add("FAMILY")
                                }
                            }
                            putJsonObject("timeline") {
                                put("type", "string")
                                putJsonArray("enum") { add("SHORT_TERM"); add("MID_TERM"); add("LONG_TERM") }
                            }
                            putJsonObject("dueDate") { put("type", "string") }
                            putJsonObject("milestones") {
                                put("type", "array")
                                putJsonObject("items") {
                                    put("type", "object")
                                    putJsonObject("properties") {
                                        putJsonObject("title") { put("type", "string") }
                                    }
                                    putJsonArray("required") { add("title") }
                                }
                            }
                        }
                        putJsonArray("required") {
                            add("title"); add("description"); add("notes")
                            add("category"); add("timeline"); add("milestones")
                        }
                    }
                }
            }
        }
    }

    private fun buildQuestionGenerationSchema(): JsonObject {
        return buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("goals") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("goal_type") { put("type", "string") }
                            putJsonObject("questions") {
                                put("type", "array")
                                putJsonObject("items") {
                                    put("type", "object")
                                    putJsonObject("properties") {
                                        putJsonObject("title") { put("type", "string") }
                                        putJsonObject("options") {
                                            put("type", "array")
                                            putJsonObject("items") { put("type", "string") }
                                        }
                                    }
                                    putJsonArray("required") { add("title"); add("options") }
                                }
                            }
                        }
                        putJsonArray("required") { add("goal_type"); add("questions") }
                    }
                }
            }
            putJsonArray("required") { add("goals") }
        }
    }
}
