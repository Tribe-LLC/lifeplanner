package az.tribe.lifeplanner.widget

import kotlinx.serialization.Serializable

@Serializable
data class WidgetDashboardData(
    val currentStreak: Int = 0,
    val totalXp: Int = 0,
    val currentLevel: Int = 1,
    val activeGoals: Int = 0,
    val habitsTotal: Int = 0,
    val habitsDoneToday: Int = 0,
    val lastUpdated: String = ""
)

@Serializable
data class WidgetHabitData(
    val id: String,
    val title: String,
    val isCompletedToday: Boolean = false,
    val currentStreak: Int = 0,
    val category: String = ""
)
