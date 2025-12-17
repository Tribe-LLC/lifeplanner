package az.tribe.lifeplanner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.ui.components.DailyMotivationCard
import az.tribe.lifeplanner.ui.components.DashboardStatsRow
import az.tribe.lifeplanner.ui.components.QuickActionsRow
import az.tribe.lifeplanner.ui.components.TodaysFocusSection
import az.tribe.lifeplanner.ui.components.WelcomeHeader
import az.tribe.lifeplanner.ui.gamification.GamificationViewModel
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign
import az.tribe.lifeplanner.ui.viewmodel.AuthState
import az.tribe.lifeplanner.ui.viewmodel.AuthViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

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

    // Inject ViewModels
    val authViewModel: AuthViewModel = koinInject()
    val gamificationViewModel: GamificationViewModel = koinViewModel()

    val authState by authViewModel.authState.collectAsState()
    val userProgress by gamificationViewModel.userProgress.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val analytics by viewModel.analytics.collectAsState()

    // Extract current user
    val currentUser = when (authState) {
        is AuthState.Authenticated -> (authState as AuthState.Authenticated).user
        is AuthState.Guest -> (authState as AuthState.Guest).user
        else -> null
    }

    // FAB state (simplified - only 2 options)
    var fabExpanded by remember { mutableStateOf(false) }

    // Calculate dashboard stats
    val activeGoals = goals.filter { it.status != GoalStatus.COMPLETED }.size
    val completedGoals = goals.filter { it.status == GoalStatus.COMPLETED }.size
    val totalProgress = if (goals.isNotEmpty()) {
        (goals.sumOf { it.progress ?: 0L } / goals.size).toInt()
    } else 0

    // Get upcoming goals (due soon, not completed)
    val upcomingGoals = goals
        .filter { it.status != GoalStatus.COMPLETED }
        .sortedBy { it.dueDate }
        .take(5)

    LaunchedEffect(Unit) {
        viewModel.loadAllGoals()
        viewModel.loadAnalytics()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Life Planner",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            // Simplified FAB with only 2 options
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedVisibility(
                    visible = fabExpanded,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // AI Suggest option
                        SmallFloatingActionButton(
                            onClick = {
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

                        // Manual Add option
                        SmallFloatingActionButton(
                            onClick = {
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
                                Text("Add Goal", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }

                // Main FAB
                FloatingActionButton(
                    onClick = { fabExpanded = !fabExpanded },
                    containerColor = MaterialTheme.colorScheme.primary,
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
        snackbarHost = { SnackbarHost(snackBarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues = PaddingValues(top = innerPadding.calculateTopPadding())),
            contentPadding = PaddingValues(horizontal = LifePlannerDesign.Padding.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.lg)
        ) {
            // Welcome Header with streak
            item {
                WelcomeHeader(
                    userName = currentUser?.displayName,
                    streak = userProgress?.currentStreak ?: 0
                )
            }

            // Dashboard Stats Row
            item {
                DashboardStatsRow(
                    activeGoals = activeGoals,
                    completedGoals = completedGoals,
                    totalProgress = totalProgress
                )
            }

            // Daily Motivation Card
            item {
                DailyMotivationCard()
            }

            // Quick Actions
            item {
                QuickActionsRow(
                    onAddGoalClick = onAddGoalClick,
                    onAiSuggestClick = goToAiGeneration
                )
            }

            // Today's Focus Section
            item {
                TodaysFocusSection(
                    upcomingGoals = upcomingGoals,
                    onGoalClick = onGoalClick
                )
            }

        }
    }
}
