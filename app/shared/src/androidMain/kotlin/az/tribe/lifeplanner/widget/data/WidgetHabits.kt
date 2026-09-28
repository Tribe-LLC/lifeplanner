package az.tribe.lifeplanner.widget.data

import az.tribe.lifeplanner.data.habits.HabitService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.context.GlobalContext

/** One row on the home screen widget. */
data class WidgetHabitRow(
    val id: String,
    val title: String,
    val meta: String,
    val done: Boolean,
    /** Counted habits go up by one per tap ("+"), others tick in one. */
    val counted: Boolean,
    /** Ticked by Health or the app's own logs; the widget shows it but does not offer a tap. */
    val selfTicking: Boolean,
)

/**
 * What the widget shows, from the same place as Today: habits due today with schedules, skips and
 * breaks applied, in the order of the day. Replaces the raw SQL that listed every active habit.
 */
object WidgetHabits {
    suspend fun load(): List<WidgetHabitRow> = runCatching { flow().first() }.getOrDefault(emptyList())

    /** Live rows, so a widget session that stays open redraws when a habit changes. */
    fun flow(): Flow<List<WidgetHabitRow>> {
        val service = GlobalContext.getOrNull()?.getOrNull<HabitService>() ?: return flowOf(emptyList())
        return service.rows.map { rows -> map(rows) }
    }

    private fun map(rows: List<az.tribe.lifeplanner.data.habits.HabitRow>): List<WidgetHabitRow> =
        rows.filter { it.stats.dueToday || it.doneToday }
            .sortedWith(compareBy({ it.doneToday }, { it.minute }))
            .map { r ->
                WidgetHabitRow(
                    id = r.habit.id,
                    title = r.habit.title,
                    meta = r.meta,
                    done = r.doneToday,
                    counted = r.habit.targetCount > 1,
                    // Health habits tick only from Health; the rest can still be ticked by hand, as in the app.
                    selfTicking = r.habit.healthMetricType != null,
                )
            }

    /** A tap on a row's circle: through HabitService, like the app and the reminder buttons. */
    suspend fun tick(habitId: String): Boolean {
        val service = GlobalContext.getOrNull()?.getOrNull<HabitService>() ?: return false
        return runCatching { service.tickToday(habitId); true }.getOrDefault(false)
    }
}
