@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package az.tribe.lifeplanner.widget

import android.content.Context
import co.touchlab.kermit.Logger
import org.koin.mp.KoinPlatform

actual class WidgetDataSyncService {

    actual suspend fun syncWidgetData(
        dashboardData: WidgetDashboardData,
        habits: List<WidgetHabitData>
    ) {
        // On Android, widgets read directly from the database, so we just trigger a refresh
        refreshWidgets()
    }

    actual suspend fun refreshWidgets() {
        try {
            val context: Context = KoinPlatform.getKoin().get()
            WidgetUpdateHelper.updateAllWidgets(context)
        } catch (e: Exception) {
            Logger.w("WidgetDataSyncService") { "Failed to refresh widgets: ${e.message}" }
        }
    }

    actual fun getPendingCheckIns(): List<String> {
        return try {
            val context: Context = KoinPlatform.getKoin().get()
            az.tribe.lifeplanner.widget.data.WidgetDatabaseHelper.getPendingCheckIns(context)
        } catch (e: Exception) {
            Logger.w("WidgetDataSyncService") { "Failed to get pending check-ins: ${e.message}" }
            emptyList()
        }
    }

    actual fun clearPendingCheckIns() {
        try {
            val context: Context = KoinPlatform.getKoin().get()
            az.tribe.lifeplanner.widget.data.WidgetDatabaseHelper.clearPendingCheckIns(context)
        } catch (e: Exception) {
            Logger.w("WidgetDataSyncService") { "Failed to clear pending check-ins: ${e.message}" }
        }
    }

    actual fun removePendingCheckIn(habitId: String) {
        try {
            val context: Context = KoinPlatform.getKoin().get()
            az.tribe.lifeplanner.widget.data.WidgetDatabaseHelper.removePendingCheckIn(context, habitId)
        } catch (e: Exception) {
            Logger.w("WidgetDataSyncService") { "Failed to remove pending check-in: ${e.message}" }
        }
    }
}
