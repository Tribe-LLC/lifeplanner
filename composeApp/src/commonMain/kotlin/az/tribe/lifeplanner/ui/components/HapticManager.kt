package az.tribe.lifeplanner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Haptic feedback manager providing predefined patterns for different UX interactions.
 * Uses native platform haptic APIs via Compose's HapticFeedback.
 */
class HapticManager(private val hapticFeedback: HapticFeedback) {

    /**
     * Light tick feedback for small toggles and button presses
     */
    fun tick() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /**
     * Medium click feedback for checkbox toggles and selection changes
     */
    fun click() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /**
     * Success feedback for completed actions (habit check-in, journal save)
     */
    fun success() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /**
     * Strong success feedback for major achievements (goal completion, level up)
     */
    fun strongSuccess() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /**
     * Celebration feedback for badge unlocks and special achievements
     */
    fun celebration() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /**
     * Warning feedback for destructive actions (delete confirmation)
     */
    fun warning() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /**
     * Error feedback for failed actions
     */
    fun error() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /**
     * Light feedback for swipe threshold reached
     */
    fun swipeThreshold() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /**
     * Medium feedback for swipe action zone (edit/complete)
     */
    fun swipeActionZone() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /**
     * Strong feedback for swipe delete zone
     */
    fun swipeDeleteZone() {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /**
     * Milestone progress feedback - gets stronger as progress increases
     */
    fun progressMilestone(progress: Float) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /**
     * Streak milestone feedback (for 5, 10, 30+ day streaks)
     */
    fun streakMilestone(days: Int) {
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

/**
 * Remember and provide a HapticManager instance for use in Composables
 */
@Composable
fun rememberHapticManager(): HapticManager {
    val hapticFeedback = LocalHapticFeedback.current
    return remember(hapticFeedback) { HapticManager(hapticFeedback) }
}
