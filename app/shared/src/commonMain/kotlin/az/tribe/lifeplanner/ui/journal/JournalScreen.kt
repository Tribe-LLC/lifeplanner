package az.tribe.lifeplanner.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import org.jetbrains.compose.resources.painterResource
import leanlifeplanner.app.shared.generated.resources.Res
import leanlifeplanner.app.shared.generated.resources.illus_empty_schedule
import leanlifeplanner.app.shared.generated.resources.illus_empty_box
import leanlifeplanner.app.shared.generated.resources.illus_empty_goals
import leanlifeplanner.app.shared.generated.resources.illus_empty_journal
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.CheckCircle
import com.adamglin.phosphoricons.regular.Circle
import com.adamglin.phosphoricons.regular.Sparkle
import com.adamglin.phosphoricons.regular.Sun
import com.adamglin.phosphoricons.regular.Moon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.JournalEntry
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.service.HabitTrackMode
import az.tribe.lifeplanner.domain.service.trackMode
import az.tribe.lifeplanner.ui.calendar.CalendarDayEvents
import az.tribe.lifeplanner.ui.components.CompactGoalRow
import az.tribe.lifeplanner.ui.home.CompactHomeMilestoneRow
import az.tribe.lifeplanner.ui.home.HomeViewModel
import az.tribe.lifeplanner.ui.components.GlassCard
import az.tribe.lifeplanner.ui.components.InlineEmptyState
import az.tribe.lifeplanner.ui.components.WeekStrip
import az.tribe.lifeplanner.ui.components.WeeklyEngagementViewModel
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign
import az.tribe.lifeplanner.ui.theme.bouncyClickable
import com.russhwolf.settings.Settings
import org.koin.compose.koinInject
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.service.GoalOptimizer
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import az.tribe.lifeplanner.ui.components.SwipeableGoalItem
import az.tribe.lifeplanner.ui.components.SwipeableHabitCard
import az.tribe.lifeplanner.ui.goal.GoalViewModel
import az.tribe.lifeplanner.core.FeatureFlags
import az.tribe.lifeplanner.ui.ability.AbilityCard
import az.tribe.lifeplanner.ui.ability.AbilityViewModel
import az.tribe.lifeplanner.ui.habit.HabitViewModel
import az.tribe.lifeplanner.ui.habit.*
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    onNavigateBack: () -> Unit,
    onEntryClick: (String) -> Unit = {},
    onNavigateToWizard: (Mood?) -> Unit = {},
    isFromBottomNav: Boolean = false,
    selectedTab: Int = 0,
    onTabSelected: (Int) -> Unit = {},
    /** Reports the hub's day lens so the Write FAB, which lives outside this screen, can file to it. */
    onSelectedDateChanged: (LocalDate) -> Unit = {},
    onGoalClick: (Goal) -> Unit = {},
    onAddGoalClick: () -> Unit = {},
    onAddHabitClick: () -> Unit = {},
    onHabitClick: (String) -> Unit = {},
    onPracticeHabit: (String) -> Unit = {},
    onNavigateToFocus: () -> Unit = {},
    onAbilityClick: (String) -> Unit = {},
    onCreateAbility: () -> Unit = {},
    onStartFocusForMilestone: (goalId: String, milestoneId: String) -> Unit = { _, _ -> },
    viewModel: JournalViewModel = koinViewModel(),
    goalViewModel: GoalViewModel = koinViewModel(),
    habitViewModel: HabitViewModel = koinViewModel(),
    abilityViewModel: AbilityViewModel = koinViewModel(),
    homeViewModel: HomeViewModel = koinViewModel(),
    weeklyEngagementViewModel: WeeklyEngagementViewModel = koinViewModel()
) {
    val entries by viewModel.entries.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showNewEntryDialog by viewModel.showNewEntryDialog.collectAsState()

    val goals by goalViewModel.goals.collectAsState()
    val habitsWithStatus by habitViewModel.habits.collectAsState()
    val habits = habitsWithStatus.map { it.habit }
    val abilities by abilityViewModel.abilities.collectAsState()

    // Own tab state locally, the NavGraphBuilder closure captures selectedTab once at
    // graph-build time, so the parent parameter is stale after first composition.
    // We call onTabSelected as a side-effect so App.kt's navContextAction FAB stays in sync.
    // rememberSaveable so the chosen hub tab survives leaving to a detail screen and coming back
    // (Compose Navigation disposes the composition; plain remember would reset us to the first tab).
    var currentTab by rememberSaveable { mutableStateOf(selectedTab) }

    // The hub is long-lived, so the view model's own init-time load would go stale. The week's output
    // now rides in the day-lens banner above every sub-tab, so recount on each tab change.
    val weeklyEngagement by weeklyEngagementViewModel.engagement.collectAsState()
    LaunchedEffect(currentTab) { weeklyEngagementViewModel.load() }

    // Shared "day lens": the selected date persists across the hub's sub-tabs (rememberSaveable via
    // epoch-day). The WeekStrip and the Journal tab both read/write it.
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var selectedEpochDay by rememberSaveable { mutableStateOf(today.toEpochDays()) }
    val selectedDate = remember(selectedEpochDay) { LocalDate.fromEpochDays(selectedEpochDay) }
    LaunchedEffect(selectedDate) { onSelectedDateChanged(selectedDate) }

    var habitToEdit by remember { mutableStateOf<Habit?>(null) }
    val listState = rememberLazyListState()
    val error by viewModel.error.collectAsState()

    val habitsCompleted = habitsWithStatus.count { it.isCompletedToday }
    val habitsTotal = habitsWithStatus.size
    val activeGoalCount = goals.count { it.status != GoalStatus.COMPLETED }

    // Declutter on revisit: habits already done *before* this visit are hidden, so the list
    // shows what is still left. Habits completed *during* this visit stay in place (with the
    // muted done styling) so the tap has visible feedback; they drop off next time the screen
    // is opened. Snapshot is taken once, the first time habits load after entering.
    var completedAtEntry by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(habitsWithStatus) {
        if (completedAtEntry == null && habitsWithStatus.isNotEmpty()) {
            completedAtEntry = habitsWithStatus.filter { it.isCompletedToday }.map { it.habit.id }.toSet()
        }
    }
    val hiddenHabitIds = completedAtEntry ?: emptySet()
    val visibleHabits = habitsWithStatus.filter { it.habit.id !in hiddenHabitIds }
    // Non-empty habit list but nothing left to show = everything was already done before this visit.
    val allHabitsCaughtUp = habitsWithStatus.isNotEmpty() && visibleHabits.isEmpty()

    val habitGroups = remember(visibleHabits) {
        val grouped = visibleHabits.groupBy { habitTimeSlot(it.habit.reminderTime) }
        listOf(HabitTimeSlot.MORNING, HabitTimeSlot.AFTERNOON, HabitTimeSlot.EVENING, HabitTimeSlot.ANYTIME)
            .mapNotNull { slot -> grouped[slot]?.takeIf { it.isNotEmpty() }?.let { slot to it } }
    }

    val sortedGoals = remember(goals) {
        val order = mapOf(GoalStatus.IN_PROGRESS to 0, GoalStatus.NOT_STARTED to 1, GoalStatus.COMPLETED to 2)
        goals.sortedBy { order[it.status] ?: 3 }
    }

    // Entries for the currently selected day (the Journal tab's day lens).
    val dayEntries = remember(entries, selectedDate) {
        entries.filter { it.date == selectedDate }.sortedByDescending { it.createdAt }
    }

    // What's planned per day (goal due dates + incomplete milestone due dates), for the strip flags
    // and the "planned this day" card. Flags appear on any day, past or future.
    val plannedByDay = remember(goals) {
        val map = mutableMapOf<LocalDate, MutableList<String>>()
        goals.forEach { g ->
            if (g.status != GoalStatus.COMPLETED) {
                map.getOrPut(g.dueDate) { mutableListOf() }.add("🎯 \"${g.title}\" due")
                g.milestones.forEach { m ->
                    if (!m.isCompleted) m.dueDate?.let { d -> map.getOrPut(d) { mutableListOf() }.add("${m.title} · ${g.title}") }
                }
            }
        }
        map
    }
    val plannedForSelected = plannedByDay[selectedDate].orEmpty()

    // Habits day lens: which habits were completed on the selected (non-today) day.
    val habitDayCompletions by habitViewModel.lensCompletions.collectAsState()
    LaunchedEffect(selectedDate) {
        habitViewModel.setLensDate(if (selectedDate == today) null else selectedDate)
    }
    // Past-edit guard: warn once before letting the user change a previous day's habits.
    val settings: Settings = koinInject()
    var pendingPastToggle by remember { mutableStateOf<String?>(null) }
    fun requestPastToggle(habitId: String) {
        if (settings.getBoolean(PAST_EDIT_WARNED_KEY, false)) {
            habitViewModel.toggleCheckInForDate(habitId, selectedDate)
        } else {
            pendingPastToggle = habitId
        }
    }

    val upcomingGoals = remember(goals) {
        goals.filter { it.status != GoalStatus.COMPLETED }.sortedBy { it.dueDate }.take(3)
    }

    // Goal tune-ups: deterministic one-tap optimizations (reschedule overdue, close out finished,
    // finish near-done, revisit stalled). Session-dismissed ones drop until next open.
    var dismissedTuneUps by remember { mutableStateOf(setOf<String>()) }
    val goalSuggestions = remember(goals, today, dismissedTuneUps) {
        GoalOptimizer.suggestions(goals, today)
            .filterNot { "${it.goalId}:${it.kind}" in dismissedTuneUps }
    }

    val sortedEntries = remember(entries) {
        entries.sortedWith(compareByDescending<JournalEntry> { it.date }.thenByDescending { it.createdAt })
    }

    val nextMilestones = remember(goals) {
        goals.filter { it.status != GoalStatus.COMPLETED && it.milestones.isNotEmpty() }
            .mapNotNull { goal -> goal.milestones.firstOrNull { !it.isCompleted }?.let { goal to it } }
            .take(5)
    }
    val milestoneFocusMinutes by homeViewModel.milestoneFocusMinutes.collectAsState()
    LaunchedEffect(nextMilestones) { homeViewModel.loadMilestoneFocusMinutes(nextMilestones) }

    val bannerTitle = when (currentTab) {
        1 -> "Goals"
        2 -> "Habits"
        3 -> if (FeatureFlags.ABILITIES_ENABLED) "Abilities" else "Journal"
        else -> "Journal"
    }
    val bannerSubtitle = when (currentTab) {
        1 -> if (activeGoalCount == 0) "No active goals" else "$activeGoalCount active"
        2 -> if (habitsTotal == 0) "No habits yet" else "$habitsCompleted/$habitsTotal done today"
        3 -> if (FeatureFlags.ABILITIES_ENABLED) {
            if (abilities.isEmpty()) "No abilities yet" else "${abilities.size} abilities"
        } else {
            if (entries.isEmpty()) "No entries yet" else "${entries.size} entries"
        }
        else -> if (entries.isEmpty()) "No entries yet" else "${entries.size} entries"
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(snackbarData = data, containerColor = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface, actionColor = MaterialTheme.colorScheme.primary, actionContentColor = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp))
            }
        }
    ) { padding ->
        // Clearance for the app's floating bottom nav pill, which draws over this screen.
        // The app's floating nav pill is NavBarHeight tall and draws over this screen. Sit the
        // switcher one small gap above it rather than floating in the middle distance.
        val switcherBottomInset = padding.calculateBottomPadding() + 64.dp + LifePlannerDesign.Spacing.xs
        val navBarInset = padding.calculateBottomPadding() + 84.dp

        Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Extra bottom room for the pinned sub-tab switcher so the last row isn't hidden.
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = navBarInset + 56.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Persistent day lens, shared across all sub-tabs.
            item(key = "week_strip") {
                WeekStrip(
                    selectedDate = selectedDate,
                    entries = entries,
                    onSelect = { selectedEpochDay = it.toEpochDays() },
                    flaggedDates = plannedByDay.keys,
                    // The week's output belongs to the week lens, not to a card of its own further
                    // down the Journal tab: expanding the strip opens the full counts in place.
                    engagement = weeklyEngagement,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // The week strip and each tab's own content already show which day is selected, so we add
            // no date label here, only a compact "Jump to today" link when the lens is off today.
            if (selectedDate != today) {
                item(key = "jump_today") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            "Jump to today",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.bouncyClickable { selectedEpochDay = today.toEpochDays() },
                        )
                    }
                }
            }

            // Tapping a flagged day shows what's planned (on any tab).
            if (plannedForSelected.isNotEmpty()) {
                item(key = "planned_day") {
                    Surface(
                        onClick = { if (currentTab != 1) { currentTab = 1; onTabSelected(1) } },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Planned ${dayLabel(selectedDate, today)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            plannedForSelected.take(4).forEach { line ->
                                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // Device-calendar events for the selected day (shared across tabs, mirrors the day lens).
            // Renders itself only when calendar permission is granted and the day has events.
            item(key = "calendar_day_events") {
                CalendarDayEvents(
                    selectedDate = selectedDate,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            // ── Tab 0: Journal ──────────────────────────────────────────────
            // The mood calendar plus the selected day's reflections. Write via the contextual FAB.
            if (currentTab == 0) {
                if (dayEntries.isEmpty()) {
                    // An empty day reads as an empty day, today included. The mood picker used to
                    // stand in here, but asking how you feel before there is anything on the page
                    // is a prompt, not a state — the writer collects the mood as its first step.
                    item(key = "day_empty") {
                        InlineEmptyState(
                            illustration = Res.drawable.illus_empty_journal,
                            title = if (selectedDate == today) "Nothing journaled today"
                            else "Nothing journaled this day",
                            subtitle = if (selectedDate == today) "Tap Write to start today's entry"
                            else "Pick another day, or tap Write to add one",
                        )
                    }
                } else {
                    items(items = dayEntries, key = { "entry_${it.id}" }) { entry ->
                        SwipeableJournalEntryCard(
                            entry = entry,
                            onClick = { onEntryClick(entry.id) },
                            onDelete = { viewModel.deleteEntry(entry.id) },
                            listState = listState,
                            modifier = Modifier.padding(horizontal = 16.dp).animateItem()
                        )
                    }
                }
            }

            // ── Tab 1: Goals ────────────────────────────────────────────────
            else if (currentTab == 1) {
                if (goalSuggestions.isNotEmpty()) {
                    item(key = "goal_tuneups") {
                        GoalTuneUpCard(
                            suggestions = goalSuggestions,
                            onApply = { s ->
                                when (s.kind) {
                                    GoalOptimizer.Kind.READY_TO_COMPLETE ->
                                        goalViewModel.updateGoalStatus(s.goalId, GoalStatus.COMPLETED)
                                    GoalOptimizer.Kind.RESCHEDULE_OVERDUE ->
                                        goals.firstOrNull { it.id == s.goalId }?.let { g ->
                                            goalViewModel.updateGoal(g.copy(dueDate = today.plus(DatePeriod(days = GoalOptimizer.RESCHEDULE_DAYS))))
                                        }
                                    GoalOptimizer.Kind.FINISH_ALMOST, GoalOptimizer.Kind.REFOCUS_STALE ->
                                        goals.firstOrNull { it.id == s.goalId }?.let(onGoalClick)
                                }
                            },
                            onDismiss = { s -> dismissedTuneUps = dismissedTuneUps + "${s.goalId}:${s.kind}" },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                if (nextMilestones.isNotEmpty()) {
                    item(key = "next_steps_header") {
                        Text("Next Steps (${nextMilestones.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface)
                    }
                    item(key = "next_steps_card") {
                        GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), cornerRadius = 16.dp) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                nextMilestones.forEachIndexed { index, (goal, milestone) ->
                                    CompactHomeMilestoneRow(
                                        goal = goal, milestone = milestone,
                                        focusMinutes = milestoneFocusMinutes[milestone.id] ?: 0,
                                        onRowClick = { onGoalClick(goal) },
                                        onStartFocus = { onStartFocusForMilestone(goal.id, milestone.id) }
                                    )
                                    if (index < nextMilestones.size - 1) {
                                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))
                                    }
                                }
                            }
                        }
                    }
                }

                if (upcomingGoals.isNotEmpty()) {
                    item(key = "priority_goals_header") {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Priority Goals", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("${upcomingGoals.size} upcoming", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                    item(key = "priority_goals_card") {
                        GlassCard(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), cornerRadius = 16.dp) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                upcomingGoals.forEachIndexed { index, goal ->
                                    CompactGoalRow(goal = goal, onClick = { onGoalClick(goal) })
                                    if (index < upcomingGoals.lastIndex) {
                                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))
                                    }
                                }
                            }
                        }
                    }
                    item(key = "all_goals_divider") {
                        Text("All Goals", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                if (sortedGoals.isEmpty()) {
                    item(key = "goals_empty") {
                        InlineEmptyState(
                            illustration = Res.drawable.illus_empty_goals,
                            title = "No goals yet",
                            subtitle = "Tap Add Goal to set your first target",
                        )
                    }
                } else {
                    items(items = sortedGoals, key = { "goal_${it.id}" }) { goal ->
                        SwipeableGoalItem(
                            goal = goal, onClick = { onGoalClick(goal) },
                            onComplete = { goalViewModel.updateGoalStatus(goal.id, GoalStatus.COMPLETED) },
                            onDelete = { goalViewModel.deleteGoal(goal.id) },
                            scrollState = listState, modifier = Modifier.animateItem()
                        )
                    }
                }
            }

            // ── Tab 2: Habits ───────────────────────────────────────────────
            else if (currentTab == 2) {
                if (habitsWithStatus.isEmpty()) {
                    item(key = "habits_empty") {
                        InlineEmptyState(
                            illustration = Res.drawable.illus_empty_schedule,
                            title = "No habits yet",
                            subtitle = "Tap New Habit to start building consistency",
                        )
                    }
                } else if (selectedDate != today) {
                    // A day view: what you did that day, done first then skipped.
                    val doneOnDay = habitsWithStatus.filter { it.habit.id in habitDayCompletions }
                    val skippedOnDay = habitsWithStatus.filter { it.habit.id !in habitDayCompletions }
                    item(key = "habits_day_header") {
                        Text(
                            "${dayLabel(selectedDate, today)} · ${doneOnDay.size} of ${habitsWithStatus.size} done",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    items(items = doneOnDay, key = { "done_${it.habit.id}" }) { hs ->
                        PastDayHabitRow(hs.habit.title, done = true, onClick = { requestPastToggle(hs.habit.id) }, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    if (skippedOnDay.isNotEmpty()) {
                        item(key = "skipped_header") {
                            Text(
                                "💤 ${skippedOnDay.size} skipped",
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(items = skippedOnDay, key = { "skip_${it.habit.id}" }) { hs ->
                            PastDayHabitRow(hs.habit.title, done = false, onClick = { requestPastToggle(hs.habit.id) }, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    } else if (doneOnDay.isNotEmpty()) {
                        item(key = "all_done") {
                            InlineEmptyState(
                                illustration = Res.drawable.illus_empty_schedule,
                                title = "Every habit done",
                                subtitle = "A perfect day. Nice one.",
                            )
                        }
                    }
                } else if (allHabitsCaughtUp) {
                    item(key = "habits_caught_up") {
                        InlineEmptyState(
                            illustration = Res.drawable.illus_empty_schedule,
                            title = "All done for today",
                            subtitle = "You completed every habit. Enjoy the space.",
                        )
                    }
                } else {
                    habitGroups.forEach { (slot, habitsInSlot) ->
                        item(key = "header_${slot.name}") { HabitSectionHeader(slot) }
                        items(items = habitsInSlot, key = { "habit_${it.habit.id}" }) { habitWithStatus ->
                            SwipeableHabitCard(
                                habitWithStatus = habitWithStatus,
                                onCheckIn = { habitViewModel.toggleCheckIn(habitWithStatus.habit.id) },
                                onDelete = { habitViewModel.deleteHabit(habitWithStatus.habit.id) },
                                onEdit = { habitToEdit = habitWithStatus.habit },
                                onCardClick = { onHabitClick(habitWithStatus.habit.id) },
                                onFocusClick = onNavigateToFocus,
                                onIncrement = if (habitWithStatus.habit.trackMode == HabitTrackMode.COUNT) {
                                    { habitViewModel.incrementCheckIn(habitWithStatus.habit.id) }
                                } else null,
                                onPractice = { onPracticeHabit(habitWithStatus.habit.id) },
                                modifier = Modifier.padding(horizontal = 16.dp).animateItem()
                            )
                        }
                    }
                }
            }

            // ── Tab 3: Abilities (hidden behind feature flag) ───────────────
            else if (currentTab == 3 && FeatureFlags.ABILITIES_ENABLED) {
                if (abilities.isEmpty()) {
                    item(key = "abilities_empty") {
                        InlineEmptyState(
                            illustration = Res.drawable.illus_empty_box,
                            title = "No abilities yet",
                            subtitle = "Tap Add Ability to start leveling up",
                        )
                    }
                } else {
                    items(items = abilities, key = { "ability_${it.id}" }) { ability ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp).animateItem()) {
                            AbilityCard(ability = ability, onClick = { onAbilityClick(ability.id) })
                        }
                    }
                }
            }
        }

        // Sub-tab switcher, pinned just above the app's bottom nav instead of scrolling away at
        // the top. It draws its own track, so it sits directly on the screen rather than in a
        // second container.
        HubTabRow(
            selectedTab = currentTab,
            onTabSelected = { tab ->
                currentTab = tab
                onTabSelected(tab)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = LifePlannerDesign.Padding.screenHorizontal)
                .padding(bottom = switcherBottomInset),
        )
        }

        // Journal entry sheet, accessible from any tab via FAB
        if (showNewEntryDialog) {
            NewJournalEntryBottomSheet(
                onDismiss = { viewModel.hideNewEntryDialog() },
                onConfirm = { title, content, mood, tags, linkedGoalId, linkedHabitId, promptUsed ->
                    // File it under the day the hub is showing, not whatever day it is now.
                    viewModel.createEntry(title = title, content = content, mood = mood, linkedGoalId = linkedGoalId, linkedHabitId = linkedHabitId, tags = tags, promptUsed = promptUsed, date = selectedDate)
                },
                goals = goals,
                habits = habits,
                viewModel = viewModel,
            )
        }

        habitToEdit?.let { habit ->
            EditHabitBottomSheet(
                habit = habit,
                onDismiss = { habitToEdit = null },
                onConfirm = { updatedHabit -> habitViewModel.updateHabit(updatedHabit); habitToEdit = null }
            )
        }

        // Past-edit warning, shown once before changing a previous day's habits.
        pendingPastToggle?.let { habitId ->
            AlertDialog(
                onDismissRequest = { pendingPastToggle = null },
                title = { Text("Change a past day?") },
                text = { Text("Editing ${dayLabel(selectedDate, today)} updates your streaks and stats, not just today. Continue?") },
                confirmButton = {
                    TextButton(onClick = {
                        settings.putBoolean(PAST_EDIT_WARNED_KEY, true)
                        habitViewModel.toggleCheckInForDate(habitId, selectedDate)
                        pendingPastToggle = null
                    }) { Text("Continue") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingPastToggle = null }) { Text("Cancel") }
                },
            )
        }
    }
}

private const val PAST_EDIT_WARNED_KEY = "past_edit_warned_v1"

/** The "Goal tune-ups" card: a few one-tap optimizations so the plan stays honest with the calendar. */
@Composable
private fun GoalTuneUpCard(
    suggestions: List<GoalOptimizer.Suggestion>,
    onApply: (GoalOptimizer.Suggestion) -> Unit,
    onDismiss: (GoalOptimizer.Suggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "✨ Tune-ups",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            suggestions.forEachIndexed { index, s ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${tuneUpEmoji(s.kind)}  ${s.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.actionLabel,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.bouncyClickable { onApply(s) },
                        )
                        Text(
                            "Not now",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.bouncyClickable { onDismiss(s) },
                        )
                    }
                }
                if (index < suggestions.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)))
                }
            }
        }
    }
}

private fun tuneUpEmoji(kind: GoalOptimizer.Kind): String = when (kind) {
    GoalOptimizer.Kind.READY_TO_COMPLETE -> "✅"
    GoalOptimizer.Kind.RESCHEDULE_OVERDUE -> "📅"
    GoalOptimizer.Kind.FINISH_ALMOST -> "🏁"
    GoalOptimizer.Kind.REFOCUS_STALE -> "🤔"
}


@Composable
private fun PastDayHabitRow(title: String, done: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (done) PhosphorIcons.Regular.CheckCircle else PhosphorIcons.Regular.Circle,
                contentDescription = if (done) "Done" else "Not done",
                tint = if (done) Color(0xFF34C759) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp),
            )
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minus(DatePeriod(days = 1)) -> "Yesterday"
    else -> {
        val dow = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
        val mon = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
        "$dow, $mon ${date.dayOfMonth}"
    }
}

@Composable
private fun HubTabRow(selectedTab: Int, onTabSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tabs = if (FeatureFlags.ABILITIES_ENABLED)
        listOf("Journal", "Goals", "Habits", "Abilities")
    else
        listOf("Journal", "Goals", "Habits")
    // A single segmented track with one thumb that slides between segments, rather than N separate
    // pills sitting inside an outer pill. The track is opaque because the list scrolls behind it.
    val trackShape = RoundedCornerShape(LifePlannerDesign.CornerRadius.full)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(SwitcherElevation, trackShape)
            .clip(trackShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), trackShape)
            .padding(SwitcherTrackPadding),
    ) {
        BoxWithConstraints {
            val segmentWidth = maxWidth / tabs.size
            val thumbOffset by animateDpAsState(
                targetValue = segmentWidth * selectedTab,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                label = "hubSwitcherThumb",
            )
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .width(segmentWidth)
                    .height(SwitcherSegmentHeight)
                    .clip(trackShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Row(Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, label ->
                    val isSelected = selectedTab == index
                    val labelColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "hubSwitcherLabel",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(SwitcherSegmentHeight)
                            .clip(trackShape)
                            .clickable(enabled = !isSelected) { onTabSelected(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = labelColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private val SwitcherSegmentHeight = 34.dp
private val SwitcherTrackPadding = 4.dp
private val SwitcherElevation = 8.dp

// ─── Habit time-of-day grouping ─────────────────────────────────────────────

private enum class HabitTimeSlot { MORNING, AFTERNOON, EVENING, ANYTIME }

private fun habitTimeSlot(reminderTime: String?): HabitTimeSlot {
    val hour = reminderTime?.split(":")?.firstOrNull()?.toIntOrNull() ?: return HabitTimeSlot.ANYTIME
    return when (hour) {
        in 5..11 -> HabitTimeSlot.MORNING
        in 12..16 -> HabitTimeSlot.AFTERNOON
        else -> HabitTimeSlot.EVENING
    }
}

@Composable
private fun HabitSectionHeader(slot: HabitTimeSlot) {
    val (icon, label, tint) = when (slot) {
        HabitTimeSlot.MORNING -> Triple(PhosphorIcons.Regular.Sun, "Morning", Color(0xFFF59E0B))
        HabitTimeSlot.AFTERNOON -> Triple(PhosphorIcons.Regular.Sun, "Afternoon", Color(0xFFEF4444))
        HabitTimeSlot.EVENING -> Triple(PhosphorIcons.Regular.Moon, "Evening", Color(0xFF6366F1))
        HabitTimeSlot.ANYTIME -> Triple(PhosphorIcons.Regular.Sparkle, "Anytime", Color(0xFF6B7280))
    }
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
        Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
