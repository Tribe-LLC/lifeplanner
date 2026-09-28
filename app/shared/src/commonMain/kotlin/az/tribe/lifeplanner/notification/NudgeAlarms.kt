package az.tribe.lifeplanner.notification

import az.tribe.lifeplanner.data.habits.Nudge

/**
 * One-off notifications at an exact date and time that open a place in the app when tapped.
 * Separate from habit reminders, which repeat and carry no destination.
 */
expect object NudgeAlarms {
    fun schedule(nudge: Nudge)
    fun cancel(id: String)
}
