package az.tribe.lifeplanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.data.repository.GoalTemplateProvider
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.model.GoalTemplate

@Composable
fun EmptyGoalsView(
    isFiltered: Boolean,
    onQuickAddClick: () -> Unit = {},
    onTemplatesClick: () -> Unit = {},
    onAiGenerateClick: () -> Unit = {},
    onTemplateClick: (GoalTemplate) -> Unit = {}
) {
    if (isFiltered) {
        // Simple filtered empty state
        FilteredEmptyState()
    } else {
        // Full empty state with templates and actions
        EmptyStateWithTemplates(
            onQuickAddClick = onQuickAddClick,
            onTemplatesClick = onTemplatesClick,
            onAiGenerateClick = onAiGenerateClick,
            onTemplateClick = onTemplateClick
        )
    }
}

@Composable
private fun FilteredEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "No goals match your search",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Try adjusting your search or filter",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun EmptyStateWithTemplates(
    onQuickAddClick: () -> Unit,
    onTemplatesClick: () -> Unit,
    onAiGenerateClick: () -> Unit,
    onTemplateClick: (GoalTemplate) -> Unit
) {
    // Get featured templates (one from each category) - safely with firstOrNull
    val featuredTemplates = listOfNotNull(
        GoalTemplateProvider.getTemplatesByCategory(GoalCategory.CAREER).firstOrNull(),
        GoalTemplateProvider.getTemplatesByCategory(GoalCategory.PHYSICAL).firstOrNull(),
        GoalTemplateProvider.getTemplatesByCategory(GoalCategory.FINANCIAL).firstOrNull(),
        GoalTemplateProvider.getTemplatesByCategory(GoalCategory.EMOTIONAL).firstOrNull(),
        GoalTemplateProvider.getTemplatesByCategory(GoalCategory.FAMILY).firstOrNull(),
        GoalTemplateProvider.getTemplatesByCategory(GoalCategory.SPIRITUAL).firstOrNull()
    )

    // Use Column instead of LazyColumn to avoid nested scrolling issues
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Welcome Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Start Your Journey",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Set meaningful goals and track your progress",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        // Quick Actions Section
        Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                title = "Quick Add",
                subtitle = "Create from scratch",
                icon = Icons.Rounded.Add,
                gradientColors = listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
                onClick = onQuickAddClick,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                title = "AI Generate",
                subtitle = "Get suggestions",
                icon = Icons.Rounded.AutoAwesome,
                gradientColors = listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
                onClick = onAiGenerateClick,
                modifier = Modifier.weight(1f)
            )
        }

        // Templates Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Popular Templates",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            OutlinedButton(
                onClick = onTemplatesClick,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.LibraryBooks,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("See All", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Template Grid (2 columns)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            featuredTemplates.chunked(2).forEach { rowTemplates ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowTemplates.forEach { template ->
                        GoalTemplateCard(
                            template = template,
                            onClick = { onTemplateClick(template) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowTemplates.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Bottom spacing for FAB
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(gradientColors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GoalTemplateCard(
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
            // Icon with category color background
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(categoryColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = template.icon,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = template.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = template.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(8.dp))

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
        }
    }
}
