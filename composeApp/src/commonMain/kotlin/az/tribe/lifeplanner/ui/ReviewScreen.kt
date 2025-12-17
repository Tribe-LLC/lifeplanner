package az.tribe.lifeplanner.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.*
import az.tribe.lifeplanner.ui.review.ReviewUiState
import az.tribe.lifeplanner.ui.review.ReviewViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel = koinViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showReviewDetail && uiState.selectedReview != null) {
        ReviewDetailScreen(
            review = uiState.selectedReview!!,
            onNavigateBack = { viewModel.navigateBack() },
            onFeedbackClick = { viewModel.showFeedbackDialog() },
            onDelete = { viewModel.deleteReview(it) }
        )
    } else {
        ReviewListScreen(
            uiState = uiState,
            onReviewClick = { viewModel.selectReview(it) },
            onGenerateReview = { viewModel.generateReview(it) },
            onNavigateBack = onNavigateBack
        )
    }

    // Feedback Dialog
    if (uiState.showFeedbackDialog) {
        FeedbackDialog(
            onDismiss = { viewModel.hideFeedbackDialog() },
            onSubmit = { rating, comment -> viewModel.submitFeedback(rating, comment) }
        )
    }

    // Error Snackbar
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewListScreen(
    uiState: ReviewUiState,
    onReviewClick: (ReviewReport) -> Unit,
    onGenerateReview: (ReviewType) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Reviews")
                        if (uiState.unreadCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge {
                                Text(uiState.unreadCount.toString())
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Generate Review Section
                    item {
                        GenerateReviewSection(
                            pendingTypes = uiState.pendingReviewTypes,
                            isGenerating = uiState.isGenerating,
                            onGenerate = onGenerateReview
                        )
                    }

                    // Recent Reviews Section
                    if (uiState.summaryCards.isNotEmpty()) {
                        item {
                            Text(
                                "Recent Reviews",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.summaryCards) { card ->
                                    ReviewSummaryCardItem(
                                        card = card,
                                        onClick = {
                                            uiState.reviews.find { it.id == card.reviewId }
                                                ?.let { onReviewClick(it) }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // All Reviews by Type
                    ReviewType.entries.forEach { type ->
                        val typeReviews = uiState.reviews.filter { it.type == type }
                        if (typeReviews.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "${type.displayName} Reviews",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            items(typeReviews) { review ->
                                ReviewListItem(
                                    review = review,
                                    onClick = { onReviewClick(review) }
                                )
                            }
                        }
                    }

                    // Empty State
                    if (uiState.reviews.isEmpty() && !uiState.isLoading) {
                        item {
                            EmptyReviewsState(
                                onGenerate = { onGenerate: ReviewType -> onGenerateReview(onGenerate) }
                            )
                        }
                    }
                }
            }

            // Error Message
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Text(error)
                }
            }
        }
    }
}

@Composable
private fun GenerateReviewSection(
    pendingTypes: List<ReviewType>,
    isGenerating: Boolean,
    onGenerate: (ReviewType) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Generate New Review",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isGenerating) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Generating your personalized review...")
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ReviewType.entries) { type ->
                        val isPending = type in pendingTypes
                        ReviewTypeChip(
                            type = type,
                            isPending = isPending,
                            onClick = { onGenerate(type) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewTypeChip(
    type: ReviewType,
    isPending: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isPending,
        onClick = onClick,
        label = { Text(type.displayName) },
        leadingIcon = {
            Icon(
                imageVector = type.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = if (isPending) {
            {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Text("!")
                }
            }
        } else null
    )
}

@Composable
private fun ReviewSummaryCardItem(
    card: ReviewSummaryCard,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(280.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = card.type.icon,
                        contentDescription = null,
                        tint = card.type.color,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        card.periodLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (card.isNew) {
                    Badge {
                        Text("New")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                card.headline,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                card.quickStats.forEach { stat ->
                    QuickStatItem(stat)
                }
            }
        }
    }
}

@Composable
private fun QuickStatItem(stat: QuickStat) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stat.value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            stat.trend?.let { trend ->
                Icon(
                    imageVector = when (trend) {
                        TrendDirection.UP -> Icons.Rounded.TrendingUp
                        TrendDirection.DOWN -> Icons.Rounded.TrendingDown
                        TrendDirection.STABLE -> Icons.Rounded.TrendingFlat
                    },
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = when (trend) {
                        TrendDirection.UP -> Color(0xFF4CAF50)
                        TrendDirection.DOWN -> Color(0xFFE57373)
                        TrendDirection.STABLE -> Color.Gray
                    }
                )
            }
        }
        Text(
            stat.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReviewListItem(
    review: ReviewReport,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (!review.isRead) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(review.type.color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = review.type.icon,
                    contentDescription = null,
                    tint = review.type.color
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        review.type.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!review.isRead) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                Text(
                    "${review.periodStart} - ${review.periodEnd}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    review.summary.take(80) + if (review.summary.length > 80) "..." else "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewDetailScreen(
    review: ReviewReport,
    onNavigateBack: () -> Unit,
    onFeedbackClick: () -> Unit,
    onDelete: (ReviewReport) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${review.type.displayName} Review") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (review.feedback == null) {
                        IconButton(onClick = onFeedbackClick) {
                            Icon(Icons.Rounded.ThumbUp, "Give Feedback")
                        }
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Rounded.Delete, "Delete")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            item {
                ReviewHeaderCard(review)
            }

            // Summary
            item {
                SectionCard(
                    title = "Summary",
                    icon = Icons.Rounded.Summarize
                ) {
                    Text(
                        review.summary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Stats
            item {
                StatsCard(review.stats)
            }

            // Highlights
            if (review.highlights.isNotEmpty()) {
                item {
                    SectionCard(
                        title = "Highlights",
                        icon = Icons.Rounded.Stars
                    ) {
                        review.highlights.forEach { highlight ->
                            HighlightItem(highlight)
                            if (highlight != review.highlights.last()) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }

            // Insights
            if (review.insights.isNotEmpty()) {
                item {
                    SectionCard(
                        title = "Insights",
                        icon = Icons.Rounded.Lightbulb
                    ) {
                        review.insights.forEach { insight ->
                            InsightItem(insight)
                            if (insight != review.insights.last()) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }

            // Recommendations
            if (review.recommendations.isNotEmpty()) {
                item {
                    SectionCard(
                        title = "Recommendations",
                        icon = Icons.Rounded.TipsAndUpdates
                    ) {
                        review.recommendations.forEach { rec ->
                            RecommendationItem(rec)
                            if (rec != review.recommendations.last()) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }

            // Feedback Status
            review.feedback?.let { feedback ->
                item {
                    FeedbackCard(feedback)
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Review") },
            text = { Text("Are you sure you want to delete this review? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(review)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ReviewHeaderCard(review: ReviewReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = review.type.color.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = review.type.icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = review.type.color
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                review.type.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${review.periodStart} to ${review.periodEnd}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Generated on ${review.generatedAt.date}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun StatsCard(stats: ReviewStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Goals Done", stats.goalsCompleted.toString(), Icons.Rounded.CheckCircle)
                StatItem("In Progress", stats.goalsInProgress.toString(), Icons.Rounded.Pending)
                StatItem("XP Earned", "+${stats.xpEarned}", Icons.Rounded.Star)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Habit Rate", "${(stats.habitCompletionRate * 100).toInt()}%", Icons.Rounded.Loop)
                StatItem("Streak", "${stats.streakDays} days", Icons.Rounded.LocalFireDepartment)
                StatItem("Journal", stats.journalEntries.toString(), Icons.Rounded.Book)
            }

            // Comparison
            stats.comparisonToPrevious?.let { comparison ->
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (comparison.overallTrend) {
                            TrendDirection.UP -> Icons.Rounded.TrendingUp
                            TrendDirection.DOWN -> Icons.Rounded.TrendingDown
                            TrendDirection.STABLE -> Icons.Rounded.TrendingFlat
                        },
                        contentDescription = null,
                        tint = when (comparison.overallTrend) {
                            TrendDirection.UP -> Color(0xFF4CAF50)
                            TrendDirection.DOWN -> Color(0xFFE57373)
                            TrendDirection.STABLE -> Color.Gray
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when (comparison.overallTrend) {
                            TrendDirection.UP -> "Improving from last period!"
                            TrendDirection.DOWN -> "Room for growth"
                            TrendDirection.STABLE -> "Staying consistent"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    icon: ImageVector
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HighlightItem(highlight: ReviewHighlight) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(highlight.category.color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = highlight.category.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = highlight.category.color
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                highlight.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                highlight.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InsightItem(insight: ReviewInsight) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = insight.type.icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                insight.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                insight.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecommendationItem(rec: ReviewRecommendation) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (rec.priority) {
                RecommendationPriority.HIGH -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                RecommendationPriority.MEDIUM -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                RecommendationPriority.LOW -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = rec.actionType.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        rec.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        rec.priority.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (rec.priority) {
                            RecommendationPriority.HIGH -> MaterialTheme.colorScheme.error
                            RecommendationPriority.MEDIUM -> MaterialTheme.colorScheme.primary
                            RecommendationPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Text(
                    rec.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FeedbackCard(feedback: ReviewFeedback) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Feedback,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Your Feedback",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (feedback.rating) {
                        FeedbackRating.HELPFUL -> Icons.Rounded.ThumbUp
                        FeedbackRating.NOT_HELPFUL -> Icons.Rounded.ThumbDown
                        FeedbackRating.NEUTRAL -> Icons.Rounded.Remove
                    },
                    contentDescription = null,
                    tint = when (feedback.rating) {
                        FeedbackRating.HELPFUL -> Color(0xFF4CAF50)
                        FeedbackRating.NOT_HELPFUL -> Color(0xFFE57373)
                        FeedbackRating.NEUTRAL -> Color.Gray
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    when (feedback.rating) {
                        FeedbackRating.HELPFUL -> "Helpful"
                        FeedbackRating.NOT_HELPFUL -> "Not Helpful"
                        FeedbackRating.NEUTRAL -> "Neutral"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            feedback.comment?.let { comment ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "\"$comment\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
private fun FeedbackDialog(
    onDismiss: () -> Unit,
    onSubmit: (FeedbackRating, String?) -> Unit
) {
    var selectedRating by remember { mutableStateOf<FeedbackRating?>(null) }
    var comment by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate this Review") },
        text = {
            Column {
                Text("How helpful was this review?")
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Not Helpful
                    FilterChip(
                        selected = selectedRating == FeedbackRating.NOT_HELPFUL,
                        onClick = { selectedRating = FeedbackRating.NOT_HELPFUL },
                        label = { Text("Not Helpful") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.ThumbDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )

                    // Neutral
                    FilterChip(
                        selected = selectedRating == FeedbackRating.NEUTRAL,
                        onClick = { selectedRating = FeedbackRating.NEUTRAL },
                        label = { Text("Neutral") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Remove,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )

                    // Helpful
                    FilterChip(
                        selected = selectedRating == FeedbackRating.HELPFUL,
                        onClick = { selectedRating = FeedbackRating.HELPFUL },
                        label = { Text("Helpful") },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.ThumbUp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedRating?.let { rating ->
                        onSubmit(rating, comment.takeIf { it.isNotBlank() })
                    }
                },
                enabled = selectedRating != null
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EmptyReviewsState(
    onGenerate: (ReviewType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.Assessment,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No Reviews Yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Generate your first review to get personalized insights about your progress",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { onGenerate(ReviewType.WEEKLY) }) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generate Weekly Review")
        }
    }
}

// Extension properties for ReviewType
private val ReviewType.displayName: String
    get() = when (this) {
        ReviewType.WEEKLY -> "Weekly"
        ReviewType.MONTHLY -> "Monthly"
        ReviewType.QUARTERLY -> "Quarterly"
        ReviewType.YEARLY -> "Yearly"
    }

private val ReviewType.icon: ImageVector
    get() = when (this) {
        ReviewType.WEEKLY -> Icons.Rounded.DateRange
        ReviewType.MONTHLY -> Icons.Rounded.CalendarMonth
        ReviewType.QUARTERLY -> Icons.Rounded.CalendarViewMonth
        ReviewType.YEARLY -> Icons.Rounded.CalendarToday
    }

private val ReviewType.color: Color
    get() = when (this) {
        ReviewType.WEEKLY -> Color(0xFF4CAF50)
        ReviewType.MONTHLY -> Color(0xFF2196F3)
        ReviewType.QUARTERLY -> Color(0xFF9C27B0)
        ReviewType.YEARLY -> Color(0xFFFF9800)
    }

// Extension properties for HighlightCategory
private val HighlightCategory.icon: ImageVector
    get() = when (this) {
        HighlightCategory.GOAL_COMPLETED -> Icons.Rounded.CheckCircle
        HighlightCategory.MILESTONE_REACHED -> Icons.Rounded.Flag
        HighlightCategory.STREAK_ACHIEVED -> Icons.Rounded.LocalFireDepartment
        HighlightCategory.HABIT_CONSISTENCY -> Icons.Rounded.Loop
        HighlightCategory.XP_MILESTONE -> Icons.Rounded.Star
        HighlightCategory.LEVEL_UP -> Icons.Rounded.TrendingUp
        HighlightCategory.NEW_BADGE -> Icons.Rounded.EmojiEvents
        HighlightCategory.PERSONAL_BEST -> Icons.Rounded.Verified
    }

private val HighlightCategory.color: Color
    get() = when (this) {
        HighlightCategory.GOAL_COMPLETED -> Color(0xFF4CAF50)
        HighlightCategory.MILESTONE_REACHED -> Color(0xFF9C27B0)
        HighlightCategory.STREAK_ACHIEVED -> Color(0xFFFF9800)
        HighlightCategory.HABIT_CONSISTENCY -> Color(0xFF2196F3)
        HighlightCategory.XP_MILESTONE -> Color(0xFFFFD700)
        HighlightCategory.LEVEL_UP -> Color(0xFF00BCD4)
        HighlightCategory.NEW_BADGE -> Color(0xFFE91E63)
        HighlightCategory.PERSONAL_BEST -> Color(0xFF8BC34A)
    }

// Extension properties for InsightType
private val InsightType.icon: ImageVector
    get() = when (this) {
        InsightType.PRODUCTIVITY_PATTERN -> Icons.Rounded.ShowChart
        InsightType.STRENGTH_AREA -> Icons.Rounded.ThumbUp
        InsightType.IMPROVEMENT_AREA -> Icons.Rounded.TrendingUp
        InsightType.HABIT_CORRELATION -> Icons.Rounded.Link
        InsightType.TIME_MANAGEMENT -> Icons.Rounded.Schedule
        InsightType.CONSISTENCY -> Icons.Rounded.Loop
        InsightType.CATEGORY_FOCUS -> Icons.Rounded.Category
    }

// Extension properties for RecommendationAction
private val RecommendationAction.icon: ImageVector
    get() = when (this) {
        RecommendationAction.CREATE_GOAL -> Icons.Rounded.Add
        RecommendationAction.ADJUST_GOAL -> Icons.Rounded.Edit
        RecommendationAction.CREATE_HABIT -> Icons.Rounded.Loop
        RecommendationAction.FOCUS_CATEGORY -> Icons.Rounded.CenterFocusStrong
        RecommendationAction.TAKE_BREAK -> Icons.Rounded.Coffee
        RecommendationAction.INCREASE_DIFFICULTY -> Icons.Rounded.TrendingUp
        RecommendationAction.REDUCE_SCOPE -> Icons.Rounded.RemoveCircle
        RecommendationAction.CELEBRATE -> Icons.Rounded.Celebration
        RecommendationAction.REFLECT -> Icons.Rounded.Lightbulb
    }

// Extension for FeedbackRating
private val FeedbackRating.stars: Int
    get() = when (this) {
        FeedbackRating.NOT_HELPFUL -> 1
        FeedbackRating.NEUTRAL -> 2
        FeedbackRating.HELPFUL -> 3
    }
