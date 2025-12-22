package az.tribe.lifeplanner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.domain.model.OnboardingData
import az.tribe.lifeplanner.domain.repository.UserRepository
import az.tribe.lifeplanner.ui.theme.modernColors
import az.tribe.lifeplanner.ui.viewmodel.AuthState
import az.tribe.lifeplanner.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit = {}
) {
    val authViewModel: AuthViewModel = koinInject()
    val userRepository: UserRepository = koinInject()
    val authState by authViewModel.authState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Store pending onboarding data to save after auth succeeds
    var pendingOnboardingData by remember { mutableStateOf<OnboardingData?>(null) }

    // Handle auth state changes
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Guest, is AuthState.Authenticated -> {
                isLoading = false
                // Save onboarding data if we have any pending
                pendingOnboardingData?.let { data ->
                    try {
                        val currentUser = userRepository.getCurrentUser()
                        if (currentUser != null) {
                            userRepository.saveOnboardingData(currentUser.id, data)
                        }
                    } catch (e: Exception) {
                        println("Failed to save onboarding data: ${e.message}")
                    }
                    pendingOnboardingData = null
                }
                onOnboardingComplete()
            }
            is AuthState.Error -> {
                isLoading = false
                errorMessage = (authState as AuthState.Error).message
            }
            else -> {}
        }
    }

    val pages = listOf(
        OnboardingPage(
            "Welcome to Lean Life Planner",
            "Craft a meaningful life with clarity and intention."
        ),
        OnboardingPage(
            "Choose Your Symbol",
            "Which icon reflects how you see yourself in this journey?"
        ),
        OnboardingPage(
            "Define Your Priorities",
            "Which 3 areas are most important to you right now?"
        ),
        OnboardingPage("Your Age Range", "Choose the age group you belong to."),
        OnboardingPage("Your Profession", "What role describes your current professional journey?"),
        OnboardingPage(
            "Relationship Status",
            "Which statement best reflects your current relationship status?"
        ),
        OnboardingPage(
            "Mindset Check-in",
            "What’s a mindset you want to strengthen in this season of life?"
        ),
        OnboardingPage("You're All Set", "Tap below to begin your lean lifestyle journey.")
    )
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { pages.size }
    )

    var selectedCategoryList by remember { mutableStateOf(listOf<String>()) }
    var age by remember { mutableStateOf("") }
    var profession by remember { mutableStateOf("") }
    var relationshipStatus by remember { mutableStateOf("") }
    var selectedSymbol by remember { mutableStateOf("") }
    var selectedMindset by remember { mutableStateOf("") }

    // Function to handle onboarding completion
    fun handleOnboardingComplete() {
        if (isLoading) return // Prevent double-clicks
        isLoading = true
        errorMessage = null

        // Store onboarding data to be saved after auth succeeds
        pendingOnboardingData = OnboardingData(
            selectedSymbol = selectedSymbol,
            priorities = selectedCategoryList,
            ageRange = age,
            profession = profession,
            relationshipStatus = relationshipStatus,
            mindset = selectedMindset
        )

        // Sign in as guest - LaunchedEffect will handle navigation when auth completes
        authViewModel.signInAsGuest()
    }

    // Function to skip onboarding entirely
    fun handleSkip() {
        if (isLoading) return
        isLoading = true
        errorMessage = null
        // No onboarding data to save
        pendingOnboardingData = null
        authViewModel.signInAsGuest()
    }

    Scaffold(
        containerColor = MaterialTheme.modernColors.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Skip button at the top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (pagerState.currentPage < pages.lastIndex) {
                    TextButton(
                        onClick = { handleSkip() },
                        enabled = !isLoading
                    ) {
                        Text(
                            text = if (isLoading) "Loading..." else "Skip",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = false
            ) { page ->
                OnboardingPageView(
                    page = pages[page],
                    selectedCategoryList = selectedCategoryList,
                    onCategoryListChange = { list -> selectedCategoryList = list },
                    age = age,
                    onAgeChange = { age = it },
                    profession = profession,
                    onProfessionChange = { profession = it },
                    relationshipStatus = relationshipStatus,
                    onRelationshipStatusChange = { relationshipStatus = it },
                    symbol = selectedSymbol,
                    onSymbolChange = { selectedSymbol = it },
                    mindset = selectedMindset,
                    onMindsetChange = { selectedMindset = it }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                        enabled = !isLoading
                    ) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(64.dp))
                }
                val currentTitle = pages[pagerState.currentPage].title
                val canProceed = when (currentTitle) {
                    "Choose Your Symbol" -> selectedSymbol.isNotEmpty()
                    "Define Your Priorities" -> selectedCategoryList.size == 3
                    "Your Age Range" -> age.isNotEmpty()
                    "Your Profession" -> profession.isNotEmpty()
                    "Relationship Status" -> relationshipStatus.isNotEmpty()
                    "Mindset Check-in" -> selectedMindset.isNotEmpty()
                    else -> true
                }
                Button(
                    onClick = {
                        if (pagerState.currentPage < pages.lastIndex) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            handleOnboardingComplete()
                        }
                    },
                    enabled = canProceed && !isLoading
                ) {
                    Text(
                        if (isLoading) "Loading..."
                        else if (pagerState.currentPage == pages.lastIndex) "Continue as Guest"
                        else "Next"
                    )
                }
            }

            // Error message display
            errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

data class OnboardingPage(val title: String, val description: String)

@Composable
fun OnboardingPageView(
    page: OnboardingPage,
    selectedCategoryList: List<String> = emptyList(),
    onCategoryListChange: (List<String>) -> Unit = {},
    age: String = "",
    onAgeChange: (String) -> Unit = {},
    profession: String = "",
    onProfessionChange: (String) -> Unit = {},
    relationshipStatus: String = "",
    onRelationshipStatusChange: (String) -> Unit = {},
    symbol: String = "",
    onSymbolChange: (String) -> Unit = {},
    mindset: String = "",
    onMindsetChange: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = page.title,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            color = MaterialTheme.colorScheme.secondary, // Soft green for modern touch
            lineHeight = 36.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        if (page.title == "Define Your Priorities") {
            Text(
                text = "Select up to 3 categories that matter most to you",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = page.description,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (page.title == "Choose Your Symbol") {
            val options = listOf("🦊", "🐢", "🦁", "🐰", "🐉")
            var selectedOption by remember { mutableStateOf(symbol) }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEach { option ->
                    Text(
                        text = option,
                        fontSize = 32.sp,
                        modifier = Modifier
                            .background(
                                color = if (option == selectedOption) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(8.dp)
                            .clickable {
                                selectedOption = option
                                onSymbolChange(option)
                            }
                    )
                }
            }
        }

        if (page.title == "Define Your Priorities") {
            val categories = listOf(
                "FINANCIAL",
                "CAREER",
                "PHYSICAL",
                "EMOTIONAL",
                "FAMILY",
                "SOCIAL",
                "SPIRITUAL"
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    val isSelected = selectedCategoryList.contains(category)
                    Button(
                        onClick = {
                            val updated = when {
                                isSelected -> selectedCategoryList.filterNot { it == category }
                                selectedCategoryList.size < 3 -> selectedCategoryList + category
                                else -> selectedCategoryList
                            }
                            onCategoryListChange(updated)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = category.first().uppercase() + category.substring(1).lowercase(),
                            color = if (isSelected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        // Age Range page
        if (page.title == "Your Age Range") {
            val ageRanges = listOf("18–24", "25–34", "35–44", "45+")
            var selectedAgeRange by remember { mutableStateOf(age) }
            ageRanges.forEach { option ->
                Button(
                    onClick = {
                        selectedAgeRange = option
                        onAgeChange(option)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedAgeRange == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background
                    ),
                    border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        option,
                        color = if (selectedAgeRange == option) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
        // Profession page
        if (page.title == "Your Profession") {
            val professions =
                listOf("Student", "Creative", "Engineer", "Manager", "Entrepreneur", "Other")
            var selectedProfession by remember { mutableStateOf(profession) }
            professions.forEach { option ->
                Button(
                    onClick = {
                        selectedProfession = option
                        onProfessionChange(option)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedProfession == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                    ),
                    border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(option,
                        color = if (selectedProfession == option) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        // Relationship Status page
        if (page.title == "Relationship Status") {
            val relationshipStatuses =
                listOf("Single", "In a Relationship", "Married", "Prefer Not to Say")
            var selectedRelationshipStatus by remember { mutableStateOf(relationshipStatus) }
            relationshipStatuses.forEach { option ->
                Button(
                    onClick = {
                        selectedRelationshipStatus = option
                        onRelationshipStatusChange(option)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedRelationshipStatus == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background
                    ),
                    border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(option,
                        color = if (selectedRelationshipStatus == option) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        if (page.title == "Mindset Check-in") {
            val mindsets =
                listOf("Consistency", "Patience", "Focus", "Gratitude", "Self-Discipline")
            var selectedMindset by remember { mutableStateOf(mindset) }
            mindsets.forEach { option ->
                Button(
                    onClick = {
                        selectedMindset = option
                        onMindsetChange(option)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedMindset == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background
                    ),
                    border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(option,
                    color = if (selectedMindset == option) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}