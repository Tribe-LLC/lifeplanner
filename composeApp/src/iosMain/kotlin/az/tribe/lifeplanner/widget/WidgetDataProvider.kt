@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package az.tribe.lifeplanner.widget

import co.touchlab.kermit.Logger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

/**
 * Writes widget data as JSON to shared App Group UserDefaults
 * so the WidgetKit extension can read it.
 */
object WidgetDataProvider {

    private const val APP_GROUP_ID = "group.az.tribe.lifeplanner"
    private const val DASHBOARD_KEY = "widget_dashboard_data"
    private const val HABITS_KEY = "widget_habits_data"
    private const val PENDING_CHECKINS_KEY = "widget_pending_checkins"

    private val json = Json { ignoreUnknownKeys = true }

    private fun getSharedDefaults(): NSUserDefaults? {
        return NSUserDefaults(suiteName = APP_GROUP_ID)
    }

    fun readPendingCheckIns(): List<String> {
        val defaults = getSharedDefaults() ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        val array = defaults.stringArrayForKey(PENDING_CHECKINS_KEY) as? List<String>
        return array ?: emptyList()
    }

    fun clearPendingCheckIns() {
        val defaults = getSharedDefaults() ?: return
        defaults.removeObjectForKey(PENDING_CHECKINS_KEY)
        defaults.synchronize()
    }

    fun writeDashboardData(data: WidgetDashboardData) {
        try {
            val defaults = getSharedDefaults()
            if (defaults == null) {
                println("WidgetDataProvider: FAILED - Could not access App Group UserDefaults for suite '$APP_GROUP_ID'")
                return
            }
            val jsonString = json.encodeToString(data)
            defaults.setObject(jsonString, forKey = DASHBOARD_KEY)
            defaults.synchronize()
            println("WidgetDataProvider: Wrote dashboard data - streak=${data.currentStreak}, habits=${data.habitsDoneToday}/${data.habitsTotal}")
        } catch (e: Exception) {
            println("WidgetDataProvider: FAILED to write dashboard data: ${e.message}")
        }
    }

    fun writeHabitsData(habits: List<WidgetHabitData>) {
        try {
            val defaults = getSharedDefaults()
            if (defaults == null) {
                println("WidgetDataProvider: FAILED - Could not access App Group UserDefaults for suite '$APP_GROUP_ID'")
                return
            }
            val jsonString = json.encodeToString(habits)
            defaults.setObject(jsonString, forKey = HABITS_KEY)
            defaults.synchronize()
            println("WidgetDataProvider: Wrote ${habits.size} habits to widget data")
        } catch (e: Exception) {
            println("WidgetDataProvider: FAILED to write habits data: ${e.message}")
        }
    }
}
