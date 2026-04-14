package az.tribe.lifeplanner.usecases.health

import az.tribe.lifeplanner.domain.enum.HealthMetricType
import az.tribe.lifeplanner.domain.model.HealthMetric
import az.tribe.lifeplanner.domain.repository.HealthRepository
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

class GetHealthTrendUseCase(private val repository: HealthRepository) {
    suspend operator fun invoke(type: HealthMetricType, days: Int = 30): List<HealthMetric> {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val start = today.minus(days, DateTimeUnit.DAY)
        return repository.getMetricsInRange(type, start, today)
    }
}
