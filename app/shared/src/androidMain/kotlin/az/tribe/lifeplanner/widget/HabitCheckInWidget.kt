package az.tribe.lifeplanner.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import az.tribe.lifeplanner.widget.data.WidgetHabitRow
import az.tribe.lifeplanner.widget.data.WidgetHabits
import az.tribe.lifeplanner.widget.receiver.HabitCheckInActionCallback
import az.tribe.lifeplanner.widget.theme.WidgetColorProviders

/**
 * The home screen widget: today's habits, due ones first in the order of the day, done ones at the
 * end. A tap on the circle ticks through HabitService (a counted habit goes up by one), a tap on the
 * name opens the app. Habits that tick themselves show without a circle.
 */
class HabitCheckInWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL_SIZE, MEDIUM_SIZE, LARGE_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initial = WidgetHabits.load()

        provideContent {
            val rows by androidx.compose.runtime.remember { WidgetHabits.flow() }.collectAsState(initial)
            val done = rows.count { it.done }
            GlanceTheme(colors = WidgetColorProviders) {
                val size = LocalSize.current
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(24.dp)
                        .background(GlanceTheme.colors.surface)
                        .padding(14.dp)
                ) {
                    when {
                        rows.isEmpty() -> EmptyHabitsView()
                        size.width < 200.dp -> SmallView(done, rows.size, rows.firstOrNull { !it.done })
                        else -> {
                            // Glance allows 10 children per column: the header plus up to 8 rows.
                            val max = if (size.height < 200.dp) 3 else 8
                            HabitsList(rows.take(max), done, rows.size)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val SMALL_SIZE = DpSize(120.dp, 110.dp)
        private val MEDIUM_SIZE = DpSize(250.dp, 110.dp)
        private val LARGE_SIZE = DpSize(250.dp, 250.dp)
    }
}

@androidx.compose.runtime.Composable
private fun EmptyHabitsView() {
    Column(
        modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Nothing due today", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface))
        Spacer(modifier = GlanceModifier.height(4.dp))
        Text("Tap to add a habit", style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
    }
}

@androidx.compose.runtime.Composable
private fun SmallView(done: Int, total: Int, next: WidgetHabitRow?) {
    Column(
        modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$done / $total", style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface))
        Text("habits today", style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            next?.let { "Next: ${it.title}" } ?: "All done",
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.primary),
            maxLines = 1,
        )
    }
}

@androidx.compose.runtime.Composable
private fun HabitsList(rows: List<WidgetHabitRow>, done: Int, total: Int) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp).clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Today", style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface))
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                if (done == total) "All done" else "$done of $total done",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary)
            )
        }
        rows.forEach { HabitRow(it) }
    }
}

@androidx.compose.runtime.Composable
private fun HabitRow(habit: WidgetHabitRow) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
            Text(
                habit.title,
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (habit.done) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
                    textDecoration = if (habit.done) androidx.glance.text.TextDecoration.LineThrough else null,
                ),
                maxLines = 1
            )
            Text(habit.meta, style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant), maxLines = 1)
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        val tickable = !habit.done && !habit.selfTicking
        Box(
            modifier = GlanceModifier
                .width(36.dp).height(36.dp)
                .cornerRadius(18.dp)
                .background(if (habit.done) GlanceTheme.colors.primary else GlanceTheme.colors.primaryContainer)
                .let {
                    if (tickable) it.clickable(
                        actionRunCallback<HabitCheckInActionCallback>(
                            parameters = actionParametersOf(HabitCheckInActionCallback.HABIT_ID_KEY to habit.id)
                        )
                    ) else it
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                when {
                    habit.done -> "\u2713"
                    habit.counted -> "+"
                    habit.selfTicking -> "\u00B7"
                    else -> ""
                },
                style = TextStyle(
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = if (habit.done) GlanceTheme.colors.onPrimary else GlanceTheme.colors.primary,
                )
            )
        }
    }
}

class HabitCheckInWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitCheckInWidget()
}
