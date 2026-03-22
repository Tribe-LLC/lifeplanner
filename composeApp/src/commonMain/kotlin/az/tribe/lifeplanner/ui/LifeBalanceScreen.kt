package az.tribe.lifeplanner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.min
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.domain.model.BalanceInsight
import az.tribe.lifeplanner.domain.model.BalanceRating
import az.tribe.lifeplanner.domain.model.BalanceRecommendation
import az.tribe.lifeplanner.domain.model.BalanceTrend
import az.tribe.lifeplanner.domain.model.CoachPersona
import az.tribe.lifeplanner.domain.model.InsightPriority
import az.tribe.lifeplanner.domain.model.LifeArea
import az.tribe.lifeplanner.domain.model.LifeAreaScore
import az.tribe.lifeplanner.domain.model.LifeBalanceReport
import az.tribe.lifeplanner.domain.model.BalanceRecommendationAction
import az.tribe.lifeplanner.ui.balance.LifeBalanceViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeBalanceScreen(
    viewModel: LifeBalanceViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onCreateHabit: (LifeArea) -> Unit = {},
    onNavigateToCoach: (coachId: String, autoMessage: String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Mark CHECK_LIFE_BALANCE objective as completed on visit
    val objectiveViewModel: az.tribe.lifeplanner.ui.objectives.BeginnerObjectiveViewModel = koinViewModel()
    LaunchedEffect(Unit) {
        objectiveViewModel.markObjectiveCompleted(az.tribe.lifeplanner.domain.model.ObjectiveType.CHECK_LIFE_BALANCE)
    }

    // Show snackbar when goal is created
    LaunchedEffect(uiState.goalCreatedFeedback) {
        uiState.goalCreatedFeedback?.let { feedback ->
            snackbarHostState.showSnackbar(feedback)
            viewModel.clearGoalFeedback()
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Life Balance",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadBalance() }) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Analyzing your life balance...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        uiState.error ?: "Unknown error",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadBalance() }) {
                        Text("Try Again")
                    }
                }
            }
        } else {
            uiState.report?.let { report ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Overall Score Card
                    item {
                        OverallScoreCard(report)
                    }

                    // Balance Wheel
                    item {
                        BalanceWheelCard(
                            areaScores = report.areaScores,
                            selectedArea = uiState.selectedArea,
                            onAreaSelected = { viewModel.selectArea(it) }
                        )
                    }

                    // Selected Area Details
                    uiState.selectedArea?.let { area ->
                        val areaScore = report.areaScores.find { it.area == area }
                        if (areaScore != null) {
                            item {
                                AreaDetailCard(areaScore = areaScore)
                            }
                        }
                    }

                    // Area Scores List
                    item {
                        Text(
                            "Area Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(report.areaScores.sortedByDescending { it.score }) { areaScore ->
                        AreaScoreRow(
                            areaScore = areaScore,
                            isSelected = areaScore.area == uiState.selectedArea,
                            onClick = {
                                viewModel.selectArea(
                                    if (uiState.selectedArea == areaScore.area) null
                                    else areaScore.area
                                )
                            }
                        )
                    }

                    // AI Insights
                    if (report.aiInsights.isNotEmpty()) {
                        item {
                            Text(
                                "Insights",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(report.aiInsights) { insight ->
                            InsightCard(
                                insight = insight,
                                onGetAdvice = { viewModel.showCoachSheetForInsight(it) }
                            )
                        }
                    }

                    // Recommendations
                    if (report.recommendations.isNotEmpty()) {
                        item {
                            Text(
                                "Recommendations",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(report.recommendations) { recommendation ->
                            RecommendationCard(
                                recommendation = recommendation,
                                isPreGenerating = uiState.isPreGenerating,
                                isCreated = uiState.createdGoalIds.contains(recommendation.targetArea.name),
                                onCreateGoal = { viewModel.createGoalFromRecommendation(recommendation) },
                                onCreateHabit = { onCreateHabit(recommendation.targetArea) }
                            )
                        }
                    }

                    // Bottom spacing
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    // Coach Selection Bottom Sheet
    val currentSelectedInsight = uiState.selectedInsight
    if (uiState.showCoachSheet && currentSelectedInsight != null) {
        CoachSelectionSheet(
            insight = currentSelectedInsight,
            relevantCoaches = uiState.relevantCoaches,
            onCoachSelected = { coachId ->
                val message = viewModel.buildInsightMessage(currentSelectedInsight)
                viewModel.hideCoachSheet()
                onNavigateToCoach(coachId, message)
            },
            onCouncilSelected = {
                val message = viewModel.buildInsightMessage(currentSelectedInsight)
                viewModel.hideCoachSheet()
                onNavigateToCoach(CoachPersona.COUNCIL_ID, message)
            },
            onDismiss = { viewModel.hideCoachSheet() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoachSelectionSheet(
    insight: BalanceInsight,
    relevantCoaches: List<CoachPersona>,
    onCoachSelected: (String) -> Unit,
    onCouncilSelected: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                "Who should you talk to?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "About: ${insight.title}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Recommended coaches section
            Text(
                "Recommended",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            relevantCoaches.forEach { coach ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCoachSelected(coach.id) },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(coach.emoji, fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                coach.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                coach.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Group Discussion section
            Text(
                "Group Discussion",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCouncilSelected() },
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.tertiaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "The Council",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "All coaches discuss together",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OverallScoreCard(report: LifeBalanceReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Overall Balance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "${report.overallScore}",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Text(
                "/ 100",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Rating Badge
            val ratingColor = when (report.balanceRating) {
                BalanceRating.EXCELLENT -> Color(0xFF4CAF50)
                BalanceRating.GOOD -> Color(0xFF8BC34A)
                BalanceRating.MODERATE -> Color(0xFFFFC107)
                BalanceRating.NEEDS_ATTENTION -> Color(0xFFFF9800)
                BalanceRating.CRITICAL -> Color(0xFFF44336)
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(ratingColor.copy(alpha = 0.2f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    report.balanceRating.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = ratingColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                report.balanceRating.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BalanceWheelCard(
    areaScores: List<LifeAreaScore>,
    selectedArea: LifeArea?,
    onAreaSelected: (LifeArea?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Balance Wheel",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Donut Chart with center score
            Box(
                modifier = Modifier.size(280.dp),
                contentAlignment = Alignment.Center
            ) {
                BalanceWheel(
                    areaScores = areaScores,
                    selectedArea = selectedArea,
                    onAreaClick = onAreaSelected,
                    modifier = Modifier.fillMaxSize()
                )

                // Center score display
                val averageScore = if (areaScores.isNotEmpty()) {
                    areaScores.sumOf { it.score } / areaScores.size
                } else 0

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$averageScore",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "avg",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legend
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(areaScores) { score ->
                    FilterChip(
                        selected = selectedArea == score.area,
                        onClick = {
                            onAreaSelected(if (selectedArea == score.area) null else score.area)
                        },
                        label = {
                            Text(
                                "${score.area.icon} ${score.score}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = getAreaColor(score.area).copy(alpha = 0.2f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceWheel(
    areaScores: List<LifeAreaScore>,
    selectedArea: LifeArea?,
    onAreaClick: (LifeArea?) -> Unit,
    modifier: Modifier = Modifier
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val outerRadius = min(size.width, size.height) / 2 * 0.9f
        val innerRadius = outerRadius * 0.5f
        val donutThickness = outerRadius - innerRadius

        val numberOfAreas = areaScores.size
        if (numberOfAreas == 0) return@Canvas

        val sweepAngle = 360f / numberOfAreas
        val gapAngle = 3f // Small gap between segments
        val actualSweep = sweepAngle - gapAngle

        // Start from top (-90 degrees)
        var currentAngle = -90f

        areaScores.forEach { areaScore ->
            val areaColor = getAreaColor(areaScore.area)
            val normalizedScore = areaScore.score / 100f
            val isSelected = areaScore.area == selectedArea

            // Calculate the filled thickness based on score
            // Score determines how much of the donut segment is filled (from outer edge inward)
            val filledThickness = donutThickness * normalizedScore
            val unfilledThickness = donutThickness - filledThickness

            // Draw background (unfilled) arc
            drawArc(
                color = surfaceVariant,
                startAngle = currentAngle + gapAngle / 2,
                sweepAngle = actualSweep,
                useCenter = false,
                topLeft = Offset(
                    centerX - outerRadius + unfilledThickness / 2,
                    centerY - outerRadius + unfilledThickness / 2
                ),
                size = androidx.compose.ui.geometry.Size(
                    (outerRadius - unfilledThickness / 2) * 2,
                    (outerRadius - unfilledThickness / 2) * 2
                ),
                style = Stroke(
                    width = filledThickness,
                    cap = androidx.compose.ui.graphics.StrokeCap.Butt
                )
            )

            // Draw filled arc (from outer edge)
            if (normalizedScore > 0) {
                drawArc(
                    color = if (isSelected) areaColor else areaColor.copy(alpha = 0.85f),
                    startAngle = currentAngle + gapAngle / 2,
                    sweepAngle = actualSweep,
                    useCenter = false,
                    topLeft = Offset(
                        centerX - outerRadius + filledThickness / 2,
                        centerY - outerRadius + filledThickness / 2
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        (outerRadius - filledThickness / 2) * 2,
                        (outerRadius - filledThickness / 2) * 2
                    ),
                    style = Stroke(
                        width = filledThickness,
                        cap = androidx.compose.ui.graphics.StrokeCap.Butt
                    )
                )
            }

            // Draw selection highlight
            if (isSelected) {
                drawArc(
                    color = areaColor,
                    startAngle = currentAngle + gapAngle / 2,
                    sweepAngle = actualSweep,
                    useCenter = false,
                    topLeft = Offset(
                        centerX - outerRadius - 4,
                        centerY - outerRadius - 4
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        (outerRadius + 4) * 2,
                        (outerRadius + 4) * 2
                    ),
                    style = Stroke(width = 3f)
                )
            }

            currentAngle += sweepAngle
        }

        // Draw inner circle (hole of the donut) for cleaner look
        drawCircle(
            color = Color.Transparent,
            radius = innerRadius,
            center = Offset(centerX, centerY)
        )
    }
}

@Composable
private fun AreaScoreRow(
    areaScore: LifeAreaScore,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            getAreaColor(areaScore.area).copy(alpha = 0.1f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "bg_color"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Text(
                areaScore.area.icon,
                fontSize = 28.sp
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Name and stats
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        areaScore.area.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Trend indicator
                    when (areaScore.trend) {
                        BalanceTrend.IMPROVING -> Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = "Improving",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                        BalanceTrend.DECLINING -> Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Declining",
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(20.dp)
                        )
                        BalanceTrend.STABLE -> {}
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    "${areaScore.goalCount} goals • ${areaScore.habitCount} habits",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Progress bar
                LinearProgressIndicator(
                    progress = { areaScore.score / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = getAreaColor(areaScore.area),
                    trackColor = getAreaColor(areaScore.area).copy(alpha = 0.2f)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Score
            Text(
                "${areaScore.score}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = getScoreColor(areaScore.score)
            )
        }
    }
}

@Composable
private fun AreaDetailCard(
    areaScore: LifeAreaScore
) {
    val areaColor = getAreaColor(areaScore.area)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = areaColor.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with icon and title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Icon with colored background
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(areaColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(areaScore.area.icon, fontSize = 24.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        areaScore.area.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        areaScore.area.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Score badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = areaColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        "${areaScore.score}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = areaColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats Row - 2x2 grid style
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(
                    label = "Goals",
                    value = "${areaScore.goalCount}",
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = "Completed",
                    value = "${areaScore.completedGoals}",
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = "Habits",
                    value = "${areaScore.habitCount}",
                    modifier = Modifier.weight(1f)
                )
            }

            // Habit completion progress
            if (areaScore.habitCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Habit Completion",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${(areaScore.habitCompletionRate * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = areaColor
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { areaScore.habitCompletionRate },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = areaColor,
                            trackColor = areaColor.copy(alpha = 0.2f)
                        )
                    }
                }
            }

        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun InsightCard(
    insight: BalanceInsight,
    onGetAdvice: (BalanceInsight) -> Unit
) {
    val priorityColor = when (insight.priority) {
        InsightPriority.HIGH -> Color(0xFFF44336)
        InsightPriority.MEDIUM -> Color(0xFFFFC107)
        InsightPriority.LOW -> Color(0xFF4CAF50)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(priorityColor)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        insight.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        insight.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (insight.relatedAreas.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            insight.relatedAreas.forEach { area ->
                                Text(
                                    area.icon,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            // Get Advice button
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { onGetAdvice(insight) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Get Advice", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: BalanceRecommendation,
    isPreGenerating: Boolean,
    isCreated: Boolean,
    onCreateGoal: () -> Unit,
    onCreateHabit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    recommendation.targetArea.icon,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        recommendation.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        recommendation.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when (recommendation.actionType) {
                BalanceRecommendationAction.CREATE_GOAL -> {
                    val goal = recommendation.preGeneratedGoal

                    if (goal != null) {
                        // Show goal preview
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    goal.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    goal.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Milestones count badge
                                    if (goal.milestones.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Flag,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    "${goal.milestones.size} milestones",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }

                                    // Timeline badge
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer
                                    ) {
                                        Text(
                                            when (goal.timeline) {
                                                az.tribe.lifeplanner.domain.enum.GoalTimeline.SHORT_TERM -> "30 days"
                                                az.tribe.lifeplanner.domain.enum.GoalTimeline.MID_TERM -> "90 days"
                                                az.tribe.lifeplanner.domain.enum.GoalTimeline.LONG_TERM -> "1 year"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Add to My Goals button
                        Button(
                            onClick = onCreateGoal,
                            enabled = !isCreated,
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (isCreated) {
                                ButtonDefaults.buttonColors(
                                    disabledContainerColor = Color(0xFF4CAF50).copy(alpha = 0.12f),
                                    disabledContentColor = Color(0xFF4CAF50)
                                )
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isCreated) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Added")
                            } else {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add to My Goals")
                            }
                        }
                    } else if (isPreGenerating) {
                        // Shimmer/loading state
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Generating smart goal...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // Fallback: show suggestion text with old-style button
                        if (recommendation.suggestedGoal != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        recommendation.suggestedGoal,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                BalanceRecommendationAction.CREATE_HABIT -> {
                    if (recommendation.suggestedHabit != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    recommendation.suggestedHabit,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(
                                    onClick = onCreateHabit,
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Create Habit", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                else -> {} // INCREASE_FOCUS, REDUCE_FOCUS, MAINTAIN_CURRENT - no action button
            }
        }
    }
}

// Helper functions

private fun getAreaColor(area: LifeArea): Color {
    return when (area) {
        LifeArea.CAREER -> Color(0xFF5E35B1)
        LifeArea.FINANCIAL -> Color(0xFF43A047)
        LifeArea.PHYSICAL -> Color(0xFFE53935)
        LifeArea.SOCIAL -> Color(0xFF1E88E5)
        LifeArea.EMOTIONAL -> Color(0xFFFF9800)
        LifeArea.SPIRITUAL -> Color(0xFF8E24AA)
        LifeArea.FAMILY -> Color(0xFFE91E63)
        LifeArea.PERSONAL_GROWTH -> Color(0xFF00ACC1)
    }
}

private fun getScoreColor(score: Int): Color {
    return when {
        score >= 70 -> Color(0xFF4CAF50)
        score >= 50 -> Color(0xFF8BC34A)
        score >= 30 -> Color(0xFFFFC107)
        score >= 15 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
}
