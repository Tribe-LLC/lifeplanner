@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package az.tribe.lifeplanner.widget

expect class WidgetDataSyncService() {
    suspend fun syncWidgetData(
        dashboardData: WidgetDashboardData,
        habits: List<WidgetHabitData>
    )

    suspend fun refreshWidgets()

    fun getPendingCheckIns(): List<String>

    fun clearPendingCheckIns()

    fun removePendingCheckIn(habitId: String)
}
