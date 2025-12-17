package az.tribe.lifeplanner.ui.components


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.GoalFilter
import az.tribe.lifeplanner.ui.theme.modernColors


@OptIn(ExperimentalMaterial3Api::class)
@Composable
 fun ModernTopAppBar(
    title: @Composable () -> Unit,
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
    currentUser: az.tribe.lifeplanner.domain.model.User? = null,
    onProfileClick: () -> Unit = {},
    onSignInClick: () -> Unit = {},
    onSignOutClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        dynamicColor,
                        dynamicColor.copy(alpha = 0.8f)
                    )
                )
            )
    ) {
        TopAppBar(
            title = {

                Column(modifier = Modifier.padding(bottom = if (showSearchBar) 8.dp else 0.dp)) {
                Box(modifier = Modifier.padding(start = 8.dp)) {
                    title()
                }

                if (showSearchBar) {
                    // Search field
                    OutlinedTextField(
                        textStyle = MaterialTheme.typography.labelLarge,
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                "Search goals...",
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.modernColors.primary,
                            unfocusedContainerColor = MaterialTheme.modernColors.primary,
                            disabledContainerColor = MaterialTheme.modernColors.primary,
                            focusedTextColor = MaterialTheme.modernColors.primary,
                            unfocusedTextColor = MaterialTheme.modernColors.primary,
                            cursorColor = MaterialTheme.modernColors.primary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    onSearchQueryChange(TextFieldValue(""))
                                    onSearchToggle(false)
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = Color.White
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    )
                }
                }
            },
            colors = TopAppBarDefaults.largeTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surface
            ),
            actions = {
                // Profile Button
                var showProfileMenu by remember { mutableStateOf(false) }
                Box {
                    UserProfileButton(
                        user = currentUser,
                        onProfileClick = { showProfileMenu = true },
                        onSignInClick = onSignInClick
                    )

                    UserProfileMenu(
                        user = currentUser,
                        expanded = showProfileMenu,
                        onDismiss = { showProfileMenu = false },
                        onViewProfile = onProfileClick,
                        onSignOut = onSignOutClick
                    )
                }


                if (!showSearchBar) {
                    IconButton(
                        onClick = { onSearchToggle(true) }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search"
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = onFilterClick
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FilterList,
                            contentDescription = "Filter"
                        )
                    }

                    // Filter dropdown menu
                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = onFilterMenuDismiss,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface)

                    ) {
                        GoalFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter.displayName) },
                                onClick = { onFilterSelected(filter) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (filter) {
                                            GoalFilter.ALL -> Icons.AutoMirrored.Rounded.FormatListBulleted
                                            GoalFilter.ACTIVE -> Icons.Rounded.PlayArrow
                                            GoalFilter.COMPLETED -> Icons.Rounded.CheckCircle
                                        },
                                        contentDescription = null,
                                        tint = if (selectedFilter == filter)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = if (selectedFilter == filter) {
                                    {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onAnalyticsClick
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Analytics,
                        contentDescription = "Analytics"
                    )
                }
            }
        )
    }
}