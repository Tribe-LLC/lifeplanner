package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

data class Goal(
    val id: String,
    val category: GoalCategory,
    val title: String,
    val description: String,
    val status: GoalStatus,
    val timeline: GoalTimeline,
    val dueDate: LocalDate,
    val progress: Long? = 0,
    val milestones: List<Milestone> = emptyList(),
    val notes: String = "",
    val createdAt: LocalDateTime,
    val completionRate: Float = 0f, // For the 60% progress shown in UI
    val isArchived: Boolean = false,
    val aiReasoning: String? = null
)

