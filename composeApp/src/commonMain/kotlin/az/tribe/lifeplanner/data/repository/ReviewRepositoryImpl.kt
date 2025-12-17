package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.BuildKonfig
import az.tribe.lifeplanner.data.mapper.*
import az.tribe.lifeplanner.di.GEMINI_PRO
import az.tribe.lifeplanner.domain.model.*
import az.tribe.lifeplanner.domain.repository.ReviewRepository
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import kotlinx.datetime.*
import kotlinx.serialization.json.*

class ReviewRepositoryImpl(
    private val database: SharedDatabase,
    private val httpClient: HttpClient
) : ReviewRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getAllReviews(): List<ReviewReport> {
        return database.getAllReviews().toDomainReviews()
    }

    override suspend fun getReviewById(id: String): ReviewReport? {
        return database.getReviewById(id)?.toDomain()
    }

    override suspend fun getReviewsByType(type: ReviewType): List<ReviewReport> {
        return database.getReviewsByType(type.name).toDomainReviews()
    }

    override suspend fun getLatestReview(type: ReviewType): ReviewReport? {
        return database.getLatestReviewByType(type.name)?.toDomain()
    }

    override suspend fun getUnreadReviews(): List<ReviewReport> {
        return database.getUnreadReviews().toDomainReviews()
    }

    override suspend fun generateReview(
        type: ReviewType,
        periodStart: LocalDate,
        periodEnd: LocalDate
    ): ReviewReport {
        // Gather user data for the period
        val stats = gatherPeriodStats(periodStart, periodEnd)
        val previousStats = getPreviousPeriodStats(type, periodStart)

        // Generate AI content
        val aiContent = generateAIReviewContent(type, periodStart, periodEnd, stats, previousStats)

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        val review = ReviewReport(
            id = generateReviewId(),
            type = type,
            periodStart = periodStart,
            periodEnd = periodEnd,
            generatedAt = now,
            summary = aiContent.summary,
            highlights = aiContent.highlights,
            insights = aiContent.insights,
            recommendations = aiContent.recommendations,
            stats = stats.copy(comparisonToPrevious = previousStats?.let { prev ->
                StatsComparison(
                    goalsCompletedDiff = stats.goalsCompleted - prev.goalsCompleted,
                    habitCompletionRateDiff = stats.habitCompletionRate - prev.habitCompletionRate,
                    xpEarnedDiff = stats.xpEarned - prev.xpEarned,
                    overallTrend = calculateOverallTrend(stats, prev)
                )
            }),
            isRead = false
        )

        // Save to database
        database.insertReview(
            id = review.id,
            type = review.type.name,
            periodStart = review.periodStart.toString(),
            periodEnd = review.periodEnd.toString(),
            generatedAt = review.generatedAt.toString(),
            summary = review.summary,
            highlightsJson = review.highlights.toJson(),
            insightsJson = review.insights.toInsightsJson(),
            recommendationsJson = review.recommendations.toRecommendationsJson(),
            statsJson = review.stats.toJson(),
            feedbackRating = null,
            feedbackComment = null,
            feedbackAt = null,
            isRead = 0
        )

        return review
    }

    private suspend fun gatherPeriodStats(start: LocalDate, end: LocalDate): ReviewStats {
        val allGoals = database.getAllGoals()
        val completedGoals = database.getCompletedGoals()
        val activeGoals = database.getActiveGoals()
        val userProgress = database.getUserProgressEntity()

        // Calculate category distribution
        val categoryCount = database.getGoalCountByCategory()
        val topCategory = categoryCount.maxByOrNull { it.value }?.key

        return ReviewStats(
            goalsCompleted = completedGoals.size,
            goalsInProgress = activeGoals.size,
            milestonesCompleted = 0, // TODO: Count completed milestones from goals
            habitsTracked = 0, // Would calculate from habit data
            habitCompletionRate = 0.75f, // Would calculate from habit check-ins
            journalEntries = userProgress?.journalEntriesCount?.toInt() ?: 0,
            xpEarned = userProgress?.totalXp?.toInt() ?: 0,
            streakDays = userProgress?.currentStreak?.toInt() ?: 0,
            mostActiveCategory = topCategory
        )
    }

    private suspend fun getPreviousPeriodStats(type: ReviewType, currentStart: LocalDate): ReviewStats? {
        val previousReview = database.getLatestReviewByType(type.name)
        return previousReview?.let {
            json.decodeFromString<ReviewStats>(it.statsJson)
        }
    }

    private fun calculateOverallTrend(current: ReviewStats, previous: ReviewStats): TrendDirection {
        var score = 0
        if (current.goalsCompleted > previous.goalsCompleted) score++
        if (current.goalsCompleted < previous.goalsCompleted) score--
        if (current.habitCompletionRate > previous.habitCompletionRate) score++
        if (current.habitCompletionRate < previous.habitCompletionRate) score--
        if (current.xpEarned > previous.xpEarned) score++
        if (current.xpEarned < previous.xpEarned) score--

        return when {
            score > 0 -> TrendDirection.UP
            score < 0 -> TrendDirection.DOWN
            else -> TrendDirection.STABLE
        }
    }

    private data class AIReviewContent(
        val summary: String,
        val highlights: List<ReviewHighlight>,
        val insights: List<ReviewInsight>,
        val recommendations: List<ReviewRecommendation>
    )

    private suspend fun generateAIReviewContent(
        type: ReviewType,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        stats: ReviewStats,
        previousStats: ReviewStats?
    ): AIReviewContent {
        val prompt = buildReviewPrompt(type, periodStart, periodEnd, stats, previousStats)

        return try {
            val response = callGeminiForReview(prompt)
            parseAIResponse(response)
        } catch (e: Exception) {
            // Fallback to generated content if AI fails
            generateFallbackContent(type, stats)
        }
    }

    private fun buildReviewPrompt(
        type: ReviewType,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        stats: ReviewStats,
        previousStats: ReviewStats?
    ): String {
        val periodName = when (type) {
            ReviewType.WEEKLY -> "week"
            ReviewType.MONTHLY -> "month"
            ReviewType.QUARTERLY -> "quarter"
            ReviewType.YEARLY -> "year"
        }

        val comparison = previousStats?.let {
            """
            Compared to last $periodName:
            - Goals completed: ${if (stats.goalsCompleted >= it.goalsCompleted) "+" else ""}${stats.goalsCompleted - it.goalsCompleted}
            - Habit rate change: ${if (stats.habitCompletionRate >= it.habitCompletionRate) "+" else ""}${((stats.habitCompletionRate - it.habitCompletionRate) * 100).toInt()}%
            - XP earned: ${if (stats.xpEarned >= it.xpEarned) "+" else ""}${stats.xpEarned - it.xpEarned}
            """.trimIndent()
        } ?: "This is the first review, no comparison available."

        return """
            Generate a ${periodName}ly review for a goal-tracking app user. Be encouraging but honest.

            Period: ${periodStart} to ${periodEnd}

            User Statistics:
            - Goals completed: ${stats.goalsCompleted}
            - Goals in progress: ${stats.goalsInProgress}
            - Habit completion rate: ${(stats.habitCompletionRate * 100).toInt()}%
            - Journal entries written: ${stats.journalEntries}
            - XP earned: ${stats.xpEarned}
            - Current streak: ${stats.streakDays} days
            - Most active category: ${stats.mostActiveCategory ?: "Various"}

            $comparison

            Please provide:
            1. A brief, motivating summary (2-3 sentences)
            2. 2-3 highlights/achievements to celebrate
            3. 2-3 insights about their patterns
            4. 2-3 actionable recommendations for next $periodName

            Keep the tone warm, supportive, and personalized.
        """.trimIndent()
    }

    private suspend fun callGeminiForReview(prompt: String): String {
        val requestBody = buildJsonObject {
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
                put("temperature", 0.7)
                put("maxOutputTokens", 1500)
            }
        }

        val response = httpClient.post {
            url("v1beta/models/$GEMINI_PRO:generateContent")
            parameter("key", BuildKonfig.GEMINI_API_KEY)
            setBody(requestBody)
        }

        val responseBody: JsonObject = response.body()
        return responseBody["candidates"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("content")
            ?.jsonObject
            ?.get("parts")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.content ?: ""
    }

    private fun parseAIResponse(response: String): AIReviewContent {
        // Parse the AI response into structured content
        // This is a simplified parser - production would be more robust
        val lines = response.split("\n").filter { it.isNotBlank() }

        val summary = lines.take(3).joinToString(" ").take(300)

        val highlights = mutableListOf<ReviewHighlight>()
        val insights = mutableListOf<ReviewInsight>()
        val recommendations = mutableListOf<ReviewRecommendation>()

        var currentSection = ""

        lines.forEach { line ->
            when {
                line.contains("highlight", ignoreCase = true) ||
                        line.contains("achievement", ignoreCase = true) -> currentSection = "highlights"
                line.contains("insight", ignoreCase = true) ||
                        line.contains("pattern", ignoreCase = true) -> currentSection = "insights"
                line.contains("recommendation", ignoreCase = true) ||
                        line.contains("suggest", ignoreCase = true) -> currentSection = "recommendations"
                line.startsWith("-") || line.startsWith("•") || line.matches(Regex("^\\d+\\..*")) -> {
                    val content = line.trimStart('-', '•', ' ').replace(Regex("^\\d+\\.\\s*"), "")
                    when (currentSection) {
                        "highlights" -> highlights.add(
                            ReviewHighlight(
                                id = generateHighlightId(),
                                title = content.take(50),
                                description = content,
                                category = HighlightCategory.PERSONAL_BEST
                            )
                        )
                        "insights" -> insights.add(
                            ReviewInsight(
                                id = generateInsightId(),
                                title = content.take(50),
                                description = content,
                                type = InsightType.PRODUCTIVITY_PATTERN
                            )
                        )
                        "recommendations" -> recommendations.add(
                            ReviewRecommendation(
                                id = generateRecommendationId(),
                                title = content.take(50),
                                description = content,
                                actionType = RecommendationAction.FOCUS_CATEGORY,
                                priority = RecommendationPriority.MEDIUM
                            )
                        )
                    }
                }
            }
        }

        return AIReviewContent(
            summary = summary,
            highlights = highlights.take(3),
            insights = insights.take(3),
            recommendations = recommendations.take(3)
        )
    }

    private fun generateFallbackContent(type: ReviewType, stats: ReviewStats): AIReviewContent {
        val periodName = when (type) {
            ReviewType.WEEKLY -> "week"
            ReviewType.MONTHLY -> "month"
            ReviewType.QUARTERLY -> "quarter"
            ReviewType.YEARLY -> "year"
        }

        return AIReviewContent(
            summary = "You've made progress this $periodName! You completed ${stats.goalsCompleted} goals and maintained a ${(stats.habitCompletionRate * 100).toInt()}% habit completion rate. Keep up the momentum!",
            highlights = listOf(
                ReviewHighlight(
                    id = generateHighlightId(),
                    title = "Goals Completed",
                    description = "You completed ${stats.goalsCompleted} goals this $periodName",
                    category = HighlightCategory.GOAL_COMPLETED
                ),
                ReviewHighlight(
                    id = generateHighlightId(),
                    title = "Consistency",
                    description = "You maintained a ${stats.streakDays}-day streak",
                    category = HighlightCategory.STREAK_ACHIEVED
                )
            ),
            insights = listOf(
                ReviewInsight(
                    id = generateInsightId(),
                    title = "Focus Area",
                    description = "Your most active category was ${stats.mostActiveCategory ?: "varied"}",
                    type = InsightType.CATEGORY_FOCUS
                )
            ),
            recommendations = listOf(
                ReviewRecommendation(
                    id = generateRecommendationId(),
                    title = "Keep Going",
                    description = "Continue building on your ${stats.mostActiveCategory ?: "current"} goals",
                    actionType = RecommendationAction.FOCUS_CATEGORY,
                    priority = RecommendationPriority.MEDIUM
                )
            )
        )
    }

    override suspend fun markAsRead(reviewId: String) {
        database.markReviewAsRead(reviewId)
    }

    override suspend fun submitFeedback(reviewId: String, rating: FeedbackRating, comment: String?) {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        database.updateReviewFeedback(reviewId, rating.name, comment, now.toString())
    }

    override suspend fun deleteReview(reviewId: String) {
        database.deleteReview(reviewId)
    }

    override suspend fun getReviewSummaryCards(): List<ReviewSummaryCard> {
        return getAllReviews().take(5).map { it.toSummaryCard() }
    }

    override suspend fun shouldGenerateReview(type: ReviewType): Boolean {
        val latest = getLatestReview(type) ?: return true
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        val daysSinceLastReview = now.toEpochDays() - latest.periodEnd.toEpochDays()

        return when (type) {
            ReviewType.WEEKLY -> daysSinceLastReview >= 7
            ReviewType.MONTHLY -> daysSinceLastReview >= 28
            ReviewType.QUARTERLY -> daysSinceLastReview >= 90
            ReviewType.YEARLY -> daysSinceLastReview >= 365
        }
    }

    override suspend fun getUnreadCount(): Long {
        return database.getUnreadReviewCount()
    }

    override suspend fun cleanupOldReviews(keepCount: Int) {
        ReviewType.entries.forEach { type ->
            val reviews = getReviewsByType(type)
            if (reviews.size > keepCount) {
                reviews.drop(keepCount).forEach { review ->
                    deleteReview(review.id)
                }
            }
        }
    }
}
