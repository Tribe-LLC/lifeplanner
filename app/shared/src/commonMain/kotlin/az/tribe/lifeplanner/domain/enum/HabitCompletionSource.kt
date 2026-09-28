package az.tribe.lifeplanner.domain.enum

/**
 * What completes a habit. [MANUAL] habits are ticked by hand; the others are credited automatically
 * when the user finishes the matching in-app session, so doing the thing inside LifePlanner counts
 * without a second trip to the habit list.
 */
enum class HabitCompletionSource(val displayName: String, val description: String) {
    MANUAL("Manually", "You tick it off yourself"),
    FOCUS("Focus session", "Minutes focused count toward it"),
    BREATHING("Breathing", "Each guided breath session counts"),
    /** Ticked by any finished workout that day (v4). Older app versions read it as MANUAL. */
    WORKOUT("Workout", "Any finished workout counts");

    companion object {
        /** Parses a stored/synced name, falling back to [MANUAL] for null or unknown values. */
        fun fromNameOrDefault(name: String?): HabitCompletionSource =
            entries.firstOrNull { it.name == name } ?: MANUAL
    }
}
