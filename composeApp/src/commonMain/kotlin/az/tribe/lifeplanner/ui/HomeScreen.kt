package az.tribe.lifeplanner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.GoalFilter
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.ui.components.CategoryHeader
import az.tribe.lifeplanner.ui.components.EmptyGoalsView
import az.tribe.lifeplanner.ui.components.GoalItem
import az.tribe.lifeplanner.ui.components.ModernTopAppBar
import az.tribe.lifeplanner.ui.components.QuickStatsSection
import az.tribe.lifeplanner.ui.components.SearchResultsSummary
import az.tribe.lifeplanner.ui.components.backgroundColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: GoalViewModel,
    onGoalClick: (Goal) -> Unit,
    goToAnalytics: () -> Unit,
    onAddGoalClick: () -> Unit,
    goToAiGeneration: () -> Unit
) {
    val snackBarHostState = remember { SnackbarHostState() }

    val goals by viewModel.goals.collectAsState()
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    var selectedFilter by remember { mutableStateOf(GoalFilter.ALL) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    val scrollState = rememberLazyListState()

    // Animation for FAB
    var fabExpanded by remember { mutableStateOf(false) }

    // Filter goals based on search and filter criteria
    val filteredGoals = remember(goals, searchQuery.text, selectedFilter) {
        goals.filter { goal ->
            val matchesSearch = if (searchQuery.text.isBlank()) true
            else goal.title.contains(searchQuery.text, ignoreCase = true) ||
                    goal.description.contains(searchQuery.text, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                GoalFilter.ALL -> true
                GoalFilter.ACTIVE -> goal.status != GoalStatus.COMPLETED
                GoalFilter.COMPLETED -> goal.status == GoalStatus.COMPLETED
            }
            matchesSearch && matchesFilter
        }
    }

    // Category expansion state
    val categoryExpansionState = remember {
        mutableStateMapOf<String, Boolean>().apply {
            // Initialize all categories as expanded
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
                defaultColor // Default if empty
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
        viewModel.loadAnalytics()
    }

    // Update ViewModel search when local search changes
    LaunchedEffect(searchQuery.text, selectedFilter) {
        viewModel.updateSearchQuery(searchQuery.text)
        viewModel.updateFilter(selectedFilter)
    }

    // Main content
    Scaffold(
        topBar = {
            ModernTopAppBar(
                title = { if (!showSearchBar) Text("Life Planner") },
                dynamicColor = dynamicColor,
                showSearchBar = showSearchBar,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSearchToggle = { showSearchBar = it },
                onFilterClick = { showFilterMenu = true },
                onAnalyticsClick = goToAnalytics,
                selectedFilter = selectedFilter,
                showFilterMenu = showFilterMenu,
                onFilterMenuDismiss = { showFilterMenu = false },
                onFilterSelected = {
                    selectedFilter = it
                    showFilterMenu = false
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Extended FAB options
                AnimatedVisibility(
                    visible = fabExpanded,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // AI Generation option
                        SmallFloatingActionButton(
                            onClick = {
                                println("AI Generation option clicked")
                                goToAiGeneration()
                                fabExpanded = false
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null
                                )
                                Text("AI Suggest", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        // Manual creation option
                        SmallFloatingActionButton(
                            onClick = {
                                println("Manual creation option clicked")
                                onAddGoalClick()
                                fabExpanded = false
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = null
                                )
                                Text("Manual", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                // Main FAB
                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                    containerColor = dynamicColor,
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    Icon(
                        imageVector = if (fabExpanded) Icons.Rounded.Close else Icons.Filled.Add,
                        contentDescription = if (fabExpanded) "Close menu" else "Add Goal"
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackBarHostState) },

        ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            state = scrollState,
            contentPadding = PaddingValues(bottom = 100.dp), // Extra padding for FAB
        ) {

            // Search Results Summary
            if (searchQuery.text.isNotEmpty() || selectedFilter != GoalFilter.ALL) {
                item {
                    SearchResultsSummary(
                        resultCount = filteredGoals.size,
                        searchQuery = searchQuery.text,
                        selectedFilter = selectedFilter,
                        onClear = {
                            searchQuery = TextFieldValue("")
                            selectedFilter = GoalFilter.ALL
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
                        isFiltered = searchQuery.text.isNotEmpty() || selectedFilter != GoalFilter.ALL
                    )
                }
            } else {
                // Category headers and goals with expansion
                groupedGoals.keys.sortedBy { it.order }.forEach { category ->
                    val categoryGoals = groupedGoals[category].orEmpty()
                    val isCategoryExpanded = categoryExpansionState[category.name] ?: true

                    if (categoryGoals.isNotEmpty()) {
                        // Sticky header for the category
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

                        // Render goals if category is expanded
                        if (isCategoryExpanded) {
                            items(
                                items = categoryGoals,
                                key = { goal -> goal.id.toString() }
                            ) { goal ->
                                GoalItem(
                                    goal = goal,
                                    onClick = { onGoalClick(goal) },
                                    scrollState = scrollState
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}