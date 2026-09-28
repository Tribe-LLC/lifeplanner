package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.HabitCompletionSource
import az.tribe.lifeplanner.domain.enum.HabitFrequency
import az.tribe.lifeplanner.domain.enum.HabitType
import az.tribe.lifeplanner.domain.enum.HealthMetricType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

data class Habit(
    val id: String,
    val title: String,
    val description: String = "",
    val category: GoalCategory,
    val frequency: HabitFrequency,
    val targetCount: Int = 1,
    val unit: String? = null,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalCompletions: Int = 0,
    val lastCompletedDate: LocalDate? = null,
    val linkedGoalId: String? = null,
    val correlationScore: Float = 0f,
    val isActive: Boolean = true,
    val createdAt: LocalDateTime,
    val reminderTime: String? = null,
    val type: HabitType = HabitType.BUILD,
    /** When set, health data auto-completes this habit (STEPS/SLEEP need [healthTarget] reached, WEIGHT/HEART_RATE any reading today). */
    val healthMetricType: HealthMetricType? = null,
    val healthTarget: Double? = null,
    /** Which in-app session credits this habit. [HabitCompletionSource.MANUAL] means only the user's own tap does. */
    val completionSource: HabitCompletionSource = HabitCompletionSource.MANUAL
)

data class HabitCheckIn(
    val id: String,
    val habitId: String,
    val date: LocalDate,
    val completed: Boolean,
    val notes: String = "",
    val count: Int = 0,
    /** When the row was last written, used to learn what time of day a habit gets done. Null when unknown. */
    val checkedAt: kotlin.time.Instant? = null,
)
