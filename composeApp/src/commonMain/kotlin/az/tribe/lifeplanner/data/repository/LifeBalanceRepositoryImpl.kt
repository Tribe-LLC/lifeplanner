package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.model.BalanceInsight
import az.tribe.lifeplanner.domain.model.BalanceRating
import az.tribe.lifeplanner.domain.model.BalanceRecommendation
import az.tribe.lifeplanner.domain.model.BalanceTrend
import az.tribe.lifeplanner.domain.model.InsightPriority
import az.tribe.lifeplanner.domain.model.LifeArea
import az.tribe.lifeplanner.domain.model.LifeAreaScore
import az.tribe.lifeplanner.domain.model.LifeBalanceReport
import az.tribe.lifeplanner.domain.model.ManualAssessment
import az.tribe.lifeplanner.domain.model.BalanceRecommendationAction
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.LifeBalanceRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class LifeBalanceRepositoryImpl(
    private val goalRepository: GoalRepository,
    private val habitRepository: HabitRepository,
    private val httpClient: HttpClient
) : LifeBalanceRepository {

    private val balanceHistory = MutableStateFlow<List<LifeBalanceReport>>(emptyList())
    private val manualAssessments = mutableListOf<ManualAssessment>()
    private var latestReport: LifeBalanceReport? = null

    override suspend fun calculateCurrentBalance(): LifeBalanceReport {
        val areaScores = getAllAreaScores()
        return generateAIInsights(areaScores)
    }

    override suspend fun getAreaScore(area: LifeArea): LifeAreaScore {
        val category = area.toGoalCategory()
        val goals = goalRepository.getAllGoals()
        val habits = habitRepository.getAllHabits()

        val areaGoals = if (category != null) {
            goals.filter { it.category == category }
        } else {
            // PERSONAL_GROWTH - aggregate from all categories with learning/growth keywords
            goals.filter { goal ->
                goal.title.lowercase().contains("learn") ||
                goal.title.lowercase().contains("read") ||
                goal.title.lowercase().contains("study") ||
                goal.title.lowercase().contains("skill") ||
                goal.title.lowercase().contains("course")
            }
        }

        val areaHabits = if (category != null) {
            habits.filter { habit ->
                habit.category == category
            }
        } else {
            habits.filter { habit ->
                habit.title.lowercase().contains("learn") ||
                habit.title.lowercase().contains("read") ||
                habit.title.lowercase().contains("study")
            }
        }

        val totalGoals = areaGoals.size
        val completedGoals = areaGoals.count { it.status == GoalStatus.COMPLETED }
        val activeGoals = areaGoals.count { it.status == GoalStatus.IN_PROGRESS }

        // Calculate habit completion rate (last 7 days)
        val habitCompletionRate = if (areaHabits.isNotEmpty()) {
            areaHabits.map { habit ->
                val streak = habit.currentStreak
                val targetDays = 7
                (streak.coerceAtMost(targetDays).toFloat() / targetDays)
            }.average().toFloat()
        } else {
            0f
        }

        // Calculate recent activity score
        val recentActivityScore = calculateRecentActivityScore(areaGoals, areaHabits)

        // Calculate overall score (0-100)
        val score = calculateAreaScore(
            totalGoals = totalGoals,
            completedGoals = completedGoals,
            activeGoals = activeGoals,
            habitCount = areaHabits.size,
            habitCompletionRate = habitCompletionRate,
            recentActivityScore = recentActivityScore
        )

        // Determine trend (simplified - compare with manual assessments or default to STABLE)
        val trend = determineTrend(area, score)

        return LifeAreaScore(
            area = area,
            score = score,
            goalCount = totalGoals,
            completedGoals = completedGoals,
            activeGoals = activeGoals,
            habitCount = areaHabits.size,
            habitCompletionRate = habitCompletionRate,
            recentActivityScore = recentActivityScore,
            trend = trend
        )
    }

    override suspend fun getAllAreaScores(): List<LifeAreaScore> {
        return LifeArea.entries.map { area ->
            getAreaScore(area)
        }
    }

    override suspend fun generateAIInsights(areaScores: List<LifeAreaScore>): LifeBalanceReport {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val overallScore = areaScores.map { it.score }.average().toInt()

        val sortedByScore = areaScores.sortedByDescending { it.score }
        val strongestAreas = sortedByScore.take(3).map { it.area }
        val weakestAreas = sortedByScore.takeLast(3).reversed().map { it.area }

        val balanceRating = calculateBalanceRating(areaScores)

        // Generate AI insights using Gemini
        val (insights, recommendations) = try {
            generateAIAnalysis(areaScores, balanceRating)
        } catch (e: Exception) {
            // Fallback to rule-based insights
            generateRuleBasedInsights(areaScores, balanceRating)
        }

        val report = LifeBalanceReport(
            id = Uuid.random().toString(),
            overallScore = overallScore,
            areaScores = areaScores,
            strongestAreas = strongestAreas,
            weakestAreas = weakestAreas,
            balanceRating = balanceRating,
            aiInsights = insights,
            recommendations = recommendations,
            generatedAt = now
        )

        latestReport = report
        return report
    }

    override suspend fun saveManualAssessment(assessment: ManualAssessment) {
        manualAssessments.removeAll { it.area == assessment.area }
        manualAssessments.add(assessment)
    }

    override suspend fun getManualAssessments(): List<ManualAssessment> {
        return manualAssessments.toList()
    }

    override fun getBalanceHistory(): Flow<List<LifeBalanceReport>> {
        return balanceHistory
    }

    override suspend fun saveBalanceReport(report: LifeBalanceReport) {
        val currentHistory = balanceHistory.value.toMutableList()
        currentHistory.add(0, report)
        // Keep only last 30 reports
        balanceHistory.value = currentHistory.take(30)
        latestReport = report
    }

    override suspend fun getLatestReport(): LifeBalanceReport? {
        return latestReport
    }

    // Helper functions

    private fun LifeArea.toGoalCategory(): GoalCategory? {
        return when (this) {
            LifeArea.CAREER -> GoalCategory.CAREER
            LifeArea.FINANCIAL -> GoalCategory.FINANCIAL
            LifeArea.PHYSICAL -> GoalCategory.PHYSICAL
            LifeArea.SOCIAL -> GoalCategory.SOCIAL
            LifeArea.EMOTIONAL -> GoalCategory.EMOTIONAL
            LifeArea.SPIRITUAL -> GoalCategory.SPIRITUAL
            LifeArea.FAMILY -> GoalCategory.FAMILY
            LifeArea.PERSONAL_GROWTH -> null // Special case - aggregated
        }
    }

    private fun calculateRecentActivityScore(
        goals: List<az.tribe.lifeplanner.domain.model.Goal>,
        habits: List<az.tribe.lifeplanner.domain.model.Habit>
    ): Int {
        var score = 0

        // Points for active goals
        score += goals.count { it.status == GoalStatus.IN_PROGRESS } * 10

        // Points for recent goal completions
        score += goals.count { it.status == GoalStatus.COMPLETED } * 15

        // Points for habits with streaks
        habits.forEach { habit ->
            score += when {
                habit.currentStreak >= 30 -> 20
                habit.currentStreak >= 14 -> 15
                habit.currentStreak >= 7 -> 10
                habit.currentStreak >= 3 -> 5
                else -> 0
            }
        }

        return score.coerceIn(0, 100)
    }

    private fun calculateAreaScore(
        totalGoals: Int,
        completedGoals: Int,
        activeGoals: Int,
        habitCount: Int,
        habitCompletionRate: Float,
        recentActivityScore: Int
    ): Int {
        // Base score from goal engagement (0-40 points)
        val goalEngagementScore = when {
            totalGoals == 0 -> 10 // No goals = low engagement
            else -> {
                val completionRatio = completedGoals.toFloat() / totalGoals
                val activeRatio = activeGoals.toFloat() / totalGoals
                ((completionRatio * 20) + (activeRatio * 20)).toInt()
            }
        }

        // Habit consistency score (0-30 points)
        val habitScore = when {
            habitCount == 0 -> 5 // No habits = low consistency
            else -> (habitCompletionRate * 30).toInt()
        }

        // Recent activity score (0-30 points)
        val activityScore = (recentActivityScore * 0.3).toInt()

        return (goalEngagementScore + habitScore + activityScore).coerceIn(0, 100)
    }

    private fun determineTrend(area: LifeArea, currentScore: Int): BalanceTrend {
        val previousAssessment = manualAssessments.find { it.area == area }
        return if (previousAssessment != null) {
            val previousScore = previousAssessment.score * 10 // Convert 1-10 to 0-100 scale
            when {
                currentScore > previousScore + 10 -> BalanceTrend.IMPROVING
                currentScore < previousScore - 10 -> BalanceTrend.DECLINING
                else -> BalanceTrend.STABLE
            }
        } else {
            BalanceTrend.STABLE
        }
    }

    private fun calculateBalanceRating(areaScores: List<LifeAreaScore>): BalanceRating {
        val overallScore = areaScores.map { it.score }.average()
        val variance = calculateVariance(areaScores.map { it.score })
        val minScore = areaScores.minOfOrNull { it.score } ?: 0

        return when {
            overallScore >= 70 && variance < 200 && minScore >= 50 -> BalanceRating.EXCELLENT
            overallScore >= 55 && variance < 400 && minScore >= 35 -> BalanceRating.GOOD
            overallScore >= 40 && minScore >= 20 -> BalanceRating.MODERATE
            minScore >= 10 -> BalanceRating.NEEDS_ATTENTION
            else -> BalanceRating.CRITICAL
        }
    }

    private fun calculateVariance(scores: List<Int>): Double {
        if (scores.isEmpty()) return 0.0
        val mean = scores.average()
        return scores.map { (it - mean) * (it - mean) }.average()
    }

    private suspend fun generateAIAnalysis(
        areaScores: List<LifeAreaScore>,
        rating: BalanceRating
    ): Pair<List<BalanceInsight>, List<BalanceRecommendation>> {
        val prompt = buildAIPrompt(areaScores, rating)

        try {
            val response = httpClient.post("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent") {
                contentType(ContentType.Application.Json)
                setBody("""
                    {
                        "contents": [{"parts": [{"text": "$prompt"}]}],
                        "generationConfig": {
                            "temperature": 0.7,
                            "maxOutputTokens": 1024
                        }
                    }
                """.trimIndent())
            }

            val responseText = response.body<String>()
            return parseAIResponse(responseText, areaScores)
        } catch (e: Exception) {
            return generateRuleBasedInsights(areaScores, rating)
        }
    }

    private fun buildAIPrompt(areaScores: List<LifeAreaScore>, rating: BalanceRating): String {
        val scoresText = areaScores.joinToString("\n") { score ->
            "${score.area.displayName}: ${score.score}/100 (${score.goalCount} goals, ${score.habitCount} habits, trend: ${score.trend})"
        }

        return """
            Analyze this life balance assessment and provide insights and recommendations.

            Overall Rating: ${rating.displayName}

            Area Scores:
            $scoresText

            Provide your response in this exact JSON format:
            {
                "insights": [
                    {"title": "...", "description": "...", "areas": ["CAREER", "FINANCIAL"], "priority": "HIGH"}
                ],
                "recommendations": [
                    {"title": "...", "description": "...", "area": "CAREER", "action": "CREATE_GOAL", "suggestedGoal": "..."}
                ]
            }

            Generate 2-3 actionable insights and 3-4 specific recommendations.
            Focus on the weakest areas and how to improve balance.
            Keep descriptions concise (1-2 sentences each).
        """.trimIndent().replace("\"", "\\\"").replace("\n", "\\n")
    }

    private fun parseAIResponse(
        response: String,
        areaScores: List<LifeAreaScore>
    ): Pair<List<BalanceInsight>, List<BalanceRecommendation>> {
        // Simplified parsing - in production, use proper JSON parsing
        // For now, return rule-based insights
        return generateRuleBasedInsights(
            areaScores,
            calculateBalanceRating(areaScores)
        )
    }

    private fun generateRuleBasedInsights(
        areaScores: List<LifeAreaScore>,
        rating: BalanceRating
    ): Pair<List<BalanceInsight>, List<BalanceRecommendation>> {
        val insights = mutableListOf<BalanceInsight>()
        val recommendations = mutableListOf<BalanceRecommendation>()

        val weakAreas = areaScores.filter { it.score < 40 }.sortedBy { it.score }
        val strongAreas = areaScores.filter { it.score >= 70 }.sortedByDescending { it.score }
        val decliningAreas = areaScores.filter { it.trend == BalanceTrend.DECLINING }

        // Insight: Overall balance
        insights.add(
            BalanceInsight(
                title = when (rating) {
                    BalanceRating.EXCELLENT -> "Well-Balanced Life"
                    BalanceRating.GOOD -> "Mostly Balanced"
                    BalanceRating.MODERATE -> "Room for Improvement"
                    BalanceRating.NEEDS_ATTENTION -> "Imbalance Detected"
                    BalanceRating.CRITICAL -> "Urgent Attention Needed"
                },
                description = rating.description,
                relatedAreas = emptyList(),
                priority = when (rating) {
                    BalanceRating.CRITICAL, BalanceRating.NEEDS_ATTENTION -> InsightPriority.HIGH
                    BalanceRating.MODERATE -> InsightPriority.MEDIUM
                    else -> InsightPriority.LOW
                }
            )
        )

        // Insight: Weak areas
        if (weakAreas.isNotEmpty()) {
            insights.add(
                BalanceInsight(
                    title = "Areas Needing Focus",
                    description = "Your ${weakAreas.take(2).joinToString(" and ") { it.area.displayName }} ${if (weakAreas.size > 1) "areas need" else "area needs"} more attention to achieve better balance.",
                    relatedAreas = weakAreas.take(2).map { it.area },
                    priority = InsightPriority.HIGH
                )
            )
        }

        // Insight: Declining trends
        if (decliningAreas.isNotEmpty()) {
            insights.add(
                BalanceInsight(
                    title = "Declining Trends",
                    description = "Watch out! ${decliningAreas.joinToString(", ") { it.area.displayName }} ${if (decliningAreas.size > 1) "are" else "is"} showing a downward trend.",
                    relatedAreas = decliningAreas.map { it.area },
                    priority = InsightPriority.MEDIUM
                )
            )
        }

        // Recommendations for weak areas
        weakAreas.take(3).forEach { areaScore ->
            if (areaScore.goalCount == 0) {
                recommendations.add(
                    BalanceRecommendation(
                        title = "Start a ${areaScore.area.displayName} Goal",
                        description = "You have no goals in this area. Setting a goal will help improve your balance.",
                        targetArea = areaScore.area,
                        actionType = BalanceRecommendationAction.CREATE_GOAL,
                        suggestedGoal = getSuggestedGoal(areaScore.area)
                    )
                )
            } else if (areaScore.habitCount == 0) {
                recommendations.add(
                    BalanceRecommendation(
                        title = "Build a ${areaScore.area.displayName} Habit",
                        description = "Daily habits in this area will help you make consistent progress.",
                        targetArea = areaScore.area,
                        actionType = BalanceRecommendationAction.CREATE_HABIT,
                        suggestedHabit = getSuggestedHabit(areaScore.area)
                    )
                )
            } else {
                recommendations.add(
                    BalanceRecommendation(
                        title = "Increase ${areaScore.area.displayName} Focus",
                        description = "Dedicate more time and energy to your existing goals in this area.",
                        targetArea = areaScore.area,
                        actionType = BalanceRecommendationAction.INCREASE_FOCUS
                    )
                )
            }
        }

        // Recommendation for overworked areas (if any area is > 85 and others are < 40)
        strongAreas.filter { it.score > 85 }.forEach { strongArea ->
            if (weakAreas.any { it.score < 30 }) {
                recommendations.add(
                    BalanceRecommendation(
                        title = "Redistribute ${strongArea.area.displayName} Energy",
                        description = "Consider shifting some focus from this high-performing area to neglected ones.",
                        targetArea = strongArea.area,
                        actionType = BalanceRecommendationAction.REDUCE_FOCUS
                    )
                )
            }
        }

        return Pair(insights.take(3), recommendations.take(4))
    }

    private fun getSuggestedGoal(area: LifeArea): String {
        return when (area) {
            LifeArea.CAREER -> "Complete a professional certification"
            LifeArea.FINANCIAL -> "Build a 3-month emergency fund"
            LifeArea.PHYSICAL -> "Exercise 3 times per week for a month"
            LifeArea.SOCIAL -> "Reconnect with 5 old friends"
            LifeArea.EMOTIONAL -> "Practice daily mindfulness for 30 days"
            LifeArea.SPIRITUAL -> "Establish a daily meditation practice"
            LifeArea.FAMILY -> "Plan monthly family activities"
            LifeArea.PERSONAL_GROWTH -> "Read 12 books this year"
        }
    }

    private fun getSuggestedHabit(area: LifeArea): String {
        return when (area) {
            LifeArea.CAREER -> "Dedicate 30 minutes daily to skill development"
            LifeArea.FINANCIAL -> "Track daily expenses"
            LifeArea.PHYSICAL -> "10-minute morning stretch"
            LifeArea.SOCIAL -> "Reach out to one friend daily"
            LifeArea.EMOTIONAL -> "5-minute gratitude journaling"
            LifeArea.SPIRITUAL -> "10-minute morning meditation"
            LifeArea.FAMILY -> "Quality time with family during dinner"
            LifeArea.PERSONAL_GROWTH -> "Read for 20 minutes before bed"
        }
    }
}
