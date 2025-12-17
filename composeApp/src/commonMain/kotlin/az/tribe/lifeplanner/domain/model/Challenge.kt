package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.domain.enum.ChallengeType
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/**
 * Represents an active or completed challenge
 */
@Serializable
data class Challenge(
    val id: String,
    val type: ChallengeType,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val currentProgress: Int = 0,
    val targetProgress: Int,
    val isCompleted: Boolean = false,
    val completedAt: LocalDateTime? = null,
    val xpEarned: Int = 0
) {
    val progressPercentage: Float
        get() = if (targetProgress > 0) (currentProgress.toFloat() / targetProgress).coerceIn(0f, 1f) else 0f

    val isExpired: Boolean
        get() {
            val today = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            return today > endDate && !isCompleted
        }

    val daysRemaining: Int
        get() {
            val today = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date
            return (endDate.toEpochDays() - today.toEpochDays()).toInt().coerceAtLeast(0)
        }
}

/**
 * Challenge target values based on type
 */
object ChallengeTargets {
    fun getTargetForType(type: ChallengeType): Int = when (type) {
        ChallengeType.DAILY_CHECK_IN -> 1
        ChallengeType.DAILY_JOURNAL -> 1
        ChallengeType.DAILY_HABIT -> 1
        ChallengeType.WEEKLY_GOALS -> 3
        ChallengeType.WEEKLY_HABITS -> 5
        ChallengeType.WEEKLY_JOURNAL -> 3
        ChallengeType.WEEKLY_MILESTONE -> 2
        ChallengeType.MONTHLY_COMPLETION -> 1
        ChallengeType.MONTHLY_STREAK -> 30
        ChallengeType.MONTHLY_BALANCED -> 8
        ChallengeType.PERFECT_DAY -> 1
        ChallengeType.CATEGORY_FOCUS -> 3
        ChallengeType.EARLY_RISER -> 7
    }
}
