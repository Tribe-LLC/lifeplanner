package az.tribe.lifeplanner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.domain.enum.DependencyType
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.model.DependencyGraph
import az.tribe.lifeplanner.domain.model.GoalDependency
import az.tribe.lifeplanner.domain.model.GoalNode
import az.tribe.lifeplanner.ui.components.backgroundColor
import az.tribe.lifeplanner.ui.components.color
import az.tribe.lifeplanner.ui.dependency.GoalDependencyViewModel
import org.koin.compose.koinInject
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DependencyGraphScreen(
    viewModel: GoalDependencyViewModel = koinInject(),
    focusGoalId: String? = null,
    onNavigateBack: () -> Unit,
    onGoalClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var selectedNodeId by remember { mutableStateOf<String?>(focusGoalId) }
    var showFilters by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<GoalCategory?>(null) }

    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Goal Dependencies") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            Icons.Rounded.FilterList,
                            contentDescription = "Filter",
                            tint = if (selectedCategory != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { scale = (scale + 0.2f).coerceAtMost(2.5f) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom In")
                }

                SmallFloatingActionButton(
                    onClick = { scale = (scale - 0.2f).coerceAtLeast(0.5f) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom Out")
                }

                FloatingActionButton(
                    onClick = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                        selectedNodeId = null
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Rounded.CenterFocusStrong,
                        contentDescription = "Reset View"
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Category filters
            if (showFilters) {
                CategoryFilterRow(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it }
                )
            }

            if (uiState.dependencyGraph.isEmpty || uiState.dependencyGraph.edges.isEmpty()) {
                // Empty state
                EmptyGraphState()
            } else {
                // Graph visualization
                Box(modifier = Modifier.weight(1f)) {
                    val filteredGraph = if (selectedCategory != null) {
                        DependencyGraph(
                            nodes = uiState.dependencyGraph.nodes.filter { it.goal.category == selectedCategory },
                            edges = uiState.dependencyGraph.edges.filter { edge ->
                                val sourceNode = uiState.dependencyGraph.getNodeByGoalId(edge.sourceGoalId)
                                val targetNode = uiState.dependencyGraph.getNodeByGoalId(edge.targetGoalId)
                                sourceNode?.goal?.category == selectedCategory &&
                                        targetNode?.goal?.category == selectedCategory
                            }
                        )
                    } else {
                        uiState.dependencyGraph
                    }

                    GraphCanvas(
                        graph = filteredGraph,
                        scale = animatedScale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        selectedNodeId = selectedNodeId,
                        onScaleChange = { scale = it },
                        onOffsetChange = { dx, dy ->
                            offsetX += dx
                            offsetY += dy
                        },
                        onNodeSelected = { nodeId ->
                            selectedNodeId = nodeId
                        },
                        onNodeDoubleClick = { nodeId ->
                            onGoalClick(nodeId)
                        }
                    )
                }

                // Selected node info panel
                selectedNodeId?.let { nodeId ->
                    uiState.dependencyGraph.getNodeByGoalId(nodeId)?.let { node ->
                        NodeInfoCard(
                            node = node,
                            allNodes = uiState.dependencyGraph.nodes,
                            onGoalClick = onGoalClick,
                            onDismiss = { selectedNodeId = null }
                        )
                    }
                }
            }

            // Legend
            GraphLegend()
        }
    }
}

@Composable
fun CategoryFilterRow(
    selectedCategory: GoalCategory?,
    onCategorySelected: (GoalCategory?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedCategory == null,
            onClick = { onCategorySelected(null) },
            label = { Text("All") }
        )

        GoalCategory.entries.take(4).forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = {
                    onCategorySelected(if (selectedCategory == category) null else category)
                },
                label = { Text(category.name.lowercase().replaceFirstChar { it.uppercase() }) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = category.backgroundColor().copy(alpha = 0.3f)
                )
            )
        }
    }
}

@Composable
fun GraphCanvas(
    graph: DependencyGraph,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    selectedNodeId: String?,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Float, Float) -> Unit,
    onNodeSelected: (String?) -> Unit,
    onNodeDoubleClick: (String) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Calculate node positions using force-directed layout
    val nodePositions = remember(graph.nodes) {
        calculateNodePositions(graph)
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceVariantColor.copy(alpha = 0.3f))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(0.5f, 2.5f)
                    onScaleChange(newScale)
                    onOffsetChange(pan.x, pan.y)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        // Check if any node was tapped
                        val tappedNode = findNodeAtPosition(
                            offset = offset,
                            nodePositions = nodePositions,
                            scale = scale,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            canvasSize = size.toSize()
                        )
                        onNodeSelected(tappedNode)
                    },
                    onDoubleTap = { offset ->
                        val tappedNode = findNodeAtPosition(
                            offset = offset,
                            nodePositions = nodePositions,
                            scale = scale,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            canvasSize = size.toSize()
                        )
                        tappedNode?.let { onNodeDoubleClick(it) }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerX = canvasWidth / 2 + offsetX
            val centerY = canvasHeight / 2 + offsetY

            // Draw edges first (so they appear behind nodes)
            graph.edges.forEach { edge ->
                val sourcePos = nodePositions[edge.sourceGoalId] ?: return@forEach
                val targetPos = nodePositions[edge.targetGoalId] ?: return@forEach

                val startX = centerX + sourcePos.first * scale
                val startY = centerY + sourcePos.second * scale
                val endX = centerX + targetPos.first * scale
                val endY = centerY + targetPos.second * scale

                // Draw edge line
                drawEdge(
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    dependencyType = edge.dependencyType,
                    scale = scale,
                    isHighlighted = selectedNodeId == edge.sourceGoalId ||
                            selectedNodeId == edge.targetGoalId
                )
            }

            // Draw nodes
            graph.nodes.forEach { node ->
                val pos = nodePositions[node.goal.id] ?: return@forEach
                val x = centerX + pos.first * scale
                val y = centerY + pos.second * scale

                val isSelected = selectedNodeId == node.goal.id
                val isConnected = selectedNodeId?.let { selected ->
                    graph.edges.any {
                        (it.sourceGoalId == selected && it.targetGoalId == node.goal.id) ||
                                (it.targetGoalId == selected && it.sourceGoalId == node.goal.id)
                    }
                } ?: false

                drawNode(
                    center = Offset(x, y),
                    node = node,
                    scale = scale,
                    isSelected = isSelected,
                    isConnected = isConnected,
                    isDimmed = selectedNodeId != null && !isSelected && !isConnected,
                    textMeasurer = textMeasurer,
                    surfaceColor = surfaceColor,
                    onSurfaceColor = onSurfaceColor,
                    primaryColor = primaryColor
                )
            }
        }
    }
}

private fun DrawScope.drawNode(
    center: Offset,
    node: GoalNode,
    scale: Float,
    isSelected: Boolean,
    isConnected: Boolean,
    isDimmed: Boolean,
    textMeasurer: TextMeasurer,
    surfaceColor: Color,
    onSurfaceColor: Color,
    primaryColor: Color
) {
    val nodeRadius = 40f * scale
    val categoryColor = node.goal.category.backgroundColor()

    val alpha = when {
        isDimmed -> 0.3f
        isConnected -> 0.9f
        else -> 1f
    }

    // Node shadow
    if (!isDimmed) {
        drawCircle(
            color = Color.Black.copy(alpha = 0.1f * alpha),
            radius = nodeRadius + 4f,
            center = center + Offset(2f, 2f)
        )
    }

    // Node circle - outer ring for category
    drawCircle(
        color = categoryColor.copy(alpha = alpha),
        radius = nodeRadius,
        center = center
    )

    // Inner circle
    drawCircle(
        color = surfaceColor.copy(alpha = alpha),
        radius = nodeRadius - 4f,
        center = center
    )

    // Progress arc
    val progress = (node.goal.progress?.toFloat() ?: 0f) / 100f
    if (progress > 0) {
        drawArc(
            color = categoryColor.copy(alpha = alpha * 0.8f),
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = Offset(center.x - nodeRadius + 4f, center.y - nodeRadius + 4f),
            size = Size((nodeRadius - 4f) * 2, (nodeRadius - 4f) * 2),
            style = Stroke(width = 6f * scale)
        )
    }

    // Selection highlight
    if (isSelected) {
        drawCircle(
            color = primaryColor,
            radius = nodeRadius + 6f,
            center = center,
            style = Stroke(width = 3f)
        )
    }

    // Draw goal title (truncated)
    val title = node.goal.title.take(12) + if (node.goal.title.length > 12) "..." else ""
    val textResult = textMeasurer.measure(
        text = title,
        style = TextStyle(
            fontSize = (10f * scale).sp,
            color = onSurfaceColor.copy(alpha = alpha),
            textAlign = TextAlign.Center
        )
    )

    drawText(
        textLayoutResult = textResult,
        topLeft = Offset(
            center.x - textResult.size.width / 2,
            center.y - textResult.size.height / 2
        )
    )
}

private fun DrawScope.drawEdge(
    start: Offset,
    end: Offset,
    dependencyType: DependencyType,
    scale: Float,
    isHighlighted: Boolean
) {
    val color = dependencyType.color()
    val alpha = if (isHighlighted) 1f else 0.5f
    val strokeWidth = if (isHighlighted) 3f * scale else 2f * scale

    // Calculate direction
    val dx = end.x - start.x
    val dy = end.y - start.y
    val distance = sqrt(dx * dx + dy * dy)
    val nodeRadius = 40f * scale

    // Shorten line to stop at node boundaries
    val ratio = nodeRadius / distance
    val actualStart = Offset(start.x + dx * ratio, start.y + dy * ratio)
    val actualEnd = Offset(end.x - dx * ratio, end.y - dy * ratio)

    // Draw line
    drawLine(
        color = color.copy(alpha = alpha),
        start = actualStart,
        end = actualEnd,
        strokeWidth = strokeWidth
    )

    // Draw arrow for directional dependencies
    if (dependencyType == DependencyType.BLOCKS ||
        dependencyType == DependencyType.BLOCKED_BY ||
        dependencyType == DependencyType.PARENT_OF ||
        dependencyType == DependencyType.CHILD_OF
    ) {
        drawArrowHead(
            tip = actualEnd,
            from = actualStart,
            color = color.copy(alpha = alpha),
            size = 12f * scale
        )
    }
}

private fun DrawScope.drawArrowHead(
    tip: Offset,
    from: Offset,
    color: Color,
    size: Float
) {
    val angle = atan2(tip.y - from.y, tip.x - from.x)
    val arrowAngle = PI / 6 // 30 degrees

    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(
            (tip.x - size * cos(angle - arrowAngle)).toFloat(),
            (tip.y - size * sin(angle - arrowAngle)).toFloat()
        )
        lineTo(
            (tip.x - size * cos(angle + arrowAngle)).toFloat(),
            (tip.y - size * sin(angle + arrowAngle)).toFloat()
        )
        close()
    }

    drawPath(path = path, color = color, style = Fill)
}

private fun calculateNodePositions(graph: DependencyGraph): Map<String, Pair<Float, Float>> {
    if (graph.nodes.isEmpty()) return emptyMap()

    val positions = mutableMapOf<String, Pair<Float, Float>>()
    val nodeCount = graph.nodes.size

    // Simple circular layout with level adjustments
    val baseRadius = 150f + (nodeCount * 20f)

    graph.nodes.forEachIndexed { index, node ->
        val level = node.level
        val radius = baseRadius + (level * 80f)
        val angle = (2 * PI * index / nodeCount).toFloat()

        positions[node.goal.id] = Pair(
            (radius * cos(angle)).toFloat(),
            (radius * sin(angle)).toFloat()
        )
    }

    return positions
}

private fun findNodeAtPosition(
    offset: Offset,
    nodePositions: Map<String, Pair<Float, Float>>,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    canvasSize: Size
): String? {
    val centerX = canvasSize.width / 2 + offsetX
    val centerY = canvasSize.height / 2 + offsetY
    val nodeRadius = 40f * scale

    nodePositions.forEach { (nodeId, pos) ->
        val nodeX = centerX + pos.first * scale
        val nodeY = centerY + pos.second * scale

        val distance = sqrt(
            (offset.x - nodeX) * (offset.x - nodeX) +
                    (offset.y - nodeY) * (offset.y - nodeY)
        )

        if (distance <= nodeRadius) {
            return nodeId
        }
    }

    return null
}

private fun androidx.compose.ui.unit.IntSize.toSize(): Size {
    return Size(width.toFloat(), height.toFloat())
}

@Composable
fun NodeInfoCard(
    node: GoalNode,
    allNodes: List<GoalNode>,
    onGoalClick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.goal.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${node.goal.category.name.lowercase().replaceFirstChar { it.uppercase() }} • ${node.goal.progress ?: 0}% complete",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.Link,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Dependencies info
            if (node.dependencies.isNotEmpty() || node.dependents.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (node.dependencies.isNotEmpty()) {
                        Column {
                            Text(
                                text = "Depends on",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            node.dependencies.take(3).forEach { dep ->
                                val linkedNode = allNodes.find { it.goal.id == dep.targetGoalId }
                                linkedNode?.let {
                                    Text(
                                        text = "• ${it.goal.title.take(20)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable { onGoalClick(it.goal.id) }
                                    )
                                }
                            }
                        }
                    }

                    if (node.dependents.isNotEmpty()) {
                        Column {
                            Text(
                                text = "Blocking",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            node.dependents.take(3).forEach { dep ->
                                val linkedNode = allNodes.find { it.goal.id == dep.sourceGoalId }
                                linkedNode?.let {
                                    Text(
                                        text = "• ${it.goal.title.take(20)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable { onGoalClick(it.goal.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // View goal button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onGoalClick(node.goal.id) },
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "View Goal Details",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun GraphLegend() {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LegendItem(
                color = DependencyType.BLOCKS.color(),
                label = "Blocks"
            )
            LegendItem(
                color = DependencyType.RELATED.color(),
                label = "Related"
            )
            LegendItem(
                color = DependencyType.PARENT_OF.color(),
                label = "Parent"
            )
            LegendItem(
                color = DependencyType.SUPPORTS.color(),
                label = "Supports"
            )
        }
    }
}

@Composable
fun LegendItem(
    color: Color,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmptyGraphState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Rounded.Link,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Text(
                text = "No Dependencies Yet",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Link your goals together to see\ntheir relationships visualized here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
