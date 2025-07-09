package az.tribe.lifeplanner.data.network

import az.tribe.lifeplanner.BuildKonfig
import az.tribe.lifeplanner.data.model.*
import az.tribe.lifeplanner.data.model.DataError.Remote
import az.tribe.lifeplanner.di.GEMINI_PRO
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import kotlinx.serialization.json.*

class GeminiServiceImpl(
    private val httpClient: HttpClient
) : GeminiService {


    // New implementation - question generation
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

        val requestBody = buildQuestionGenerationRequest(prompt)
        return safeCall {
            httpClient.post {
                url("v1beta/models/$GEMINI_PRO:generateContent")
                parameter("key",BuildKonfig.GEMINI_API_KEY)
                setBody(requestBody)
            }
        }
    }

    // New implementation - goals from answers
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

        val requestBody = buildGoalGenerationRequest(prompt)
        return safeCall {
            httpClient.post {
                url("v1beta/models/$GEMINI_PRO:generateContent")
                parameter("key",BuildKonfig.GEMINI_API_KEY)
                setBody(requestBody)
            }
        }
    }

    private fun formatAnswersForPrompt(userAnswers: UserQuestionnaireAnswers): String {
        return userAnswers.answers.joinToString("\n") { answer ->
            "- ${answer.questionTitle}: ${answer.selectedOption}"
        }
    }

    private fun buildGoalGenerationRequest(prompt: String): JsonObject {
        return buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", prompt)
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                putJsonObject("responseSchema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("goals") {
                            put("type", "array")
                            putJsonObject("items") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("title") {
                                        put("type", "string")
                                    }
                                    putJsonObject("description") {
                                        put("type", "string")
                                    }
                                    putJsonObject("notes") {
                                        put("type", "string")
                                    }
                                    putJsonObject("category") {
                                        put("type", "string")
                                        putJsonArray("enum") {
                                            add("FINANCIAL")
                                            add("CAREER")
                                            add("PHYSICAL")
                                            add("SOCIAL")
                                            add("EMOTIONAL")
                                            add("SPIRITUAL")
                                            add("FAMILY")
                                        }
                                    }
                                    putJsonObject("timeline") {
                                        put("type", "string")
                                        putJsonArray("enum") {
                                            add("SHORT_TERM")
                                            add("MID_TERM")
                                            add("LONG_TERM")
                                        }
                                    }
                                    putJsonObject("dueDate") {
                                        put("type", "string")
                                    }
                                    putJsonObject("milestones") {
                                        put("type", "array")
                                        putJsonObject("items") {
                                            put("type", "object")
                                            putJsonObject("properties") {
                                                putJsonObject("title") {
                                                    put("type", "string")
                                                }
                                            }
                                            putJsonArray("required") {
                                                add("title")
                                            }
                                        }
                                    }
                                }
                                putJsonArray("required") {
                                    add("title")
                                    add("description")
                                    add("notes")
                                    add("category")
                                    add("timeline")
                                    add("milestones")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun buildQuestionGenerationRequest(prompt: String): JsonObject {
        return buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", prompt)
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                putJsonObject("responseSchema") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("goals") {
                            put("type", "array")
                            putJsonObject("items") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("goal_type") {
                                        put("type", "string")
                                    }
                                    putJsonObject("questions") {
                                        put("type", "array")
                                        putJsonObject("items") {
                                            put("type", "object")
                                            putJsonObject("properties") {
                                                putJsonObject("title") {
                                                    put("type", "string")
                                                }
                                                putJsonObject("options") {
                                                    put("type", "array")
                                                    putJsonObject("items") {
                                                        put("type", "string")
                                                    }
                                                }
                                            }
                                            putJsonArray("required") {
                                                add("title")
                                                add("options")
                                            }
                                        }
                                    }
                                }
                                putJsonArray("required") {
                                    add("goal_type")
                                    add("questions")
                                }
                            }
                        }
                    }
                    putJsonArray("required") {
                        add("goals")
                    }
                }
            }
        }
    }
}