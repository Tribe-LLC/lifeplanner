package az.tribe.lifeplanner.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import az.tribe.lifeplanner.MainActivity
import az.tribe.lifeplanner.widget.data.WidgetDatabaseHelper
import az.tribe.lifeplanner.widget.receiver.HabitCheckInActionCallback
import az.tribe.lifeplanner.widget.theme.StreakFireColor
import az.tribe.lifeplanner.widget.theme.SuccessColor
import az.tribe.lifeplanner.widget.theme.WidgetColorProviders

class HabitCheckInWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(MEDIUM_SIZE, LARGE_SIZE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val habits = WidgetDatabaseHelper.getHabitsForWidget(context, limit = 6)

        provideContent {
            GlanceTheme(colors = WidgetColorProviders) {
                val size = LocalSize.current
                val isMedium = size.height < 200.dp
                val maxHabits = if (isMedium) 3 else 6

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(16.dp)
                        .background(GlanceTheme.colors.surface)
                        .padding(12.dp)
                ) {
                    if (habits.isEmpty()) {
                        EmptyHabitsView()
                    } else {
                        HabitsList(
                            habits = habits.take(maxHabits),
                            showStreak = !isMedium
                        )
                    }
                }
            }
        }
    }

    companion object {
        private val MEDIUM_SIZE = DpSize(250.dp, 110.dp)
        private val LARGE_SIZE = DpSize(250.dp, 250.dp)
    }
}

@androidx.compose.runtime.Composable
private fun EmptyHabitsView() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No habits yet",
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.onSurfaceVariant
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text(
            text = "Tap to add your first habit",
            style = TextStyle(
                fontSize = 12.sp,
                color = GlanceTheme.colors.onSurfaceVariant
            ),
            modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>())
        )
    }
}

@androidx.compose.runtime.Composable
private fun HabitsList(habits: List<WidgetHabitData>, showStreak: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Habits",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            val doneCount = habits.count { it.isCompletedToday }
            Text(
                text = "$doneCount/${habits.size}",
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = SuccessColor
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Habit items
        habits.forEach { habit ->
            HabitRow(habit = habit, showStreak = showStreak)
            Spacer(modifier = GlanceModifier.height(4.dp))
        }
    }
}

@androidx.compose.runtime.Composable
private fun HabitRow(habit: WidgetHabitData, showStreak: Boolean) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(8.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox button
        if (habit.isCompletedToday) {
            Text(
                text = "\u2705",
                style = TextStyle(fontSize = 18.sp)
            )
        } else {
            Text(
                text = "\u2B1C",
                style = TextStyle(fontSize = 18.sp),
                modifier = GlanceModifier.clickable(
                    actionRunCallback<HabitCheckInActionCallback>(
                        parameters = actionParametersOf(
                            HabitCheckInActionCallback.HABIT_ID_KEY to habit.id
                        )
                    )
                )
            )
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        // Habit title - tap opens app
        Text(
            text = habit.title,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (habit.isCompletedToday)
                    GlanceTheme.colors.onSurfaceVariant
                else
                    GlanceTheme.colors.onSurface
            ),
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity<MainActivity>()),
            maxLines = 1
        )

        // Streak count (large widget only)
        if (showStreak && habit.currentStreak > 0) {
            Text(
                text = "\uD83D\uDD25${habit.currentStreak}",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = StreakFireColor
                )
            )
        }
    }
}

class HabitCheckInWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitCheckInWidget()
}
