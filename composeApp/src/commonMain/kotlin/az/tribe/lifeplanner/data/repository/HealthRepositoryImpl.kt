package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.data.health.HealthDataManager
import az.tribe.lifeplanner.data.mapper.createHealthMetric
import az.tribe.lifeplanner.data.mapper.toDomain
import az.tribe.lifeplanner.data.mapper.toDomainHealthMetrics
import az.tribe.lifeplanner.domain.enum.HealthMetricType
import az.tribe.lifeplanner.domain.model.HealthMetric
import az.tribe.lifeplanner.domain.model.HealthMetricSource
import az.tribe.lifeplanner.domain.repository.HealthRepository
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import az.tribe.lifeplanner.data.analytics.Analytics
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

class HealthRepositoryImpl(
    private val database: SharedDatabase,
    private val syncManager: SyncManager,
    private val healthDataManager: HealthDataManager
) : HealthRepository {

    override fun observeMetricsByType(type: HealthMetricType): Flow<List<HealthMetric>> {
        return database.observeHealthMetricsByType(type.name)
            .map { entities -> entities.toDomainHealthMetrics() }
    }

    override suspend fun getMetricsInRange(
        type: HealthMetricType,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<HealthMetric> {
        return database.getHealthMetricsByTypeAndDateRange(
            type.name,
            startDate.toString(),
            endDate.toString()
        ).toDomainHealthMetrics()
    }

    override suspend fun getLatestMetric(type: HealthMetricType): HealthMetric? {
        return database.getLatestHealthMetric(type.name)?.toDomain()
    }

    override suspend fun insertMetric(metric: HealthMetric) {
        database.insertHealthMetric(
            id = metric.id,
            metricType = metric.metricType.name,
            value = metric.value,
            unit = metric.unit,
            date = metric.date.toString(),
            source = metric.source.name,
            recordedAt = metric.recordedAt.toString(),
            createdAt = metric.createdAt.toString()
        )
        syncManager.requestSync()
    }

    override suspend fun insertMetrics(metrics: List<HealthMetric>) {
        metrics.forEach { metric ->
            database.insertHealthMetric(
                id = metric.id,
                metricType = metric.metricType.name,
                value = metric.value,
                unit = metric.unit,
                date = metric.date.toString(),
                source = metric.source.name,
                recordedAt = metric.recordedAt.toString(),
                createdAt = metric.createdAt.toString()
            )
        }
        if (metrics.isNotEmpty()) {
            syncManager.requestSync()
        }
    }

    override suspend fun syncFromPlatform() {
        try {
            if (!healthDataManager.isAvailable() || !healthDataManager.hasPermissions()) {
                Logger.d("HealthRepository") { "Health data not available or no permissions" }
                return
            }

            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val thirtyDaysAgo = today.minus(DatePeriod(days = 30))

            // Sync steps — aggregate by date since readSteps returns individual records
            val stepsData = healthDataManager.readStepsForDateRange(thirtyDaysAgo, today)
            val stepsMetrics = stepsData
                .groupBy { it.date }
                .map { (date, points) ->
                    createHealthMetric(
                        metricType = HealthMetricType.STEPS,
                        value = points.sumOf { it.value },
                        date = date,
                        source = HealthMetricSource.PLATFORM
                    )
                }
            insertMetrics(stepsMetrics)

            // Sync weight — try 30 days first, fallback to 90 then 365 if empty
            val weightData = healthDataManager.readRecentWeight(days = 30).ifEmpty {
                healthDataManager.readRecentWeight(days = 90)
            }.ifEmpty {
                healthDataManager.readRecentWeight(days = 365)
            }
            val weightMetrics = weightData
                .groupBy { it.date }
                .map { (date, points) ->
                    createHealthMetric(
                        metricType = HealthMetricType.WEIGHT,
                        value = points.last().value,
                        date = date,
                        source = HealthMetricSource.PLATFORM
                    )
                }
            insertMetrics(weightMetrics)

            // Sync heart rate — average per day
            val heartRateData = healthDataManager.readHeartRate(days = 30)
            val heartRateMetrics = heartRateData
                .groupBy { it.date }
                .map { (date, points) ->
                    createHealthMetric(
                        metricType = HealthMetricType.HEART_RATE,
                        value = points.map { it.value }.average(),
                        date = date,
                        source = HealthMetricSource.PLATFORM
                    )
                }
            insertMetrics(heartRateMetrics)

            // Sync sleep — total hours per night
            val sleepData = healthDataManager.readSleep(days = 30)
            val sleepMetrics = sleepData
                .groupBy { it.date }
                .map { (date, points) ->
                    createHealthMetric(
                        metricType = HealthMetricType.SLEEP,
                        value = points.sumOf { it.value },
                        date = date,
                        source = HealthMetricSource.PLATFORM
                    )
                }
            insertMetrics(sleepMetrics)

            Logger.i("HealthRepository") {
                "Synced ${stepsMetrics.size} steps, ${weightMetrics.size} weight, " +
                        "${heartRateMetrics.size} heart rate, ${sleepMetrics.size} sleep records"
            }
            Analytics.healthSyncCompleted(
                steps = stepsMetrics.size,
                weight = weightMetrics.size,
                heartRate = heartRateMetrics.size,
                sleep = sleepMetrics.size
            )
        } catch (e: Exception) {
            Logger.e("HealthRepository") { "Failed to sync health data: ${e.message}" }
        }
    }

    override suspend fun getTodaySteps(): Long? {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val metric = database.getHealthMetricsByTypeAndDateRange(
            HealthMetricType.STEPS.name,
            today.toString(),
            today.toString()
        ).firstOrNull()
        return metric?.value_?.toLong()
    }

    override suspend fun getLatestWeight(): Double? {
        return database.getLatestHealthMetric(HealthMetricType.WEIGHT.name)?.value_
    }
}
