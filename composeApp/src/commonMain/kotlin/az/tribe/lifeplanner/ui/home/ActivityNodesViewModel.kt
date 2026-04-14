package az.tribe.lifeplanner.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.repository.FocusRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.JournalRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

class ActivityNodesViewModel(
    goalRepository: GoalRepository,
    habitRepository: HabitRepository,
    journalRepository: JournalRepository,
    focusRepository: FocusRepository,
) : ViewModel() {

    private val sevenDaysAgo = Clock.System.now()
        .minus(7, DateTimeUnit.DAY, TimeZone.currentSystemDefault())
        .toLocalDateTime(TimeZone.currentSystemDefault())

    val activityNodes: StateFlow<List<ActivityNode>> = combine(
        goalRepository.observeAllGoals(),
        habitRepository.observeHabitsWithTodayStatus(),
        journalRepository.observeAllEntries(),
        focusRepository.observeAllSessions(),
    ) { goals, habits, entries, sessions ->
        val nodes = mutableListOf<ActivityNode>()

        val goalMap = goals.associateBy { it.id }

        // Recent goals created in last 7 days
        goals.filter { it.createdAt >= sevenDaysAgo && it.status != GoalStatus.COMPLETED }
            .forEach { nodes.add(ActivityNode.GoalCreated(it)) }

        // Completed goals (recent)
        goals.filter { it.status == GoalStatus.COMPLETED && it.createdAt >= sevenDaysAgo }
            .forEach { nodes.add(ActivityNode.GoalCompleted(it)) }

        // Habits completed today
        habits.filter { (_, completed) -> completed }
            .forEach { (habit, _) -> nodes.add(ActivityNode.HabitCheckedIn(habit)) }

        // Journal entries from last 7 days
        entries.filter { it.createdAt >= sevenDaysAgo }
            .forEach { nodes.add(ActivityNode.JournalWritten(it)) }

        // Focus sessions from last 7 days
        sessions.filter { it.startedAt >= sevenDaysAgo }
            .forEach { nodes.add(ActivityNode.FocusCompleted(it, goalMap[it.goalId]?.title)) }

        nodes.sortedByDescending { it.timestamp }.take(10)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
