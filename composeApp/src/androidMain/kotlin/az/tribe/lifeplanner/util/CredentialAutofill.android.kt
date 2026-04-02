package az.tribe.lifeplanner.util

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import co.touchlab.kermit.Logger

@Composable
actual fun SaveCredentialEffect(
    email: String,
    password: String,
    trigger: Boolean,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    LaunchedEffect(trigger) {
        if (!trigger || email.isBlank() || password.isBlank() || activity == null) {
            onComplete()
            return@LaunchedEffect
        }
        try {
            val credentialManager = CredentialManager.create(activity)
            credentialManager.createCredential(
                context = activity,
                request = CreatePasswordRequest(id = email, password = password)
            )
            Logger.d("CredentialManager") { "Password saved for $email" }
        } catch (e: Exception) {
            // User cancelled or save not supported — not an error worth surfacing
            Logger.d("CredentialManager") { "Password save skipped: ${e.message}" }
        }
        onComplete()
    }
}

@Composable
actual fun GetCredentialEffect(
    trigger: Boolean,
    onCredentialReceived: (email: String, password: String) -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    LaunchedEffect(trigger) {
        if (!trigger || activity == null) {
            onComplete()
            return@LaunchedEffect
        }
        try {
            val credentialManager = CredentialManager.create(activity)
            val request = GetCredentialRequest(
                credentialOptions = listOf(GetPasswordOption())
            )
            val result = credentialManager.getCredential(context = activity, request = request)
            val credential = result.credential
            if (credential is PasswordCredential) {
                Logger.d("CredentialManager") { "Credential retrieved for ${credential.id}" }
                onCredentialReceived(credential.id, credential.password)
            }
        } catch (e: GetCredentialCancellationException) {
            Logger.d("CredentialManager") { "User cancelled credential picker" }
        } catch (e: NoCredentialException) {
            Logger.d("CredentialManager") { "No saved credentials found" }
        } catch (e: Exception) {
            Logger.w("CredentialManager") { "GetCredential failed: ${e.message}" }
        }
        onComplete()
    }
}
