package az.tribe.lifeplanner.domain.enum

import kotlinx.serialization.Serializable

@Serializable
enum class HabitFrequency(val displayName: String, val daysInterval: Int) {
    DAILY("Daily", 1),
    WEEKLY("Weekly", 7),
    WEEKDAYS("Weekdays", 1),
    WEEKENDS("Weekends", 1),
    CUSTOM("Custom", 1);

    companion object {
        fun fromString(value: String): HabitFrequency {
            return entries.find { it.name == value } ?: DAILY
        }
    }
}
