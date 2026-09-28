package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.Trip
import az.tribe.lifeplanner.domain.model.TripItem
import az.tribe.lifeplanner.domain.model.TripItemKind
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/** One day of forecast for a trip day. */
data class DayForecast(val date: LocalDate, val maxC: Int, val minC: Int, val rainChance: Int?, val wmoCode: Int)

/** The pure parts of trips, so they can be tested without a device or the network. */
object TripPlanner {

    /** Where a trip spend went, from what it was called. Every trip spend is filed as "travel" in Money. */
    enum class SpendKind(val label: String, val words: List<String>) {
        FLIGHTS("Flights", listOf("flight", "fly", "plane", "airline")),
        STAY("Stay", listOf("hotel", "airbnb", "hostel", "stay", "room", "booking")),
        GETTING_AROUND("Getting around", listOf("train", "taxi", "uber", "bus", "metro", "ferry", "car", "fuel", "esim")),
        FOOD("Food", listOf("food", "dinner", "lunch", "breakfast", "coffee", "restaurant", "meal")),
        FUN("Fun", listOf("museum", "tour", "ticket", "show", "fun", "gift", "shopping")),
        OTHER("Other", emptyList());

        companion object {
            /** From the name, or the "Stay: Park Hotel" prefix [titleFor] adds when the name alone would not say. */
            fun of(title: String): SpendKind {
                val t = title.lowercase().trim()
                entries.firstOrNull { t == it.label.lowercase() || t.startsWith(it.label.lowercase() + ":") }?.let { return it }
                return entries.firstOrNull { k -> k.words.any { it in t } } ?: OTHER
            }

            /** What to call a spend so [of] files it where the user put it. */
            fun titleFor(kind: SpendKind, note: String): String {
                val n = note.trim()
                return when {
                    n.isEmpty() -> kind.label
                    of(n) == kind -> n
                    else -> "${kind.label}: $n"
                }
            }
        }
    }

    fun days(trip: Trip): List<LocalDate> {
        val n = (trip.endDate.toEpochDays() - trip.startDate.toEpochDays()).toInt().coerceIn(0, 60)
        return (0..n).map { trip.startDate.plus(DatePeriod(days = it)) }
    }

    fun isActive(trip: Trip, today: LocalDate) = today >= trip.startDate && today <= trip.endDate

    fun isOver(trip: Trip, today: LocalDate) = today > trip.endDate

    /** The trip that matters now: the one under way, else the next one, else null. */
    fun current(trips: List<Trip>, today: LocalDate): Trip? =
        trips.firstOrNull { isActive(it, today) } ?: trips.filter { it.startDate > today }.minByOrNull { it.startDate }

    /** "10 to 18 October", or "28 September to 3 October" across months. */
    fun range(trip: Trip): String {
        val s = trip.startDate
        val e = trip.endDate
        return when {
            s == e -> "${s.day} ${monthName(s.month)}"
            s.month == e.month && s.year == e.year -> "${s.day} to ${e.day} ${monthName(e.month)}"
            else -> "${s.day} ${monthName(s.month)} to ${e.day} ${monthName(e.month)}"
        }
    }

    /** "in 12 days", "tomorrow", "day 3 of 9", "ended". */
    fun countdown(trip: Trip, today: LocalDate): String {
        val until = (trip.startDate.toEpochDays() - today.toEpochDays()).toInt()
        val total = days(trip).size
        return when {
            until > 1 -> "in $until days"
            until == 1 -> "tomorrow"
            isActive(trip, today) -> "day ${(today.toEpochDays() - trip.startDate.toEpochDays()).toInt() + 1} of $total"
            else -> "ended"
        }
    }

    /** What every trip starts with in "Before you go". Editable after. */
    fun defaultChecklist(tripId: String, abroad: Boolean = true): List<TripItem> = buildList {
        if (abroad) add("Check your passport or ID")
        add("Book getting to the airport or station")
        if (abroad) add("Get an eSIM or roaming")
        add("Pack")
    }.mapIndexed { i, title -> TripItem(id = "${tripId}_todo_$i", tripId = tripId, kind = TripItemKind.TODO, title = title, sortOrder = i) }

    /** The packing line from the forecast: "light jacket, umbrella". Null without a forecast. */
    fun packHint(forecast: List<DayForecast>): String? {
        if (forecast.isEmpty()) return null
        val hi = forecast.maxOf { it.maxC }
        val lo = forecast.minOf { it.minC }
        val rain = forecast.any { (it.rainChance ?: 0) >= 50 || it.wmoCode in 51..67 || it.wmoCode in 80..82 }
        val snow = forecast.any { it.wmoCode in 71..77 || it.wmoCode in 85..86 }
        val items = buildList {
            when {
                lo <= 3 || snow -> add("a warm coat")
                lo <= 12 -> add("a light jacket")
            }
            if (hi >= 26) add("light clothes and sunscreen")
            if (rain) add("an umbrella")
        }
        return if (items.isEmpty()) "Mild weather, pack light" else "From the weather: " + items.joinToString(", ")
    }

    data class TripBudget(val spent: Double, val left: Double?, val byKind: List<Pair<SpendKind, Double>>)

    fun budget(trip: Trip, spends: List<LifeLog>): TripBudget {
        val sum = spends.sumOf { it.amount ?: 0.0 }
        val byKind = spends.groupBy { SpendKind.of(it.title) }
            .map { (k, v) -> k to v.sumOf { it.amount ?: 0.0 } }
            .sortedByDescending { it.second }
        return TripBudget(sum, trip.budget?.let { it - sum }, byKind)
    }

    /** Title-case a place name the user typed ("tokyo" -> "Tokyo"). */
    fun placeName(raw: String): String = raw.trim().split(' ').filter { it.isNotBlank() }
        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    fun monthName(m: Month) = m.name.lowercase().replaceFirstChar { it.uppercase() }
}
