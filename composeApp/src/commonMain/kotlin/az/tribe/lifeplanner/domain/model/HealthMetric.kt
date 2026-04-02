package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.domain.enum.HealthMetricType
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

data class HealthMetric(
    val id: String,
    val metricType: HealthMetricType,
    val value: Double,
    val unit: String,
    val date: LocalDate,
    val source: HealthMetricSource,
    val recordedAt: LocalDateTime,
    val createdAt: LocalDateTime
)

enum class HealthMetricSource {
    PLATFORM,
    MANUAL
}
