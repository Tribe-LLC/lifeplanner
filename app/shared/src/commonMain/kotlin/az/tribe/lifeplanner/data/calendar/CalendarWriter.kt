@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package az.tribe.lifeplanner.data.calendar

/**
 * Puts LifePlanner plans on the device calendar (workouts now; trips and study blocks next), the
 * "Plans as events" half of the calendar connection. Uses the calendar permission the reader
 * already asks for, which includes write on both platforms. Every call is a no-op returning
 * null/false without permission, so callers never need to check first.
 */
expect class CalendarWriter() {
    suspend fun canWrite(): Boolean

    /** Adds an event to the user's default calendar. Returns its id, or null if it could not. */
    suspend fun addEvent(title: String, startEpochMs: Long, endEpochMs: Long, notes: String? = null): String?

    suspend fun moveEvent(id: String, startEpochMs: Long, endEpochMs: Long): Boolean

    suspend fun deleteEvent(id: String): Boolean
}
