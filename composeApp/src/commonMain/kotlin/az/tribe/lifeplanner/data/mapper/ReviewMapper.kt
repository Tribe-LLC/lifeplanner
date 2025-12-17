package az.tribe.lifeplanner.data.mapper

import az.tribe.lifeplanner.database.ReviewReportEntity
import az.tribe.lifeplanner.domain.model.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val json = Json { ignoreUnknownKeys = true }

/**
 * ReviewReportEntity to Domain mapper
 */
fun ReviewReportEntity.toDomain(): ReviewReport = ReviewReport(
    id = id,
    type = ReviewType.valueOf(type),
    periodStart = LocalDate.parse(periodStart),
    periodEnd = LocalDate.parse(periodEnd),
    generatedAt = LocalDateTime.parse(generatedAt),
    summary = summary,
    highlights = json.decodeFromString(highlightsJson),
    insights = json.decodeFromString(insightsJson),
    recommendations = json.decodeFromString(recommendationsJson),
    stats = json.decodeFromString(statsJson),
    feedback = if (feedbackRating != null) {
        ReviewFeedback(
            rating = FeedbackRating.valueOf(feedbackRating),
            comment = feedbackComment,
            submittedAt = feedbackAt?.let { LocalDateTime.parse(it) }
                ?: LocalDateTime.parse(generatedAt)
        )
    } else null,
    isRead = isRead != 0L
)

/**
 * List mapper
 */
fun List<ReviewReportEntity>.toDomainReviews(): List<ReviewReport> = map { it.toDomain() }

/**
 * Serialize highlights to JSON
 */
fun List<ReviewHighlight>.toJson(): String = json.encodeToString(
    kotlinx.serialization.builtins.ListSerializer(ReviewHighlight.serializer()),
    this
)

/**
 * Serialize insights to JSON
 */
fun List<ReviewInsight>.toInsightsJson(): String = json.encodeToString(
    kotlinx.serialization.builtins.ListSerializer(ReviewInsight.serializer()),
    this
)

/**
 * Serialize recommendations to JSON
 */
fun List<ReviewRecommendation>.toRecommendationsJson(): String = json.encodeToString(
    kotlinx.serialization.builtins.ListSerializer(ReviewRecommendation.serializer()),
    this
)

/**
 * Serialize stats to JSON
 */
fun ReviewStats.toJson(): String = json.encodeToString(ReviewStats.serializer(), this)

/**
 * Create a new review ID
 */
@OptIn(ExperimentalUuidApi::class)
fun generateReviewId(): String = Uuid.random().toString()

/**
 * Create highlight ID
 */
@OptIn(ExperimentalUuidApi::class)
fun generateHighlightId(): String = "highlight_${Uuid.random()}"

/**
 * Create insight ID
 */
@OptIn(ExperimentalUuidApi::class)
fun generateInsightId(): String = "insight_${Uuid.random()}"

/**
 * Create recommendation ID
 */
@OptIn(ExperimentalUuidApi::class)
fun generateRecommendationId(): String = "rec_${Uuid.random()}"

/**
 * Convert ReviewReport to summary card
 */
fun ReviewReport.toSummaryCard(): ReviewSummaryCard {
    val periodLabel = when (type) {
        ReviewType.WEEKLY -> "Week of ${formatDateShort(periodStart)}"
        ReviewType.MONTHLY -> "${getMonthName(periodStart.monthNumber)} ${periodStart.year}"
        ReviewType.QUARTERLY -> "Q${getQuarter(periodStart.monthNumber)} ${periodStart.year}"
        ReviewType.YEARLY -> "${periodStart.year} Year in Review"
    }

    return ReviewSummaryCard(
        reviewId = id,
        type = type,
        periodLabel = periodLabel,
        headline = summary.take(100) + if (summary.length > 100) "..." else "",
        quickStats = listOf(
            QuickStat("Goals", "${stats.goalsCompleted} completed", stats.comparisonToPrevious?.let {
                when {
                    it.goalsCompletedDiff > 0 -> TrendDirection.UP
                    it.goalsCompletedDiff < 0 -> TrendDirection.DOWN
                    else -> TrendDirection.STABLE
                }
            }),
            QuickStat("Habits", "${(stats.habitCompletionRate * 100).toInt()}%", stats.comparisonToPrevious?.let {
                when {
                    it.habitCompletionRateDiff > 0 -> TrendDirection.UP
                    it.habitCompletionRateDiff < 0 -> TrendDirection.DOWN
                    else -> TrendDirection.STABLE
                }
            }),
            QuickStat("XP", "+${stats.xpEarned}", stats.comparisonToPrevious?.let {
                when {
                    it.xpEarnedDiff > 0 -> TrendDirection.UP
                    it.xpEarnedDiff < 0 -> TrendDirection.DOWN
                    else -> TrendDirection.STABLE
                }
            })
        ),
        isNew = !isRead
    )
}

private fun formatDateShort(date: LocalDate): String {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return "${months[date.monthNumber - 1]} ${date.dayOfMonth}"
}

private fun getMonthName(month: Int): String {
    val months = listOf("January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December")
    return months[month - 1]
}

private fun getQuarter(month: Int): Int = (month - 1) / 3 + 1
