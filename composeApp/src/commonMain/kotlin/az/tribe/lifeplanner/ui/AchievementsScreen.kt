package az.tribe.lifeplanner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.BadgeType
import az.tribe.lifeplanner.domain.model.BadgeCategory
import az.tribe.lifeplanner.domain.model.BadgeRequirements
import az.tribe.lifeplanner.ui.components.AvailableChallengeCard
import az.tribe.lifeplanner.ui.components.BadgeCard
import az.tribe.lifeplanner.ui.components.ChallengeCard
import az.tribe.lifeplanner.ui.components.GamificationStatCard
import az.tribe.lifeplanner.ui.components.LevelProgressBar
import az.tribe.lifeplanner.ui.gamification.GamificationViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onNavigateBack: () -> Unit,
    viewModel: GamificationViewModel = koinInject()
) {
    val userProgress by viewModel.userProgress.collectAsState()
    val badges by viewModel.badges.collectAsState()
    val activeChallenges by viewModel.activeChallenges.collectAsState()
    val completedChallenges by viewModel.completedChallenges.collectAsState()
    val availableChallenges by viewModel.availableChallenges.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Badges", "Challenges")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Achievements") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            if (!isLoading) {
                when (selectedTab) {
                    0 -> OverviewTab(
                        userProgress = userProgress,
                        badges = badges.size,
                        totalBadges = BadgeType.entries.size,
                        activeChallenges = activeChallenges.size,
                        recentBadges = badges.take(5).map { it.type }
                    )
                    1 -> BadgesTab(
                        earnedBadges = badges.map { it.type to it }.toMap(),
                        onBadgeClick = { viewModel.markBadgeAsSeen(it.id) }
                    )
                    2 -> ChallengesTab(
                        activeChallenges = activeChallenges,
                        completedChallenges = completedChallenges,
                        availableChallenges = availableChallenges,
                        onStartChallenge = { viewModel.startChallenge(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(
    userProgress: az.tribe.lifeplanner.domain.model.UserProgress?,
    badges: Int,
    totalBadges: Int,
    activeChallenges: Int,
    recentBadges: List<BadgeType>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Level Progress Card
        item {
            userProgress?.let { progress ->
                LevelProgressBar(
                    currentLevel = progress.currentLevel,
                    currentXp = progress.xpInCurrentLevel,
                    xpForNextLevel = progress.xpForNextLevel,
                    progress = progress.levelProgress,
                    title = progress.title
                )
            }
        }

        // Quick Stats Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GamificationStatCard(
                    icon = Icons.Default.LocalFireDepartment,
                    label = "Streak",
                    value = "${userProgress?.currentStreak ?: 0}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                GamificationStatCard(
                    icon = Icons.Default.EmojiEvents,
                    label = "Badges",
                    value = "$badges/$totalBadges",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                GamificationStatCard(
                    icon = Icons.Default.TrendingUp,
                    label = "Goals",
                    value = "${userProgress?.goalsCompleted ?: 0}",
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Total XP Card
        item {
            GamificationStatCard(
                icon = Icons.Default.Star,
                label = "Total XP Earned",
                value = "${userProgress?.totalXp ?: 0}",
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Activity Stats
        item {
            Column {
                Text(
                    text = "Activity Stats",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatItem(
                        label = "Habits Completed",
                        value = "${userProgress?.habitsCompleted ?: 0}",
                        modifier = Modifier.weight(1f)
                    )
                    StatItem(
                        label = "Journal Entries",
                        value = "${userProgress?.journalEntriesCount ?: 0}",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatItem(
                        label = "Longest Streak",
                        value = "${userProgress?.longestStreak ?: 0} days",
                        modifier = Modifier.weight(1f)
                    )
                    StatItem(
                        label = "Active Challenges",
                        value = "$activeChallenges",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Recent Badges
        if (recentBadges.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Recent Badges",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentBadges) { badgeType ->
                            BadgeCard(
                                badge = null,
                                badgeType = badgeType,
                                isEarned = true,
                                modifier = Modifier.width(100.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun BadgesTab(
    earnedBadges: Map<BadgeType, az.tribe.lifeplanner.domain.model.Badge>,
    onBadgeClick: (az.tribe.lifeplanner.domain.model.Badge) -> Unit
) {
    val categories = BadgeCategory.entries

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        categories.forEach { category ->
            val badgesInCategory = BadgeType.entries.filter {
                BadgeRequirements.getCategory(it) == category
            }

            if (badgesInCategory.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.height(
                                ((badgesInCategory.size + 2) / 3 * 140).dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(badgesInCategory) { badgeType ->
                                val badge = earnedBadges[badgeType]
                                BadgeCard(
                                    badge = badge,
                                    badgeType = badgeType,
                                    isEarned = badge != null,
                                    onClick = { badge?.let { onBadgeClick(it) } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengesTab(
    activeChallenges: List<az.tribe.lifeplanner.domain.model.Challenge>,
    completedChallenges: List<az.tribe.lifeplanner.domain.model.Challenge>,
    availableChallenges: List<az.tribe.lifeplanner.domain.enum.ChallengeType>,
    onStartChallenge: (az.tribe.lifeplanner.domain.enum.ChallengeType) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Active Challenges
        if (activeChallenges.isNotEmpty()) {
            item {
                Text(
                    text = "Active Challenges",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(activeChallenges) { challenge ->
                ChallengeCard(challenge = challenge)
            }
        }

        // Available Challenges
        if (availableChallenges.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Start a Challenge",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(availableChallenges.take(5)) { challengeType ->
                AvailableChallengeCard(
                    challengeType = challengeType,
                    onStartChallenge = onStartChallenge
                )
            }
        }

        // Completed Challenges
        if (completedChallenges.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Completed (${completedChallenges.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(completedChallenges.take(5)) { challenge ->
                ChallengeCard(challenge = challenge)
            }
        }

        // Empty state
        if (activeChallenges.isEmpty() && completedChallenges.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(16.dp)
                                .height(64.dp),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "No challenges yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Start a challenge above to earn XP!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}
