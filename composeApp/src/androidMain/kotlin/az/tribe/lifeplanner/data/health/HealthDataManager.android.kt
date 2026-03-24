@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package az.tribe.lifeplanner.data.health

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import co.touchlab.kermit.Logger
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import org.koin.mp.KoinPlatform
import java.time.Duration
import java.time.Instant

actual class HealthDataManager {

    private val context: Context by lazy { KoinPlatform.getKoin().get() }

    private fun getClient(): HealthConnectClient? {
        return try {
            if (Build.VERSION.SDK_INT < 28) return null
            val status = HealthConnectClient.getSdkStatus(context)
            Logger.d("HealthDataManager") { "Health Connect SDK status: ${if (status == HealthConnectClient.SDK_AVAILABLE) "AVAILABLE" else "NOT_AVAILABLE($status)"}" }
            if (status == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else null
        } catch (e: Exception) {
            Logger.w("HealthDataManager") { "Health Connect not available: ${e.message}" }
            null
        }
    }

    actual suspend fun isAvailable(): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        return try {
            val status = HealthConnectClient.getSdkStatus(context)
            status == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            Logger.w("HealthDataManager") { "Health Connect availability check failed: ${e.message}" }
            false
        }
    }

    fun getSdkStatusCode(): Int {
        return try {
            if (Build.VERSION.SDK_INT < 28) -1
            else HealthConnectClient.getSdkStatus(context)
        } catch (e: Exception) { -1 }
    }

    actual suspend fun hasPermissions(): Boolean {
        val client = getClient() ?: return false
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            Logger.d("HealthDataManager") { "Granted permissions: $granted" }
            Logger.d("HealthDataManager") { "Required permissions: $REQUIRED_PERMISSIONS" }
            // Return true if ANY health permission is granted (we'll sync whatever we can)
            val hasAny = granted.any { it in REQUIRED_PERMISSIONS }
            Logger.d("HealthDataManager") { "Has any health permission: $hasAny" }
            hasAny
        } catch (e: Exception) {
            Logger.w("HealthDataManager") { "Permission check failed: ${e.message}" }
            false
        }
    }

    actual suspend fun readTodaySteps(): Long? {
        val client = getClient() ?: return null
        return try {
            val now = Instant.now()
            val startOfDay = java.time.LocalDate.now()
                .atStartOfDay()
                .toInstant(java.time.ZoneOffset.systemDefault().rules.getOffset(now))
            val response = client.aggregate(
                androidx.health.connect.client.request.AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                )
            )
            response[StepsRecord.COUNT_TOTAL]
        } catch (e: Exception) {
            Logger.w("HealthDataManager") { "Failed to read steps: ${e.message}" }
            null
        }
    }

    actual suspend fun readStepsForDateRange(start: LocalDate, end: LocalDate): List<HealthDataPoint> {
        val client = getClient() ?: return emptyList()
        return try {
            val tz = TimeZone.currentSystemDefault()
            val startKtx = start.atStartOfDayIn(tz)
            val startInstant = Instant.ofEpochSecond(startKtx.epochSeconds, startKtx.nanosecondsOfSecond.toLong())
            val endDate = end.plus(1, DateTimeUnit.DAY)
            val endKtx = endDate.atStartOfDayIn(tz)
            val endInstant = Instant.ofEpochSecond(endKtx.epochSeconds, endKtx.nanosecondsOfSecond.toLong())

            Logger.d("HealthDataManager") { "Reading steps: start=$startInstant, end=$endInstant" }

            // First try reading raw records to check if ANY step data exists
            val rawResponse = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
                )
            )
            Logger.d("HealthDataManager") { "Raw step records found: ${rawResponse.records.size}" }
            if (rawResponse.records.isNotEmpty()) {
                val first = rawResponse.records.first()
                Logger.d("HealthDataManager") { "First step record: count=${first.count}, start=${first.startTime}, end=${first.endTime}" }
            }

            // Now aggregate by day
            val response = client.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant),
                    timeRangeSlicer = Duration.ofDays(1)
                )
            )
            Logger.d("HealthDataManager") { "Steps aggregated: ${response.size} day buckets" }
            response.mapNotNull { result ->
                val steps = result.result[StepsRecord.COUNT_TOTAL] ?: return@mapNotNull null
                val date = result.startTime.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                HealthDataPoint(
                    value = steps.toDouble(),
                    date = LocalDate(date.year, date.monthValue, date.dayOfMonth),
                    recordedAt = result.startTime.toString()
                )
            }
        } catch (e: Exception) {
            Logger.e("HealthDataManager") { "Failed to read steps range: ${e.message}\n${e.stackTraceToString()}" }
            emptyList()
        }
    }

    actual suspend fun readRecentWeight(days: Int): List<HealthDataPoint> {
        val client = getClient() ?: return emptyList()
        return try {
            val now = Instant.now()
            val startTime = now.minus(Duration.ofDays(days.toLong()))

            Logger.d("HealthDataManager") { "Reading weight for last $days days" }
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, now)
                )
            )
            Logger.d("HealthDataManager") { "Weight records found: ${response.records.size}" }
            response.records.map { record ->
                val date = record.time.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                HealthDataPoint(
                    value = record.weight.inKilograms,
                    date = LocalDate(date.year, date.monthValue, date.dayOfMonth),
                    recordedAt = record.time.toString()
                )
            }
        } catch (e: Exception) {
            Logger.w("HealthDataManager") { "Failed to read weight: ${e.message}" }
            emptyList()
        }
    }

    actual suspend fun readHeartRate(days: Int): List<HealthDataPoint> {
        val client = getClient() ?: return emptyList()
        return try {
            val now = Instant.now()
            val startTime = now.minus(Duration.ofDays(days.toLong()))

            Logger.d("HealthDataManager") { "Reading heart rate for last $days days" }
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, now)
                )
            )
            Logger.d("HealthDataManager") { "Heart rate records found: ${response.records.size}" }
            response.records.flatMap { record ->
                record.samples.map { sample ->
                    val date = sample.time.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    HealthDataPoint(
                        value = sample.beatsPerMinute.toDouble(),
                        date = LocalDate(date.year, date.monthValue, date.dayOfMonth),
                        recordedAt = sample.time.toString()
                    )
                }
            }
        } catch (e: Exception) {
            Logger.e("HealthDataManager") { "Failed to read heart rate: ${e.message}" }
            emptyList()
        }
    }

    actual suspend fun readSleep(days: Int): List<HealthDataPoint> {
        val client = getClient() ?: return emptyList()
        return try {
            val now = Instant.now()
            val startTime = now.minus(Duration.ofDays(days.toLong()))

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, now)
                )
            )
            response.records.map { record ->
                val durationHours = Duration.between(record.startTime, record.endTime).toMinutes() / 60.0
                val date = record.startTime.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                HealthDataPoint(
                    value = durationHours,
                    date = LocalDate(date.year, date.monthValue, date.dayOfMonth),
                    recordedAt = record.startTime.toString()
                )
            }
        } catch (e: Exception) {
            Logger.w("HealthDataManager") { "Failed to read sleep: ${e.message}" }
            emptyList()
        }
    }

    companion object {
        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )
    }
}
