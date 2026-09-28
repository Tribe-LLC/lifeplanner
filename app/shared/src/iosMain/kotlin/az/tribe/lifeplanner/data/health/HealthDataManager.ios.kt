@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package az.tribe.lifeplanner.data.health

import co.touchlab.kermit.Logger
import com.viktormykhailiv.kmp.health.HealthDataType
import com.viktormykhailiv.kmp.health.HealthManagerFactory
import com.viktormykhailiv.kmp.health.aggregateSteps
import com.viktormykhailiv.kmp.health.readExercise
import com.viktormykhailiv.kmp.health.readHeartRate
import com.viktormykhailiv.kmp.health.records.ExerciseSessionRecord
import com.viktormykhailiv.kmp.health.records.ExerciseType
import com.viktormykhailiv.kmp.health.records.metadata.Metadata
import com.viktormykhailiv.kmp.health.readSleep
import com.viktormykhailiv.kmp.health.readSteps
import com.viktormykhailiv.kmp.health.readWeight
import com.viktormykhailiv.kmp.health.duration
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val TAG = "HealthDataManager"

@OptIn(ExperimentalTime::class)
actual class HealthDataManager {

    private val manager = HealthManagerFactory().createManager()

    actual suspend fun isAvailable(): Boolean {
        var result = false
        manager.isAvailable()
            .onSuccess { result = it }
            .onFailure { Logger.w(TAG) { "isAvailable failed: ${it.message}" } }
        return result
    }

    actual suspend fun hasPermissions(): Boolean {
        if (!isAvailable()) return false
        var authorized = false
        manager.requestAuthorization(
            readTypes = listOf(
                HealthDataType.Steps,
                HealthDataType.Weight,
                HealthDataType.HeartRate,
                HealthDataType.Sleep,
                HealthDataType.Exercise(),
            ),
            writeTypes = listOf(HealthDataType.Exercise())
        ).onSuccess { authorized = it }
            .onFailure { Logger.w(TAG) { "Authorization failed: ${it.message}" } }
        return authorized
    }

    actual suspend fun readTodaySteps(): Long? {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val todayKtx = kotlinx.datetime.Clock.System.now()
        val startOfDayKtx = todayKtx.toLocalDateTime(tz).date.atStartOfDayIn(tz)
        val startOfDay = Instant.fromEpochMilliseconds(startOfDayKtx.toEpochMilliseconds())

        var steps: Long? = null
        manager.aggregateSteps(
            startTime = startOfDay,
            endTime = now
        ).onSuccess { aggregate ->
            steps = aggregate.count
        }.onFailure {
            Logger.w(TAG) { "Failed to read today steps: ${it.message}" }
        }
        return steps
    }

    actual suspend fun readStepsForDateRange(
        start: LocalDate,
        end: LocalDate
    ): List<HealthDataPoint> {
        val tz = TimeZone.currentSystemDefault()
        val startInstant = start.atStartOfDayIn(tz).let {
            Instant.fromEpochMilliseconds(it.toEpochMilliseconds())
        }
        val endInstant = end.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz).let {
            Instant.fromEpochMilliseconds(it.toEpochMilliseconds())
        }

        var result = emptyList<HealthDataPoint>()
        manager.readSteps(
            startTime = startInstant,
            endTime = endInstant
        ).onSuccess { records ->
            result = records.map { record ->
                val dateKtx = kotlinx.datetime.Instant.fromEpochMilliseconds(
                    record.startTime.toEpochMilliseconds()
                ).toLocalDateTime(tz).date
                HealthDataPoint(
                    value = record.count.toDouble(),
                    date = dateKtx,
                    recordedAt = record.startTime.toString()
                )
            }
        }.onFailure {
            Logger.w(TAG) { "Failed to read steps range: ${it.message}" }
        }
        return result
    }

    actual suspend fun readRecentWeight(days: Int): List<HealthDataPoint> {
        val now = Clock.System.now()
        val startTime = now - days.days
        val tz = TimeZone.currentSystemDefault()

        var result = emptyList<HealthDataPoint>()
        manager.readWeight(
            startTime = startTime,
            endTime = now
        ).onSuccess { records ->
            result = records.map { record ->
                val dateKtx = kotlinx.datetime.Instant.fromEpochMilliseconds(
                    record.time.toEpochMilliseconds()
                ).toLocalDateTime(tz).date
                HealthDataPoint(
                    value = record.weight.inKilograms,
                    date = dateKtx,
                    recordedAt = record.time.toString()
                )
            }
        }.onFailure {
            Logger.w(TAG) { "Failed to read weight: ${it.message}" }
        }
        return result
    }

    actual suspend fun readHeartRate(days: Int): List<HealthDataPoint> {
        val now = Clock.System.now()
        val startTime = now - days.days
        val tz = TimeZone.currentSystemDefault()

        var result = emptyList<HealthDataPoint>()
        manager.readHeartRate(
            startTime = startTime,
            endTime = now
        ).onSuccess { records ->
            result = records.flatMap { record ->
                record.samples.map { sample ->
                    val dateKtx = kotlinx.datetime.Instant.fromEpochMilliseconds(
                        sample.time.toEpochMilliseconds()
                    ).toLocalDateTime(tz).date
                    HealthDataPoint(
                        value = sample.beatsPerMinute.toDouble(),
                        date = dateKtx,
                        recordedAt = sample.time.toString()
                    )
                }
            }
        }.onFailure {
            Logger.w(TAG) { "Failed to read heart rate: ${it.message}" }
        }
        return result
    }

    actual suspend fun readSleep(days: Int): List<HealthDataPoint> {
        val now = Clock.System.now()
        val startTime = now - days.days
        val tz = TimeZone.currentSystemDefault()

        var result = emptyList<HealthDataPoint>()
        manager.readSleep(
            startTime = startTime,
            endTime = now
        ).onSuccess { records ->
            result = records.map { record ->
                val durationHours = record.duration.inWholeMinutes / 60.0
                val dateKtx = kotlinx.datetime.Instant.fromEpochMilliseconds(
                    record.startTime.toEpochMilliseconds()
                ).toLocalDateTime(tz).date
                HealthDataPoint(
                    value = durationHours,
                    date = dateKtx,
                    recordedAt = record.startTime.toString()
                )
            }
        }.onFailure {
            Logger.w(TAG) { "Failed to read sleep: ${it.message}" }
        }
        return result
    }

    actual suspend fun readWorkouts(days: Int): List<HealthWorkout> {
        val now = Clock.System.now()
        var result = emptyList<HealthWorkout>()
        manager.readExercise(startTime = now - days.days, endTime = now)
            .onSuccess { records ->
                result = records.map { r ->
                    HealthWorkout(
                        id = r.metadata.id.ifEmpty { "hk@${r.startTime.toEpochMilliseconds()}" },
                        kind = r.exerciseType.toKind(),
                        title = r.title,
                        startEpochMs = r.startTime.toEpochMilliseconds(),
                        endEpochMs = r.endTime.toEpochMilliseconds(),
                        fromThisApp = r.metadata.id.startsWith("lp-"),
                    )
                }
            }
            .onFailure { Logger.w(TAG) { "Failed to read workouts: ${it.message}" } }
        return result
    }

    actual suspend fun canWriteWorkouts(): Boolean {
        if (!isAvailable()) return false
        var ok = false
        manager.isAuthorized(readTypes = emptyList(), writeTypes = listOf(HealthDataType.Exercise()))
            .onSuccess { ok = it }
        return ok
    }

    actual suspend fun writeWorkout(kind: WorkoutKind, title: String, startEpochMs: Long, endEpochMs: Long, clientId: String): Boolean {
        if (endEpochMs <= startEpochMs) return false
        var ok = false
        manager.writeData(
            listOf(
                ExerciseSessionRecord(
                    startTime = Instant.fromEpochMilliseconds(startEpochMs),
                    endTime = Instant.fromEpochMilliseconds(endEpochMs),
                    exerciseType = kind.toExerciseType(),
                    title = title,
                    exerciseRoute = null,
                    metadata = Metadata.manualEntry(id = clientId),
                )
            )
        ).onSuccess { ok = true }
            .onFailure { Logger.w(TAG) { "Failed to write workout: ${it.message}" } }
        return ok
    }
}

private fun ExerciseType.toKind(): WorkoutKind = when (this) {
    ExerciseType.Running, ExerciseType.RunningTreadmill -> WorkoutKind.RUN
    ExerciseType.Walking, ExerciseType.Hiking -> WorkoutKind.WALK
    ExerciseType.Biking, ExerciseType.BikingStationary -> WorkoutKind.BIKE
    ExerciseType.StrengthTraining, ExerciseType.Calisthenics -> WorkoutKind.STRENGTH
    ExerciseType.Yoga, ExerciseType.Pilates -> WorkoutKind.YOGA
    ExerciseType.SwimmingPool, ExerciseType.SwimmingOpenWater -> WorkoutKind.SWIM
    ExerciseType.HighIntensityIntervalTraining -> WorkoutKind.HIIT
    else -> WorkoutKind.OTHER
}

private fun WorkoutKind.toExerciseType(): ExerciseType = when (this) {
    WorkoutKind.RUN -> ExerciseType.Running
    WorkoutKind.WALK -> ExerciseType.Walking
    WorkoutKind.BIKE -> ExerciseType.Biking
    WorkoutKind.STRENGTH -> ExerciseType.StrengthTraining
    WorkoutKind.YOGA -> ExerciseType.Yoga
    WorkoutKind.SWIM -> ExerciseType.SwimmingPool
    WorkoutKind.HIIT -> ExerciseType.HighIntensityIntervalTraining
    WorkoutKind.OTHER -> ExerciseType.OtherWorkout
}
