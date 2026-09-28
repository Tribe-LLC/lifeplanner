package az.tribe.lifeplanner.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import co.touchlab.kermit.Logger

object WidgetUpdateHelper {

    /** v4 ships one widget, today's habits. The v3 dashboard and quick-actions widgets are no longer offered. */
    suspend fun updateAllWidgets(context: Context) {
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
