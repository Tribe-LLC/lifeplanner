package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.model.Trip
import az.tribe.lifeplanner.domain.service.DayForecast
import az.tribe.lifeplanner.domain.service.TravelMode
import az.tribe.lifeplanner.domain.service.TripPlanner
import az.tribe.lifeplanner.domain.service.TripPlanner.SpendKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripPlannerTest {

    private val today = LocalDate(2026, 9, 28)
    private fun trip(start: LocalDate, end: LocalDate, budget: Double? = null) =
        Trip(id = "t", destination = "Tokyo", startDate = start, endDate = end, budget = budget, currency = "EUR")

    @Test
    fun rangeAndCountdownReadLikeTheCanvas() {
        val t = trip(LocalDate(2026, 10, 10), LocalDate(2026, 10, 18))
        assertEquals("10 to 18 October", TripPlanner.range(t))
        assertEquals("in 12 days", TripPlanner.countdown(t, today))
        assertEquals("tomorrow", TripPlanner.countdown(t, LocalDate(2026, 10, 9)))
        assertEquals("day 3 of 9", TripPlanner.countdown(t, LocalDate(2026, 10, 12)))
        assertEquals("ended", TripPlanner.countdown(t, LocalDate(2026, 10, 19)))
        assertEquals("28 September to 3 October", TripPlanner.range(trip(today, LocalDate(2026, 10, 3))))
    }

    @Test
    fun currentPrefersTheTripUnderWay() {
        val later = trip(LocalDate(2026, 11, 1), LocalDate(2026, 11, 5)).copy(id = "later")
        val next = trip(LocalDate(2026, 10, 10), LocalDate(2026, 10, 12)).copy(id = "next")
        val now = trip(LocalDate(2026, 9, 27), LocalDate(2026, 9, 30)).copy(id = "now")
        assertEquals("next", TripPlanner.current(listOf(later, next), today)?.id)
        assertEquals("now", TripPlanner.current(listOf(later, next, now), today)?.id)
        assertNull(TripPlanner.current(listOf(trip(LocalDate(2026, 9, 1), LocalDate(2026, 9, 5))), today))
    }

    @Test
    fun budgetSplitsSpendsByWhatTheyWere() {
        fun spend(title: String, amount: Double) = LifeLog(
            id = title, area = PlanArea.MONEY, kind = LogKind.EXPENSE, title = title, amount = amount,
            category = "travel", occurredAt = LocalDateTime(today, LocalTime(9, 0)), tripId = "t",
        )
        val b = TripPlanner.budget(
            trip(LocalDate(2026, 10, 10), LocalDate(2026, 10, 18), budget = 2400.0),
            listOf(spend("Flights to Tokyo", 920.0), spend("Hotel Shinjuku", 640.0), spend("Airport train", 25.0)),
        )
        assertEquals(1585.0, b.spent)
        assertEquals(815.0, b.left)
        assertEquals(listOf(SpendKind.FLIGHTS, SpendKind.STAY, SpendKind.GETTING_AROUND), b.byKind.map { it.first })
    }

    @Test
    fun spendsStayWhereTheUserFiledThem() {
        assertEquals(SpendKind.GETTING_AROUND, SpendKind.of(SpendKind.titleFor(SpendKind.GETTING_AROUND, "")))
        assertEquals("JR pass", SpendKind.titleFor(SpendKind.OTHER, "JR pass"))
        assertEquals("Getting around: JR pass", SpendKind.titleFor(SpendKind.GETTING_AROUND, "JR pass"))
        assertEquals(SpendKind.GETTING_AROUND, SpendKind.of("Getting around: JR pass"))
        assertEquals("Hotel Gracery", SpendKind.titleFor(SpendKind.STAY, "Hotel Gracery"))
    }

    @Test
    fun packingFollowsTheWeather() {
        assertNull(TripPlanner.packHint(emptyList()))
        val cool = listOf(DayForecast(today, 19, 11, 70, 61))
        val hint = TripPlanner.packHint(cool)!!
        assertTrue("light jacket" in hint && "umbrella" in hint, hint)
        assertEquals("Mild weather, pack light", TripPlanner.packHint(listOf(DayForecast(today, 22, 15, 10, 1))))
    }

    @Test
    fun tripDaysNeitherKeepNorBreakAStreak() {
        val done = setOf(LocalDate(2026, 9, 28), LocalDate(2026, 9, 24), LocalDate(2026, 9, 23))
        val paused = setOf(LocalDate(2026, 9, 25), LocalDate(2026, 9, 26), LocalDate(2026, 9, 27))
        assertEquals(3, TravelMode.streak(done, paused, today))
        assertEquals(1, TravelMode.streak(done, emptySet(), today))
        assertEquals(0, TravelMode.streak(emptySet(), paused, today))
    }
}
