package az.tribe.lifeplanner.data.mapper

import az.tribe.lifeplanner.database.HabitCheckInEntity
import az.tribe.lifeplanner.database.HabitEntity
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.HabitFrequency
import az.tribe.lifeplanner.testutil.testHabit
import az.tribe.lifeplanner.testutil.testHabitCheckIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.*

class HabitMapperTest {

    // ── HabitEntity.toDomain ────────────────────────────────────────

    @Test
    fun `HabitEntity toDomain maps all basic fields`() {
        val entity = HabitEntity(
            id = "habit-1",
            title = "Morning Run",
            description = "Run 5k",
            category = "PHYSICAL",
            frequency = "DAILY",
            targetCount = 1L,
            currentStreak = 5L,
            longestStreak = 10L,
            totalCompletions = 30L,
            lastCompletedDate = "2026-03-05",
            linkedGoalId = "goal-1",
            correlationScore = 0.85,
            isActive = 1L,
            createdAt = "2026-01-01T08:00:00",
            reminderTime = "07:00",
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val habit = entity.toDomain()

        assertEquals("habit-1", habit.id)
        assertEquals("Morning Run", habit.title)
        assertEquals("Run 5k", habit.description)
        assertEquals(GoalCategory.PHYSICAL, habit.category)
        assertEquals(HabitFrequency.DAILY, habit.frequency)
        assertEquals(1, habit.targetCount)
        assertEquals(5, habit.currentStreak)
        assertEquals(10, habit.longestStreak)
        assertEquals(30, habit.totalCompletions)
        assertEquals(LocalDate(2026, 3, 5), habit.lastCompletedDate)
        assertEquals("goal-1", habit.linkedGoalId)
        assertEquals(0.85f, habit.correlationScore, 0.001f)
        assertTrue(habit.isActive)
        assertEquals(LocalDateTime(2026, 1, 1, 8, 0, 0), habit.createdAt)
        assertEquals("07:00", habit.reminderTime)
    }

    @Test
    fun `HabitEntity toDomain maps isActive 0 to false`() {
        val entity = HabitEntity(
            id = "h-inactive",
            title = "T",
            description = "",
            category = "CAREER",
            frequency = "WEEKLY",
            targetCount = 1L,
            currentStreak = 0L,
            longestStreak = 0L,
            totalCompletions = 0L,
            lastCompletedDate = null,
            linkedGoalId = null,
            correlationScore = 0.0,
            isActive = 0L,
            createdAt = "2026-01-01T00:00:00",
            reminderTime = null,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertFalse(entity.toDomain().isActive)
    }

    @Test
    fun `HabitEntity toDomain maps null lastCompletedDate`() {
        val entity = HabitEntity(
            id = "h-no-date",
            title = "T",
            description = "",
            category = "CAREER",
            frequency = "DAILY",
            targetCount = 1L,
            currentStreak = 0L,
            longestStreak = 0L,
            totalCompletions = 0L,
            lastCompletedDate = null,
            linkedGoalId = null,
            correlationScore = 0.0,
            isActive = 1L,
            createdAt = "2026-01-01T00:00:00",
            reminderTime = null,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertNull(entity.toDomain().lastCompletedDate)
    }

    @Test
    fun `HabitEntity toDomain maps null linkedGoalId`() {
        val entity = HabitEntity(
            id = "h-no-goal",
            title = "T",
            description = "",
            category = "CAREER",
            frequency = "DAILY",
            targetCount = 1L,
            currentStreak = 0L,
            longestStreak = 0L,
            totalCompletions = 0L,
            lastCompletedDate = null,
            linkedGoalId = null,
            correlationScore = 0.0,
            isActive = 1L,
            createdAt = "2026-01-01T00:00:00",
            reminderTime = null,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertNull(entity.toDomain().linkedGoalId)
    }

    @Test
    fun `HabitEntity toDomain maps null reminderTime`() {
        val entity = HabitEntity(
            id = "h-no-reminder",
            title = "T",
            description = "",
            category = "CAREER",
            frequency = "DAILY",
            targetCount = 1L,
            currentStreak = 0L,
            longestStreak = 0L,
            totalCompletions = 0L,
            lastCompletedDate = null,
            linkedGoalId = null,
            correlationScore = 0.0,
            isActive = 1L,
            createdAt = "2026-01-01T00:00:00",
            reminderTime = null,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertNull(entity.toDomain().reminderTime)
    }

    @Test
    fun `HabitEntity toDomain maps all HabitFrequency values`() {
        for (freq in HabitFrequency.entries) {
            val entity = HabitEntity(
                id = "h-${freq.name}",
                title = "T",
                description = "",
                category = "CAREER",
                frequency = freq.name,
                targetCount = 1L,
                currentStreak = 0L,
                longestStreak = 0L,
                totalCompletions = 0L,
                lastCompletedDate = null,
                linkedGoalId = null,
                correlationScore = 0.0,
                isActive = 1L,
                createdAt = "2026-01-01T00:00:00",
                reminderTime = null,
                sync_updated_at = null,
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
            )
            assertEquals(freq, entity.toDomain().frequency, "Failed for frequency $freq")
        }
    }

    @Test
    fun `HabitEntity toDomain falls back to DAILY for unknown frequency`() {
        val entity = HabitEntity(
            id = "h-unknown-freq",
            title = "T",
            description = "",
            category = "CAREER",
            frequency = "UNKNOWN_FREQ",
            targetCount = 1L,
            currentStreak = 0L,
            longestStreak = 0L,
            totalCompletions = 0L,
            lastCompletedDate = null,
            linkedGoalId = null,
            correlationScore = 0.0,
            isActive = 1L,
            createdAt = "2026-01-01T00:00:00",
            reminderTime = null,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertEquals(HabitFrequency.DAILY, entity.toDomain().frequency)
    }

    @Test
    fun `HabitEntity toDomain maps all GoalCategory values`() {
        for (category in GoalCategory.entries) {
            val entity = HabitEntity(
                id = "h-${category.name}",
                title = "T",
                description = "",
                category = category.name,
                frequency = "DAILY",
                targetCount = 1L,
                currentStreak = 0L,
                longestStreak = 0L,
                totalCompletions = 0L,
                lastCompletedDate = null,
                linkedGoalId = null,
                correlationScore = 0.0,
                isActive = 1L,
                createdAt = "2026-01-01T00:00:00",
                reminderTime = null,
                sync_updated_at = null,
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
            )
            assertEquals(category, entity.toDomain().category, "Failed for category $category")
        }
    }

    @Test
    fun `HabitEntity toDomain parses instant createdAt`() {
        val entity = HabitEntity(
            id = "h-tz",
            title = "T",
            description = "",
            category = "CAREER",
            frequency = "DAILY",
            targetCount = 1L,
            currentStreak = 0L,
            longestStreak = 0L,
            totalCompletions = 0L,
            lastCompletedDate = null,
            linkedGoalId = null,
            correlationScore = 0.0,
            isActive = 1L,
            createdAt = "2026-03-06T10:00:00Z",
            reminderTime = null,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertEquals(LocalDateTime(2026, 3, 6, 10, 0, 0), entity.toDomain().createdAt)
    }

    // ── Habit.toEntity ──────────────────────────────────────────────

    @Test
    fun `Habit toEntity maps all fields correctly`() {
        val habit = testHabit(
            id = "habit-1",
            title = "Meditate",
            description = "10 min daily",
            category = GoalCategory.EMOTIONAL,
            frequency = HabitFrequency.WEEKDAYS,
            targetCount = 1,
            currentStreak = 3,
            longestStreak = 14,
            totalCompletions = 50,
            lastCompletedDate = LocalDate(2026, 3, 5),
            linkedGoalId = "goal-99",
            correlationScore = 0.5f,
            isActive = true,
            createdAt = LocalDateTime(2026, 1, 15, 8, 0, 0),
            reminderTime = "08:00"
        )

        val entity = habit.toEntity()

        assertEquals("habit-1", entity.id)
        assertEquals("Meditate", entity.title)
        assertEquals("10 min daily", entity.description)
        assertEquals("EMOTIONAL", entity.category)
        assertEquals("WEEKDAYS", entity.frequency)
        assertEquals(1L, entity.targetCount)
        assertEquals(3L, entity.currentStreak)
        assertEquals(14L, entity.longestStreak)
        assertEquals(50L, entity.totalCompletions)
        assertEquals("2026-03-05", entity.lastCompletedDate)
        assertEquals("goal-99", entity.linkedGoalId)
        assertEquals(0.5, entity.correlationScore, 0.001)
        assertEquals(1L, entity.isActive)
        assertEquals("08:00", entity.reminderTime)
    }

    @Test
    fun `Habit toEntity maps isActive true to 1L`() {
        val habit = testHabit(isActive = true)
        assertEquals(1L, habit.toEntity().isActive)
    }

    @Test
    fun `Habit toEntity maps isActive false to 0L`() {
        val habit = testHabit(isActive = false)
        assertEquals(0L, habit.toEntity().isActive)
    }

    @Test
    fun `Habit toEntity maps null lastCompletedDate`() {
        val habit = testHabit(lastCompletedDate = null)
        assertNull(habit.toEntity().lastCompletedDate)
    }

    @Test
    fun `Habit toEntity maps null linkedGoalId`() {
        val habit = testHabit(linkedGoalId = null)
        assertNull(habit.toEntity().linkedGoalId)
    }

    @Test
    fun `Habit toEntity maps null reminderTime`() {
        val habit = testHabit(reminderTime = null)
        assertNull(habit.toEntity().reminderTime)
    }

    @Test
    fun `Habit toEntity sets sync fields`() {
        val entity = testHabit().toEntity()
        assertNotNull(entity.sync_updated_at)
        assertEquals(0L, entity.is_deleted)
        assertEquals(0L, entity.sync_version)
        assertNull(entity.last_synced_at)
    }

    // ── Habit round trip ────────────────────────────────────────────

    @Test
    fun `Habit round trip preserves id`() {
        val original = testHabit(id = "rt-habit")
        val restored = original.toEntity().toDomain()
        assertEquals(original.id, restored.id)
    }

    @Test
    fun `Habit round trip preserves title and description`() {
        val original = testHabit(title = "My Habit", description = "Details")
        val restored = original.toEntity().toDomain()
        assertEquals(original.title, restored.title)
        assertEquals(original.description, restored.description)
    }

    @Test
    fun `Habit round trip preserves category and frequency`() {
        val original = testHabit(category = GoalCategory.FAMILY, frequency = HabitFrequency.WEEKENDS)
        val restored = original.toEntity().toDomain()
        assertEquals(original.category, restored.category)
        assertEquals(original.frequency, restored.frequency)
    }

    @Test
    fun `Habit round trip preserves streak fields`() {
        val original = testHabit(currentStreak = 7, longestStreak = 30, totalCompletions = 100)
        val restored = original.toEntity().toDomain()
        assertEquals(original.currentStreak, restored.currentStreak)
        assertEquals(original.longestStreak, restored.longestStreak)
        assertEquals(original.totalCompletions, restored.totalCompletions)
    }

    @Test
    fun `Habit round trip preserves lastCompletedDate`() {
        val original = testHabit(lastCompletedDate = LocalDate(2026, 3, 5))
        val restored = original.toEntity().toDomain()
        assertEquals(original.lastCompletedDate, restored.lastCompletedDate)
    }

    @Test
    fun `Habit round trip preserves null lastCompletedDate`() {
        val original = testHabit(lastCompletedDate = null)
        val restored = original.toEntity().toDomain()
        assertNull(restored.lastCompletedDate)
    }

    @Test
    fun `Habit round trip preserves isActive`() {
        val original = testHabit(isActive = false)
        val restored = original.toEntity().toDomain()
        assertEquals(original.isActive, restored.isActive)
    }

    @Test
    fun `Habit round trip preserves correlationScore`() {
        val original = testHabit(correlationScore = 0.95f)
        val restored = original.toEntity().toDomain()
        assertEquals(original.correlationScore, restored.correlationScore, 0.01f)
    }

    @Test
    fun `Habit round trip preserves targetCount`() {
        val original = testHabit(targetCount = 5)
        val restored = original.toEntity().toDomain()
        assertEquals(original.targetCount, restored.targetCount)
    }

    // ── HabitCheckInEntity.toDomain ─────────────────────────────────

    @Test
    fun `HabitCheckInEntity toDomain maps all fields`() {
        val entity = HabitCheckInEntity(
            id = "ci-1",
            habitId = "habit-1",
            date = "2026-03-06",
            completed = 1L,
            notes = "Great session",
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val checkIn = entity.toDomain()

        assertEquals("ci-1", checkIn.id)
        assertEquals("habit-1", checkIn.habitId)
        assertEquals(LocalDate(2026, 3, 6), checkIn.date)
        assertTrue(checkIn.completed)
        assertEquals("Great session", checkIn.notes)
    }

    @Test
    fun `HabitCheckInEntity toDomain maps completed 0 to false`() {
        val entity = HabitCheckInEntity(
            id = "ci-2",
            habitId = "habit-1",
            date = "2026-03-06",
            completed = 0L,
            notes = "",
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertFalse(entity.toDomain().completed)
    }

    // ── HabitCheckIn.toEntity ───────────────────────────────────────

    @Test
    fun `HabitCheckIn toEntity maps all fields`() {
        val checkIn = testHabitCheckIn(
            id = "ci-1",
            habitId = "habit-1",
            date = LocalDate(2026, 3, 6),
            completed = true,
            notes = "Done"
        )

        val entity = checkIn.toEntity()

        assertEquals("ci-1", entity.id)
        assertEquals("habit-1", entity.habitId)
        assertEquals("2026-03-06", entity.date)
        assertEquals(1L, entity.completed)
        assertEquals("Done", entity.notes)
    }

    @Test
    fun `HabitCheckIn toEntity maps completed false to 0L`() {
        val checkIn = testHabitCheckIn(completed = false)
        assertEquals(0L, checkIn.toEntity().completed)
    }

    @Test
    fun `HabitCheckIn toEntity maps completed true to 1L`() {
        val checkIn = testHabitCheckIn(completed = true)
        assertEquals(1L, checkIn.toEntity().completed)
    }

    @Test
    fun `HabitCheckIn toEntity sets sync fields`() {
        val entity = testHabitCheckIn().toEntity()
        assertNotNull(entity.sync_updated_at)
        assertEquals(0L, entity.is_deleted)
        assertEquals(0L, entity.sync_version)
        assertNull(entity.last_synced_at)
    }

    // ── HabitCheckIn round trip ─────────────────────────────────────

    @Test
    fun `HabitCheckIn round trip preserves id`() {
        val original = testHabitCheckIn(id = "rt-ci")
        val restored = original.toEntity().toDomain()
        assertEquals(original.id, restored.id)
    }

    @Test
    fun `HabitCheckIn round trip preserves habitId`() {
        val original = testHabitCheckIn(habitId = "h-42")
        val restored = original.toEntity().toDomain()
        assertEquals(original.habitId, restored.habitId)
    }

    @Test
    fun `HabitCheckIn round trip preserves date`() {
        val original = testHabitCheckIn(date = LocalDate(2026, 12, 25))
        val restored = original.toEntity().toDomain()
        assertEquals(original.date, restored.date)
    }

    @Test
    fun `HabitCheckIn round trip preserves completed`() {
        val original = testHabitCheckIn(completed = false)
        val restored = original.toEntity().toDomain()
        assertEquals(original.completed, restored.completed)
    }

    @Test
    fun `HabitCheckIn round trip preserves notes`() {
        val original = testHabitCheckIn(notes = "Important note")
        val restored = original.toEntity().toDomain()
        assertEquals(original.notes, restored.notes)
    }

    // ── List mappers ────────────────────────────────────────────────

    @Test
    fun `toDomainHabits maps empty list`() {
        assertTrue(emptyList<HabitEntity>().toDomainHabits().isEmpty())
    }

    @Test
    fun `toDomainHabits maps multiple entities`() {
        val entities = listOf(
            HabitEntity(
                id = "h1", title = "A", description = "", category = "CAREER",
                frequency = "DAILY", targetCount = 1L, currentStreak = 0L,
                longestStreak = 0L, totalCompletions = 0L, lastCompletedDate = null,
                linkedGoalId = null, correlationScore = 0.0, isActive = 1L,
                createdAt = "2026-01-01T00:00:00", reminderTime = null,
                sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
            ),
            HabitEntity(
                id = "h2", title = "B", description = "", category = "PHYSICAL",
                frequency = "WEEKLY", targetCount = 3L, currentStreak = 5L,
                longestStreak = 10L, totalCompletions = 20L, lastCompletedDate = "2026-03-01",
                linkedGoalId = "g1", correlationScore = 0.5, isActive = 0L,
                createdAt = "2026-01-01T00:00:00", reminderTime = "09:00",
                sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
            )
        )

        val result = entities.toDomainHabits()
        assertEquals(2, result.size)
        assertEquals("h1", result[0].id)
        assertEquals("h2", result[1].id)
        assertFalse(result[1].isActive)
    }

    @Test
    fun `toDomainCheckIns maps empty list`() {
        assertTrue(emptyList<HabitCheckInEntity>().toDomainCheckIns().isEmpty())
    }

    @Test
    fun `toDomainCheckIns maps multiple entities`() {
        val entities = listOf(
            HabitCheckInEntity("c1", "h1", "2026-03-01", 1L, "", null, 0L, 0L, null),
            HabitCheckInEntity("c2", "h1", "2026-03-02", 0L, "skipped", null, 0L, 0L, null)
        )

        val result = entities.toDomainCheckIns()
        assertEquals(2, result.size)
        assertTrue(result[0].completed)
        assertFalse(result[1].completed)
    }

    // ── createNewHabit ──────────────────────────────────────────────

    @Test
    fun `createNewHabit sets defaults correctly`() {
        val habit = createNewHabit(
            title = "Exercise",
            category = GoalCategory.PHYSICAL,
            frequency = HabitFrequency.DAILY
        )

        assertEquals("Exercise", habit.title)
        assertEquals("", habit.description)
        assertEquals(GoalCategory.PHYSICAL, habit.category)
        assertEquals(HabitFrequency.DAILY, habit.frequency)
        assertEquals(1, habit.targetCount)
        assertEquals(0, habit.currentStreak)
        assertEquals(0, habit.longestStreak)
        assertEquals(0, habit.totalCompletions)
        assertNull(habit.lastCompletedDate)
        assertNull(habit.linkedGoalId)
        assertEquals(0f, habit.correlationScore)
        assertTrue(habit.isActive)
        assertNull(habit.reminderTime)
    }

    @Test
    fun `createNewHabit uses provided optional parameters`() {
        val habit = createNewHabit(
            title = "Read",
            description = "Read 30 pages",
            category = GoalCategory.CAREER,
            frequency = HabitFrequency.WEEKDAYS,
            targetCount = 30,
            linkedGoalId = "goal-42",
            reminderTime = "21:00"
        )

        assertEquals("Read 30 pages", habit.description)
        assertEquals(30, habit.targetCount)
        assertEquals("goal-42", habit.linkedGoalId)
        assertEquals("21:00", habit.reminderTime)
    }

    @Test
    fun `createNewHabit generates unique ids`() {
        val h1 = createNewHabit(title = "A", category = GoalCategory.CAREER, frequency = HabitFrequency.DAILY)
        val h2 = createNewHabit(title = "B", category = GoalCategory.CAREER, frequency = HabitFrequency.DAILY)
        assertNotEquals(h1.id, h2.id)
    }

    // ── createNewCheckIn ────────────────────────────────────────────

    @Test
    fun `createNewCheckIn sets defaults correctly`() {
        val date = LocalDate(2026, 3, 6)
        val checkIn = createNewCheckIn(habitId = "h1", date = date)

        assertEquals("h1", checkIn.habitId)
        assertEquals(date, checkIn.date)
        assertTrue(checkIn.completed)
        assertEquals("", checkIn.notes)
    }

    @Test
    fun `createNewCheckIn uses provided optional parameters`() {
        val date = LocalDate(2026, 3, 6)
        val checkIn = createNewCheckIn(
            habitId = "h1",
            date = date,
            completed = false,
            notes = "Was sick"
        )

        assertFalse(checkIn.completed)
        assertEquals("Was sick", checkIn.notes)
    }

    @Test
    fun `createNewCheckIn generates unique ids`() {
        val date = LocalDate(2026, 3, 6)
        val c1 = createNewCheckIn(habitId = "h1", date = date)
        val c2 = createNewCheckIn(habitId = "h1", date = date)
        assertNotEquals(c1.id, c2.id)
    }
}
