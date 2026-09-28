package az.tribe.lifeplanner.data.study

/**
 * iOS has no ongoing notification to drive the timer from; a Live Activity would need a widget
 * extension. The timer pauses in the app, and the "your minutes are up" notice is scheduled in
 * common code, so these stay empty.
 */
actual object StudyTimerNotice {
    actual fun show(active: ActiveStudy) {}
    actual fun clear() {}
    actual fun saved(subject: String, minutes: Int) {}
}
