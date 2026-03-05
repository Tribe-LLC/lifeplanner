package az.tribe.lifeplanner.ui

import az.tribe.lifeplanner.util.PlatformBackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SignalWifiOff
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.ui.theme.modernColors
import az.tribe.lifeplanner.ui.viewmodel.AuthState
import az.tribe.lifeplanner.ui.viewmodel.AuthViewModel
import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit = {}
) {
    val authViewModel: AuthViewModel = koinInject()
    val authState by authViewModel.authState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var finishRequested by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 4 }
    )

    // Handle finish: sign in as guest if not authenticated, then mark onboarding complete
    fun handleFinish() {
        if (isLoading) return
        finishRequested = true
        val state = authState
        if (state is AuthState.Guest || state is AuthState.Authenticated) {
            // Already authenticated — just complete onboarding
            authViewModel.completeOnboarding()
            onOnboardingComplete()
        } else {
            // Need to sign in first
            isLoading = true
            authViewModel.signInAsGuest()
        }
    }

    // When auth state resolves after sign-in, complete onboarding
    LaunchedEffect(authState) {
        if (finishRequested) {
            when (authState) {
                is AuthState.Guest, is AuthState.Authenticated -> {
                    isLoading = false
                    authViewModel.completeOnboarding()
                    onOnboardingComplete()
                }
                is AuthState.Error -> {
                    isLoading = false
                    Logger.e("OnboardingScreen") { "Auth error: ${(authState as AuthState.Error).message}" }
                }
                else -> {}
            }
        }
    }

    // Back handler: scroll pager back or do nothing on first page
    PlatformBackHandler(enabled = pagerState.currentPage > 0) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.modernColors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Skip link at top-right (hidden on last page)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (pagerState.currentPage < 3) {
                    TextButton(
                        onClick = { handleFinish() },
                        enabled = !isLoading
                    ) {
                        Text(
                            if (isLoading) "Loading..." else "Skip",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Main pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> OfflinePromisePage()
                    2 -> AiPreviewPage()
                    3 -> QuickTourPage()
                }
            }

            // Page indicator dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            // Bottom button
            Button(
                onClick = {
                    if (pagerState.currentPage < 3) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        handleFinish()
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 32.dp)
                    .height(52.dp)
            ) {
                Text(
                    text = when {
                        isLoading -> "Loading..."
                        pagerState.currentPage == 0 -> "Start"
                        pagerState.currentPage == 3 -> "Go to Dashboard"
                        pagerState.currentPage == 2 -> "Get Started"
                        else -> "Next"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }
    }
}

// --- Page composables ---

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Lean Life Planner",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Goals. Habits. Life. Offline-first.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun OfflinePromisePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Visual icons row
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Icon(
                Icons.Rounded.SignalWifiOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFF4CAF50)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Works Anywhere",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No internet needed. Track streaks, set goals, zero stress.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AiPreviewPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Rounded.Psychology,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "AI-Powered Coaching",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        val features = listOf(
            Icons.Rounded.AutoAwesome to "Goal suggestions tailored to you",
            Icons.Rounded.TrackChanges to "Habit coaching and streak tracking",
            Icons.Rounded.BarChart to "Weekly reviews and life balance insights"
        )
        features.forEach { (icon, text) ->
            FeatureRow(icon = icon, text = text)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = Color(0xFF4CAF50)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun QuickTourPage() {
    val tourPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 3 }
    )

    val slides = listOf(
        "Set goals and track milestones",
        "Build habits — streaks auto-count",
        "Offline works. Online? AI helps."
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Quick Tour",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        HorizontalPager(
            state = tourPagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) { page ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = slides[page],
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tour page indicator dots
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (index == tourPagerState.currentPage) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == tourPagerState.currentPage)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
    }
}
