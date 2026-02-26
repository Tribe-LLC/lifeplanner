package az.tribe.lifeplanner.widget.receiver

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import az.tribe.lifeplanner.widget.WidgetUpdateHelper
import az.tribe.lifeplanner.widget.data.WidgetDatabaseHelper
import co.touchlab.kermit.Logger

class HabitCheckInActionCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val habitId = parameters[HABIT_ID_KEY] ?: return

        Logger.i("HabitCheckInActionCallback") { "Checking in habit: $habitId" }

        val success = WidgetDatabaseHelper.performHabitCheckIn(context, habitId)

        if (success) {
            Logger.i("HabitCheckInActionCallback") { "Check-in successful, refreshing widgets" }
            WidgetUpdateHelper.updateAllWidgets(context)
        } else {
            Logger.w("HabitCheckInActionCallback") { "Check-in failed for habit: $habitId" }
        }
    }

    companion object {
        val HABIT_ID_KEY = ActionParameters.Key<String>("habit_id")
    }
}
