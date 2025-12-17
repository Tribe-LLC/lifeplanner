package az.tribe.lifeplanner.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.domain.model.FeedbackRating
import az.tribe.lifeplanner.domain.model.ReviewReport
import az.tribe.lifeplanner.domain.model.ReviewSummaryCard
import az.tribe.lifeplanner.domain.model.ReviewType
import az.tribe.lifeplanner.domain.repository.ReviewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

data class ReviewUiState(
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val reviews: List<ReviewReport> = emptyList(),
    val summaryCards: List<ReviewSummaryCard> = emptyList(),
    val selectedReview: ReviewReport? = null,
    val unreadCount: Long = 0,
    val error: String? = null,
    val showReviewDetail: Boolean = false,
    val showFeedbackDialog: Boolean = false,
    val pendingReviewTypes: List<ReviewType> = emptyList()
)

class ReviewViewModel(
    private val reviewRepository: ReviewRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    init {
        loadReviews()
        checkPendingReviews()
    }

    fun loadReviews() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val reviews = reviewRepository.getAllReviews()
                val summaryCards = reviewRepository.getReviewSummaryCards()
                val unreadCount = reviewRepository.getUnreadCount()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    reviews = reviews,
                    summaryCards = summaryCards,
                    unreadCount = unreadCount
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    private fun checkPendingReviews() {
        viewModelScope.launch {
            try {
                val pendingTypes = ReviewType.entries.filter { type ->
                    reviewRepository.shouldGenerateReview(type)
                }
                _uiState.value = _uiState.value.copy(pendingReviewTypes = pendingTypes)
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }

    fun selectReview(review: ReviewReport) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedReview = review,
                showReviewDetail = true
            )

            // Mark as read if not already
            if (!review.isRead) {
                try {
                    reviewRepository.markAsRead(review.id)
                    // Update local state
                    val updatedReviews = _uiState.value.reviews.map {
                        if (it.id == review.id) it.copy(isRead = true) else it
                    }
                    val unreadCount = reviewRepository.getUnreadCount()
                    _uiState.value = _uiState.value.copy(
                        reviews = updatedReviews,
                        selectedReview = review.copy(isRead = true),
                        unreadCount = unreadCount
                    )
                } catch (e: Exception) {
                    // Silently fail
                }
            }
        }
    }

    fun selectReviewById(reviewId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val review = reviewRepository.getReviewById(reviewId)
                if (review != null) {
                    selectReview(review)
                }
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun generateReview(type: ReviewType) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, error = null)

            try {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                val (start, end) = when (type) {
                    ReviewType.WEEKLY -> {
                        val start = now.minus(7, DateTimeUnit.DAY)
                        start to now
                    }
                    ReviewType.MONTHLY -> {
                        val start = now.minus(1, DateTimeUnit.MONTH)
                        start to now
                    }
                    ReviewType.QUARTERLY -> {
                        val start = now.minus(3, DateTimeUnit.MONTH)
                        start to now
                    }
                    ReviewType.YEARLY -> {
                        val start = now.minus(1, DateTimeUnit.YEAR)
                        start to now
                    }
                }

                val review = reviewRepository.generateReview(type, start, end)

                // Reload reviews and select the new one
                loadReviews()
                selectReview(review)

                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    pendingReviewTypes = _uiState.value.pendingReviewTypes - type
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    error = e.message ?: "Failed to generate review"
                )
            }
        }
    }

    fun showFeedbackDialog() {
        _uiState.value = _uiState.value.copy(showFeedbackDialog = true)
    }

    fun hideFeedbackDialog() {
        _uiState.value = _uiState.value.copy(showFeedbackDialog = false)
    }

    fun submitFeedback(rating: FeedbackRating, comment: String?) {
        val review = _uiState.value.selectedReview ?: return

        viewModelScope.launch {
            try {
                reviewRepository.submitFeedback(review.id, rating, comment)

                // Update local state
                val updatedReview = review.copy(
                    feedback = az.tribe.lifeplanner.domain.model.ReviewFeedback(
                        rating = rating,
                        comment = comment,
                        submittedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    )
                )
                _uiState.value = _uiState.value.copy(
                    selectedReview = updatedReview,
                    showFeedbackDialog = false
                )

                loadReviews()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    showFeedbackDialog = false
                )
            }
        }
    }

    fun deleteReview(review: ReviewReport) {
        viewModelScope.launch {
            try {
                reviewRepository.deleteReview(review.id)

                if (_uiState.value.selectedReview?.id == review.id) {
                    _uiState.value = _uiState.value.copy(
                        selectedReview = null,
                        showReviewDetail = false
                    )
                }

                loadReviews()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun navigateBack() {
        _uiState.value = _uiState.value.copy(
            showReviewDetail = false,
            selectedReview = null
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun getReviewsByType(type: ReviewType): List<ReviewReport> {
        return _uiState.value.reviews.filter { it.type == type }
    }

    fun cleanupOldReviews() {
        viewModelScope.launch {
            try {
                reviewRepository.cleanupOldReviews(12)
                loadReviews()
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }
}
