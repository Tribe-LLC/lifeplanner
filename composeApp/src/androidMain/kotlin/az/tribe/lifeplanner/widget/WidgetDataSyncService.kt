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
        // Android widgets write directly to DB, no pending queue needed
        return emptyList()
    }

    actual fun clearPendingCheckIns() {
        // No-op on Android
    }
}
