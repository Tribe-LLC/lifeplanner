package az.tribe.lifeplanner.data.study

/**
 * The running Study timer outside the app. On Android an ongoing notification with a stopwatch,
 * Pause or Resume, and Stop, all working without opening the app. On iOS nothing yet: the timer
 * pauses in the app and a notice comes when the aimed-for minutes are up.
 */
expect object StudyTimerNotice {
    /** Shows or updates the notice for [active], running or paused. */
    fun show(active: ActiveStudy)

    fun clear()

    /** After Stop from the notice itself: a short "Saved 45 min of Maths" in its place. */
    fun saved(subject: String, minutes: Int)
}
