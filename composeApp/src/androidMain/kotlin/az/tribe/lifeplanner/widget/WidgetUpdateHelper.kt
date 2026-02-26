package az.tribe.lifeplanner.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import co.touchlab.kermit.Logger

object WidgetUpdateHelper {

    suspend fun updateAllWidgets(context: Context) {
        try {
            DailyDashboardWidget().updateAll(context)
        } catch (e: Exception) {
            Logger.w("WidgetUpdateHelper") { "Failed to update dashboard widgets: ${e.message}" }
        }
        try {
            HabitCheckInWidget().updateAll(context)
        } catch (e: Exception) {
            Logger.w("WidgetUpdateHelper") { "Failed to update habit widgets: ${e.message}" }
        }
    }

    suspend fun updateDashboardWidgets(context: Context) {
        try {
            DailyDashboardWidget().updateAll(context)
        } catch (e: Exception) {
            Logger.w("WidgetUpdateHelper") { "Failed to update dashboard widgets: ${e.message}" }
        }
    }

    suspend fun updateHabitWidgets(context: Context) {
        try {
            HabitCheckInWidget().updateAll(context)
        } catch (e: Exception) {
            Logger.w("WidgetUpdateHelper") { "Failed to update habit widgets: ${e.message}" }
        }
    }
}
