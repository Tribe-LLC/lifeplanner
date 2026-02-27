package az.tribe.lifeplanner.ui.balance

/**
 * Simple singleton to pass auto-message between LifeBalance screen and AIChatScreen.
 * Avoids navigation argument length issues for long messages.
 */
object InsightMessageHolder {
    var pendingMessage: String? = null
}
