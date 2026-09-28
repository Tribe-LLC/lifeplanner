package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.DayFacts
import az.tribe.lifeplanner.domain.service.LifeFacts
import az.tribe.lifeplanner.domain.service.LifeFactsMath
import az.tribe.lifeplanner.testutil.testGoal
import az.tribe.lifeplanner.testutil.testMilestone
import az.tribe.lifeplanner.ui.v4.life.V4LifeInsightsViewModel
import az.tribe.lifeplanner.ui.v4.today.CarryOver
import az.tribe.lifeplanner.ui.v4.today.DayItemType
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LifeFactsTest {
    // Sunday 27 Sep 2026; today is Monday 28.
    private val today = LocalDate(2026, 9, 28)
    private fun ago(n: Int) = today.minus(DatePeriod(days = n))

    private fun facts(days: List<DayFacts>, areas: Set<PlanArea> = setOf(PlanArea.HABITS), slipped: List<String> = emptyList()) =
        LifeFacts(today = today, days = days.sortedBy { it.date }, areas = areas, slipped = slipped)

    /** 20 past days where good sleep goes with more kept habits. */
    private fun sleepyMonth() = (1..20).map { n ->
        val good = n % 2 == 0
        DayFacts(ago(n), habitsDue = 4, habitsKept = if (good) 4 else 2, sleep = if (good) 7.5 else 5.5)
    } + DayFacts(today, habitsDue = 4, habitsKept = 1)

    @Test
    fun patternsWaitForTwoWeeksOfData() {
        val f = facts((1..5).map { DayFacts(ago(it), habitsDue = 2, habitsKept = 1) })
        assertEquals(9, LifeFactsMath.daysUntilPatterns(f))
        assertTrue(LifeFactsMath.patterns(f).isEmpty())
    }

    @Test
    fun sleepPatternIsFoundAndSizedRight() {
        val p = LifeFactsMath.patterns(facts(sleepyMonth()))
        assertEquals(0, LifeFactsMath.daysUntilPatterns(facts(sleepyMonth())))
        val first = p.first()
        // 100% kept vs 50% kept is 100% more.
        assertEquals("After 7 or more hours of sleep, you keep 100% more habits.", first.text)
        assertEquals(10, first.days)
    }

    @Test
    fun smallDifferencesAreNotPatterns() {
        val days = (1..20).map { n -> DayFacts(ago(n), habitsDue = 10, habitsKept = if (n % 2 == 0) 8 else 7, sleep = if (n % 2 == 0) 7.5 else 5.5) }
        assertTrue(LifeFactsMath.patterns(facts(days)).none { it.text.contains("sleep") })
    }

    @Test
    fun digestOnlyMentionsPickedAreas() {
        val days = (0..7).map { DayFacts(ago(it), habitsDue = 2, habitsKept = 1, workouts = 1, studyMin = 30) }
        val justHabits = LifeFactsMath.digest(facts(days))
        assertTrue(justHabits.contains("Habits: kept 7 of 14 due (50%)"))
        assertTrue(!justHabits.contains("Workouts") && !justHabits.contains("Study"))
        val more = LifeFactsMath.digest(facts(days, areas = setOf(PlanArea.HABITS, PlanArea.FITNESS, PlanArea.STUDY)))
        assertTrue(more.contains("Workouts: 7 done"))
        assertTrue(more.contains("Study: 3h 30m this week"))
        assertTrue(more.contains("Today so far: 1 of 2 habits"))
        assertEquals("", LifeFactsMath.digest(facts(emptyList())))
    }

    @Test
    fun openerPicksShortSleepBeforeSlips() {
        val tired = (1..3).map { DayFacts(ago(it), sleep = 5.0) }
        assertTrue(LifeFactsMath.opener(facts(tired, slipped = listOf("Read")), 9).text.startsWith("Three short nights"))
        val slipped = LifeFactsMath.opener(facts(emptyList(), slipped = listOf("Read")), 9)
        assertEquals("Read has slipped lately. Keep it, make it easier, or let it go?", slipped.text)
        assertEquals(2, slipped.replies.size)
    }

    @Test
    fun openerFallsBackByTimeOfDay() {
        assertTrue(LifeFactsMath.opener(facts(emptyList()), 8).text.startsWith("Morning"))
        assertEquals("How is the day going?", LifeFactsMath.opener(facts(emptyList()), 14).text)
        assertEquals("How did today go?", LifeFactsMath.opener(facts(emptyList()), 20).text)
    }

    @Test
    fun weekComparesWithTheWeekBefore() {
        val end = ago(1)
        val days = (1..14).map { n -> DayFacts(ago(n), habitsDue = 2, habitsKept = if (n <= 7) 2 else 1, mood = 4.0) } +
            DayFacts(ago(3), habitsDue = 3, habitsKept = 3)
        val w = LifeFactsMath.week(facts(days), end)
        assertEquals(ago(7), w.from)
        assertEquals(100, w.keptPct)
        assertEquals(50, w.keptPctBefore)
        assertEquals(3, w.bestDay?.habitsKept)
    }

    @Test
    fun reviewShowsFromSaturdayEveningToMonday() {
        val sat = LocalDate(2026, 9, 26)
        assertNull(V4LifeInsightsViewModel.reviewWeekEnd(sat, 17))
        assertEquals(LocalDate(2026, 9, 27), V4LifeInsightsViewModel.reviewWeekEnd(sat, 18))
        assertEquals(LocalDate(2026, 9, 27), V4LifeInsightsViewModel.reviewWeekEnd(LocalDate(2026, 9, 27), 9))
        assertEquals(LocalDate(2026, 9, 27), V4LifeInsightsViewModel.reviewWeekEnd(today, 9))
        assertNull(V4LifeInsightsViewModel.reviewWeekEnd(today.plus(DatePeriod(days = 1)), 9))
    }

    @Test
    fun formatting() {
        assertEquals("7h 30m", LifeFactsMath.hours(7.5))
        assertEquals("8h", LifeFactsMath.hours(8.0))
        assertEquals("45 min", LifeFactsMath.minutes(45))
        assertEquals("3.7", LifeFactsMath.oneDecimal(3.66))
    }

    // ── Carry over ──────────────────────────────────────────────────────────

    private fun plan(id: String, kind: LogKind, area: PlanArea, day: LocalDate, status: LogStatus = LogStatus.PLANNED, category: String? = null) =
        LifeLog(id = id, area = area, kind = kind, status = status, title = id, category = category, occurredAt = LocalDateTime(day, LocalTime(9, 0)), source = LifeLog.SOURCE_PLAN)

    @Test
    fun carriesOverdueStepsAndRecentMissedPlans() {
        val goal = testGoal(
            id = "g", title = "Run a 10k",
            milestones = listOf(
                testMilestone("m1", "Run 5k", dueDate = ago(1)),
                testMilestone("m2", "Done one", isCompleted = true, dueDate = ago(2)),
                testMilestone("m3", "Due today", dueDate = today),
                testMilestone("m4", "No date"),
            ),
        )
        val finished = testGoal(id = "g2", status = GoalStatus.COMPLETED, milestones = listOf(testMilestone("x", dueDate = ago(3))))
        val logs = listOf(
            plan("Legs", LogKind.WORKOUT, PlanArea.FITNESS, ago(2)),
            plan("Old legs", LogKind.WORKOUT, PlanArea.FITNESS, ago(9)),
            plan("Done arms", LogKind.WORKOUT, PlanArea.FITNESS, ago(1), status = LogStatus.DONE),
            plan("Maths", LogKind.STUDY, PlanArea.STUDY, ago(1), category = "block"),
            plan("Exam", LogKind.STUDY, PlanArea.STUDY, ago(1), category = "exam"),
            plan("Tonight", LogKind.WORKOUT, PlanArea.FITNESS, today),
        )
        val items = CarryOver.items(listOf(goal, finished), logs, today)
        assertEquals(listOf("s_m1", "w_Legs", "b_Maths"), items.map { it.key })
        assertEquals("Run a 10k, planned for yesterday", items[0].sub)
        assertEquals("Workout, planned 2 days ago", items[1].sub)
        assertEquals(DayItemType.STUDY, items[2].type)
    }
}
