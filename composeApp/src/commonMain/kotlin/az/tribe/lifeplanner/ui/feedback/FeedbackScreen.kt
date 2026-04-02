package az.tribe.lifeplanner.ui.feedback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.adamglin.phosphoricons.regular.Bug
import com.adamglin.phosphoricons.regular.CheckCircle
import com.adamglin.phosphoricons.regular.ChatCircleText
import com.adamglin.phosphoricons.regular.Lightbulb
import com.adamglin.phosphoricons.regular.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.data.analytics.Analytics
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign

enum class FeedbackCategory(
    val label: String,
    val icon: ImageVector,
    val description: String,
    val analyticsKey: String
) {
    GENERAL("General Feedback", PhosphorIcons.Regular.ChatCircleText, "Share your thoughts about the app", "general"),
    FEATURE_REQUEST("Feature Request", PhosphorIcons.Regular.Lightbulb, "Suggest a new feature or improvement", "feature_request"),
    BUG_REPORT("Bug Report", PhosphorIcons.Regular.Bug, "Report something that isn't working", "bug_report")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeedbackScreen(
    onNavigateBack: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<FeedbackCategory?>(null) }
    var feedbackText by remember { mutableStateOf("") }
    var rating by remember { mutableIntStateOf(0) }
    var isSubmitted by remember { mutableStateOf(false) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }

    val featureRequestTags = listOf("Goals", "Habits", "Journal", "Focus Timer", "AI Coach", "Gamification", "Sync", "UI/UX", "Other")
    val bugReportTags = listOf("Crash", "Data Loss", "Sync Issue", "UI Glitch", "Performance", "Notifications", "Other")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feedback", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(PhosphorIcons.Regular.ArrowLeft, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        AnimatedVisibility(
            visible = isSubmitted,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            // Success state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = LifePlannerDesign.Padding.screenHorizontal),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF10B981), Color(0xFF34D399))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        PhosphorIcons.Regular.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "Thank you!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Your feedback helps us make Life Planner better for everyone.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = onNavigateBack,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done")
                }
            }
        }

        AnimatedVisibility(
            visible = !isSubmitted,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    start = LifePlannerDesign.Padding.screenHorizontal,
                    end = LifePlannerDesign.Padding.screenHorizontal,
                    bottom = padding.calculateBottomPadding() + 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Category selection
                item {
                    Text(
                        "What would you like to share?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FeedbackCategory.entries.forEach { category ->
                            FeedbackCategoryCard(
                                category = category,
                                isSelected = selectedCategory == category,
                                onClick = {
                                    selectedCategory = category
                                    selectedTags = emptySet()
                                }
                            )
                        }
                    }
                }

                // Rating (only for general feedback)
                if (selectedCategory == FeedbackCategory.GENERAL) {
                    item {
                        Text(
                            "How would you rate your experience?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(12.dp))
                        RatingBar(
                            rating = rating,
                            onRatingChanged = { rating = it }
                        )
                    }
                }

                // Tags
                if (selectedCategory == FeedbackCategory.FEATURE_REQUEST || selectedCategory == FeedbackCategory.BUG_REPORT) {
                    item {
                        val tags = if (selectedCategory == FeedbackCategory.FEATURE_REQUEST) featureRequestTags else bugReportTags
                        Text(
                            if (selectedCategory == FeedbackCategory.FEATURE_REQUEST) "Related area" else "Issue type",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tags.forEach { tag ->
                                FilterChip(
                                    selected = tag in selectedTags,
                                    onClick = {
                                        selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag
                                    },
                                    label = { Text(tag, style = MaterialTheme.typography.bodySmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                }

                // Text input
                if (selectedCategory != null) {
                    item {
                        val placeholder = when (selectedCategory) {
                            FeedbackCategory.GENERAL -> "Tell us what you think..."
                            FeedbackCategory.FEATURE_REQUEST -> "Describe the feature you'd like to see..."
                            FeedbackCategory.BUG_REPORT -> "Describe what happened and how to reproduce it..."
                            null -> ""
                        }
                        OutlinedTextField(
                            value = feedbackText,
                            onValueChange = { feedbackText = it },
                            placeholder = { Text(placeholder) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                        Text(
                            "${feedbackText.length}/1000",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }

                // Submit button
                if (selectedCategory != null) {
                    item {
                        val canSubmit = feedbackText.isNotBlank() && feedbackText.length <= 1000
                        Button(
                            onClick = {
                                submitFeedback(
                                    category = selectedCategory!!,
                                    text = feedbackText,
                                    rating = if (selectedCategory == FeedbackCategory.GENERAL) rating else null,
                                    tags = selectedTags
                                )
                                isSubmitted = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled = canSubmit,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                "Submit Feedback",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackCategoryCard(
    category: FeedbackCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        color = bgColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    category.icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RatingBar(
    rating: Int,
    onRatingChanged: (Int) -> Unit
) {
    val labels = listOf("", "Poor", "Fair", "Good", "Great", "Amazing")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            (1..5).forEach { star ->
                val isActive = star <= rating
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .clickable { onRatingChanged(star) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        PhosphorIcons.Regular.Star,
                        contentDescription = "Rate $star",
                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        if (rating > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                labels[rating],
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun submitFeedback(
    category: FeedbackCategory,
    text: String,
    rating: Int?,
    tags: Set<String>
) {
    // Send structured event to PostHog
    val properties = buildMap<String, Any> {
        put("category", category.analyticsKey)
        put("text", text.take(1000))
        if (rating != null && rating > 0) put("rating", rating)
        if (tags.isNotEmpty()) put("tags", tags.joinToString(","))
    }
    PostHogAnalytics.capture("feedback_submitted", properties)

    // Also fire typed analytics
    when (category) {
        FeedbackCategory.FEATURE_REQUEST -> Analytics.featureRequestSubmitted(
            category = tags.firstOrNull() ?: "general"
        )
        else -> Analytics.feedbackSubmitted(
            category = category.analyticsKey,
            rating = rating
        )
    }
}
