package az.tribe.lifeplanner.domain.model

import kotlinx.datetime.LocalDate

data class Milestone(
    val id: String,
    val title: String,
    val isCompleted: Boolean = false,
    val dueDate: LocalDate? = null
)