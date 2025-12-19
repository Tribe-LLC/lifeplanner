package az.tribe.lifeplanner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.ui.theme.modernColors
import az.tribe.lifeplanner.ui.viewmodel.AuthState
import az.tribe.lifeplanner.ui.viewmodel.AuthViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    onSignInSuccess: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val authViewModel: AuthViewModel = koinInject()
    val authState by authViewModel.authState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showResetPasswordDialog by remember { mutableStateOf(false) }

    // Track if we've already navigated to prevent double navigation
    var hasNavigated by remember { mutableStateOf(false) }

    // Handle auth state changes
    LaunchedEffect(authState) {
        if (!hasNavigated) {
            when (authState) {
                is AuthState.Authenticated, is AuthState.Guest -> {
                    hasNavigated = true
                    onSignInSuccess()
                }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSignUp) "Create Account" else "Sign In") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.modernColors.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        containerColor = MaterialTheme.modernColors.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo or Icon
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.modernColors.primary
            )

            Spacer(Modifier.height(16.dp))

            // Title
            Text(
                text = if (isSignUp) "Create Your Account" else "Welcome Back",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (isSignUp)
                    "Sign up to start planning your life"
                else
                    "Sign in to continue your journey",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // Display Name Field (only for sign up)
            if (isSignUp) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.modernColors.primary,
                        focusedLabelColor = MaterialTheme.modernColors.primary,
                        focusedLeadingIconColor = MaterialTheme.modernColors.primary,
                        cursorColor = MaterialTheme.modernColors.primary
                    )
                )
                Spacer(Modifier.height(16.dp))
            }

            // Email Field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.modernColors.primary,
                    focusedLabelColor = MaterialTheme.modernColors.primary,
                    focusedLeadingIconColor = MaterialTheme.modernColors.primary,
                    cursorColor = MaterialTheme.modernColors.primary
                )
            )

            Spacer(Modifier.height(16.dp))

            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.modernColors.primary,
                    focusedLabelColor = MaterialTheme.modernColors.primary,
                    focusedLeadingIconColor = MaterialTheme.modernColors.primary,
                    cursorColor = MaterialTheme.modernColors.primary
                )
            )

            // Forgot Password (only for sign in)
            if (!isSignUp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showResetPasswordDialog = true }) {
                        Text(
                            "Forgot Password?",
                            color = MaterialTheme.modernColors.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Sign In/Sign Up Button
            Button(
                onClick = {
                    if (isSignUp) {
                        if (displayName.isNotBlank() && email.isNotBlank() && password.isNotBlank()) {
                            authViewModel.signUpWithEmail(email, password, displayName)
                        }
                    } else {
                        if (email.isNotBlank() && password.isNotBlank()) {
                            authViewModel.signInWithEmail(email, password)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = authState !is AuthState.Loading &&
                        email.isNotBlank() &&
                        password.isNotBlank() &&
                        (!isSignUp || displayName.isNotBlank()),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.modernColors.primary
                )
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        if (isSignUp) "Sign Up" else "Sign In",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    "OR",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))

            // Continue as Guest Button
            OutlinedButton(
                onClick = { authViewModel.signInAsGuest() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = authState !is AuthState.Loading,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.modernColors.primary
                )
            ) {
                Text(
                    "Continue as Guest",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(24.dp))

            // Toggle Sign In/Sign Up
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isSignUp) "Already have an account?" else "Don't have an account?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = {
                    isSignUp = !isSignUp
                    // Clear error when switching
                    authViewModel.refreshAuthState()
                }) {
                    Text(
                        if (isSignUp) "Sign In" else "Sign Up",
                        color = MaterialTheme.modernColors.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Error Message
            if (authState is AuthState.Error) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = (authState as AuthState.Error).message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    // Password Reset Dialog
    if (showResetPasswordDialog) {
        var resetEmail by remember { mutableStateOf(email) }
        var resetSent by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                showResetPasswordDialog = false
                resetSent = false
            },
            title = { Text("Reset Password") },
            text = {
                Column {
                    if (!resetSent) {
                        Text("Enter your email address and we'll send you a link to reset your password.")
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = resetEmail,
                            onValueChange = { resetEmail = it },
                            label = { Text("Email") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else {
                        Text("Password reset email sent! Please check your inbox.")
                    }
                }
            },
            confirmButton = {
                if (!resetSent) {
                    TextButton(
                        onClick = {
                            if (resetEmail.isNotBlank()) {
                                authViewModel.sendPasswordResetEmail(resetEmail)
                                resetSent = true
                            }
                        },
                        enabled = resetEmail.isNotBlank()
                    ) {
                        Text("Send Reset Link")
                    }
                } else {
                    TextButton(onClick = {
                        showResetPasswordDialog = false
                        resetSent = false
                    }) {
                        Text("OK")
                    }
                }
            },
            dismissButton = {
                if (!resetSent) {
                    TextButton(onClick = {
                        showResetPasswordDialog = false
                        resetSent = false
                    }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}
