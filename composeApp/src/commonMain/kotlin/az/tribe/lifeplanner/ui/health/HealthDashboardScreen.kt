package az.tribe.lifeplanner.ui.health

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.ArrowLeft
import com.adamglin.phosphoricons.bold.Plus
import com.adamglin.phosphoricons.bold.Footprints
import com.adamglin.phosphoricons.bold.Barbell
import com.adamglin.phosphoricons.bold.ArrowsClockwise
import com.adamglin.phosphoricons.bold.CloudCheck
import com.adamglin.phosphoricons.bold.CloudArrowUp
import com.adamglin.phosphoricons.bold.CloudSlash
import az.tribe.lifeplanner.ui.components.AnimatedIcon
import az.tribe.lifeplanner.ui.components.IconAnimationEffect
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import com.adamglin.phosphoricons.bold.TrendDown
import com.adamglin.phosphoricons.bold.TrendUp
import com.adamglin.phosphoricons.bold.Minus
import com.adamglin.phosphoricons.bold.Heartbeat
import com.adamglin.phosphoricons.bold.Moon
import com.adamglin.phosphoricons.bold.CaretDown
import com.adamglin.phosphoricons.bold.CaretUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import az.tribe.lifeplanner.domain.model.HealthMetric
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.number
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthDashboardScreen(
    navController: NavController,
    viewModel: HealthViewModel = koinViewModel()
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val permissionState by viewModel.permissionState.collectAsState()
    val todaySteps by viewModel.todaySteps.collectAsState()
    val stepsHistory by viewModel.stepsHistory.collectAsState()
    val weightHistory by viewModel.weightHistory.collectAsState()
    val latestWeight by viewModel.latestWeight.collectAsState()
    val heartRateHistory by viewModel.heartRateHistory.collectAsState()
    val latestHeartRate by viewModel.latestHeartRate.collectAsState()
    val sleepHistory by viewModel.sleepHistory.collectAsState()
    val latestSleep by viewModel.latestSleep.collectAsState()
    val showWeightDialog by viewModel.showWeightDialog.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    val requestPermissions = rememberHealthPermissionLauncher { granted ->
        if (granted) {
            viewModel.onPermissionsGranted()
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(PhosphorIcons.Bold.ArrowLeft, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.syncHealth() }) {
                        val syncIcon = when {
                            isLoading -> PhosphorIcons.Bold.CloudArrowUp
                            permissionState == HealthPermissionState.DENIED ||
                            permissionState == HealthPermissionState.NOT_AVAILABLE -> PhosphorIcons.Bold.CloudSlash
                            else -> PhosphorIcons.Bold.CloudCheck
                        }
                        val syncTint = when {
                            isLoading -> MaterialTheme.colorScheme.primary
                            permissionState == HealthPermissionState.DENIED ||
                            permissionState == HealthPermissionState.NOT_AVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.primary
                        }
                        AnimatedIcon(
                            imageVector = syncIcon,
                            contentDescription = "Sync",
                            animate = !isLoading && permissionState == HealthPermissionState.GRANTED,
                            tint = syncTint,
                            effect = IconAnimationEffect.PULSE
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                isLoading && todaySteps == null && weightHistory.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                permissionState == HealthPermissionState.NOT_AVAILABLE -> {
                    HealthNotAvailableCard()
                }
                permissionState == HealthPermissionState.DENIED -> {
                    PermissionDeniedCard(onRequestPermissions = requestPermissions)
                }
                else -> {
                    // Steps card with ring
                    StepsCard(todaySteps = todaySteps, stepsGoal = 10_000L)

                    // Steps last 7 days — collapsed, just total
                    if (stepsHistory.isNotEmpty()) {
                        val weeklyTotal = stepsHistory.sumOf { it.value }.toLong()
                        ExpandableMetricCard(
                            title = "Last 7 Days",
                            icon = PhosphorIcons.Bold.Footprints,
                            iconTint = MaterialTheme.colorScheme.primary,
                            summaryValue = formatCompact(weeklyTotal.toDouble()),
                            initialExpanded = false
                        ) {
                            StepsDetailView(data = stepsHistory)
                        }
                    }

                    // Heart rate — collapsed, just latest avg
                    if (heartRateHistory.isNotEmpty() || latestHeartRate != null) {
                        ExpandableMetricCard(
                            title = "Heart Rate",
                            icon = PhosphorIcons.Bold.Heartbeat,
                            iconTint = Color(0xFFE57373),
                            summaryValue = latestHeartRate?.let { "${it.roundToInt()} bpm" },
                            initialExpanded = false
                        ) {
                            HeartRateDetailView(data = heartRateHistory)
                        }
                    }

                    // Sleep — collapsed, just last night
                    if (sleepHistory.isNotEmpty() || latestSleep != null) {
                        ExpandableMetricCard(
                            title = "Sleep",
                            icon = PhosphorIcons.Bold.Moon,
                            iconTint = Color(0xFF7986CB),
                            summaryValue = latestSleep?.let { formatSleepDuration(it) },
                            initialExpanded = false
                        ) {
                            SleepDetailView(data = sleepHistory)
                        }
                    }

                    // Weight card
                    WeightCard(
                        latestWeight = latestWeight,
                        weightHistory = weightHistory,
                        onAddWeight = { viewModel.showAddWeightDialog() }
                    )

                    // Weight trend — collapsed
                    if (weightHistory.size >= 2) {
                        ExpandableMetricCard(
                            title = "Weight Trend",
                            icon = PhosphorIcons.Bold.Barbell,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            initialExpanded = false
                        ) {
                            WeightDetailView(data = weightHistory)
                        }
                    }
                }
            }
        }
    }

    if (showWeightDialog) {
        ManualWeightDialog(
            onDismiss = { viewModel.dismissWeightDialog() },
            onConfirm = { weight -> viewModel.addManualWeight(weight) }
        )
    }
}

@Composable
private fun ExpandableMetricCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    summaryValue: String? = null,
    initialExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initialExpanded) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = iconTint
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (summaryValue != null && !expanded) {
                        Text(
                            text = summaryValue,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = iconTint
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(
                        if (expanded) PhosphorIcons.Bold.CaretUp else PhosphorIcons.Bold.CaretDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun StepsDetailView(data: List<HealthMetric>, stepsGoal: Long = 10_000L) {
    if (data.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No data yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val maxSteps = data.maxOf { it.value }.coerceAtLeast(stepsGoal.toDouble())

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        data.forEach { metric ->
            val fraction = (metric.value / maxSteps).toFloat().coerceIn(0f, 1f)
            val animatedFraction by animateFloatAsState(
                targetValue = fraction,
                animationSpec = tween(600)
            )
            val metGoal = metric.value >= stepsGoal
            val barColor = if (metGoal) Color(0xFF4CAF50) else Color(0xFFA5D6A7)
            val todayHighlight = isToday(metric.date)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (todayHighlight) Modifier.background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        ) else Modifier
                    )
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${dayLabel(metric.date)} ${metric.date.day}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (todayHighlight) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.width(52.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedFraction)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(barColor)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatCompact(metric.value),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.End
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Summary row
        val dailyAvg = data.sumOf { it.value } / data.size
        val weeklyTotal = data.sumOf { it.value }.toLong()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Daily Average",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatCompact(dailyAvg),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Weekly Total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatCompact(weeklyTotal.toDouble()),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HeartRateDetailView(data: List<HealthMetric>) {
    if (data.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No data yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val minBpm = data.minOf { it.value }
    val maxBpm = data.maxOf { it.value }
    val avgBpm = data.sumOf { it.value } / data.size

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Top stats: Min / Avg / Max
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${minBpm.roundToInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF42A5F5)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Avg",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${avgBpm.roundToInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF66BB6A)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Max",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${maxBpm.roundToInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFA726)
                )
            }
        }

        // Canvas line chart with cubic Bezier
        val range = (maxBpm - minBpm).coerceAtLeast(1.0)
        val lineColor = Color(0xFFE57373)

        if (data.size == 1) {
            // Single data point — just show the value centered
            Box(
                modifier = Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${data[0].value.roundToInt()} bpm",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = heartRateZoneColor(data[0].value)
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val paddingY = 16.dp.toPx()
                val chartHeight = size.height - paddingY * 2
                val stepX = size.width / (data.size - 1)

                fun yPos(value: Double): Float =
                    paddingY + chartHeight - ((value - minBpm) / range * chartHeight).toFloat()

                // Gradient fill under curve
                val fillPath = Path().apply {
                    moveTo(0f, size.height)
                    // First point
                    lineTo(0f, yPos(data[0].value))
                    for (i in 0 until data.size - 1) {
                        val x0 = i * stepX
                        val x1 = (i + 1) * stepX
                        val y0 = yPos(data[i].value)
                        val y1 = yPos(data[i + 1].value)
                        val cx = (x0 + x1) / 2f
                        cubicTo(cx, y0, cx, y1, x1, y1)
                    }
                    lineTo((data.size - 1) * stepX, size.height)
                    close()
                }
                drawPath(
                    fillPath,
                    Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.3f), lineColor.copy(alpha = 0.0f))
                    )
                )

                // Smooth line
                val linePath = Path().apply {
                    moveTo(0f, yPos(data[0].value))
                    for (i in 0 until data.size - 1) {
                        val x0 = i * stepX
                        val x1 = (i + 1) * stepX
                        val y0 = yPos(data[i].value)
                        val y1 = yPos(data[i + 1].value)
                        val cx = (x0 + x1) / 2f
                        cubicTo(cx, y0, cx, y1, x1, y1)
                    }
                }
                drawPath(linePath, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

                // Dots colored by zone
                data.forEachIndexed { index, metric ->
                    val x = index * stepX
                    val y = yPos(metric.value)
                    val zoneColor = heartRateZoneColor(metric.value)
                    drawCircle(color = zoneColor, radius = 5.dp.toPx(), center = Offset(x, y))
                    drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(x, y))
                }
            }
        }

        // X-axis day labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEach { metric ->
                Text(
                    text = dayLabel(metric.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Zone legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ZoneDot(color = Color(0xFF42A5F5), label = "<60 Low")
            ZoneDot(color = Color(0xFF66BB6A), label = "60-100 Normal")
            ZoneDot(color = Color(0xFFFFA726), label = ">100 High")
        }
    }
}

@Composable
private fun SleepDetailView(data: List<HealthMetric>, sleepGoal: Double = 8.0) {
    if (data.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No data yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val maxHours = data.maxOf { it.value }.coerceAtLeast(sleepGoal)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        data.forEach { metric ->
            val fraction = (metric.value / maxHours).toFloat().coerceIn(0f, 1f)
            val animatedFraction by animateFloatAsState(
                targetValue = fraction,
                animationSpec = tween(600)
            )
            val color = sleepColor(metric.value)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${dayLabel(metric.date)} ${metric.date.day}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(52.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedFraction)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(color)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatSleepDuration(metric.value),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(48.dp),
                    textAlign = TextAlign.End
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Summary
        val weeklyAvg = data.sumOf { it.value } / data.size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Weekly Avg",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatSleepDuration(weeklyAvg),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${sleepGoal.toInt()}h goal",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (weeklyAvg >= sleepGoal) "Meeting goal" else "Below goal",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (weeklyAvg >= sleepGoal) Color(0xFF66BB6A) else Color(0xFFFFA726)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Color legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ZoneDot(color = Color(0xFFEF5350), label = "<6h")
            ZoneDot(color = Color(0xFFFFA726), label = "6-7h")
            ZoneDot(color = Color(0xFF66BB6A), label = "7-9h")
            ZoneDot(color = Color(0xFF42A5F5), label = ">9h")
        }
    }
}

@Composable
private fun WeightDetailView(data: List<HealthMetric>) {
    if (data.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No data yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val minWeight = data.minOf { it.value }
    val maxWeight = data.maxOf { it.value }
    val lineColor = Color(0xFF7E57C2)

    // Trend calculation
    val trendText = if (data.size >= 2) {
        val first = data.first().value
        val last = data.last().value
        val diff = last - first
        val rounded = (diff * 10).roundToInt() / 10.0
        when {
            rounded < -0.05 -> {
                val display = ((-rounded) * 10).roundToInt() / 10.0
                "-$display kg this week"
            }
            rounded > 0.05 -> {
                val display = (rounded * 10).roundToInt() / 10.0
                "+$display kg this week"
            }
            else -> "Stable this week"
        }
    } else {
        "Stable this week"
    }

    val trendIcon = if (data.size >= 2) {
        val diff = data.last().value - data.first().value
        when {
            diff < -0.05 -> PhosphorIcons.Bold.TrendDown
            diff > 0.05 -> PhosphorIcons.Bold.TrendUp
            else -> PhosphorIcons.Bold.Minus
        }
    } else {
        PhosphorIcons.Bold.Minus
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Trend header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                trendIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = lineColor
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = trendText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = lineColor
            )
        }

        if (data.size == 1) {
            Box(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                val w = data[0].value
                val display = (w * 10).roundToInt() / 10.0
                Text(
                    text = "$display kg",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = lineColor
                )
            }
        } else {
            val range = (maxWeight - minWeight).coerceAtLeast(0.1)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val paddingY = 16.dp.toPx()
                val chartHeight = size.height - paddingY * 2
                val stepX = size.width / (data.size - 1)

                fun yPos(value: Double): Float =
                    paddingY + chartHeight - ((value - minWeight) / range * chartHeight).toFloat()

                // Gradient fill
                val fillPath = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(0f, yPos(data[0].value))
                    for (i in 0 until data.size - 1) {
                        val x0 = i * stepX
                        val x1 = (i + 1) * stepX
                        val y0 = yPos(data[i].value)
                        val y1 = yPos(data[i + 1].value)
                        val cx = (x0 + x1) / 2f
                        cubicTo(cx, y0, cx, y1, x1, y1)
                    }
                    lineTo((data.size - 1) * stepX, size.height)
                    close()
                }
                drawPath(
                    fillPath,
                    Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.25f), lineColor.copy(alpha = 0.0f))
                    )
                )

                // Smooth line
                val linePath = Path().apply {
                    moveTo(0f, yPos(data[0].value))
                    for (i in 0 until data.size - 1) {
                        val x0 = i * stepX
                        val x1 = (i + 1) * stepX
                        val y0 = yPos(data[i].value)
                        val y1 = yPos(data[i + 1].value)
                        val cx = (x0 + x1) / 2f
                        cubicTo(cx, y0, cx, y1, x1, y1)
                    }
                }
                drawPath(linePath, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

                // Dots
                data.forEachIndexed { index, metric ->
                    val x = index * stepX
                    val y = yPos(metric.value)
                    drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(x, y))
                    drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = Offset(x, y))
                }
            }
        }

        // X-axis day labels
        if (data.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                data.forEach { metric ->
                    Text(
                        text = "${metric.date.day}/${metric.date.month.number}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Min / Max
        val minDisplay = (minWeight * 10).roundToInt() / 10.0
        val maxDisplay = (maxWeight * 10).roundToInt() / 10.0
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Min: $minDisplay kg",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Max: $maxDisplay kg",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ZoneDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun dayLabel(date: LocalDate): String {
    return when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "Mon"
        DayOfWeek.TUESDAY -> "Tue"
        DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"
        DayOfWeek.FRIDAY -> "Fri"
        DayOfWeek.SATURDAY -> "Sat"
        DayOfWeek.SUNDAY -> "Sun"
        else -> ""
    }
}

private fun isToday(date: LocalDate): Boolean {
    val today = kotlinx.datetime.Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date
    return date == today
}

private fun sleepColor(hours: Double): Color = when {
    hours < 6.0 -> Color(0xFFEF5350)
    hours < 7.0 -> Color(0xFFFFA726)
    hours <= 9.0 -> Color(0xFF66BB6A)
    else -> Color(0xFF42A5F5)
}

private fun heartRateZoneColor(bpm: Double): Color = when {
    bpm < 60.0 -> Color(0xFF42A5F5)
    bpm <= 100.0 -> Color(0xFF66BB6A)
    else -> Color(0xFFFFA726)
}

@Composable
private fun StepsCard(todaySteps: Long?, stepsGoal: Long) {
    val steps = todaySteps ?: 0L
    val progress = (steps.toFloat() / stepsGoal).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(100.dp)
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)

                Canvas(modifier = Modifier.size(100.dp)) {
                    val strokeWidth = 10.dp.toPx()
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = primaryColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        PhosphorIcons.Bold.Footprints,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${(progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column {
                Text(
                    text = "Today's Steps",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = steps.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Goal: $stepsGoal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun WeightCard(
    latestWeight: Double?,
    weightHistory: List<HealthMetric>,
    onAddWeight: () -> Unit
) {
    val trend = if (weightHistory.size >= 2) {
        val recent = weightHistory.takeLast(2)
        val diff = recent.last().value - recent.first().value
        when {
            diff > 0.1 -> WeightTrend.UP
            diff < -0.1 -> WeightTrend.DOWN
            else -> WeightTrend.STABLE
        }
    } else WeightTrend.STABLE

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    PhosphorIcons.Bold.Barbell,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Weight",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (latestWeight != null) "${((latestWeight * 10).roundToInt() / 10.0)} kg" else "No data",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (latestWeight != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = when (trend) {
                                    WeightTrend.UP -> PhosphorIcons.Bold.TrendUp
                                    WeightTrend.DOWN -> PhosphorIcons.Bold.TrendDown
                                    WeightTrend.STABLE -> PhosphorIcons.Bold.Minus
                                },
                                contentDescription = trend.name,
                                modifier = Modifier.size(20.dp),
                                tint = when (trend) {
                                    WeightTrend.UP -> Color(0xFFE57373)
                                    WeightTrend.DOWN -> Color(0xFF81C784)
                                    WeightTrend.STABLE -> MaterialTheme.colorScheme.onSecondaryContainer
                                }
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onAddWeight) {
                Icon(
                    PhosphorIcons.Bold.Plus,
                    contentDescription = "Add weight",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun HealthNotAvailableCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                PhosphorIcons.Bold.Barbell,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Health Data Not Available",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Health Connect (Android) or Apple Health (iOS) is not available on this device.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionDeniedCard(onRequestPermissions: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                PhosphorIcons.Bold.Footprints,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Health Permissions Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Grant access to steps, weight, heart rate and sleep data to see your health metrics here.\n\nOn Android, make sure Health Connect is installed from Play Store.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Button(
                onClick = onRequestPermissions,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Grant Access")
            }
        }
    }
}

@Composable
private fun ManualWeightDialog(
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var weightText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Weight") },
        text = {
            Column {
                Text(
                    text = "Enter your current weight in kilograms",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = weightText,
                    onValueChange = {
                        weightText = it
                        isError = false
                    },
                    label = { Text("Weight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("Enter a valid weight (e.g., 70.5)") }
                    } else null
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val weight = weightText.toDoubleOrNull()
                    if (weight != null && weight > 0 && weight < 500) {
                        onConfirm(weight)
                    } else {
                        isError = true
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatCompact(value: Double): String {
    val longVal = value.toLong()
    return when {
        longVal >= 10_000 -> {
            val k = longVal / 1000.0
            val rounded = (k * 10).roundToInt() / 10.0
            if (rounded == rounded.toLong().toDouble()) "${rounded.toLong()}K"
            else "${rounded}K"
        }
        else -> longVal.toString()
    }
}

private fun formatSleepDuration(hours: Double): String {
    val h = hours.toInt()
    val m = ((hours - h) * 60).roundToInt()
    return "${h}h ${m}m"
}

private enum class WeightTrend { UP, DOWN, STABLE }
