package az.tribe.lifeplanner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.data.repository.GoalTemplateProvider
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.model.GoalTemplate
import az.tribe.lifeplanner.domain.model.TemplateDifficulty
import az.tribe.lifeplanner.ui.theme.backgroundColor
import az.tribe.lifeplanner.ui.theme.containerColor
import az.tribe.lifeplanner.ui.theme.modernColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePickerScreen(
    onBackClick: () -> Unit,
    onTemplateSelected: (GoalTemplate) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<GoalCategory?>(null) }
    var selectedTemplate by remember { mutableStateOf<GoalTemplate?>(null) }

    val templates = remember(selectedCategory) {
        if (selectedCategory == null) {
            GoalTemplateProvider.getAllTemplates()
        } else {
            GoalTemplateProvider.getTemplatesByCategory(selectedCategory!!)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.modernColors.background,
        topBar = {
            LargeTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.modernColors.background,
                    titleContentColor = MaterialTheme.modernColors.textPrimary
                ),
                title = {
                    Column {
                        Text(
                            "Goal Templates",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            "Choose a template to get started",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.modernColors.textSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.modernColors.textPrimary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Category Filter Chips
            CategoryFilterRow(
                selectedCategory = selectedCategory,
                onCategorySelected = { category ->
                    selectedCategory = if (selectedCategory == category) null else category
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Templates List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(templates, key = { it.id }) { template ->
                    TemplateCard(
                        template = template,
                        isSelected = selectedTemplate?.id == template.id,
                        onClick = {
                            selectedTemplate = if (selectedTemplate?.id == template.id) null else template
                        },
                        onUseTemplate = { onTemplateSelected(template) }
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    selectedCategory: GoalCategory?,
    onCategorySelected: (GoalCategory) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GoalCategory.entries.forEach { category ->
            val isSelected = selectedCategory == category
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) category.backgroundColor() else Color.Transparent,
                label = "chipBg"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else category.backgroundColor(),
                label = "chipContent"
            )

            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(category) },
                label = {
                    Text(
                        category.name.lowercase().replaceFirstChar { it.uppercase() },
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = category.backgroundColor(),
                    selectedLabelColor = Color.White,
                    containerColor = category.containerColor(),
                    labelColor = category.backgroundColor()
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = category.backgroundColor().copy(alpha = 0.3f),
                    selectedBorderColor = category.backgroundColor()
                )
            )
        }
    }
}

@Composable
private fun TemplateCard(
    template: GoalTemplate,
    isSelected: Boolean,
    onClick: () -> Unit,
    onUseTemplate: () -> Unit
) {
    val categoryColor = template.category.backgroundColor()
    val elevation by animateFloatAsState(
        targetValue = if (isSelected) 8f else 0f,
        label = "elevation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, categoryColor)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Icon and Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Emoji Icon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(template.category.containerColor()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = template.icon.ifEmpty { getCategoryEmoji(template.category) },
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = template.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.modernColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = template.category.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall,
                            color = categoryColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Difficulty Badge
                DifficultyBadge(difficulty = template.difficulty)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Description
            Text(
                text = template.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.modernColors.textSecondary,
                maxLines = if (isSelected) 4 else 2,
                overflow = TextOverflow.Ellipsis
            )

            // Expanded Content
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Timeline Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = "Timeline: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.modernColors.textSecondary
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = template.suggestedTimeline.name
                                    .replace("_", " ")
                                    .lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Milestones Preview
                    Text(
                        text = "Suggested Milestones:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.modernColors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    template.suggestedMilestones.take(3).forEachIndexed { index, milestone ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(categoryColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = milestone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.modernColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (template.suggestedMilestones.size > 3) {
                        Text(
                            text = "+${template.suggestedMilestones.size - 3} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = categoryColor,
                            modifier = Modifier.padding(start = 14.dp, top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Use Template Button
                    Button(
                        onClick = onUseTemplate,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = categoryColor
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Use This Template")
                    }
                }
            }
        }
    }
}

@Composable
private fun DifficultyBadge(difficulty: TemplateDifficulty) {
    val (color, stars) = when (difficulty) {
        TemplateDifficulty.EASY -> Color(0xFF28C76F) to 1
        TemplateDifficulty.MEDIUM -> Color(0xFFFF9F43) to 2
        TemplateDifficulty.HARD -> Color(0xFFEA5455) to 3
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        repeat(3) { index ->
            Icon(
                imageVector = if (index < stars) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
                tint = if (index < stars) color else color.copy(alpha = 0.3f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

private fun getCategoryEmoji(category: GoalCategory): String {
    return when (category) {
        GoalCategory.CAREER -> "💼"
        GoalCategory.FINANCIAL -> "💰"
        GoalCategory.PHYSICAL -> "💪"
        GoalCategory.SOCIAL -> "👥"
        GoalCategory.EMOTIONAL -> "💚"
        GoalCategory.SPIRITUAL -> "🕊️"
        GoalCategory.FAMILY -> "👨‍👩‍👧‍👦"
    }
}
