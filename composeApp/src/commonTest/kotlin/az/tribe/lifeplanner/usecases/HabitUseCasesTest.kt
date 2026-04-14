package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.HabitFrequency
import az.tribe.lifeplanner.testutil.FakeHabitRepository
import az.tribe.lifeplanner.testutil.testHabit
import az.tribe.lifeplanner.testutil.testHabitCheckIn
import az.tribe.lifeplanner.usecases.habit.*
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.*

class HabitUseCasesTest {

    private lateinit var repo: FakeHabitRepository

    private val today: LocalDate
        get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    @BeforeTest
    fun setUp() {
        repo = FakeHabitRepository()
    }

    // ── CreateHabitUseCase ───────────────────────────────────────────

    @Test
    fun `CreateHabit inserts a habit`() = runTest {
        val useCase = CreateHabitUseCase(repo)
        val habit = testHabit()

        useCase(habit)

        val all = repo.getAllHabits()
        assertEquals(1, all.size)
        assertEquals(habit, all.first())
    }

    @Test
    fun `CreateHabit preserves all habit fields`() = runTest {
        val useCase = CreateHabitUseCase(repo)
        val habit = testHabit(
            id = "h1",
            title = "Meditate",
            category = GoalCategory.SPIRITUAL,
            frequency = HabitFrequency.DAILY,
            linkedGoalId = "g1"
        )

        useCase(habit)

        val stored = repo.getAllHabits().first()
        assertEquals("Meditate", stored.title)
        assertEquals(GoalCategory.SPIRITUAL, stored.category)
        assertEquals(HabitFrequency.DAILY, stored.frequency)
        assertEquals("g1", stored.linkedGoalId)
    }

    @Test
    fun `CreateHabit allows multiple habits`() = runTest {
        val useCase = CreateHabitUseCase(repo)

        useCase(testHabit(id = "h1"))
        useCase(testHabit(id = "h2"))
        useCase(testHabit(id = "h3"))

        assertEquals(3, repo.getAllHabits().size)
    }

    // ── UpdateHabitUseCase ───────────────────────────────────────────

    @Test
    fun `UpdateHabit updates an existing habit`() = runTest {
        val useCase = UpdateHabitUseCase(repo)
        repo.setHabits(listOf(testHabit(id = "h1", title = "Old")))

        useCase(testHabit(id = "h1", title = "New"))

        assertEquals("New", repo.getAllHabits().first().title)
    }

    @Test
    fun `UpdateHabit does nothing for nonexistent habit`() = runTest {
        val useCase = UpdateHabitUseCase(repo)
        repo.setHabits(listOf(testHabit(id = "h1", title = "Original")))

        useCase(testHabit(id = "nonexistent", title = "Nope"))

        assertEquals(1, repo.getAllHabits().size)
        assertEquals("Original", repo.getAllHabits().first().title)
    }

    @Test
    fun `UpdateHabit can change category`() = runTest {
        val useCase = UpdateHabitUseCase(repo)
        repo.setHabits(listOf(testHabit(id = "h1", category = GoalCategory.PHYSICAL)))

        useCase(testHabit(id = "h1", category = GoalCategory.CAREER))

        assertEquals(GoalCategory.CAREER, repo.getAllHabits().first().category)
    }

    // ── DeleteHabitUseCase ───────────────────────────────────────────

    @Test
    fun `DeleteHabit removes habit by id`() = runTest {
        val useCase = DeleteHabitUseCase(repo)
        repo.setHabits(listOf(testHabit(id = "h1"), testHabit(id = "h2")))

        useCase("h1")

        assertEquals(1, repo.getAllHabits().size)
        assertEquals("h2", repo.getAllHabits().first().id)
    }

    @Test
    fun `DeleteHabit does nothing for nonexistent id`() = runTest {
        val useCase = DeleteHabitUseCase(repo)
        repo.setHabits(listOf(testHabit(id = "h1")))

        useCase("nonexistent")

        assertEquals(1, repo.getAllHabits().size)
    }

    @Test
    fun `DeleteHabit on empty repository does not crash`() = runTest {
        val useCase = DeleteHabitUseCase(repo)

        useCase("any-id")

        assertTrue(repo.getAllHabits().isEmpty())
    }

    // ── CheckInHabitUseCase ──────────────────────────────────────────

    @Test
    fun `CheckInHabit creates a check-in with default date`() = runTest {
        val useCase = CheckInHabitUseCase(repo)
        repo.setHabits(listOf(testHabit(id = "h1")))

        val checkIn = useCase("h1")

        assertEquals("h1", checkIn.habitId)
        assertEquals(today, checkIn.date)
        assertTrue(checkIn.completed)
    }

    @Test
    fun `CheckInHabit creates a check-in with custom date`() = runTest {
        val useCase = CheckInHabitUseCase(repo)
        repo.setHabits(listOf(testHabit(id = "h1")))
        val customDate = LocalDate(2026, 1, 15)

        val checkIn = useCase("h1", date = customDate)

        assertEquals(customDate, checkIn.date)
    }

    @Test
    fun `CheckInHabit creates a check-in with notes`() = runTest {
        val useCase = CheckInHabitUseCase(repo)
        repo.setHabits(listOf(testHabit(id = "h1")))

        val checkIn = useCase("h1", notes = "Felt great today")

        assertEquals("Felt great today", checkIn.notes)
    }

    @Test
    fun `CheckInHabit creates a check-in with custom date and notes`() = runTest {
        val useCase = CheckInHabitUseCase(repo)
        repo.setHabits(listOf(testHabit(id = "h1")))
        val customDate = LocalDate(2026, 2, 28)

        val checkIn = useCase("h1", date = customDate, notes = "Good session")

        assertEquals(customDate, checkIn.date)
        assertEquals("Good session", checkIn.notes)
    }

    @Test
    fun `CheckInHabit default notes is empty`() = runTest {
        val useCase = CheckInHabitUseCase(repo)
        repo.setHabits(listOf(testHabit(id = "h1")))

        val checkIn = useCase("h1")

        assertEquals("", checkIn.notes)
    }

    // ── UncheckHabitUseCase ──────────────────────────────────────────

    @Test
    fun `UncheckHabit returns true when check-in exists`() = runTest {
        val useCase = UncheckHabitUseCase(repo)
        repo.setHabits(listOf(testHabit(id = "h1")))
        repo.setCheckIns(listOf(testHabitCheckIn(id = "ci1", habitId = "h1", date = today)))

        val result = useCase("h1", today)

        assertTrue(result)
    }

    @Test
    fun `UncheckHabit returns false when no check-in exists`() = runTest {
        val useCase = UncheckHabitUseCase(repo)
        repo.setHabits(listOf(testHabit(id = "h1")))

        val result = useCase("h1", today)

        assertFalse(result)
    }

    @Test
    fun `UncheckHabit deletes the check-in record`() = runTest {
        val useCase = UncheckHabitUseCase(repo)
        repo.setHabits(listOf(testHabit(id = "h1")))
        repo.setCheckIns(listOf(testHabitCheckIn(id = "ci1", habitId = "h1", date = today)))

        useCase("h1", today)

        val remaining = repo.getCheckInsByHabitId("h1")
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun `UncheckHabit returns false for wrong date`() = runTest {
        val useCase = UncheckHabitUseCase(repo)
        repo.setHabits(listOf(testHabit(id = "h1")))
        repo.setCheckIns(listOf(testHabitCheckIn(id = "ci1", habitId = "h1", date = LocalDate(2026, 1, 1))))

        val result = useCase("h1", today)

        assertFalse(result)
    }

    // ── GetAllHabitsUseCase ──────────────────────────────────────────

    @Test
    fun `GetAllHabits returns all habits`() = runTest {
        val useCase = GetAllHabitsUseCase(repo)
        repo.setHabits(listOf(testHabit(id = "h1"), testHabit(id = "h2")))

        val result = useCase()

        assertEquals(2, result.size)
    }

    @Test
    fun `GetAllHabits returns empty list when no habits`() = runTest {
        val useCase = GetAllHabitsUseCase(repo)

        val result = useCase()

        assertTrue(result.isEmpty())
    }

    // ── GetHabitsByGoalUseCase ────────────────────────────────────────

    @Test
    fun `GetHabitsByGoal returns habits linked to goal`() = runTest {
        val useCase = GetHabitsByGoalUseCase(repo)
        repo.setHabits(listOf(
            testHabit(id = "h1", linkedGoalId = "g1"),
            testHabit(id = "h2", linkedGoalId = "g2"),
            testHabit(id = "h3", linkedGoalId = "g1")
        ))

        val result = useCase("g1")

        assertEquals(2, result.size)
        assertTrue(result.all { it.linkedGoalId == "g1" })
    }

    @Test
    fun `GetHabitsByGoal returns empty when no habits linked`() = runTest {
        val useCase = GetHabitsByGoalUseCase(repo)
        repo.setHabits(listOf(testHabit(id = "h1", linkedGoalId = "g2")))

        val result = useCase("g1")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `GetHabitsByGoal returns empty from empty repository`() = runTest {
        val useCase = GetHabitsByGoalUseCase(repo)

        val result = useCase("g1")

        assertTrue(result.isEmpty())
    }

    // ── GetHabitsWithTodayStatusUseCase ───────────────────────────────

    @Test
    fun `GetHabitsWithTodayStatus returns habits with completion status`() = runTest {
        val useCase = GetHabitsWithTodayStatusUseCase(repo)
        repo.setHabits(listOf(testHabit(id = "h1"), testHabit(id = "h2")))
        repo.setCheckIns(listOf(testHabitCheckIn(habitId = "h1", date = today)))

        val result = useCase(today)

        assertEquals(2, result.size)
        val h1Status = result.find { it.first.id == "h1" }
        val h2Status = result.find { it.first.id == "h2" }
        assertNotNull(h1Status)
        assertNotNull(h2Status)
        assertTrue(h1Status.second)
        assertFalse(h2Status.second)
    }

    @Test
    fun `GetHabitsWithTodayStatus returns empty for no habits`() = runTest {
        val useCase = GetHabitsWithTodayStatusUseCase(repo)

        val result = useCase(today)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `GetHabitsWithTodayStatus all unchecked when no check-ins`() = runTest {
        val useCase = GetHabitsWithTodayStatusUseCase(repo)
        repo.setHabits(listOf(testHabit(id = "h1"), testHabit(id = "h2")))

        val result = useCase(today)

        assertTrue(result.all { !it.second })
    }
}
