package az.tribe.lifeplanner.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.unit.ColorProvider
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
import az.tribe.lifeplanner.widget.theme.StreakFireColor
import az.tribe.lifeplanner.widget.theme.SuccessColor
import az.tribe.lifeplanner.widget.theme.WidgetColorProviders
import az.tribe.lifeplanner.widget.theme.XpBarBackground
import az.tribe.lifeplanner.widget.theme.XpBarFill
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DailyDashboardWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(SMALL_SIZE, MEDIUM_SIZE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDatabaseHelper.getDashboardData(context)

        provideContent {
            GlanceTheme(colors = WidgetColorProviders) {
                val size = LocalSize.current
                val isSmall = size.width < 250.dp

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(16.dp)
                        .background(GlanceTheme.colors.surface)
                        .clickable(actionStartActivity<MainActivity>())
                        .padding(12.dp)
                ) {
                    if (isSmall) {
                        SmallDashboard(data)
                    } else {
                        MediumDashboard(data)
                    }
                }
            }
        }
    }

    companion object {
        private val SMALL_SIZE = DpSize(110.dp, 110.dp)
        private val MEDIUM_SIZE = DpSize(250.dp, 110.dp)
    }
}

@androidx.compose.runtime.Composable
private fun SmallDashboard(data: WidgetDashboardData) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Streak
        Text(
            text = "\uD83D\uDD25",
            style = TextStyle(fontSize = 24.sp)
        )
        Text(
            text = "${data.currentStreak}",
            style = TextStyle(
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = StreakFireColor
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))

        // Habits ratio
        Text(
            text = "${data.habitsDoneToday}/${data.habitsTotal} habits",
            style = TextStyle(
                fontSize = 12.sp,
                color = GlanceTheme.colors.onSurfaceVariant
            )
        )
        Spacer(modifier = GlanceModifier.height(2.dp))

        // Level
        Text(
            text = "Lv. ${data.currentLevel}",
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.primary
            )
        )
    }
}

@androidx.compose.runtime.Composable
private fun MediumDashboard(data: WidgetDashboardData) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Header row with date
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\uD83D\uDD25 ${data.currentStreak} day streak",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = StreakFireColor
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d")),
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Stats row
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Habits done
            StatItem(
                label = "Habits",
                value = "${data.habitsDoneToday}/${data.habitsTotal}",
                color = SuccessColor,
                modifier = GlanceModifier.defaultWeight()
            )
            // Active goals
            StatItem(
                label = "Goals",
                value = "${data.activeGoals}",
                color = GlanceTheme.colors.primary,
                modifier = GlanceModifier.defaultWeight()
            )
            // Level
            StatItem(
                label = "Level",
                value = "${data.currentLevel}",
                color = GlanceTheme.colors.primary,
                modifier = GlanceModifier.defaultWeight()
            )
        }

        Spacer(modifier = GlanceModifier.height(8.dp))

        // XP progress bar
        val xpForCurrent = az.tribe.lifeplanner.domain.model.UserProgress.calculateTotalXpForLevel(data.currentLevel)
        val xpInLevel = (data.totalXp - xpForCurrent).coerceAtLeast(0)
        val xpNeeded = az.tribe.lifeplanner.domain.model.UserProgress.calculateXpForLevel(data.currentLevel)
        val progress = if (xpNeeded > 0) (xpInLevel.toFloat() / xpNeeded).coerceIn(0f, 1f) else 0f

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "XP",
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Row(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(6.dp)
                    .cornerRadius(3.dp)
                    .background(XpBarBackground)
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = GlanceModifier
                            .height(6.dp)
                            .width((progress * 100).dp)
                            .cornerRadius(3.dp)
                            .background(XpBarFill)
                    ) {}
                }
            }
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = "$xpInLevel/$xpNeeded",
                style = TextStyle(
                    fontSize = 10.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun StatItem(
    label: String,
    value: String,
    color: ColorProvider,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
        Text(
            text = label,
            style = TextStyle(
                fontSize = 10.sp,
                color = GlanceTheme.colors.onSurfaceVariant
            )
        )
    }
}

class DailyDashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyDashboardWidget()
}
