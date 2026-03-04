package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.domain.enum.AmbientSound
import az.tribe.lifeplanner.domain.enum.FocusTheme
import az.tribe.lifeplanner.domain.enum.Mood
import kotlinx.datetime.LocalDateTime

data class FocusSession(
    val id: String,
    val goalId: String,
    val milestoneId: String,
    val plannedDurationMinutes: Int,
    val actualDurationSeconds: Int,
    val wasCompleted: Boolean,
    val xpEarned: Int,
    val startedAt: LocalDateTime,
    val completedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val mood: Mood? = null,
    val ambientSound: AmbientSound? = null,
    val focusTheme: FocusTheme? = null
) {
    val actualMinutes: Int
        get() = actualDurationSeconds / 60

    val completionPercentage: Float
        get() = if (plannedDurationMinutes > 0) {
            (actualDurationSeconds.toFloat() / (plannedDurationMinutes * 60)).coerceIn(0f, 1f)
        } else 0f
}
