package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.Aisle
import az.tribe.lifeplanner.domain.service.CoachContext
import az.tribe.lifeplanner.domain.service.CoachIngredient
import az.tribe.lifeplanner.domain.service.MealCoach
import az.tribe.lifeplanner.domain.service.MealNotes
import az.tribe.lifeplanner.domain.service.MealPlanner
import az.tribe.lifeplanner.domain.service.MealSlot
import az.tribe.lifeplanner.domain.service.MealWeek
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MealWeekTest {

    // A Monday.
    private val today = LocalDate(2026, 9, 28)

    private fun meal(id: String, title: String, date: LocalDate, slot: MealSlot, status: LogStatus = LogStatus.DONE, notes: String? = null, group: String? = null) = LifeLog(
        id = id, area = PlanArea.MEALS, kind = LogKind.MEAL, status = status, title = title, category = slot.key,
        occurredAt = LocalDateTime(date, slot.usualTime), notes = notes, externalId = group,
    )

    private fun ago(n: Int) = today.minus(DatePeriod(days = n))
    private fun ahead(n: Int) = today.plus(DatePeriod(days = n))

    // ── Repeat last week ─────────────────────────────────────────────────────

    @Test
    fun lastWeekLandsOnTheSameWeekdays() {
        val logs = listOf(
            meal("a", "Chilli", ago(7), MealSlot.DINNER),
            meal("b", "Stir fry", ago(5), MealSlot.DINNER, LogStatus.PLANNED),
            meal("c", "Oats", ago(1), MealSlot.BREAKFAST),
            meal("old", "Soup", ago(8), MealSlot.DINNER),
        )
        val copies = MealWeek.repeatLastWeek(logs, today)
        assertEquals(listOf("a" to today, "b" to ahead(2), "c" to ahead(6)), copies.map { it.source.id to it.date })
        assertEquals(MealSlot.BREAKFAST, copies.last().slot)
    }

    @Test
    fun repeatSkipsTakenSlotsSkippedMealsAndMealsEatenOut() {
        val spend = LifeLog(
            id = "s", area = PlanArea.MONEY, kind = LogKind.EXPENSE, title = "Ramen, lunch", amount = 12.5, category = "food",
            occurredAt = LocalDateTime(ago(6), MealSlot.LUNCH.usualTime), externalId = "g1",
        )
        val logs = listOf(
            meal("a", "Chilli", ago(7), MealSlot.DINNER),
            meal("taken", "Pasta", today, MealSlot.DINNER, LogStatus.PLANNED),
            meal("out", "Ramen", ago(6), MealSlot.LUNCH, group = "g1"), spend,
            meal("skip", "Salad", ago(4), MealSlot.LUNCH, LogStatus.SKIPPED),
            meal("ok", "Wrap", ago(3), MealSlot.LUNCH),
        )
        assertEquals(listOf("ok"), MealWeek.repeatLastWeek(logs, today).map { it.source.id })
    }

    // ── Rotation ─────────────────────────────────────────────────────────────

    @Test
    fun rotationPutsTheMostMadeFirstThenWhatWasEatenLately() {
        val logs = listOf(
            meal("1", "Chilli", ago(20), MealSlot.DINNER), meal("2", "Chilli", ago(13), MealSlot.DINNER), meal("3", "Chilli", ago(6), MealSlot.DINNER),
            meal("4", "Stir fry", ago(9), MealSlot.DINNER), meal("5", "stir fry", ago(2), MealSlot.DINNER),
            meal("6", "Shakshuka", ago(1), MealSlot.BREAKFAST),
            meal("7", "Fish tacos", ago(4), MealSlot.DINNER),
            meal("8", "Leftover chilli", ago(5), MealSlot.LUNCH, notes = MealNotes(leftoverOf = "3").encode()),
        )
        val r = MealWeek.rotation(logs)
        assertEquals(listOf("Chilli", "Stir fry", "Shakshuka", "Fish tacos"), r.map { it.name })
        assertEquals(3, r.first().times)
    }

    // ── What to buy ──────────────────────────────────────────────────────────

    @Test
    fun ingredientKeyDropsAmountsAndPrep() {
        assertEquals("garlic", MealWeek.ingredientKey("2 cloves garlic, minced"))
        assertEquals("beef mince", MealWeek.ingredientKey("500 g beef mince"))
        assertEquals("egg", MealWeek.ingredientKey("Eggs"))
        assertEquals("egg", MealWeek.ingredientKey("6 eggs"))
        assertEquals("tomato", MealWeek.ingredientKey("2 tomatoes"))
        assertEquals("milk", MealWeek.ingredientKey("1 l milk"))
        assertEquals("chopped tomato", MealWeek.ingredientKey("1 can chopped tomatoes"))
        assertEquals("olive oil", MealWeek.ingredientKey("2 tbsp olive oil"))
    }

    @Test
    fun onlyWhatIsMissingLandsOnTheList() {
        val items = listOf("1 onion, chopped", "2 cloves garlic", "500 g beef mince", "Salt", "Olive oil", "1 Onion")
        val fresh = MealWeek.toBuy(items, onList = listOf("Beef mince"), have = setOf("salt", "olive oil"))
        assertEquals(listOf("1 onion", "2 cloves garlic"), fresh)
    }

    @Test
    fun staplesAreNotAddedTwice() {
        assertEquals(listOf("Bread"), MealWeek.toBuy(MealWeek.DEFAULT_STAPLES, listOf("2 l milk", "6 eggs"), emptySet()))
    }

    @Test
    fun listsSurviveTheBudgetRow() {
        val text = MealWeek.encodeList(listOf(" Milk ", "Eggs", "milk", "", "Bread"))
        assertEquals(listOf("Milk", "Eggs", "Bread"), MealWeek.decodeList(text))
        assertEquals(emptyList(), MealWeek.decodeList(null))
    }

    @Test
    fun coachAisleIsUsedOnlyWhenTheWordsDoNotPlaceIt() {
        fun item(title: String, unit: String?) = LifeLog(
            id = title, area = PlanArea.MEALS, kind = LogKind.NOTE, status = LogStatus.PLANNED, title = title,
            category = MealPlanner.CATEGORY_SHOPPING, unit = unit, occurredAt = LocalDateTime(today, MealSlot.LUNCH.usualTime),
        )
        assertEquals(Aisle.DAIRY, MealPlanner.aisleOf(item("Halloumi", "DAIRY")))
        assertEquals(Aisle.PRODUCE, MealPlanner.aisleOf(item("2 onions", "PANTRY")))
        assertEquals(Aisle.OTHER, MealPlanner.aisleOf(item("Halloumi", null)))
    }

    // ── Plan my week ─────────────────────────────────────────────────────────

    private val dates = listOf(ahead(2), ahead(4), ahead(5))

    @Test
    fun coachDinnersLandOnTheEveningsAskedFor() {
        val raw = """
            {"dinners":[
              {"day":"${ahead(4)}","title":"Veggie fried rice.","minutes":20,"ingredients":[
                {"item":"rice","quantity":"300 g","aisle":"pantry"},{"item":"red pepper","quantity":"1","aisle":"produce"}]},
              {"day":"${ahead(2)}","title":"Chicken fajitas","minutes":"25 min","ingredients":[{"item":"chicken thighs","quantity":"500 g","aisle":"meat"}]},
              {"day":"2031-01-01","title":"Tomato soup","minutes":30,"ingredients":["2 cans tomatoes"]},
              {"day":"${ahead(5)}","title":"Extra one","minutes":10,"ingredients":[]}
            ]}
        """.trimIndent()
        val dinners = MealCoach.parse(raw, dates)
        assertEquals(listOf(ahead(2) to "Chicken fajitas", ahead(4) to "Veggie fried rice", ahead(5) to "Extra one"), dinners.map { it.date to it.title })
        assertEquals(25, dinners.first().minutes)
        assertEquals("300 g rice", dinners[1].ingredients.first().line)
        assertEquals(Aisle.PANTRY, dinners[1].ingredients.first().aisle)
    }

    @Test
    fun coachDinnersWithoutGoodDaysFillTheFreeEveningsInOrder() {
        val raw = """{"dinners":[{"title":"A","ingredients":[]},{"day":"nope","title":"B"},{"title":"C"},{"title":"D"},{"title":""}]}"""
        assertEquals(listOf(ahead(2) to "A", ahead(4) to "B", ahead(5) to "C"), MealCoach.parse(raw, dates).map { it.date to it.title })
    }

    @Test
    fun aBrokenAnswerIsNothingNotACrash() {
        assertEquals(emptyList(), MealCoach.parse("not json", dates))
        assertEquals(emptyList(), MealCoach.parse("""{"meals":[]}""", dates))
    }

    @Test
    fun promptCarriesWhatThePageKnows() {
        val p = MealCoach.prompt(dates, CoachContext(foodBudget = "€64 left for food this week", household = 2, likes = listOf("Chilli"), have = listOf("rice")), avoid = listOf("Pizza"))
        assertTrue("€64 left for food this week" in p)
        assertTrue("Cooking for 2 people" in p)
        assertTrue("Chilli" in p && "rice" in p && "Pizza" in p)
        assertTrue(ahead(2).toString() in p)
        assertEquals("2 red peppers", CoachIngredient("red peppers", "2").line)
        assertEquals(Aisle.DAIRY, MealCoach.aisleOf("Dairy"))
        assertEquals(Aisle.PRODUCE, MealCoach.aisleOf("fruit and veg"))
    }
}
