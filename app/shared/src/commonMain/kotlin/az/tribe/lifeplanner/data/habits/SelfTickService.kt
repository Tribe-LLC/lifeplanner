package az.tribe.lifeplanner.data.habits

import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.service.SelfTick
import co.touchlab.kermit.Logger
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Ticks habits set to tick themselves from what the app logs (a workout, a breathing break, study
 * time), whichever screen logged it. Watches today's logs while the app runs. Only ever raises a
 * count to what the logs justify, and only when the logs justify more than last time, so an untick
 * by hand stays unticked until something new is logged.
 */
class SelfTickService(
    private val service: HabitService,
    private val habits: HabitRepository,
    private val logs: LifeLogRepository,
) {
    private val tz = TimeZone.currentSystemDefault()
    /** What was last applied per habit and day ("id:date" to count), so a manual untick sticks. */
    private val applied = mutableMapOf<String, Int>()

    @OptIn(FlowPreview::class)
    suspend fun run() {
        val today = Clock.System.todayIn(tz)
        combine(service.rows, logs.observeInRange(today.minus(DatePeriod(days = 1)), today.plus(DatePeriod(days = 1)))) { rows, ls -> rows to ls }
            .debounce(800)
            .collect { (rows, ls) ->
                val now = Clock.System.todayIn(tz)
                val todays = ls.filter { it.date == now }
                rows.filter { SelfTick.of(it.habit) !in NOT_FROM_LOGS && !it.stats.skippedToday }.forEach { r ->
                    val want = SelfTick.wanted(r.habit, todays) ?: return@forEach
                    val key = "${r.habit.id}:$now"
                    if ((applied[key] ?: 0) >= want) return@forEach
                    applied[key] = want
                    runCatching {
                        val have = habits.getCheckInByHabitAndDate(r.habit.id, now)
                        if (have?.completed == true) return@runCatching
                        if (r.habit.targetCount > 1) {
                            val delta = want - (have?.count ?: 0)
                            if (delta > 0) habits.addCount(r.habit.id, now, delta)
                        } else {
                            habits.checkIn(r.habit.id, now, notes = NOTE)
                        }
                        PostHogAnalytics.capture("v4_habit_self_ticked", mapOf("by" to SelfTick.of(r.habit).name))
                    }.onFailure { Logger.w("SelfTick") { "${r.habit.title}: ${it.message}" } }
                }
            }
    }

    companion object {
        const val NOTE = "Ticked itself"
        private val NOT_FROM_LOGS = setOf(SelfTick.ME, SelfTick.STEPS, SelfTick.SLEEP)
    }
}
