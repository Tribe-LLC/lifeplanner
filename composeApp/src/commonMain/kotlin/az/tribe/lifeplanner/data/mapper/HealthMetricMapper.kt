package az.tribe.lifeplanner.data.mapper

import az.tribe.lifeplanner.database.HealthMetricEntity
import az.tribe.lifeplanner.domain.enum.HealthMetricType
import az.tribe.lifeplanner.domain.model.HealthMetric
import az.tribe.lifeplanner.domain.model.HealthMetricSource
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun HealthMetricEntity.toDomain(): HealthMetric {
    return HealthMetric(
        id = id,
        metricType = HealthMetricType.valueOf(metricType),
        value = value_,
        unit = unit,
        date = LocalDate.parse(date),
        source = HealthMetricSource.valueOf(source),
        recordedAt = parseLocalDateTime(recordedAt),
        createdAt = parseLocalDateTime(createdAt)
    )
}

fun HealthMetric.toEntity(): HealthMetricEntity {
    return HealthMetricEntity(
        id = id,
        metricType = metricType.name,
        value_ = value,
        unit = unit,
        date = date.toString(),
        source = source.name,
        recordedAt = recordedAt.toString(),
        createdAt = createdAt.toString(),
        sync_updated_at = Clock.System.now().toString(),
        is_deleted = 0L,
        sync_version = 0L,
        last_synced_at = null
    )
}

fun List<HealthMetricEntity>.toDomainHealthMetrics(): List<HealthMetric> {
    return map { it.toDomain() }
}

@OptIn(ExperimentalUuidApi::class)
fun createHealthMetric(
    metricType: HealthMetricType,
    value: Double,
    date: LocalDate,
    source: HealthMetricSource = HealthMetricSource.PLATFORM
): HealthMetric {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return HealthMetric(
        id = "${metricType.name}_${date}",
        metricType = metricType,
        value = value,
        unit = metricType.unit,
        date = date,
        source = source,
        recordedAt = now,
        createdAt = now
    )
}

@OptIn(ExperimentalUuidApi::class)
fun createManualHealthMetric(
    metricType: HealthMetricType,
    value: Double,
    date: LocalDate
): HealthMetric {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return HealthMetric(
        id = "manual_${metricType.name}_${date}_${Uuid.random()}",
        metricType = metricType,
        value = value,
        unit = metricType.unit,
        date = date,
        source = HealthMetricSource.MANUAL,
        recordedAt = now,
        createdAt = now
    )
}
