package az.tribe.lifeplanner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

    val templates = remember(selectedCategory) {
        if (selectedCategory == null) {
            GoalTemplateProvider.getAllTemplates()
        } else {
            GoalTemplateProvider.getTemplatesByCategory(selectedCategory!!)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = MaterialTheme.modernColors.background,
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.modernColors.background,
                    scrolledContainerColor = MaterialTheme.modernColors.background,
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

            // Templates Grid
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // Group templates into rows of 2
                val chunkedTemplates = templates.chunked(2)

                items(chunkedTemplates.size) { rowIndex ->
                    val rowTemplates = chunkedTemplates[rowIndex]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowTemplates.forEach { template ->
                            ModernTemplateCard(
                                template = template,
                                onClick = { onTemplateSelected(template) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill empty space if odd number
                        if (rowTemplates.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
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
        // "All" chip
        FilterChip(
            selected = selectedCategory == null,
            onClick = { onCategorySelected(selectedCategory ?: GoalCategory.CAREER) },
            label = {
                Text(
                    "All",
                    fontWeight = if (selectedCategory == null) FontWeight.SemiBold else FontWeight.Normal
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = Color.White,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )

        GoalCategory.entries.forEach { category ->
            val isSelected = selectedCategory == category

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
private fun ModernTemplateCard(
    template: GoalTemplate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = template.category.backgroundColor()

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row with icon and difficulty
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Icon with category color background
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(categoryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = template.icon.ifEmpty { getCategoryEmoji(template.category) },
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                // Difficulty Badge
                DifficultyBadge(difficulty = template.difficulty)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = template.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                color = MaterialTheme.modernColors.textPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = template.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom row with category and timeline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category chip
                Surface(
                    shape = RoundedCornerShape(50),
                    color = categoryColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = template.category.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Timeline chip
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = template.suggestedTimeline.name
                            .replace("_", " ")
                            .lowercase()
                            .split(" ")
                            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Milestones count hint
            Text(
                text = "${template.suggestedMilestones.size} milestones included",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
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
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        repeat(3) { index ->
            Icon(
                imageVector = if (index < stars) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
                tint = if (index < stars) color else color.copy(alpha = 0.3f),
                modifier = Modifier.size(10.dp)
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
