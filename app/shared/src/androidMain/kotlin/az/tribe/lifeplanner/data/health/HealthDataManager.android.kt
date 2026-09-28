@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package az.tribe.lifeplanner.data.health

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.feature.ExperimentalMindfulnessSessionApi
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.Metadata
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

    actual suspend fun readWorkouts(days: Int): List<HealthWorkout> {
        val client = getClient() ?: return emptyList()
        return try {
            val now = Instant.now()
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(now.minus(Duration.ofDays(days.toLong())), now)
                )
            )
            response.records.map { r ->
                HealthWorkout(
                    id = r.metadata.id,
                    kind = r.exerciseType.toKind(),
                    title = r.title,
                    startEpochMs = r.startTime.toEpochMilli(),
                    endEpochMs = r.endTime.toEpochMilli(),
                    fromThisApp = r.metadata.dataOrigin.packageName == context.packageName,
                )
            }
        } catch (e: Exception) {
            Logger.w("HealthDataManager") { "Failed to read workouts: ${e.message}" }
            emptyList()
        }
    }

    actual suspend fun canWriteWorkouts(): Boolean {
        val client = getClient() ?: return false
        return try {
            WRITE_EXERCISE in client.permissionController.getGrantedPermissions()
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun writeWorkout(kind: WorkoutKind, title: String, startEpochMs: Long, endEpochMs: Long, clientId: String): Boolean {
        val client = getClient() ?: return false
        if (endEpochMs <= startEpochMs) return false
        return try {
            val start = Instant.ofEpochMilli(startEpochMs)
            val end = Instant.ofEpochMilli(endEpochMs)
            val rules = java.time.ZoneId.systemDefault().rules
            client.insertRecords(
                listOf(
                    ExerciseSessionRecord(
                        startTime = start,
                        startZoneOffset = rules.getOffset(start),
                        endTime = end,
                        endZoneOffset = rules.getOffset(end),
                        metadata = Metadata.manualEntry(clientRecordId = clientId),
                        exerciseType = kind.toExerciseType(),
                        title = title,
                    )
                )
            )
            true
        } catch (e: Exception) {
            Logger.w("HealthDataManager") { "Failed to write workout: ${e.message}" }
            false
        }
    }

    actual suspend fun canWriteFoodAndWater(): Boolean {
        val client = getClient() ?: return false
        return try {
            client.permissionController.getGrantedPermissions().let { WRITE_NUTRITION in it && WRITE_HYDRATION in it }
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun requestFoodAndWater(): Boolean = canWriteFoodAndWater()

    actual suspend fun writeMeal(name: String, slot: String, atEpochMs: Long, kcal: Double?, proteinG: Double?, clientId: String): Boolean {
        val client = getClient() ?: return false
        if (kcal == null && proteinG == null) return false
        return try {
            // A meal is a moment; Health Connect wants a span, so it gets a quarter of an hour.
            val start = Instant.ofEpochMilli(atEpochMs)
            val end = start.plusSeconds(15 * 60)
            val rules = java.time.ZoneId.systemDefault().rules
            client.insertRecords(
                listOf(
                    NutritionRecord(
                        startTime = start,
                        startZoneOffset = rules.getOffset(start),
                        endTime = end,
                        endZoneOffset = rules.getOffset(end),
                        metadata = Metadata.manualEntry(clientRecordId = clientId),
                        name = name,
                        mealType = when (slot) {
                            "breakfast" -> MealType.MEAL_TYPE_BREAKFAST
                            "lunch" -> MealType.MEAL_TYPE_LUNCH
                            "dinner" -> MealType.MEAL_TYPE_DINNER
                            "snack" -> MealType.MEAL_TYPE_SNACK
                            else -> MealType.MEAL_TYPE_UNKNOWN
                        },
                        energy = kcal?.let { Energy.kilocalories(it) },
                        protein = proteinG?.let { Mass.grams(it) },
                    )
                )
            )
            true
        } catch (e: Exception) {
            Logger.w("HealthDataManager") { "Failed to write meal: ${e.message}" }
            false
        }
    }

    actual suspend fun writeWater(ml: Double, atEpochMs: Long, clientId: String): Boolean {
        val client = getClient() ?: return false
        if (ml <= 0) return false
        return try {
            val start = Instant.ofEpochMilli(atEpochMs)
            val end = start.plusSeconds(60)
            val rules = java.time.ZoneId.systemDefault().rules
            client.insertRecords(
                listOf(
                    HydrationRecord(
                        startTime = start,
                        startZoneOffset = rules.getOffset(start),
                        endTime = end,
                        endZoneOffset = rules.getOffset(end),
                        metadata = Metadata.manualEntry(clientRecordId = clientId),
                        volume = Volume.milliliters(ml),
                    )
                )
            )
            true
        } catch (e: Exception) {
            Logger.w("HealthDataManager") { "Failed to write water: ${e.message}" }
            false
        }
    }

    @OptIn(ExperimentalMindfulnessSessionApi::class)
    actual suspend fun supportsMindful(): Boolean {
        val client = getClient() ?: return false
        return try {
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    @OptIn(ExperimentalMindfulnessSessionApi::class)
    actual suspend fun canWriteMind(): Boolean {
        val client = getClient() ?: return false
        if (!supportsMindful()) return false
        return try {
            WRITE_MINDFULNESS in client.permissionController.getGrantedPermissions()
        } catch (e: Exception) {
            false
        }
    }

    actual suspend fun requestMind(): Boolean = canWriteMind()

    @OptIn(ExperimentalMindfulnessSessionApi::class)
    actual suspend fun writeMindful(startEpochMs: Long, endEpochMs: Long, clientId: String): Boolean {
        val client = getClient() ?: return false
        if (endEpochMs <= startEpochMs || !supportsMindful()) return false
        return try {
            val start = Instant.ofEpochMilli(startEpochMs)
            val end = Instant.ofEpochMilli(endEpochMs)
            val rules = java.time.ZoneId.systemDefault().rules
            client.insertRecords(
                listOf(
                    MindfulnessSessionRecord(
                        startTime = start,
                        startZoneOffset = rules.getOffset(start),
                        endTime = end,
                        endZoneOffset = rules.getOffset(end),
                        metadata = Metadata.manualEntry(clientRecordId = clientId),
                        mindfulnessSessionType = MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_BREATHING,
                        title = "Breathing",
                    )
                )
            )
            true
        } catch (e: Exception) {
            Logger.w("HealthDataManager") { "Failed to write mindful minutes: ${e.message}" }
            false
        }
    }

    /** Health Connect has no place for moods; they stay in LifePlanner on Android. */
    actual suspend fun writeMood(valence: Double, labels: List<String>, associations: List<String>, atEpochMs: Long, clientId: String): Boolean = false

    companion object {
        @OptIn(ExperimentalMindfulnessSessionApi::class)
        private val WRITE_MINDFULNESS = HealthPermission.getWritePermission(MindfulnessSessionRecord::class)

        /**
         * What the permission screen asks for. Mindful minutes only where Health Connect supports
         * them, since asking for a permission it does not know can fail the whole request.
         */
        @OptIn(ExperimentalMindfulnessSessionApi::class)
        fun permissionsToRequest(context: Context): Set<String> = try {
            val client = HealthConnectClient.getOrCreate(context)
            val mindful = client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
            if (mindful) REQUIRED_PERMISSIONS + WRITE_MINDFULNESS else REQUIRED_PERMISSIONS
        } catch (e: Exception) {
            REQUIRED_PERMISSIONS
        }

        private val WRITE_EXERCISE = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
        private val WRITE_NUTRITION = HealthPermission.getWritePermission(NutritionRecord::class)
        private val WRITE_HYDRATION = HealthPermission.getWritePermission(HydrationRecord::class)

        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            WRITE_EXERCISE,
            WRITE_NUTRITION,
            WRITE_HYDRATION,
        )

        private fun Int.toKind(): WorkoutKind = when (this) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> WorkoutKind.RUN
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING, ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> WorkoutKind.WALK
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> WorkoutKind.BIKE
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
            ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> WorkoutKind.STRENGTH
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA, ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
            ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> WorkoutKind.YOGA
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> WorkoutKind.SWIM
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> WorkoutKind.HIIT
            else -> WorkoutKind.OTHER
        }

        private fun WorkoutKind.toExerciseType(): Int = when (this) {
            WorkoutKind.RUN -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
            WorkoutKind.WALK -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
            WorkoutKind.BIKE -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
            WorkoutKind.STRENGTH -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
            WorkoutKind.YOGA -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA
            WorkoutKind.SWIM -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
            WorkoutKind.HIIT -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
            WorkoutKind.OTHER -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
        }
    }
}
