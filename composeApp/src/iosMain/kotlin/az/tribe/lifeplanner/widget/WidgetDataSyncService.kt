@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package az.tribe.lifeplanner.widget

import co.touchlab.kermit.Logger
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNotificationName

actual class WidgetDataSyncService {

    actual suspend fun syncWidgetData(
        dashboardData: WidgetDashboardData,
        habits: List<WidgetHabitData>
    ) {
        WidgetDataProvider.writeDashboardData(dashboardData)
        WidgetDataProvider.writeHabitsData(habits)
        refreshWidgets()
    }

    actual suspend fun refreshWidgets() {
        try {
            // Post a notification that Swift code can observe to reload widget timelines.
            // The WidgetKit framework is accessed from Swift side.
            NSNotificationCenter.defaultCenter.postNotificationName(
                WIDGET_REFRESH_NOTIFICATION,
                `object` = null
            )
        } catch (e: Exception) {
            Logger.w("WidgetDataSyncService") { "Failed to post widget refresh notification: ${e.message}" }
        }
    }

    actual fun getPendingCheckIns(): List<String> {
        return WidgetDataProvider.readPendingCheckIns()
    }

    actual fun clearPendingCheckIns() {
        WidgetDataProvider.clearPendingCheckIns()
    }

    actual fun removePendingCheckIn(habitId: String) {
        WidgetDataProvider.removePendingCheckIn(habitId)
    }

    companion object {
        val WIDGET_REFRESH_NOTIFICATION: NSNotificationName = "az.tribe.lifeplanner.refreshWidgets" as NSNotificationName
    }
}
