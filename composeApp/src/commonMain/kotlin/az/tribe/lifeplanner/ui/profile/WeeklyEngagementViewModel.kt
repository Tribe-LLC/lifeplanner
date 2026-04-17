package az.tribe.lifeplanner.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.domain.model.MessageRole
import az.tribe.lifeplanner.domain.repository.ChatRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.RetrospectiveRepository
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

data class WeeklyEngagement(
    val habitCheckIns: Int,
    val goalsCreated: Int,
    val journalEntries: Int,
    val focusSessionsCompleted: Int,
    val aiCoachMessages: Int
)

class WeeklyEngagementViewModel(
    private val retrospectiveRepository: RetrospectiveRepository,
    private val goalRepository: GoalRepository,
    private val chatRepository: ChatRepository,
) : ViewModel() {

    private val _engagement = MutableStateFlow<WeeklyEngagement?>(null)
    val engagement: StateFlow<WeeklyEngagement?> = _engagement.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                val weekStart = today.minus(DatePeriod(days = 6))

                val snapshots = retrospectiveRepository.observeWeeklySnapshots().first()
                val habitCheckIns = snapshots.sumOf { it.habitSummary.completedHabits }
                val journalEntries = snapshots.sumOf { it.journalEntries.size }
                val focusCompleted = snapshots.sumOf { day ->
                    day.focusSessions.count { it.wasCompleted }
                }

                val goalsCreated = goalRepository.getAllGoals()
                    .count { it.createdAt.date >= weekStart }

                val recentSessions = chatRepository.getAllSessions()
                    .filter { it.lastMessageAt.date >= weekStart }
                var aiMessages = 0
                for (session in recentSessions) {
                    aiMessages += chatRepository.getMessages(session.id)
                        .count { it.role == MessageRole.ASSISTANT && it.timestamp.date >= weekStart }
                }

                _engagement.value = WeeklyEngagement(
                    habitCheckIns = habitCheckIns,
                    goalsCreated = goalsCreated,
                    journalEntries = journalEntries,
                    focusSessionsCompleted = focusCompleted,
                    aiCoachMessages = aiMessages
                )
            } catch (e: Exception) {
                Logger.e("WeeklyEngagementVM") { "Load failed: ${e.message}" }
            }
        }
    }
}
