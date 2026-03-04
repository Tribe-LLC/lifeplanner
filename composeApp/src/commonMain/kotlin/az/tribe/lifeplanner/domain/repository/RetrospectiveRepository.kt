package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.DaySnapshot
import kotlinx.datetime.LocalDate

interface RetrospectiveRepository {
    suspend fun getDaySnapshot(date: LocalDate): DaySnapshot
    suspend fun getDatesWithActivity(start: LocalDate, end: LocalDate): Set<LocalDate>
}
