package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.model.Trip
import az.tribe.lifeplanner.domain.model.TripItemKind
import az.tribe.lifeplanner.domain.service.Booking
import az.tribe.lifeplanner.domain.service.BookingKind
import az.tribe.lifeplanner.domain.service.Bookings
import az.tribe.lifeplanner.domain.service.CountryCurrency
import az.tribe.lifeplanner.domain.service.FxTable
import az.tribe.lifeplanner.domain.service.TripMeta
import az.tribe.lifeplanner.domain.service.TripPlanner
import az.tribe.lifeplanner.domain.service.TripPlanner.SpendKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TravelV5Test {

    private val fx = FxTable("EUR", mapOf("JPY" to 160.0, "USD" to 1.1))
    private val trip = Trip(id = "t", destination = "Tokyo", startDate = LocalDate(2026, 10, 10), endDate = LocalDate(2026, 10, 18), budget = 1600.0, currency = "EUR")

    private fun spend(title: String, amount: Double, currency: String, date: LocalDate, category: String = "travel") = LifeLog(
        id = "$title$date", area = PlanArea.MONEY, kind = LogKind.EXPENSE, title = title, amount = amount, currency = currency,
        category = category, occurredAt = LocalDateTime(date, LocalTime(12, 0)), tripId = "t",
    )

    @Test
    fun countryGivesTheLocalMoney() {
        assertEquals("JPY", CountryCurrency.of("jp"))
        assertEquals("EUR", CountryCurrency.of("PT"))
        assertEquals("AZN", CountryCurrency.of("AZ"))
        assertNull(CountryCurrency.of("ZZ"))
        assertNull(CountryCurrency.of(null))
    }

    @Test
    fun tripMetaKeepsWhateverElseIsInTheNotes() {
        val withCc = TripMeta.withCountry(trip, "JP")
        assertEquals("JPY", TripMeta.of(withCc).localCurrency)
        assertEquals("JP", TripMeta.of(withCc).countryCode)
        val noted = trip.copy(notes = "Bring the adapter")
        val both = TripMeta.withCountry(noted, "JP")
        assertEquals("JPY", TripMeta.of(both).localCurrency)
        assertTrue(both.notes!!.endsWith("Bring the adapter"))
        assertNull(TripMeta.of(noted).localCurrency)
    }

    @Test
    fun budgetConvertsLocalSpendsAndSortsFoodByCategory() {
        val b = TripPlanner.budget(
            trip,
            listOf(
                spend("Flights to Tokyo", 700.0, "EUR", LocalDate(2026, 9, 28)),
                spend("Ramen", 1600.0, "JPY", LocalDate(2026, 10, 11), category = "food"),
                spend("Temple", 20.0, "THB", LocalDate(2026, 10, 11)),
            ),
            fx,
        )
        assertEquals(710.0, b.spent, 1e-9)
        assertEquals(890.0, b.left!!, 1e-9)
        assertEquals(mapOf("THB" to 20.0), b.unconverted)
        assertEquals(listOf(SpendKind.FLIGHTS, SpendKind.FOOD), b.byKind.map { it.first })
    }

    @Test
    fun walletSpreadsWhatIsLeftOverTheDaysToGo() {
        val today = LocalDate(2026, 10, 12) // day 3 of 9, 7 days to go counting today
        val spends = listOf(
            spend("Flights", 900.0, "EUR", LocalDate(2026, 9, 1)),
            spend("Ramen", 1600.0, "JPY", today, category = "food"),
        )
        val w = assertNotNull(TripPlanner.wallet(trip, spends, today, fx))
        assertEquals(7, w.daysLeft)
        assertEquals(100.0, w.perDay, 1e-9)
        assertEquals(90.0, w.leftToday, 1e-9)
        assertEquals(690.0, w.leftTotal, 1e-9)
        assertNull(TripPlanner.wallet(trip, spends, LocalDate(2026, 10, 1), fx))
        assertNull(TripPlanner.wallet(trip.copy(budget = null), spends, today, fx))
    }

    @Test
    fun recapReadsLikeTheCard() {
        val spends = listOf(
            spend("Hotel Gracery", 800.0, "EUR", LocalDate(2026, 9, 1)),
            spend("Flights", 400.0, "EUR", LocalDate(2026, 9, 1)),
            spend("Ramen", 6400.0, "JPY", LocalDate(2026, 10, 12), category = "food"),
        )
        val planned = (10..15).map { LocalDate(2026, 10, it) }.toSet()
        val r = TripPlanner.recap(trip, spends, planned, 18, fx)
        assertEquals("Tokyo, 9 days, €1,240, mostly Stay, 18°C, 6 of 9 days planned", r.line)
        assertEquals(360.0, r.underBudget!!, 1e-9)
        assertTrue(r.shareText.startsWith("Tokyo, 10 to 18 October. 9 days, €1,240 spent (mostly Stay)"))
        assertTrue(TripPlanner.recapOnToday(trip, LocalDate(2026, 10, 19)))
        assertTrue(TripPlanner.recapOnToday(trip, LocalDate(2026, 10, 21)))
        assertTrue(!TripPlanner.recapOnToday(trip, LocalDate(2026, 10, 22)))
        assertTrue(!TripPlanner.recapOnToday(trip, LocalDate(2026, 10, 18)))
    }

    // ── Bookings ──

    private val flight = Booking(
        BookingKind.FLIGHT, "JL 42", from = "LHR", to = "HND",
        start = LocalDateTime(2026, 10, 9, 19, 5), end = LocalDateTime(2026, 10, 10, 15, 30), code = "ABC123", price = 920.0, currency = "EUR",
    )
    private val hotel = Booking(
        BookingKind.HOTEL, "Hotel Gracery Shinjuku", start = LocalDateTime(2026, 10, 10, 15, 0), end = LocalDateTime(2026, 10, 18, 11, 0),
        code = "4471-XK", price = 68000.0, currency = "JPY",
    )

    @Test
    fun bookingsShowOnTheDaysTheyTouch() {
        assertEquals(listOf("JL 42, LHR to HND, 19:05"), Bookings.linesFor(flight, LocalDate(2026, 10, 9)))
        assertEquals(listOf("JL 42 lands 15:30, HND"), Bookings.linesFor(flight, LocalDate(2026, 10, 10)))
        assertEquals(listOf("Hotel Gracery Shinjuku, check in 15:00"), Bookings.linesFor(hotel, LocalDate(2026, 10, 10)))
        assertEquals(listOf("Check out 11:00, Hotel Gracery Shinjuku"), Bookings.linesFor(hotel, LocalDate(2026, 10, 18)))
        assertTrue(Bookings.linesFor(hotel, LocalDate(2026, 10, 12)).isEmpty())
        assertEquals(SpendKind.STAY, BookingKind.HOTEL.spendKind)
    }

    @Test
    fun bookingsRoundTripThroughTheItemNotes() {
        assertEquals(flight, Bookings.decode(Bookings.encode(flight)))
        assertEquals(hotel, Bookings.decode(Bookings.encode(hotel)))
        assertNull(Bookings.decode("Pack the charger"))
        assertNull(Bookings.decode(null))
    }

    @Test
    fun readsTheAiAnswerLoosely() {
        val o = Json.parseToJsonElement(
            """{"type":"flight","name":"Japan Airlines","number":"JL 42","from":"LHR","to":"HND","start":"2026-10-09T19:05","end":"2026-10-10 15:30","confirmation":"ABC123","price":920,"currency":"eur"}""",
        ).jsonObject
        assertEquals(flight, Bookings.fromAi(o))
        val stay = Bookings.fromAi(Json.parseToJsonElement("""{"type":"stay","name":"Casa Azul","start":"2026-10-10","price":"1,200"}""").jsonObject)
        assertEquals(BookingKind.HOTEL, stay?.kind)
        assertEquals(1200.0, stay?.price)
        assertEquals(LocalDateTime(2026, 10, 10, 0, 0), stay?.start)
        assertNull(Bookings.fromAi(Json.parseToJsonElement("""{"type":"flight"}""").jsonObject))
    }

    @Test
    fun previewDatesReadBackWhatTheyShow() {
        val near = LocalDate(2026, 10, 10)
        assertEquals("10 Oct 15:00", Bookings.formatWhen(LocalDateTime(2026, 10, 10, 15, 0)))
        assertEquals("10 Oct", Bookings.formatWhen(LocalDateTime(2026, 10, 10, 0, 0)))
        assertEquals(LocalDateTime(2026, 10, 10, 15, 0), Bookings.parseWhen("10 Oct 15:00", near))
        assertEquals(LocalDateTime(2026, 10, 12, 9, 5), Bookings.parseWhen("oct 12 9:05", near))
        assertEquals(LocalDateTime(2027, 1, 2, 0, 0), Bookings.parseWhen("2 January", LocalDate(2026, 12, 20)))
        assertEquals(LocalDateTime(2026, 10, 10, 15, 0), Bookings.parseWhen("2026-10-10 15:00", near))
        assertNull(Bookings.parseWhen("soon", near))
    }

    @Test
    fun unknownItemKindsAreLeftOutNotShownAsTodos() {
        assertEquals(TripItemKind.BOOKING, TripItemKind.fromKeyOrNull("booking"))
        assertNull(TripItemKind.fromKeyOrNull("reservation"))
        assertEquals(TripItemKind.TODO, TripItemKind.fromKey("todo"))
    }
}
