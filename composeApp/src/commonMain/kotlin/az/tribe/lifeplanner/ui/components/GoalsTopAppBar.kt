package az.tribe.lifeplanner.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.GoalFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsTopAppBar(
    dynamicColor: Color,
    showSearchBar: Boolean,
    searchQuery: TextFieldValue,
    onSearchQueryChange: (TextFieldValue) -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onFilterClick: () -> Unit,
    onAnalyticsClick: () -> Unit,
    selectedFilter: GoalFilter,
    showFilterMenu: Boolean,
    onFilterMenuDismiss: () -> Unit,
    onFilterSelected: (GoalFilter) -> Unit,
    onTemplatesClick: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    val animatedColor by animateColorAsState(
        targetValue = dynamicColor,
        animationSpec = tween(durationMillis = 300)
    )

        TopAppBar(
            scrollBehavior = scrollBehavior,
            title = {
                if (showSearchBar) {
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search goals...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        trailingIcon = {
                            IconButton(onClick = {
                                onSearchQueryChange(TextFieldValue(""))
                                onSearchToggle(false)
                            }) {
                                Icon(Icons.Rounded.Close, "Close search")
                            }
                        }
                    )
                } else {
                    Text(
                        "Goals",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = animatedColor
                    )
                }
            },
            actions = {
                if (!showSearchBar) {
                    // Templates button
                    IconButton(onClick = onTemplatesClick) {
                        Icon(
                            Icons.Rounded.LibraryBooks,
                            contentDescription = "Templates",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Search button
                    IconButton(onClick = { onSearchToggle(true) }) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Filter button with dropdown
                    Box {
                        IconButton(onClick = onFilterClick) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (selectedFilter != GoalFilter.ALL)
                                            animatedColor.copy(alpha = 0.1f)
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.FilterList,
                                    contentDescription = "Filter",
                                    tint = if (selectedFilter != GoalFilter.ALL)
                                        animatedColor
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (selectedFilter != GoalFilter.ALL) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = selectedFilter.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = animatedColor
                                    )
                                }
                            }
                        }

                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = onFilterMenuDismiss
                        ) {
                            GoalFilter.entries.forEach { filter ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = filter.icon,
                                                contentDescription = null,
                                                tint = if (filter == selectedFilter)
                                                    MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                filter.displayName,
                                                fontWeight = if (filter == selectedFilter)
                                                    FontWeight.Bold
                                                else FontWeight.Normal
                                            )
                                        }
                                    },
                                    onClick = { onFilterSelected(filter) }
                                )
                            }
                        }
                    }

                    // Analytics button
                    IconButton(onClick = onAnalyticsClick) {
                        Icon(
                            Icons.Rounded.Analytics,
                            contentDescription = "Analytics",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
}
