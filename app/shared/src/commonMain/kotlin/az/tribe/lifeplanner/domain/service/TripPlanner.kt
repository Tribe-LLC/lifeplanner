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

            /** Like [of], falling back on the Money category for spends logged from quick add ("ramen 1200" is food). */
            fun of(log: LifeLog): SpendKind = of(log.title).takeIf { it != OTHER } ?: when (log.category) {
                "food" -> FOOD
                "transport" -> GETTING_AROUND
                "fun", "shopping" -> FUN
                else -> OTHER
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

    /** A trip's money in its budget currency ([Trip.currency]); spends with no known rate are kept apart. */
    data class TripBudget(
        val spent: Double,
        val left: Double?,
        val byKind: List<Pair<SpendKind, Double>>,
        val unconverted: Map<String, Double> = emptyMap(),
    )

    fun budget(trip: Trip, spends: List<LifeLog>, fx: FxTable? = null): TripBudget {
        val total = Fx.total(spends, trip.currency, fx)
        val byKind = spends.groupBy { SpendKind.of(it) }
            .map { (k, v) -> k to Fx.total(v, trip.currency, fx).amount }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
        return TripBudget(total.amount, trip.budget?.let { it - total.amount }, byKind, total.unconverted)
    }

    /**
     * What is left to spend today on a trip under way, in the budget currency: what was left this
     * morning spread over the days still to go, less today's spends. Null before or after the trip,
     * or without a budget.
     */
    data class Wallet(val leftToday: Double, val perDay: Double, val leftTotal: Double, val daysLeft: Int)

    fun wallet(trip: Trip, spends: List<LifeLog>, today: LocalDate, fx: FxTable? = null): Wallet? {
        val budget = trip.budget ?: return null
        if (!isActive(trip, today)) return null
        val before = Fx.total(spends.filter { it.date < today }, trip.currency, fx).amount
        val todays = Fx.total(spends.filter { it.date == today }, trip.currency, fx).amount
        val daysLeft = (trip.endDate.toEpochDays() - today.toEpochDays()).toInt() + 1
        val perDay = (budget - before) / daysLeft
        return Wallet(perDay - todays, perDay, budget - before - todays, daysLeft)
    }

    /** What a finished trip adds up to, for the recap card and its shared text. */
    data class Recap(
        val destination: String,
        val range: String,
        val days: Int,
        val spent: Double,
        val currency: String?,
        val mostly: SpendKind?,
        val avgHigh: Int?,
        val plannedDays: Int,
        val underBudget: Double?,
    ) {
        /** "Tokyo, 9 days, €1,240, mostly Stay, 18°C, 6 of 9 days planned". */
        val line: String
            get() = listOfNotNull(
                "$destination, $days ${if (days == 1) "day" else "days"}",
                az.tribe.lifeplanner.core.MoneyFormat.format(spent, currency).takeIf { spent > 0 },
                mostly?.let { "mostly ${it.label}" },
                avgHigh?.let { "$it°C" },
                "$plannedDays of $days days planned",
            ).joinToString(", ")

        val shareText: String
            get() = "$destination, $range. " + listOfNotNull(
                "$days ${if (days == 1) "day" else "days"}",
                if (spent > 0) "${az.tribe.lifeplanner.core.MoneyFormat.format(spent, currency)} spent" + (mostly?.let { " (mostly ${it.label})" } ?: "") else null,
                avgHigh?.let { "$it°C on average" },
                "$plannedDays of $days days planned",
            ).joinToString(", ") + ". Planned with LifePlanner."
    }

    fun recap(trip: Trip, spends: List<LifeLog>, plannedDates: Set<LocalDate>, avgHigh: Int?, fx: FxTable? = null): Recap {
        val b = budget(trip, spends, fx)
        val all = days(trip)
        return Recap(
            destination = trip.destination,
            range = range(trip),
            days = all.size,
            spent = b.spent,
            currency = trip.currency,
            mostly = b.byKind.firstOrNull()?.first?.takeIf { it != SpendKind.OTHER },
            avgHigh = avgHigh,
            plannedDays = all.count { it in plannedDates },
            underBudget = b.left?.takeIf { it > 0 },
        )
    }

    /** Whether the recap belongs on Today: the three days after a trip ends. */
    fun recapOnToday(trip: Trip, today: LocalDate): Boolean {
        val since = (today.toEpochDays() - trip.endDate.toEpochDays()).toInt()
        return since in 1..3
    }

    /** Title-case a place name the user typed ("tokyo" -> "Tokyo"). */
    fun placeName(raw: String): String = raw.trim().split(' ').filter { it.isNotBlank() }
        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    fun monthName(m: Month) = m.name.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * Small facts about a trip kept in [Trip.notes] as "cc=JP;local=JPY": the destination's country and
 * the money spent there. Anything else already in the notes is kept as it was.
 */
data class TripMeta(val countryCode: String? = null, val localCurrency: String? = null, private val other: String? = null) {

    fun encode(): String? {
        val parts = listOfNotNull(countryCode?.let { "cc=$it" }, localCurrency?.let { "local=$it" })
        return if (parts.isEmpty()) other else (listOfNotNull(parts.joinToString(";"), other).joinToString("\n"))
    }

    companion object {
        fun of(trip: Trip): TripMeta = decode(trip.notes)

        fun decode(notes: String?): TripMeta {
            if (notes.isNullOrBlank()) return TripMeta()
            val first = notes.lineSequence().first()
            if (!first.startsWith("cc=") && !first.startsWith("local=")) return TripMeta(other = notes)
            val map = first.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            val rest = notes.lineSequence().drop(1).joinToString("\n").takeIf { it.isNotBlank() }
            return TripMeta(map["cc"], map["local"], rest)
        }

        /** The trip with its country and local money set. */
        fun withCountry(trip: Trip, countryCode: String?): Trip {
            val cc = countryCode?.uppercase() ?: return trip
            val m = of(trip)
            return trip.copy(notes = m.copy(countryCode = cc, localCurrency = CountryCurrency.of(cc)).encode())
        }
    }
}
