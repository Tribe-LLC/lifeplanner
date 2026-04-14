package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.HabitFrequency
import az.tribe.lifeplanner.domain.enum.HabitType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

data class Habit(
    val id: String,
    val title: String,
    val description: String = "",
    val category: GoalCategory,
    val frequency: HabitFrequency,
    val targetCount: Int = 1,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalCompletions: Int = 0,
    val lastCompletedDate: LocalDate? = null,
    val linkedGoalId: String? = null,
    val correlationScore: Float = 0f,
    val isActive: Boolean = true,
    val createdAt: LocalDateTime,
    val reminderTime: String? = null,
    val type: HabitType = HabitType.BUILD
)

data class HabitCheckIn(
    val id: String,
    val habitId: String,
    val date: LocalDate,
    val completed: Boolean,
    val notes: String = ""
)
