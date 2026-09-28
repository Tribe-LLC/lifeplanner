package az.tribe.lifeplanner.data.integrations

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the user has switched on in "Connect once" and "Connected apps". This is intent, not
 * permission: the platform grant is checked separately, and a switch that is on without a grant
 * just asks again. Kept per device, because permissions are per device too.
 */
class IntegrationPrefs(private val settings: Settings) {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    data class Snapshot(
        val health: Boolean,
        val calendar: Boolean,
        val reminders: Boolean,
        /** Per data type switches, by [DataFlow.key]. Missing means on. */
        val off: Set<String>,
    ) {
        fun isOn(flow: DataFlow) = flow.key !in off
    }

    fun setHealth(on: Boolean) = write { settings.putBoolean(KEY_HEALTH, on) }
    fun setCalendar(on: Boolean) = write { settings.putBoolean(KEY_CALENDAR, on) }
    fun setReminders(on: Boolean) = write { settings.putBoolean(KEY_REMINDERS, on) }

    fun setFlow(flow: DataFlow, on: Boolean) = write {
        val off = readOff().toMutableSet()
        if (on) off -= flow.key else off += flow.key
        settings.putString(KEY_OFF, off.joinToString(","))
    }

    private fun write(block: () -> Unit) {
        block()
        _state.value = read()
    }

    private fun readOff(): Set<String> =
        settings.getString(KEY_OFF, "").split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun read() = Snapshot(
        health = settings.getBoolean(KEY_HEALTH, false),
        calendar = settings.getBoolean(KEY_CALENDAR, false),
        reminders = settings.getBoolean(KEY_REMINDERS, false),
        off = readOff(),
    )

    private companion object {
        const val KEY_HEALTH = "v4_connect_health"
        const val KEY_CALENDAR = "v4_connect_calendar"
        const val KEY_REMINDERS = "v4_connect_reminders"
        const val KEY_OFF = "v4_flows_off"
    }
}

enum class FlowDirection { BOTH, IN, OUT }

/**
 * One kind of data that crosses between LifePlanner and a platform app, as listed on the
 * Connected apps screen. [direction] is what the app supports, not what the user picked.
 */
enum class DataFlow(val key: String, val source: Source, val label: String, val note: String, val direction: FlowDirection) {
    WORKOUTS("workouts", Source.HEALTH, "Workouts", "Your watch in, your plans out", FlowDirection.BOTH),
    STEPS("steps", Source.HEALTH, "Steps", "Counts toward walking habits", FlowDirection.IN),
    HEART("heart", Source.HEALTH, "Heart rate", "Resting and during workouts", FlowDirection.IN),
    SLEEP("sleep", Source.HEALTH, "Sleep", "Shapes tomorrow's plan", FlowDirection.BOTH),
    WEIGHT("weight", Source.HEALTH, "Weight", "Logged here or on a scale", FlowDirection.BOTH),
    WATER("water", Source.HEALTH, "Water", "Glasses you tick off", FlowDirection.BOTH),
    MINDFUL("mindful", Source.HEALTH, "Mindful minutes", "From breathing sessions", FlowDirection.OUT),
    EVENTS_IN("events_in", Source.CALENDAR, "Your events", "Today plans around your meetings", FlowDirection.IN),
    EVENTS_OUT("events_out", Source.CALENDAR, "Plans as events", "Trips, study blocks and workouts", FlowDirection.OUT);

    enum class Source { HEALTH, CALENDAR }
}
