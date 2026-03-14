package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.testutil.FakeJournalRepository
import az.tribe.lifeplanner.testutil.testJournalEntry
import az.tribe.lifeplanner.usecases.journal.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.*

class JournalUseCasesTest {

    private lateinit var repo: FakeJournalRepository

    @BeforeTest
    fun setUp() {
        repo = FakeJournalRepository()
    }

    // ── CreateJournalEntryUseCase ────────────────────────────────────

    @Test
    fun `CreateJournalEntry inserts an entry`() = runTest {
        val useCase = CreateJournalEntryUseCase(repo)
        val entry = testJournalEntry()

        useCase(entry)

        val all = repo.getAllEntries()
        assertEquals(1, all.size)
        assertEquals(entry, all.first())
    }

    @Test
    fun `CreateJournalEntry preserves all fields`() = runTest {
        val useCase = CreateJournalEntryUseCase(repo)
        val entry = testJournalEntry(
            id = "j1",
            title = "Gratitude",
            content = "I am grateful for...",
            mood = Mood.HAPPY,
            linkedGoalId = "g1",
            tags = listOf("morning", "gratitude")
        )

        useCase(entry)

        val stored = repo.getAllEntries().first()
        assertEquals("Gratitude", stored.title)
        assertEquals("I am grateful for...", stored.content)
        assertEquals(Mood.HAPPY, stored.mood)
        assertEquals("g1", stored.linkedGoalId)
        assertEquals(listOf("morning", "gratitude"), stored.tags)
    }

    @Test
    fun `CreateJournalEntry allows multiple entries`() = runTest {
        val useCase = CreateJournalEntryUseCase(repo)

        useCase(testJournalEntry(id = "j1"))
        useCase(testJournalEntry(id = "j2"))

        assertEquals(2, repo.getAllEntries().size)
    }

    // ── UpdateJournalEntryUseCase ────────────────────────────────────

    @Test
    fun `UpdateJournalEntry updates an existing entry`() = runTest {
        val useCase = UpdateJournalEntryUseCase(repo)
        repo.setEntries(listOf(testJournalEntry(id = "j1", title = "Old")))

        useCase(testJournalEntry(id = "j1", title = "New"))

        assertEquals("New", repo.getAllEntries().first().title)
    }

    @Test
    fun `UpdateJournalEntry can change mood`() = runTest {
        val useCase = UpdateJournalEntryUseCase(repo)
        repo.setEntries(listOf(testJournalEntry(id = "j1", mood = Mood.SAD)))

        useCase(testJournalEntry(id = "j1", mood = Mood.HAPPY))

        assertEquals(Mood.HAPPY, repo.getAllEntries().first().mood)
    }

    @Test
    fun `UpdateJournalEntry does nothing for nonexistent entry`() = runTest {
        val useCase = UpdateJournalEntryUseCase(repo)
        repo.setEntries(listOf(testJournalEntry(id = "j1", title = "Original")))

        useCase(testJournalEntry(id = "nonexistent", title = "Nope"))

        assertEquals(1, repo.getAllEntries().size)
        assertEquals("Original", repo.getAllEntries().first().title)
    }

    // ── DeleteJournalEntryUseCase ────────────────────────────────────

    @Test
    fun `DeleteJournalEntry removes entry by id`() = runTest {
        val useCase = DeleteJournalEntryUseCase(repo)
        repo.setEntries(listOf(testJournalEntry(id = "j1"), testJournalEntry(id = "j2")))

        useCase("j1")

        assertEquals(1, repo.getAllEntries().size)
        assertEquals("j2", repo.getAllEntries().first().id)
    }

    @Test
    fun `DeleteJournalEntry does nothing for nonexistent id`() = runTest {
        val useCase = DeleteJournalEntryUseCase(repo)
        repo.setEntries(listOf(testJournalEntry(id = "j1")))

        useCase("nonexistent")

        assertEquals(1, repo.getAllEntries().size)
    }

    @Test
    fun `DeleteJournalEntry on empty repository does not crash`() = runTest {
        val useCase = DeleteJournalEntryUseCase(repo)

        useCase("any-id")

        assertTrue(repo.getAllEntries().isEmpty())
    }

    // ── GetAllJournalEntriesUseCase ──────────────────────────────────

    @Test
    fun `GetAllJournalEntries returns all entries`() = runTest {
        val useCase = GetAllJournalEntriesUseCase(repo)
        repo.setEntries(listOf(
            testJournalEntry(id = "j1"),
            testJournalEntry(id = "j2"),
            testJournalEntry(id = "j3")
        ))

        val result = useCase()

        assertEquals(3, result.size)
    }

    @Test
    fun `GetAllJournalEntries returns empty list when no entries`() = runTest {
        val useCase = GetAllJournalEntriesUseCase(repo)

        val result = useCase()

        assertTrue(result.isEmpty())
    }

    // ── GetRecentJournalEntriesUseCase ───────────────────────────────

    @Test
    fun `GetRecentJournalEntries returns limited entries sorted by createdAt`() = runTest {
        val useCase = GetRecentJournalEntriesUseCase(repo)
        repo.setEntries(listOf(
            testJournalEntry(id = "j1", createdAt = LocalDateTime(2026, 3, 1, 10, 0)),
            testJournalEntry(id = "j2", createdAt = LocalDateTime(2026, 3, 5, 10, 0)),
            testJournalEntry(id = "j3", createdAt = LocalDateTime(2026, 3, 3, 10, 0))
        ))

        val result = useCase(limit = 2)

        assertEquals(2, result.size)
        // Sorted descending: j2 first, then j3
        assertEquals("j2", result[0].id)
        assertEquals("j3", result[1].id)
    }

    @Test
    fun `GetRecentJournalEntries uses default limit of 10`() = runTest {
        val useCase = GetRecentJournalEntriesUseCase(repo)
        val entries = (1..15).map {
            testJournalEntry(id = "j$it", createdAt = LocalDateTime(2026, 1, it.coerceAtMost(28), 10, 0))
        }
        repo.setEntries(entries)

        val result = useCase()

        assertEquals(10, result.size)
    }

    @Test
    fun `GetRecentJournalEntries returns all when fewer than limit`() = runTest {
        val useCase = GetRecentJournalEntriesUseCase(repo)
        repo.setEntries(listOf(testJournalEntry(id = "j1"), testJournalEntry(id = "j2")))

        val result = useCase(limit = 10)

        assertEquals(2, result.size)
    }

    @Test
    fun `GetRecentJournalEntries returns empty for empty repository`() = runTest {
        val useCase = GetRecentJournalEntriesUseCase(repo)

        val result = useCase()

        assertTrue(result.isEmpty())
    }

    // ── GetJournalEntriesByGoalUseCase ───────────────────────────────

    @Test
    fun `GetJournalEntriesByGoal returns entries linked to goal`() = runTest {
        val useCase = GetJournalEntriesByGoalUseCase(repo)
        repo.setEntries(listOf(
            testJournalEntry(id = "j1", linkedGoalId = "g1"),
            testJournalEntry(id = "j2", linkedGoalId = "g2"),
            testJournalEntry(id = "j3", linkedGoalId = "g1")
        ))

        val result = useCase("g1")

        assertEquals(2, result.size)
        assertTrue(result.all { it.linkedGoalId == "g1" })
    }

    @Test
    fun `GetJournalEntriesByGoal returns empty when no entries linked`() = runTest {
        val useCase = GetJournalEntriesByGoalUseCase(repo)
        repo.setEntries(listOf(testJournalEntry(id = "j1", linkedGoalId = "g2")))

        val result = useCase("g1")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `GetJournalEntriesByGoal returns empty from empty repository`() = runTest {
        val useCase = GetJournalEntriesByGoalUseCase(repo)

        val result = useCase("g1")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `GetJournalEntriesByGoal excludes entries with null linkedGoalId`() = runTest {
        val useCase = GetJournalEntriesByGoalUseCase(repo)
        repo.setEntries(listOf(
            testJournalEntry(id = "j1", linkedGoalId = null),
            testJournalEntry(id = "j2", linkedGoalId = "g1")
        ))

        val result = useCase("g1")

        assertEquals(1, result.size)
        assertEquals("j2", result.first().id)
    }

    @Test
    fun `GetRecentJournalEntries with limit 1 returns only the newest`() = runTest {
        val useCase = GetRecentJournalEntriesUseCase(repo)
        repo.setEntries(listOf(
            testJournalEntry(id = "j1", createdAt = LocalDateTime(2026, 3, 1, 10, 0)),
            testJournalEntry(id = "j2", createdAt = LocalDateTime(2026, 3, 6, 10, 0)),
            testJournalEntry(id = "j3", createdAt = LocalDateTime(2026, 3, 4, 10, 0))
        ))

        val result = useCase(limit = 1)

        assertEquals(1, result.size)
        assertEquals("j2", result.first().id)
    }
}
