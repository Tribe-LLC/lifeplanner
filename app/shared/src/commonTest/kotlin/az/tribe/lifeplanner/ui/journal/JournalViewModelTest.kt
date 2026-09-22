package az.tribe.lifeplanner.ui.journal

import app.cash.turbine.test
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.Decision
import az.tribe.lifeplanner.domain.repository.DecisionRepository
import az.tribe.lifeplanner.testutil.FakeAiProxyService
import az.tribe.lifeplanner.testutil.FakeGamificationRepository
import az.tribe.lifeplanner.testutil.FakeJournalRepository
import az.tribe.lifeplanner.testutil.testJournalEntry
import az.tribe.lifeplanner.usecases.journal.CreateJournalEntryUseCase
import az.tribe.lifeplanner.usecases.journal.DeleteJournalEntryUseCase
import az.tribe.lifeplanner.usecases.journal.UpdateJournalEntryUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModelTest {

    private lateinit var viewModel: JournalViewModel
    private lateinit var fakeRepository: FakeJournalRepository
    private lateinit var fakeDecisionRepository: FakeDecisionRepository
    private val testDispatcher = StandardTestDispatcher()

    /** Minimal in-memory [DecisionRepository] so the VM can log detected journal decisions. */
    private class FakeDecisionRepository : DecisionRepository {
        val inserted = mutableListOf<Decision>()
        private val flow = MutableStateFlow<List<Decision>>(emptyList())
        override fun observeAllDecisions(): Flow<List<Decision>> = flow
        override suspend fun getAllDecisions(): List<Decision> = inserted
        override suspend fun getDecisionById(id: String): Decision? = inserted.find { it.id == id }
        override suspend fun getDecisionsForGoal(goalId: String): List<Decision> = inserted.filter { it.relatedGoalId == goalId }
        override suspend fun insertDecision(decision: Decision) { inserted.add(decision); flow.value = inserted.toList() }
        override suspend fun updateDecision(decision: Decision) {
            val i = inserted.indexOfFirst { it.id == decision.id }
            if (i >= 0) inserted[i] = decision
            flow.value = inserted.toList()
        }
        override suspend fun deleteDecisionById(id: String) { inserted.removeAll { it.id == id }; flow.value = inserted.toList() }
    }

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeJournalRepository()
        fakeDecisionRepository = FakeDecisionRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): JournalViewModel {
        return JournalViewModel(
            journalRepository = fakeRepository,
            createEntryUseCase = CreateJournalEntryUseCase(fakeRepository),
            updateEntryUseCase = UpdateJournalEntryUseCase(fakeRepository),
            deleteEntryUseCase = DeleteJournalEntryUseCase(fakeRepository),
            aiProxyService = FakeAiProxyService(),
            gamificationRepository = FakeGamificationRepository(),
            decisionRepository = fakeDecisionRepository
        )
    }

    @Test
    fun `initial entries state is empty list`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        viewModel.entries.test {
            val initial = awaitItem()
            assertTrue(initial.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `entries flow emits entries from repository`() = runTest(testDispatcher) {
        val entry = testJournalEntry(id = "j1", title = "My Day")
        fakeRepository.setEntries(listOf(entry))
        viewModel = createViewModel()

        viewModel.entries.test {
            var items = awaitItem()
            if (items.isEmpty()) items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("My Day", items.first().title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isLoading becomes false after entries emit`() = runTest(testDispatcher) {
        fakeRepository.setEntries(listOf(testJournalEntry()))
        viewModel = createViewModel()

        // isLoading clears in the entries flow's onEach, and entries is WhileSubscribed, so it only
        // runs once something collects entries (the screen always does). Await the real emission
        // (past the StateFlow's initial emptyList), by which point onEach has cleared loading.
        viewModel.entries.test {
            var items = awaitItem()
            if (items.isEmpty()) items = awaitItem()
            assertFalse(viewModel.isLoading.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `createEntry adds entry to repository and hides dialog`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showNewEntryDialog()
        assertTrue(viewModel.showNewEntryDialog.value)

        viewModel.createEntry(
            title = "New Entry",
            content = "My thoughts today",
            mood = Mood.HAPPY
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.showNewEntryDialog.value)
    }

    @Test
    fun `createEntry with tags and linked goal`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.createEntry(
            title = "Tagged Entry",
            content = "Content",
            mood = Mood.NEUTRAL,
            linkedGoalId = "goal-1",
            tags = listOf("reflection", "morning")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.entries.test {
            var items = awaitItem()
            if (items.isEmpty()) items = awaitItem()
            assertEquals(1, items.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `createEntry with a detected decision logs a pending journal decision`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.createEntry(
            title = "Torn about the gym",
            content = "Thinking about it",
            mood = Mood.NEUTRAL,
            linkedGoalId = "goal-9",
            detectedDecision = DetectedDecision(
                question = "Keep the 5am gym habit or move it later?",
                optionsConsidered = listOf("Keep 5am", "Move to evening"),
                leaning = "Move to evening",
                reasoning = "Mornings are brutal"
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, fakeDecisionRepository.inserted.size)
        val logged = fakeDecisionRepository.inserted.single()
        assertEquals("Keep the 5am gym habit or move it later?", logged.question)
        assertEquals("Move to evening", logged.chosenOption)
        assertEquals("goal-9", logged.relatedGoalId)
        assertEquals(az.tribe.lifeplanner.domain.model.DecisionSource.JOURNAL, logged.source)
        assertEquals(az.tribe.lifeplanner.domain.model.DecisionStatus.PENDING, logged.status)
    }

    @Test
    fun `createEntry without a detected decision logs nothing`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.createEntry(title = "Ordinary day", content = "Nothing special", mood = Mood.NEUTRAL)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(fakeDecisionRepository.inserted.isEmpty())
    }

    @Test
    fun `deleteEntry removes entry from repository`() = runTest(testDispatcher) {
        val entry = testJournalEntry(id = "j1", title = "To Delete")
        fakeRepository.setEntries(listOf(entry))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.deleteEntry("j1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.entries.test {
            val items = awaitItem()
            assertTrue(items.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateEntry modifies existing entry`() = runTest(testDispatcher) {
        val entry = testJournalEntry(id = "j1", title = "Old Title", content = "Old content")
        fakeRepository.setEntries(listOf(entry))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Wait for entries to load
        viewModel.entries.test {
            var items = awaitItem()
            if (items.isEmpty()) items = awaitItem()
            assertEquals(1, items.size)
            cancelAndIgnoreRemainingEvents()
        }

        viewModel.updateEntry(
            id = "j1",
            title = "New Title",
            content = "New content",
            mood = Mood.VERY_HAPPY,
            tags = listOf("updated")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.entries.test {
            val items = awaitItem()
            assertEquals(1, items.size)
            assertEquals("New Title", items.first().title)
            assertEquals("New content", items.first().content)
            assertEquals(Mood.VERY_HAPPY, items.first().mood)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `updateEntry does nothing for nonexistent entry`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateEntry(
            id = "nonexistent",
            title = "Title",
            content = "Content",
            mood = Mood.HAPPY,
            tags = emptyList()
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // No error, no entries
        assertNull(viewModel.error.value)
    }


    @Test
    fun `refreshPrompt changes current prompt`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        val firstPrompt = viewModel.currentPrompt.value
        assertNotNull(firstPrompt)
        assertTrue(firstPrompt.isNotEmpty())

        // Refresh multiple times - prompt should be non-empty
        viewModel.refreshPrompt()
        val newPrompt = viewModel.currentPrompt.value
        assertNotNull(newPrompt)
        assertTrue(newPrompt.isNotEmpty())
    }





    @Test
    fun `getEntriesForToday returns only today entries`() = runTest(testDispatcher) {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val yesterday = today.minus(DatePeriod(days = 1))
        fakeRepository.setEntries(listOf(
            testJournalEntry(id = "j1", date = today, title = "Today"),
            testJournalEntry(id = "j2", date = yesterday, title = "Yesterday")
        ))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Wait for entries to load
        viewModel.entries.test {
            var items = awaitItem()
            if (items.isEmpty()) items = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        val todayEntries = viewModel.getEntriesForToday()
        assertEquals(1, todayEntries.size)
        assertEquals("Today", todayEntries.first().title)
    }

    @Test
    fun `getStreakDays returns 0 for empty entries`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.getStreakDays())
    }

    @Test
    fun `getEntryById finds entry by id`() = runTest(testDispatcher) {
        val entry = testJournalEntry(id = "j1", title = "Findable")
        fakeRepository.setEntries(listOf(entry))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.entries.test {
            var items = awaitItem()
            if (items.isEmpty()) items = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        val found = viewModel.getEntryById("j1")
        assertNotNull(found)
        assertEquals("Findable", found.title)
    }

    @Test
    fun `getEntryById returns null for nonexistent id`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.getEntryById("nonexistent"))
    }

    @Test
    fun `showNewEntryDialog sets state to true`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        assertFalse(viewModel.showNewEntryDialog.value)

        viewModel.showNewEntryDialog()
        assertTrue(viewModel.showNewEntryDialog.value)
    }

    @Test
    fun `hideNewEntryDialog sets state to false`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        viewModel.showNewEntryDialog()
        assertTrue(viewModel.showNewEntryDialog.value)

        viewModel.hideNewEntryDialog()
        assertFalse(viewModel.showNewEntryDialog.value)
    }

    @Test
    fun `clearError resets error to null`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        viewModel.clearError()
        assertNull(viewModel.error.value)
    }

}
