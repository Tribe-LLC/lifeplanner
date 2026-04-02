package az.tribe.lifeplanner.data.tutorial

import com.russhwolf.settings.Settings

class TutorialManager(private val settings: Settings) {

    fun isFirstVisit(screenKey: String): Boolean =
        !settings.getBoolean("hint_seen_$screenKey", false)

    fun markSeen(screenKey: String) {
        settings.putBoolean("hint_seen_$screenKey", true)
    }

    fun resetAll() {
        ALL_SCREEN_KEYS.forEach { settings.remove("hint_seen_$it") }
    }

    companion object {
        val ALL_SCREEN_KEYS = listOf(
            "home", "goals", "journal", "habits", "focus",
            "life_balance", "analytics", "ai_chat", "achievements", "retrospective"
        )
    }
}
