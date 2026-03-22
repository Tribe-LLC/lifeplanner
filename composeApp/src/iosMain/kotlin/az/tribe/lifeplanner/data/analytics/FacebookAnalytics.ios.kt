package az.tribe.lifeplanner.data.analytics

interface FacebookAnalyticsBridge {
    fun logEvent(eventName: String, parameters: Map<String, String>)
}

actual object FacebookAnalytics {
    var bridge: FacebookAnalyticsBridge? = null

    actual fun logCompleteRegistration(method: String) {
        bridge?.logEvent("fb_mobile_complete_registration", mapOf("fb_registration_method" to method))
    }

    actual fun logCompleteTutorial() {
        bridge?.logEvent("fb_mobile_tutorial_completion", mapOf("fb_success" to "1"))
    }

    actual fun logViewContent(contentId: String, contentType: String) {
        bridge?.logEvent("fb_mobile_content_view", mapOf("fb_content_id" to contentId, "fb_content_type" to contentType))
    }

    actual fun logAchieveLevel(level: Int) {
        bridge?.logEvent("fb_mobile_level_achieved", mapOf("fb_level" to level.toString()))
    }

    actual fun logUnlockAchievement(description: String) {
        bridge?.logEvent("fb_mobile_achievement_unlocked", mapOf("fb_description" to description))
    }

    actual fun logSearch(query: String, contentType: String) {
        bridge?.logEvent("fb_mobile_search", mapOf("fb_search_string" to query, "fb_content_type" to contentType))
    }
}
