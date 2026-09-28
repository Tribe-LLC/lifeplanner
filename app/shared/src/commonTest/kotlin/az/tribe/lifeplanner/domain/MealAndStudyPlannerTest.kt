package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.MealPlanner
import az.tribe.lifeplanner.domain.service.MealSlot
import az.tribe.lifeplanner.domain.service.StudyKind
import az.tribe.lifeplanner.domain.service.StudyPlanner
import az.tribe.lifeplanner.domain.service.StudyTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MealAndStudyPlannerTest {

    private val today = LocalDate(2026, 9, 28)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = LocalDateTime(d, LocalTime(h, m))

    private fun meal(title: String, at: LocalDateTime, category: String? = null, group: String? = null, status: LogStatus = LogStatus.DONE, id: String = title + at) =
        LifeLog(id = id, area = PlanArea.MEALS, kind = LogKind.MEAL, status = status, title = title, category = category, occurredAt = at, externalId = group)

    private fun spend(amount: Double, at: LocalDateTime, category: String = "food", group: String? = null) =
        LifeLog(id = "s$amount$at", area = PlanArea.MONEY, kind = LogKind.EXPENSE, title = "x", amount = amount, category = category, occurredAt = at, externalId = group)

    @Test
    fun slotComesFromFilingThenTitleThenTime() {
        assertEquals(MealSlot.DINNER, MealPlanner.slotOf(meal("Soup", at(today, 8), category = "dinner")))
        assertEquals(MealSlot.LUNCH, MealPlanner.slotOf(meal("Ramen, lunch", at(today, 19))))
        assertEquals(MealSlot.BREAKFAST, MealPlanner.slotOf(meal("Oats", at(today, 7, 30))))
        assertEquals(MealSlot.DINNER, MealPlanner.slotOf(meal("Pasta", at(today, 20))))
        assertEquals(MealSlot.SNACK, MealPlanner.slotOf(meal("Apple", at(today, 16))))
        assertEquals("Ramen", MealPlanner.dishName(meal("Ramen, lunch", at(today, 13))))
        assertEquals("Lunch", MealPlanner.dishName(meal("Lunch", at(today, 13))))
    }

    @Test
    fun weekCountsHomeOutAndFood() {
        val logs = listOf(
            meal("Ramen, lunch", at(today, 13), group = "g1"), spend(12.5, at(today, 13), group = "g1"),
            meal("Oats", at(today, 8)),
            meal("Stir fry", at(today.minusDays(1), 19)),
            spend(40.0, at(today.minusDays(2), 10)),
            spend(9.0, at(today, 10), category = "transport"),
            meal("Tacos", at(today.plusDays(2), 19), category = "dinner", status = LogStatus.PLANNED),
        )
        val w = MealPlanner.week(logs, today)
        assertEquals(3, w.eaten)
        assertEquals(1, w.out)
        assertEquals(2, w.home)
        assertEquals(52.5, w.foodSpent)
        assertEquals(1, w.plannedDinners)
        assertEquals(2, w.perDay.last().second)
    }

    @Test
    fun favouritesAreTheRepeats() {
        val logs = listOf(
            meal("Oats", at(today, 8)), meal("Oats", at(today.minusDays(1), 8)), meal("Toast", at(today.minusDays(2), 8)),
            meal("Pasta", at(today, 20)),
        )
        assertEquals(listOf("Oats", "Toast"), MealPlanner.favourites(logs, MealSlot.BREAKFAST))
        assertEquals("Oats", MealPlanner.favourites(logs).first())
    }

    @Test
    fun shoppingListSplitsAndSkipsWhatIsThere() {
        val existing = listOf(
            LifeLog(id = "1", area = PlanArea.MEALS, kind = LogKind.NOTE, status = LogStatus.PLANNED, title = "Milk", category = MealPlanner.CATEGORY_SHOPPING, occurredAt = at(today, 9)),
        )
        assertEquals(listOf("Eggs", "2 onions", "Rice"), MealPlanner.parseShopping("eggs, milk and 2 onions\n- rice", existing))
        assertEquals(1, MealPlanner.shopping(existing).size)
    }

    private fun study(title: String, d: LocalDate, minutes: Int?, kind: StudyKind, status: LogStatus = LogStatus.DONE, notes: String? = null, id: String = "$title$d$kind") =
        LifeLog(id = id, area = PlanArea.STUDY, kind = LogKind.STUDY, status = status, title = title, durationMin = minutes, category = kind.key, occurredAt = at(d, 0), notes = notes)

    @Test
    fun studyWeekAddsLogsAndFocusTime() {
        val logs = listOf(
            study("Biology", today, 45, StudyKind.SESSION),
            study("Biology", today.minusDays(1), 30, StudyKind.BLOCK),
            study("Maths", today.minusDays(2), 60, StudyKind.BLOCK, status = LogStatus.PLANNED),
            study("Maths", today.plusDays(5), null, StudyKind.EXAM, status = LogStatus.PLANNED),
        )
        val times = StudyPlanner.times(logs, listOf(StudyTime(today.minusDays(3), 25, null)))
        val w = StudyPlanner.week(times, today, 300)
        assertEquals(100, w.minutes)
        assertEquals(listOf("Biology" to 75, "Other" to 25), w.bySubject)
        assertEquals(2, w.streakDays)
        assertEquals("in 5 days", StudyPlanner.countdown(today.plusDays(5), today))
        assertEquals(1, StudyPlanner.upcoming(logs, today).size)
        assertEquals("1h 40m", StudyPlanner.formatMinutes(100))
    }

    @Test
    fun blocksSpreadBeforeTheExamAndLeanLate() {
        val exam = today.plusDays(10)
        val days = StudyPlanner.spreadBefore(exam, today, 4)
        assertEquals(4, days.size)
        assertEquals(today.plusDays(9), days.last())
        assertTrue(days.all { it < exam && it >= today })
        assertEquals(days.size, days.toSet().size)
        val busy = setOf(today.plusDays(9))
        assertTrue(today.plusDays(9) !in StudyPlanner.spreadBefore(exam, today, 3, busy))
        assertEquals(3, StudyPlanner.spreadBefore(today.plusDays(2), today, 3).size)
        assertEquals(emptyList(), StudyPlanner.spreadBefore(today, today, 3))
    }

    private fun LocalDate.minusDays(n: Int) = LocalDate.fromEpochDays(toEpochDays() - n)
    private fun LocalDate.plusDays(n: Int) = LocalDate.fromEpochDays(toEpochDays() + n)
}

class MealExtrasTest {
    private val today = LocalDate(2026, 9, 28)

    @Test
    fun aislesAndLeftovers() {
        assertEquals(az.tribe.lifeplanner.domain.service.Aisle.PRODUCE, MealPlanner.aisleOf("2 red onions"))
        assertEquals(az.tribe.lifeplanner.domain.service.Aisle.DAIRY, MealPlanner.aisleOf("Eggs"))
        assertEquals(az.tribe.lifeplanner.domain.service.Aisle.PANTRY, MealPlanner.aisleOf("400g pasta"))
        assertEquals(az.tribe.lifeplanner.domain.service.Aisle.OTHER, MealPlanner.aisleOf("Birthday candles"))
        val tomorrow = LocalDate.fromEpochDays(today.toEpochDays() + 1)
        val slots = MealPlanner.leftoverSlots(today, MealSlot.DINNER, 2, setOf(tomorrow to MealSlot.DINNER))
        assertEquals(listOf(tomorrow to MealSlot.LUNCH, LocalDate.fromEpochDays(today.toEpochDays() + 2) to MealSlot.LUNCH), slots)
        assertEquals(listOf(today to MealSlot.DINNER), MealPlanner.leftoverSlots(today, MealSlot.LUNCH, 1, emptySet()))
    }

    @Test
    fun recipePagesBecomeDishes() {
        val html = """
            <html><head><title>Best Chili | Site</title>
            <script type="application/ld+json">{"@context":"https://schema.org","@graph":[{"@type":"WebPage","name":"x"},
            {"@type":["Recipe"],"name":"Weeknight Chili &amp; Rice","recipeIngredient":["1 onion","400g beans","2 tbsp oil"],
             "recipeYield":["4","4 servings"],"prepTime":"PT15M","cookTime":"PT45M","nutrition":{"calories":"520 kcal","proteinContent":"28 g"}}]}
            </script></head></html>
        """.trimIndent()
        val r = az.tribe.lifeplanner.domain.service.RecipeParser.parse(html, "https://x.test/chili")!!
        assertEquals("Weeknight Chili & Rice", r.name)
        assertEquals(3, r.ingredients.size)
        assertEquals(4, r.servings)
        assertEquals(60, r.minutes)
        assertEquals(520.0, r.kcalPerServing)
        assertEquals(28.0, r.proteinPerServing)
        assertEquals("Best Chili", az.tribe.lifeplanner.domain.service.RecipeParser.parse("<title>Best Chili | Site</title>")?.name)
        assertEquals(90, az.tribe.lifeplanner.domain.service.RecipeParser.isoMinutes("PT1H30M"))
    }
}

class MealNotesTest {
    @Test
    fun notesRoundTrip() {
        val n = az.tribe.lifeplanner.domain.service.MealNotes("https://x.test/r", 28.0, listOf("1 onion", "rice"), null)
        assertEquals(n, az.tribe.lifeplanner.domain.service.MealNotes.decode(n.encode()))
        assertEquals(az.tribe.lifeplanner.domain.service.MealNotes(), az.tribe.lifeplanner.domain.service.MealNotes.decode("just a note"))
    }
}

class StudyRolloverTest {
    private val today = LocalDate(2026, 9, 28)
    private fun d(n: Int) = LocalDate.fromEpochDays(today.toEpochDays() + n)
    private fun row(id: String, title: String, date: LocalDate, kind: StudyKind, status: LogStatus = LogStatus.PLANNED, notes: String? = null) =
        LifeLog(id = id, area = PlanArea.STUDY, kind = LogKind.STUDY, status = status, title = title, category = kind.key, occurredAt = LocalDateTime(date, LocalTime(0, 0)), notes = notes)

    @Test
    fun missedBlocksMoveBeforeTheirExam() {
        val exam = row("e", "Biology", d(5), StudyKind.EXAM)
        val logs = listOf(
            exam,
            row("b1", "Biology", d(-2), StudyKind.BLOCK, notes = "e"),
            row("b2", "Biology", d(-1), StudyKind.BLOCK, notes = "e"),
            row("b3", "Biology", d(4), StudyKind.BLOCK, notes = "e"),
            row("x", "Maths", d(-1), StudyKind.BLOCK),
            row("old", "History", d(-3), StudyKind.BLOCK, notes = "past"),
            row("past", "History", d(-1), StudyKind.EXAM),
        )
        val missed = StudyPlanner.missed(logs, today)
        assertEquals(listOf("old", "b1", "b2", "x"), missed.map { it.id })
        val moved = StudyPlanner.rollover(missed, logs, today)
        assertTrue(moved["b1"]!! < d(5) && moved["b2"]!! < d(5))
        assertTrue(moved["b1"] != d(4) && moved["b2"] != d(4))
        assertTrue(moved["x"]!! >= today)
        assertTrue("old" !in moved)
    }
}

class RealRecipeShoppingTest {
    private fun aisle(s: String) = MealPlanner.aisleOf(MealPlanner.shoppingItem(s)).name

    @Test
    fun aRealChiliRecipeSortsSensibly() {
        val expect = mapOf(
            "3 (15 ounce) cans chili beans, drained" to "PANTRY",
            "1 (6 ounce) can tomato paste" to "PANTRY",
            "2 (28 ounce) cans diced tomatoes with juice" to "PANTRY",
            "0.25 cup chili powder" to "PANTRY",
            "1 teaspoon dried basil" to "PANTRY",
            "1 teaspoon ground black pepper" to "PANTRY",
            "4 cubes beef bouillon" to "PANTRY",
            "1 tablespoon Worcestershire sauce" to "PANTRY",
            "2 green chile peppers, seeded and chopped" to "PRODUCE",
            "3 stalks celery, chopped" to "PRODUCE",
            "1 tablespoon minced garlic" to "PRODUCE",
            "2 pounds ground beef chuck" to "MEAT",
            "1 (8 ounce) package shredded Cheddar cheese" to "DAIRY",
            "0.5 cup beer" to "DRINKS",
        )
        expect.forEach { (line, a) -> assertEquals(a, aisle(line), line) }
        assertEquals("2 teaspoons hot pepper sauce", MealPlanner.shoppingItem("2 teaspoons hot pepper sauce (such as Tabasco®)"))
        assertEquals(listOf("3 (15 ounce) cans chili beans", "1 onion"), MealPlanner.parseShopping("3 (15 ounce) cans chili beans, drained\n1 onion, chopped", perLine = true))
    }
}

class StudyNamesTest {
    private fun due(title: String, kind: StudyKind) = LifeLog(id = title, area = PlanArea.STUDY, kind = LogKind.STUDY, title = title, category = kind.key, occurredAt = LocalDateTime(LocalDate(2026, 10, 1), LocalTime(0, 0)))

    @Test
    fun namesDoNotRepeatThemselves() {
        assertEquals("BIO101 final exam", StudyPlanner.dueName(due("BIO101 final exam", StudyKind.EXAM)))
        assertEquals("BIO101 midterm", StudyPlanner.dueLine(due("BIO101 midterm", StudyKind.EXAM)))
        assertEquals("Biology exam", StudyPlanner.dueLine(due("Biology", StudyKind.EXAM)))
        assertEquals("History essay due", StudyPlanner.dueLine(due("History essay", StudyKind.DEADLINE)))
        assertEquals("Due: History essay", StudyPlanner.dueEvent(due("History essay", StudyKind.DEADLINE)))
    }
}
