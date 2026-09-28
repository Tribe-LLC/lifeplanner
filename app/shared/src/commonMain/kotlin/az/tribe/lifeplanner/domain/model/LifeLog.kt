package az.tribe.lifeplanner.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** Whether a log is something that happened, or something planned for [LifeLog.occurredAt]. */
enum class LogStatus(val key: String) {
    PLANNED("planned"), DONE("done"), SKIPPED("skipped");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: DONE
    }
}

enum class LogKind(val key: String) {
    EXPENSE("expense"), INCOME("income"), WORKOUT("workout"), MEAL("meal"), STUDY("study"),
    SLEEP("sleep"), WATER("water"), MOOD("mood"), NOTE("note");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: NOTE
    }
}

/**
 * One thing that happened, or is planned, in some area: a spend, a workout, a meal, a study block.
 * The fourth building block next to plans, routines and budgets. A single row can matter to more
 * than one area (a restaurant meal is a meal and a spend): those are two rows sharing [externalId]
 * from quick add, so each area reads only its own.
 */
data class LifeLog(
    val id: String,
    val area: PlanArea,
    val kind: LogKind,
    val status: LogStatus = LogStatus.DONE,
    val title: String,
    val amount: Double? = null,
    val currency: String? = null,
    val category: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val durationMin: Int? = null,
    val occurredAt: LocalDateTime,
    val source: String = SOURCE_MANUAL,
    val externalId: String? = null,
    val tripId: String? = null,
    val notes: String? = null,
) {
    val date: LocalDate get() = occurredAt.date

    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_QUICK_ADD = "quick_add"
        const val SOURCE_HEALTH = "health"
        const val SOURCE_TIMER = "timer"
        const val SOURCE_IMPORT = "import"
        /** Planned ahead. Stays on the row once done, so un-ticking puts it back to planned. */
        const val SOURCE_PLAN = "plan"
    }
}

enum class BudgetPeriod(val key: String) {
    WEEK("week"), MONTH("month"), TRIP("trip");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: MONTH
    }
}

/**
 * A limit or a target for an area over a period. Money budgets use [METRIC_SPEND] (optionally for
 * one [category]); other areas use it for targets, like workouts per week.
 */
data class Budget(
    val id: String,
    val area: PlanArea,
    val metric: String,
    val category: String? = null,
    val amount: Double,
    val currency: String? = null,
    val period: BudgetPeriod,
    val tripId: String? = null,
) {
    companion object {
        const val METRIC_SPEND = "spend"
        const val METRIC_WORKOUTS = "workouts"
    }
}

data class Trip(
    val id: String,
    val destination: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val budget: Double? = null,
    val currency: String? = null,
    val travelMode: Boolean = true,
    val notes: String? = null,
)

enum class TripItemKind(val key: String) {
    TODO("todo"), DAY("day"),
    /** A flight, hotel or train pasted from a confirmation; its details are in the notes. */
    BOOKING("booking");

    companion object {
        fun fromKey(key: String) = fromKeyOrNull(key) ?: TODO

        /** Null for a kind this version does not know, so a newer row is left out rather than shown as a to-do. */
        fun fromKeyOrNull(key: String) = entries.firstOrNull { it.key == key }
    }
}

data class TripItem(
    val id: String,
    val tripId: String,
    val kind: TripItemKind,
    val title: String,
    val notes: String? = null,
    val date: LocalDate? = null,
    val isDone: Boolean = false,
    val sortOrder: Int = 0,
)
