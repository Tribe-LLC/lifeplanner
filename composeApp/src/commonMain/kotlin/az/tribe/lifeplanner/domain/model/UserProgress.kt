package az.tribe.lifeplanner.domain.model

import kotlinx.datetime.LocalDate

data class UserProgress(
    val currentStreak: Int,
    val lastCheckInDate: LocalDate?
)
