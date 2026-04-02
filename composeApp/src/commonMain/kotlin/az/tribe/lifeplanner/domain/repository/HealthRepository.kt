package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.enum.HealthMetricType
import az.tribe.lifeplanner.domain.model.HealthMetric
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface HealthRepository {
    fun observeMetricsByType(type: HealthMetricType): Flow<List<HealthMetric>>

    suspend fun getMetricsInRange(
        type: HealthMetricType,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<HealthMetric>

    suspend fun getLatestMetric(type: HealthMetricType): HealthMetric?

    suspend fun insertMetric(metric: HealthMetric)

    suspend fun insertMetrics(metrics: List<HealthMetric>)

    suspend fun syncFromPlatform()

    suspend fun getTodaySteps(): Long?

    suspend fun getLatestWeight(): Double?
}
