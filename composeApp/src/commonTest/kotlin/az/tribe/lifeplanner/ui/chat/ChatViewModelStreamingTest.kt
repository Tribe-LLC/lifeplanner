package az.tribe.lifeplanner.ui.chat

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.ChatMessage
import az.tribe.lifeplanner.domain.model.ChatSession
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.GoalAnalytics
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.HabitCheckIn
import az.tribe.lifeplanner.domain.model.JournalEntry
import az.tribe.lifeplanner.domain.model.MessageRole
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.model.UserContext
import az.tribe.lifeplanner.domain.repository.ChatRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.JournalRepository
import az.tribe.lifeplanner.domain.repository.StreamingChatEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelStreamingTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockChatRepo: MockChatRepository
    private lateinit var viewModel: ChatViewModel

    private val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    private val testSession = ChatSession(
        id = "session-1",
        title = "Test Chat",
        messages = emptyList(),
        createdAt = now,
        lastMessageAt = now,
        coachId = "luna_general"
    )

    private val testUserContext = UserContext(
        userName = "Test",
        totalGoals = 0,
        completedGoals = 0,
        activeGoals = 0,
        currentStreak = 0,
        totalXp = 0,
        level = 1,
        recentMilestones = emptyList(),
        upcomingDeadlines = emptyList(),
        habitCompletionRate = 0f,
        journalEntryCount = 0,
        primaryCategories = emptyList()
    )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockChatRepo = MockChatRepository()
        mockChatRepo.sessionsToReturn = listOf(testSession)
        mockChatRepo.userContextToReturn = testUserContext
        mockChatRepo.sessionByIdToReturn = testSession

        viewModel = ChatViewModel(
            chatRepository = mockChatRepo,
            goalRepository = StubGoalRepository(),
            habitRepository = StubHabitRepository(),
            journalRepository = StubJournalRepository(),
            coachRepository = null
        )

        // Advance past init{} coroutines
        testDispatcher.scheduler.advanceUntilIdle()

        // Select the coach to enter chat mode (sets currentSession)
        viewModel.selectSessionById(testSession.id)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun streamingUpdatesStreamingTextProgressively() = runTest(testDispatcher) {
        val completedMsg = ChatMessage(
            id = "msg-resp",
            content = "Hi there!",
            role = MessageRole.ASSISTANT,
            timestamp = now
        )

        mockChatRepo.streamingFlow = flow {
            emit(StreamingChatEvent.PartialText("Hi", "Hi"))
            emit(StreamingChatEvent.PartialText(" there!", "Hi there!"))
            emit(StreamingChatEvent.Completed(completedMsg))
        }
        // Return the completed message when getMessages is called after streaming
        mockChatRepo.messagesToReturn = listOf(completedMsg)

        viewModel.sendMessage("Hello")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isStreaming, "Should not be streaming after completion")
        assertFalse(state.isSending, "Should not be sending after completion")
        assertNull(state.streamingText, "streamingText should be null after completion")
        assertNull(state.error, "Should have no error")
    }

    @Test
    fun streamingErrorResetsState() = runTest(testDispatcher) {
        mockChatRepo.streamingFlow = flow {
            emit(StreamingChatEvent.PartialText("Hi", "Hi"))
            emit(StreamingChatEvent.Error("Connection lost"))
        }

        viewModel.sendMessage("Hello")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isStreaming, "Should not be streaming after error")
        assertFalse(state.isSending, "Should not be sending after error")
        assertNull(state.streamingText, "streamingText should be null after error")
        assertEquals("Connection lost", state.error)
    }

    @Test
    fun flowCompletesWithoutTerminalEventSetsError() = runTest(testDispatcher) {
        // Empty flow — completes without emitting PartialText, Completed, or Error
        mockChatRepo.streamingFlow = emptyFlow()
        mockChatRepo.messagesToReturn = emptyList()

        viewModel.sendMessage("Hello")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isStreaming, "Should not be streaming")
        assertFalse(state.isSending, "Should not be sending")
        assertEquals("Response failed. Please try again.", state.error)
    }
}

// =============================================================================
// Mock / Stub implementations
// =============================================================================

class MockChatRepository : ChatRepository {
    var sessionsToReturn: List<ChatSession> = emptyList()
    var sessionByIdToReturn: ChatSession? = null
    var messagesToReturn: List<ChatMessage> = emptyList()
    var userContextToReturn: UserContext? = null
    var streamingFlow: Flow<StreamingChatEvent> = emptyFlow()

    override suspend fun getAllSessions() = sessionsToReturn
    override suspend fun getSessionById(sessionId: String) = sessionByIdToReturn
    override suspend fun createSession(title: String, coachId: String): ChatSession {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return ChatSession(id = "new", title = title, messages = emptyList(), createdAt = now, lastMessageAt = now, coachId = coachId)
    }
    override suspend fun getSessionByCoachId(coachId: String) = sessionByIdToReturn
    override suspend fun getOrCreateSessionForCoach(coachId: String) = sessionByIdToReturn ?: createSession("test", coachId)
    override suspend fun deleteSession(sessionId: String) {}
    override suspend fun getMessages(sessionId: String) = messagesToReturn
    override suspend fun getRecentMessages(sessionId: String, limit: Int) = messagesToReturn.takeLast(limit)
    override suspend fun addUserMessage(sessionId: String, content: String, relatedGoalId: String?): ChatMessage {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return ChatMessage(id = "user-msg", content = content, role = MessageRole.USER, timestamp = now)
    }
    override suspend fun addAssistantMessage(sessionId: String, content: String, metadata: String?): ChatMessage {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return ChatMessage(id = "asst-msg", content = content, role = MessageRole.ASSISTANT, timestamp = now)
    }
    override suspend fun sendMessage(sessionId: String, userMessage: String, userContext: UserContext, relatedGoalId: String?): ChatMessage {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        return ChatMessage(id = "resp", content = "response", role = MessageRole.ASSISTANT, timestamp = now)
    }
    override suspend fun getUserContext() = userContextToReturn ?: throw Exception("No context")
    override suspend fun updateSessionSummary(sessionId: String, summary: String) {}
    override suspend fun deleteOldSessions(beforeDate: String) {}
    override suspend fun getSessionCount() = sessionsToReturn.size.toLong()
    override suspend fun markSuggestionExecuted(messageId: String, suggestionId: String) {}
    override fun sendMessageStreaming(sessionId: String, userMessage: String, userContext: UserContext, relatedGoalId: String?) = streamingFlow
}

// Minimal stubs — only need to compile; not exercised by streaming tests.

class StubGoalRepository : GoalRepository {
    override fun observeAllGoals(): Flow<List<Goal>> = flowOf(emptyList())
    override suspend fun getAllGoals() = emptyList<Goal>()
    override suspend fun insertGoal(goal: Goal) {}
    override suspend fun insertGoals(goals: List<Goal>) {}
    override suspend fun updateGoal(goal: Goal) {}
    override suspend fun deleteGoalById(id: String) {}
    override suspend fun deleteAllGoals() {}
    override suspend fun getGoalsByTimeline(timeline: GoalTimeline) = emptyList<Goal>()
    override suspend fun getGoalsByCategory(category: GoalCategory) = emptyList<Goal>()
    override suspend fun updateProgress(id: String, progress: Int) {}
    override suspend fun updateGoalNotes(id: String, notes: String) {}
    override suspend fun archiveGoal(id: String) {}
    override suspend fun unarchiveGoal(id: String) {}
    override suspend fun searchGoals(query: String) = emptyList<Goal>()
    override suspend fun getActiveGoals() = emptyList<Goal>()
    override suspend fun getCompletedGoals() = emptyList<Goal>()
    override suspend fun getUpcomingDeadlines(days: Int) = emptyList<Goal>()
    override suspend fun getAnalytics() = GoalAnalytics(0, 0, 0, 0.0f, 0, emptyMap(), emptyMap(), emptyMap(), 0, 0)
    override suspend fun addMilestone(goalId: String, milestone: Milestone) {}
    override suspend fun updateMilestone(milestone: Milestone) {}
    override suspend fun deleteMilestone(milestoneId: String) {}
    override suspend fun toggleMilestoneCompletion(milestoneId: String, isCompleted: Boolean) {}
}

class StubHabitRepository : HabitRepository {
    override fun observeHabitsWithTodayStatus(): Flow<List<Pair<Habit, Boolean>>> = flowOf(emptyList())
    override suspend fun getAllHabits() = emptyList<Habit>()
    override suspend fun getHabitById(id: String): Habit? = null
    override suspend fun getHabitsByCategory(category: GoalCategory) = emptyList<Habit>()
    override suspend fun getHabitsByGoalId(goalId: String) = emptyList<Habit>()
    override suspend fun insertHabit(habit: Habit) {}
    override suspend fun updateHabit(habit: Habit) {}
    override suspend fun deleteHabit(id: String) {}
    override suspend fun deactivateHabit(id: String) {}
    override suspend fun checkIn(habitId: String, date: LocalDate, notes: String) = HabitCheckIn(id = "ci", habitId = habitId, date = date, completed = true, notes = notes)
    override suspend fun getCheckInsByHabitId(habitId: String) = emptyList<HabitCheckIn>()
    override suspend fun getCheckInsByDate(date: LocalDate) = emptyList<HabitCheckIn>()
    override suspend fun getCheckInByHabitAndDate(habitId: String, date: LocalDate): HabitCheckIn? = null
    override suspend fun getCheckInsInRange(habitId: String, startDate: LocalDate, endDate: LocalDate) = emptyList<HabitCheckIn>()
    override suspend fun deleteCheckIn(id: String) {}
    override suspend fun calculateStreak(habitId: String) = 0
    override suspend fun updateStreakAfterCheckIn(habitId: String) {}
    override suspend fun getHabitsWithTodayStatus(today: LocalDate) = emptyList<Pair<Habit, Boolean>>()
    override suspend fun getHabitCompletionRate(habitId: String, days: Int) = 0f
    override suspend fun invalidateCache() {}
}

class StubJournalRepository : JournalRepository {
    override fun observeAllEntries(): Flow<List<JournalEntry>> = flowOf(emptyList())
    override suspend fun getAllEntries() = emptyList<JournalEntry>()
    override suspend fun getEntryById(id: String): JournalEntry? = null
    override suspend fun getEntriesByDate(date: LocalDate) = emptyList<JournalEntry>()
    override suspend fun getEntriesByGoalId(goalId: String) = emptyList<JournalEntry>()
    override suspend fun getEntriesByHabitId(habitId: String) = emptyList<JournalEntry>()
    override suspend fun getEntriesByMood(mood: Mood) = emptyList<JournalEntry>()
    override suspend fun getEntriesInRange(startDate: LocalDate, endDate: LocalDate) = emptyList<JournalEntry>()
    override suspend fun getRecentEntries(limit: Int) = emptyList<JournalEntry>()
    override suspend fun insertEntry(entry: JournalEntry) {}
    override suspend fun updateEntry(entry: JournalEntry) {}
    override suspend fun deleteEntry(id: String) {}
    override suspend fun searchEntries(query: String) = emptyList<JournalEntry>()
    override suspend fun getMoodStats(startDate: LocalDate, endDate: LocalDate) = emptyMap<Mood, Int>()
}
