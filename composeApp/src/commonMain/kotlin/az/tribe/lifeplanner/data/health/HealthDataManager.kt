@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package az.tribe.lifeplanner.data.health

import kotlinx.datetime.LocalDate

data class HealthDataPoint(
    val value: Double,
    val date: LocalDate,
    val recordedAt: String
)

expect class HealthDataManager() {
    suspend fun isAvailable(): Boolean
    suspend fun hasPermissions(): Boolean
    suspend fun readTodaySteps(): Long?
    suspend fun readStepsForDateRange(start: LocalDate, end: LocalDate): List<HealthDataPoint>
    suspend fun readRecentWeight(days: Int = 30): List<HealthDataPoint>
    suspend fun readHeartRate(days: Int = 30): List<HealthDataPoint>
    suspend fun readSleep(days: Int = 30): List<HealthDataPoint>
}
