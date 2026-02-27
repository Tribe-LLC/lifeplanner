package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.domain.enum.GoalCategory
import kotlinx.datetime.LocalDateTime

/**
 * Represents different areas of life for balance tracking.
 * Maps to GoalCategory for automatic score calculation.
 */
enum class LifeArea(val displayName: String, val description: String, val icon: String) {
    CAREER("Career", "Professional growth, skills, and work achievements", "💼"),
    FINANCIAL("Financial", "Money management, savings, and financial security", "💰"),
    PHYSICAL("Physical", "Health, fitness, and physical wellbeing", "💪"),
    SOCIAL("Social", "Friendships, networking, and social connections", "👥"),
    EMOTIONAL("Emotional", "Mental health, self-awareness, and emotional intelligence", "🧠"),
    SPIRITUAL("Spiritual", "Purpose, mindfulness, and inner peace", "🧘"),
    FAMILY("Family", "Family relationships and home life", "👨‍👩‍👧‍👦"),
    PERSONAL_GROWTH("Personal Growth", "Learning, hobbies, and self-improvement", "📚")
}

/**
 * Score for a single life area (0-100)
 */
data class LifeAreaScore(
    val area: LifeArea,
    val score: Int, // 0-100
    val goalCount: Int,
    val completedGoals: Int,
    val activeGoals: Int,
    val habitCount: Int,
    val habitCompletionRate: Float, // 0.0-1.0
    val recentActivityScore: Int, // Based on recent check-ins and updates
    val trend: BalanceTrend
)

/**
 * Trend direction for a life area
 */
enum class BalanceTrend {
    IMPROVING,
    STABLE,
    DECLINING
}

/**
 * Overall life balance report
 */
data class LifeBalanceReport(
    val id: String,
    val overallScore: Int, // Average of all areas (0-100)
    val areaScores: List<LifeAreaScore>,
    val strongestAreas: List<LifeArea>,
    val weakestAreas: List<LifeArea>,
    val balanceRating: BalanceRating,
    val aiInsights: List<BalanceInsight>,
    val recommendations: List<BalanceRecommendation>,
    val generatedAt: LocalDateTime
)

/**
 * Overall balance rating
 */
enum class BalanceRating(val displayName: String, val description: String) {
    EXCELLENT("Excellent Balance", "Your life areas are well-balanced. Keep up the great work!"),
    GOOD("Good Balance", "Most areas are healthy with minor imbalances to address."),
    MODERATE("Moderate Balance", "Some areas need attention. Consider redistributing focus."),
    NEEDS_ATTENTION("Needs Attention", "Significant imbalances detected. Time to reassess priorities."),
    CRITICAL("Critical Imbalance", "Major areas are being neglected. Immediate action recommended.")
}

/**
 * AI-generated insight about balance
 */
data class BalanceInsight(
    val title: String,
    val description: String,
    val relatedAreas: List<LifeArea>,
    val priority: InsightPriority
)

enum class InsightPriority {
    HIGH, MEDIUM, LOW
}

/**
 * AI-generated recommendation for improving balance
 */
data class BalanceRecommendation(
    val title: String,
    val description: String,
    val targetArea: LifeArea,
    val actionType: BalanceRecommendationAction,
    val suggestedGoal: String? = null,
    val suggestedHabit: String? = null,
    val preGeneratedGoal: Goal? = null
)

enum class BalanceRecommendationAction {
    CREATE_GOAL,
    CREATE_HABIT,
    INCREASE_FOCUS,
    REDUCE_FOCUS,
    MAINTAIN_CURRENT
}

/**
 * User's manual assessment for an area (optional override)
 */
data class ManualAssessment(
    val area: LifeArea,
    val score: Int, // 1-10 from user
    val notes: String? = null,
    val assessedAt: LocalDateTime
)

/**
 * Maps LifeArea to GoalCategory. PERSONAL_GROWTH maps to CAREER as fallback.
 */
fun LifeArea.toGoalCategory(): GoalCategory {
    return when (this) {
        LifeArea.CAREER -> GoalCategory.CAREER
        LifeArea.FINANCIAL -> GoalCategory.FINANCIAL
        LifeArea.PHYSICAL -> GoalCategory.PHYSICAL
        LifeArea.SOCIAL -> GoalCategory.SOCIAL
        LifeArea.EMOTIONAL -> GoalCategory.EMOTIONAL
        LifeArea.SPIRITUAL -> GoalCategory.SPIRITUAL
        LifeArea.FAMILY -> GoalCategory.FAMILY
        LifeArea.PERSONAL_GROWTH -> GoalCategory.CAREER
    }
}
