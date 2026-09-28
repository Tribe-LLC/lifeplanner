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

    /** Workouts from the last [days], from any app or watch. Empty without permission. */
    suspend fun readWorkouts(days: Int = 14): List<HealthWorkout>

    /** Whether the user let us save workouts. Reading and writing are separate grants. */
    suspend fun canWriteWorkouts(): Boolean

    /**
     * Saves a finished workout. [clientId] is our own id for it, so Health can tell a re-save from
     * a new workout and so [readWorkouts] can mark it [HealthWorkout.fromThisApp]. False if refused.
     */
    suspend fun writeWorkout(kind: WorkoutKind, title: String, startEpochMs: Long, endEpochMs: Long, clientId: String): Boolean
}

enum class WorkoutKind(val label: String) {
    RUN("Run"), WALK("Walk"), BIKE("Ride"), STRENGTH("Strength"), YOGA("Yoga"), SWIM("Swim"), HIIT("HIIT"), OTHER("Workout");

    companion object {
        /** Best guess from what the user typed, so "leg day" is strength and "5k" is a run. */
        fun fromTitle(title: String): WorkoutKind {
            val t = title.lowercase()
            return when {
                listOf("run", "jog", "5k", "10k", "marathon").any { it in t } -> RUN
                listOf("walk", "hike", "steps").any { it in t } -> WALK
                listOf("bike", "cycl", "ride", "spin").any { it in t } -> BIKE
                listOf("yoga", "stretch", "pilates", "mobility").any { it in t } -> YOGA
                listOf("swim", "pool").any { it in t } -> SWIM
                listOf("hiit", "interval", "crossfit", "tabata").any { it in t } -> HIIT
                listOf("gym", "strength", "weights", "lift", "leg", "upper", "lower", "push", "pull", "core", "abs").any { it in t } -> STRENGTH
                else -> OTHER
            }
        }
    }
}

data class HealthWorkout(
    val id: String,
    val kind: WorkoutKind,
    val title: String?,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val fromThisApp: Boolean,
) {
    val minutes: Int get() = ((endEpochMs - startEpochMs) / 60_000L).toInt()
}
