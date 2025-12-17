package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.FeedbackRating
import az.tribe.lifeplanner.domain.model.ReviewReport
import az.tribe.lifeplanner.domain.model.ReviewSummaryCard
import az.tribe.lifeplanner.domain.model.ReviewType
import kotlinx.datetime.LocalDate

/**
 * Repository interface for AI-generated reviews
 */
interface ReviewRepository {
    /**
     * Get all reviews
     */
    suspend fun getAllReviews(): List<ReviewReport>

    /**
     * Get a specific review by ID
     */
    suspend fun getReviewById(id: String): ReviewReport?

    /**
     * Get reviews by type (weekly, monthly, etc.)
     */
    suspend fun getReviewsByType(type: ReviewType): List<ReviewReport>

    /**
     * Get the latest review of a specific type
     */
    suspend fun getLatestReview(type: ReviewType): ReviewReport?

    /**
     * Get all unread reviews
     */
    suspend fun getUnreadReviews(): List<ReviewReport>

    /**
     * Generate a new review for the specified period
     */
    suspend fun generateReview(
        type: ReviewType,
        periodStart: LocalDate,
        periodEnd: LocalDate
    ): ReviewReport

    /**
     * Mark a review as read
     */
    suspend fun markAsRead(reviewId: String)

    /**
     * Submit feedback for a review
     */
    suspend fun submitFeedback(reviewId: String, rating: FeedbackRating, comment: String? = null)

    /**
     * Delete a review
     */
    suspend fun deleteReview(reviewId: String)

    /**
     * Get summary cards for home screen display
     */
    suspend fun getReviewSummaryCards(): List<ReviewSummaryCard>

    /**
     * Check if a review should be generated (based on last review date)
     */
    suspend fun shouldGenerateReview(type: ReviewType): Boolean

    /**
     * Get unread review count
     */
    suspend fun getUnreadCount(): Long

    /**
     * Clean up old reviews (keep only last N of each type)
     */
    suspend fun cleanupOldReviews(keepCount: Int = 12)
}
