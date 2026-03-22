package az.tribe.lifeplanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.GoalFilter
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.ui.components.AddGoalBottomSheet
import az.tribe.lifeplanner.ui.components.CategoryHeader
import az.tribe.lifeplanner.ui.components.EmptyGoalsView
import az.tribe.lifeplanner.ui.components.GoalsTopAppBar
import az.tribe.lifeplanner.ui.components.SwipeableGoalItem
import az.tribe.lifeplanner.ui.components.SearchResultsSummary
import az.tribe.lifeplanner.ui.components.backgroundColor
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    viewModel: GoalViewModel,
    onGoalClick: (Goal) -> Unit,
    onAddGoalClick: () -> Unit,
    onAiGenerateClick: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val snackBarHostState = remember { SnackbarHostState() }
    val goals by viewModel.goals.collectAsState()

    // Show smart reminder snackbar events
    LaunchedEffect(Unit) {
        viewModel.reminderEvent.collect { message ->
            snackBarHostState.showSnackbar(message)
        }
    }

    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showAddGoalSheet by remember { mutableStateOf(false) }

    val scrollState = rememberLazyListState()

    // Filter by search, then auto-sort: In Progress → Not Started → Completed
    val filteredGoals = remember(goals, searchQuery.text) {
        val statusOrder = mapOf(
            GoalStatus.IN_PROGRESS to 0,
            GoalStatus.NOT_STARTED to 1,
            GoalStatus.COMPLETED to 2
        )
        goals.filter { goal ->
            if (searchQuery.text.isBlank()) true
            else goal.title.contains(searchQuery.text, ignoreCase = true) ||
                    goal.description.contains(searchQuery.text, ignoreCase = true)
        }.sortedBy { statusOrder[it.status] ?: 1 }
    }

    // Category expansion state
    val categoryExpansionState = remember {
        mutableStateMapOf<String, Boolean>().apply {
            goals.map { it.category.name }.distinct().forEach { categoryName ->
                this[categoryName] = true
            }
        }
    }

    val defaultColor = MaterialTheme.colorScheme.primary

    // Calculate dynamic color based on scroll position
    val dynamicColor by remember {
        derivedStateOf {
            if (filteredGoals.isEmpty()) {
                defaultColor
            } else {
                val visibleGoals = scrollState.layoutInfo.visibleItemsInfo
                if (visibleGoals.isNotEmpty()) {
                    val firstVisibleGoalId = visibleGoals.firstOrNull()?.key
                    val firstVisibleGoal = filteredGoals.firstOrNull {
                        it.id.toString() == firstVisibleGoalId.toString()
                    }
                    firstVisibleGoal?.category?.backgroundColor() ?: defaultColor
                } else {
                    defaultColor
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadAllGoals()
    }

    LaunchedEffect(searchQuery.text) {
        viewModel.updateSearchQuery(searchQuery.text)
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            GoalsTopAppBar(
                dynamicColor = dynamicColor,
                showSearchBar = showSearchBar,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSearchToggle = { showSearchBar = it },
                onBack = onBack,
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            // Single FAB that opens the Add Goal bottom sheet
            // Wrapped in Box with bottom padding to stay above bottom nav
            Box(modifier = Modifier) {
                FloatingActionButton(
                    onClick = { showAddGoalSheet = true },
                    containerColor = dynamicColor,
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add Goal"
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackBarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.primary,
                    actionContentColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // Goals List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = LifePlannerDesign.Padding.screenVertical,
                    bottom = 140.dp // Space for bottom nav and FAB
                ),
                state = scrollState
            ) {
                // Search Results Summary
                if (searchQuery.text.isNotEmpty()) {
                    item {
                        SearchResultsSummary(
                            resultCount = filteredGoals.size,
                            searchQuery = searchQuery.text,
                            selectedFilter = GoalFilter.ALL,
                            onClear = {
                                searchQuery = TextFieldValue("")
                                showSearchBar = false
                            }
                        )
                    }
                }

                // Category headers and goals
                val groupedGoals = filteredGoals.groupBy { it.category }

                if (groupedGoals.isEmpty()) {
                    item {
                        EmptyGoalsView(
                            isFiltered = searchQuery.text.isNotEmpty(),
                            onQuickAddClick = onAddGoalClick,
                            onTemplatesClick = onAddGoalClick,
                            onAiGenerateClick = onAiGenerateClick,
                            onTemplateClick = { template ->
                                onAddGoalClick()
                            }
                        )
                    }
                } else {
                    groupedGoals.keys.sortedBy { it.order }.forEach { category ->
                        val categoryGoals = groupedGoals[category].orEmpty()
                        val isCategoryExpanded = categoryExpansionState[category.name] ?: true

                        if (categoryGoals.isNotEmpty()) {
                            stickyHeader(key = "header_${category.name}") {
                                CategoryHeader(
                                    category = category,
                                    goalCount = categoryGoals.size,
                                    expanded = isCategoryExpanded,
                                    onExpandChange = { isExpanded ->
                                        categoryExpansionState[category.name] = isExpanded
                                    }
                                )
                            }

                            if (isCategoryExpanded) {
                                items(
                                    items = categoryGoals,
                                    key = { goal -> goal.id.toString() }
                                ) { goal ->
                                    SwipeableGoalItem(
                                        goal = goal,
                                        onClick = { onGoalClick(goal) },
                                        onComplete = {
                                            viewModel.updateGoalStatus(goal.id, GoalStatus.COMPLETED)
                                        },
                                        onDelete = {
                                            viewModel.deleteGoal(goal.id)
                                        },
                                        scrollState = scrollState,
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Goal Bottom Sheet
    if (showAddGoalSheet) {
        AddGoalBottomSheet(
            onDismiss = { showAddGoalSheet = false },
            onQuickAddClick = {
                showAddGoalSheet = false
                onAddGoalClick()
            },
            onAiGenerateClick = {
                showAddGoalSheet = false
                onAiGenerateClick()
            }
        )
    }
}
