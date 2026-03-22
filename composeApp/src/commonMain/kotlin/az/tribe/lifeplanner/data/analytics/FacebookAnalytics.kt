package az.tribe.lifeplanner.data.analytics

expect object FacebookAnalytics {
    fun logCompleteRegistration(method: String)
    fun logCompleteTutorial()
    fun logViewContent(contentId: String, contentType: String)
    fun logAchieveLevel(level: Int)
    fun logUnlockAchievement(description: String)
    fun logSearch(query: String, contentType: String)
}
