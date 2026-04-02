package az.tribe.lifeplanner.ui

import az.tribe.lifeplanner.BuildKonfig
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.ui.viewmodel.AuthState
import az.tribe.lifeplanner.ui.viewmodel.AuthViewModel
import az.tribe.lifeplanner.data.analytics.Analytics
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.random.Random

@Composable
fun WelcomeScreen(
    onComplete: () -> Unit = {}
) {
    val authViewModel: AuthViewModel = koinInject()
    val authState by authViewModel.authState.collectAsState()

    LaunchedEffect(Unit) {
        Analytics.onboardingStarted()
    }

    var hasNavigated by remember { mutableStateOf(false) }
    var animationDone by remember { mutableStateOf(false) }
    val fadeOut = remember { Animatable(1f) }

    // Auto-create guest in background immediately
    LaunchedEffect(Unit) {
        authViewModel.signInAsGuest()
    }

    // When both animation is done and auth is ready, fade out and navigate
    LaunchedEffect(animationDone, authState) {
        if (!hasNavigated && animationDone && (authState is AuthState.Guest || authState is AuthState.Authenticated)) {
            hasNavigated = true
            authViewModel.completeOnboarding()
            fadeOut.animateTo(0f, animationSpec = tween(600))
            onComplete()
        }
    }

    // Mark animation done after minimum splash duration
    LaunchedEffect(Unit) {
        delay(5000)
        animationDone = true
    }

    // Animated gradient shift for a living background feel
    val infiniteTransition = rememberInfiniteTransition(label = "welcomeBg")
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientShift"
    )

    Box(modifier = Modifier.fillMaxSize().alpha(fadeOut.value)) {
        // Animated gradient background — no audio, no ExoPlayer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0D0D2B),
                            Color(0xFF1A1A4E).copy(alpha = 0.6f + gradientShift * 0.4f),
                            Color(0xFF2D1B69).copy(alpha = 0.5f + (1f - gradientShift) * 0.3f),
                            Color(0xFF0D0D2B)
                        )
                    )
                )
        )

        // Subtle overlay for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.2f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.4f),
                            Color.Black.copy(alpha = 0.7f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        // Content — centered typewriter headline
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TypewriterHeadline()
        }
    }
}

private val glitchChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$%&*!?<>/"
private val neonCyan = Color(0xFF00F0FF)
private val neonPink = Color(0xFFFF00E5)
private val neonGreen = Color(0xFF39FF14)

@Composable
private fun TypewriterHeadline() {
    val fullText = "Design\nYour\nFuture"
    var displayText by remember { mutableStateOf("") }
    var glitchPhase by remember { mutableStateOf(false) }
    var showCursor by remember { mutableStateOf(true) }
    var settled by remember { mutableStateOf(false) }

    // Character-by-character typing with glitch scramble per character
    LaunchedEffect(Unit) {
        delay(600)
        for (i in fullText.indices) {
            val targetChar = fullText[i]
            if (targetChar == '\n') {
                displayText += '\n'
                delay(300)
                continue
            }
            // Scramble 3 random chars before settling
            for (j in 0 until 3) {
                val scrambled = glitchChars[Random.nextInt(glitchChars.length)]
                displayText = fullText.substring(0, i) + scrambled
                delay(40)
            }
            displayText = fullText.substring(0, i + 1)
            delay(60)
        }
        // Glitch flash after complete
        delay(200)
        glitchPhase = true
        delay(100)
        glitchPhase = false
        delay(80)
        glitchPhase = true
        delay(60)
        glitchPhase = false
        settled = true
        // Blink cursor a few times then hide
        repeat(4) {
            delay(500)
            showCursor = !showCursor
        }
        showCursor = false
    }

    // Subtle neon pulse for the settled state
    val infiniteTransition = rememberInfiniteTransition(label = "neon")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Glitch horizontal offset
    val glitchOffset = if (glitchPhase) Random.nextInt(-8, 8) else 0

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.offset { IntOffset(glitchOffset, 0) }
    ) {
        val lines = displayText.split('\n')
        lines.forEachIndexed { lineIndex, line ->
            Row {
                Text(
                    text = buildAnnotatedString {
                        line.forEachIndexed { charIndex, char ->
                            val color = if (settled) {
                                // Gradient effect: cyan → white → pink across the line
                                val ratio = if (line.length > 1) charIndex.toFloat() / (line.length - 1) else 0.5f
                                when {
                                    ratio < 0.3f -> neonCyan.copy(alpha = glowAlpha)
                                    ratio > 0.7f -> neonPink.copy(alpha = glowAlpha)
                                    else -> Color.White
                                }
                            } else if (charIndex == line.length - 1 && !settled) {
                                neonGreen // active typing char
                            } else {
                                Color.White.copy(alpha = 0.9f)
                            }
                            withStyle(SpanStyle(color = color)) {
                                append(char)
                            }
                        }
                        // Blinking cursor
                        if (showCursor && lineIndex == lines.lastIndex) {
                            withStyle(SpanStyle(color = neonCyan)) {
                                append("_")
                            }
                        }
                    },
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    letterSpacing = 3.sp,
                    lineHeight = 52.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AuthBottomSheet(
    isSignUp: Boolean,
    authViewModel: AuthViewModel,
    authState: AuthState,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val magicLinkSent by authViewModel.magicLinkSent.collectAsState()

    fun hideKeyboard() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    // Allow toggling between sign-up and sign-in within the same sheet
    var showSignUp by remember { mutableStateOf(isSignUp) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var usePasswordMode by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }
    var showOtpInput by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }

    // Server error: "email"/"password"/"general" → message
    var serverFieldError by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun clearErrors() { attempted = false; serverFieldError = null }
    fun onEmailChange(value: String) {
        email = value
        if (serverFieldError?.first == "email" || serverFieldError?.first == "general") serverFieldError = null
    }
    fun onPasswordChange(value: String) {
        password = value
        if (serverFieldError?.first == "password" || serverFieldError?.first == "general") serverFieldError = null
    }
    fun onDisplayNameChange(value: String) {
        displayName = value
        if (serverFieldError?.first == "name") serverFieldError = null
    }

    // Validation helpers
    val emailRegex = remember { Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$") }
    fun nameError(): String? = when {
        !attempted -> null
        displayName.isBlank() -> "Name is required"
        displayName.length < 2 -> "At least 2 characters"
        serverFieldError?.first == "name" -> serverFieldError?.second
        else -> null
    }
    fun emailError(): String? = when {
        !attempted -> null
        email.isBlank() -> "Email is required"
        !emailRegex.matches(email.trim()) -> "Enter a valid email"
        serverFieldError?.first == "email" -> serverFieldError?.second
        else -> null
    }
    fun passwordError(): String? = when {
        !attempted -> null
        password.isBlank() -> "Password is required"
        password.length < 6 -> "At least 6 characters"
        serverFieldError?.first == "password" -> serverFieldError?.second
        else -> null
    }
    fun generalError(): String? = if (serverFieldError?.first == "general") serverFieldError?.second else null
    fun isFormValid(includesName: Boolean): Boolean {
        val emailOk = email.isNotBlank() && emailRegex.matches(email.trim())
        val passOk = password.isNotBlank() && password.length >= 6
        val nameOk = !includesName || (displayName.isNotBlank() && displayName.length >= 2)
        return emailOk && passOk && nameOk
    }

    // Track which error we already handled so stale errors don't re-trigger on recomposition
    var lastHandledError by remember { mutableStateOf<String?>(null) }

    // Map server errors to fields inline — no snackbar
    LaunchedEffect(authState) {
        if (authState is AuthState.Error) {
            val errorMsg = (authState as AuthState.Error).message
            // Skip if we already handled this exact error (e.g. sheet reopened with stale state)
            if (errorMsg == lastHandledError) return@LaunchedEffect
            lastHandledError = errorMsg

            keyboardController?.hide()
            attempted = true
            val msg = errorMsg.lowercase()
            when {
                "email" in msg && ("invalid" in msg || "format" in msg || "not found" in msg || "already" in msg) ->
                    serverFieldError = "email" to errorMsg
                "password" in msg && ("weak" in msg || "short" in msg || "incorrect" in msg || "wrong" in msg || "invalid" in msg) ->
                    serverFieldError = "password" to errorMsg
                else -> serverFieldError = "general" to errorMsg
            }
        } else {
            // Reset tracked error when state changes away from Error
            lastHandledError = null
        }
    }

    // Watch for successful auth — only navigate when a real auth transition happens.
    // Track the initial user ID so we don't auto-close when the sheet opens on an
    // already-authenticated screen (e.g. Guest opening sign-in from Profile).
    val initialUserId = remember {
        when (authState) {
            is AuthState.Authenticated -> (authState as AuthState.Authenticated).user.id
            is AuthState.Guest -> (authState as AuthState.Guest).user.id
            else -> null
        }
    }
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                val newId = (authState as AuthState.Authenticated).user.id
                if (newId != initialUserId) onSuccess()
            }
            is AuthState.Guest -> {
                val newId = (authState as AuthState.Guest).user.id
                if (newId != initialUserId) onSuccess()
            }
            else -> {}
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            authViewModel.clearMagicLinkState()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Show different header based on verification state
            val isVerifying = authState is AuthState.EmailVerificationPending

            Text(
                text = when {
                    isVerifying -> "Verify Your Email"
                    showSignUp -> "Create Account"
                    else -> "Welcome Back"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = when {
                    isVerifying -> "Enter the 6-digit code we sent to your email"
                    showSignUp -> "Sign up to start planning your life"
                    !usePasswordMode -> "We'll send a magic link to your email"
                    else -> "Sign in with your password"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            if (showSignUp && !isVerifying) {
                val emailFocus = remember { FocusRequester() }
                val passwordFocus = remember { FocusRequester() }
                val isCurrentlyGuest = authState is AuthState.Guest

                fun submitSignUp() {
                    attempted = true
                    hideKeyboard()
                    if (isFormValid(includesName = true)) {
                        if (isCurrentlyGuest) {
                            // Link guest account — preserves UID and all local data
                            authViewModel.linkGuestAccount(email.trim(), password, displayName.trim())
                        } else {
                            authViewModel.signUpWithEmail(email.trim(), password, displayName.trim())
                        }
                    }
                }

                val nameErr = nameError()
                val emailErr = emailError()
                val passErr = passwordError()

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { onDisplayNameChange(it) },
                    label = { Text("Display Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    supportingText = nameErr?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    isError = nameErr != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { emailFocus.requestFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { onEmailChange(it) },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    supportingText = emailErr?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    isError = emailErr != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(emailFocus),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { onPasswordChange(it) },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    supportingText = if (passErr != null) {
                        { Text(passErr, color = MaterialTheme.colorScheme.error) }
                    } else if (!attempted) {
                        { Text("Minimum 6 characters", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else null,
                    isError = passErr != null,
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { submitSignUp() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { submitSignUp() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = authState !is AuthState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (authState is AuthState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text(
                            if (isCurrentlyGuest) "Link Account" else "Create Account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, color = Color.White
                        )

                    }
                }

                generalError()?.let { error ->
                    Spacer(Modifier.height(12.dp))
                    InlineErrorBanner(error)
                }

                // Toggle to sign-in (only for non-guest users creating a fresh account)
                if (!isCurrentlyGuest) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = {
                        showSignUp = false
                        clearErrors()
                        email = ""; password = ""; displayName = ""
                    }) {
                        Text(
                            "Already have an account? Sign in",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else if (isVerifying) {
                // --- EMAIL VERIFICATION: OTP + deep link auto-detect ---
                val pendingEmail = (authState as AuthState.EmailVerificationPending).email
                var verifyCode by remember { mutableStateOf("") }

                // Start polling for verification done on another device (keyed on email to avoid duplicate loops)
                LaunchedEffect(pendingEmail) {
                    authViewModel.startVerificationPolling()
                }

                // Auto-submit when 6 digits entered
                LaunchedEffect(verifyCode) {
                    if (verifyCode.length == 6) {
                        hideKeyboard()
                        authViewModel.verifySignupOtp(pendingEmail, verifyCode)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFF4CAF50).copy(alpha = 0.1f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Check your inbox!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "We sent an email to $pendingEmail",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4CAF50).copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Option 1: Tap the link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Tap the link in the email to verify instantly",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Divider with "or"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Text(
                        "  or enter the code  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                }

                Spacer(Modifier.height(16.dp))

                // Option 2: Enter the 6-digit code
                OutlinedTextField(
                    value = verifyCode,
                    onValueChange = { v ->
                        if (v.length <= 6 && v.all { it.isDigit() }) verifyCode = v
                    },
                    label = { Text("6-digit code") },
                    placeholder = { Text("000000") },
                    leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        hideKeyboard()
                        if (verifyCode.length == 6) {
                            authViewModel.verifySignupOtp(pendingEmail, verifyCode)
                        }
                    }),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                generalError()?.let { error ->
                    Spacer(Modifier.height(12.dp))
                    InlineErrorBanner(error)
                }

                Spacer(Modifier.height(12.dp))

                TextButton(
                    onClick = {
                        verifyCode = ""
                        serverFieldError = null
                        authViewModel.resendVerificationEmail(pendingEmail)
                    }
                ) {
                    Text(
                        "Didn't get it? Resend",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (!usePasswordMode) {
                // --- SIGN IN: Magic Link (primary) ---
                if (!magicLinkSent) {
                    fun submitMagicLink() {
                        attempted = true
                        hideKeyboard()
                        if (email.isNotBlank() && emailRegex.matches(email.trim())) {
                            authViewModel.sendMagicLink(email.trim())
                        }
                    }

                    val magicEmailErr = emailError()

                    OutlinedTextField(
                        value = email,
                        onValueChange = { onEmailChange(it) },
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        supportingText = magicEmailErr?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        isError = magicEmailErr != null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submitMagicLink() }),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = { submitMagicLink() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = authState !is AuthState.Loading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (authState is AuthState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.Email, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Send Magic Link", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }

                    generalError()?.let { error ->
                        Spacer(Modifier.height(12.dp))
                        InlineErrorBanner(error)
                    }
                } else {
                    // Magic link sent — show OTP entry
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color(0xFF4CAF50).copy(alpha = 0.1f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            "Magic link sent to $email!\nCheck your inbox.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4CAF50),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    if (!showOtpInput) {
                        TextButton(onClick = { showOtpInput = true }) {
                            Text("Enter code manually", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { if (it.length <= 6) otpCode = it },
                            label = { Text("6-digit code") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(onGo = {
                                hideKeyboard()
                                if (email.isNotBlank() && otpCode.length == 6) {
                                    authViewModel.verifyOtp(email, otpCode)
                                }
                            }),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                hideKeyboard()
                                if (email.isNotBlank() && otpCode.length == 6) {
                                    authViewModel.verifyOtp(email, otpCode)
                                }
                            },
                            enabled = otpCode.length == 6 && authState !is AuthState.Loading,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            if (authState is AuthState.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                            } else {
                                Text("Verify Code", style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    TextButton(onClick = {
                        authViewModel.clearMagicLinkState()
                        otpCode = ""
                        showOtpInput = false
                    }) {
                        Text("Send a new link", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(8.dp))

                TextButton(onClick = { usePasswordMode = true; clearErrors() }) {
                    Text("Use password instead",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }

                TextButton(onClick = {
                    showSignUp = true
                    clearErrors()
                    email = ""; password = ""
                    authViewModel.clearMagicLinkState()
                }) {
                    Text(
                        "New here? Create account",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                // --- SIGN IN: Password mode ---
                val signInPasswordFocus = remember { FocusRequester() }

                fun submitSignIn() {
                    attempted = true
                    hideKeyboard()
                    if (email.isNotBlank() && emailRegex.matches(email.trim()) && password.isNotBlank()) {
                        authViewModel.signInWithEmail(email.trim(), password)
                    }
                }

                val signInEmailErr = emailError()
                val signInPassErr = passwordError()

                OutlinedTextField(
                    value = email,
                    onValueChange = { onEmailChange(it) },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    supportingText = signInEmailErr?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    isError = signInEmailErr != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { signInPasswordFocus.requestFocus() }),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { onPasswordChange(it) },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    supportingText = signInPassErr?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    isError = signInPassErr != null,
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { submitSignIn() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(signInPasswordFocus),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { submitSignIn() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = authState !is AuthState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (authState is AuthState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("Sign In", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }

                generalError()?.let { error ->
                    Spacer(Modifier.height(12.dp))
                    InlineErrorBanner(error)
                }

                Spacer(Modifier.height(8.dp))

                TextButton(onClick = {
                    usePasswordMode = false
                    clearErrors()
                    authViewModel.clearMagicLinkState()
                }) {
                    Text("Use magic link instead",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                }

                TextButton(onClick = {
                    showSignUp = true
                    clearErrors()
                    usePasswordMode = false
                    email = ""; password = ""
                    authViewModel.clearMagicLinkState()
                }) {
                    Text(
                        "New here? Create account",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

        }
    }
}

@Composable
private fun InlineErrorBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.errorContainer,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
