package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.domain.enum.GoalCategory
import kotlinx.datetime.LocalDate

data class DailyQuest(
    val id: String,
    val title: String,
    val description: String,
    val category: GoalCategory?,
    val experienceReward: Int,
    val isCompleted: Boolean,
    val dateAssigned: LocalDate
)