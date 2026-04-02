package az.tribe.lifeplanner.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * iOS: password saving to iCloud Keychain is handled automatically by the system
 * when text fields carry the correct content type (username / password).
 * No explicit API call is needed here.
 */
@Composable
actual fun SaveCredentialEffect(
    email: String,
    password: String,
    trigger: Boolean,
    onComplete: () -> Unit
) {
    LaunchedEffect(trigger) {
        if (trigger) onComplete()
    }
}

/**
 * iOS: the system keyboard suggestion bar shows saved credentials automatically
 * when the text field has a password content type — no explicit picker needed.
 */
@Composable
actual fun GetCredentialEffect(
    trigger: Boolean,
    onCredentialReceived: (email: String, password: String) -> Unit,
    onComplete: () -> Unit
) {
    LaunchedEffect(trigger) {
        if (trigger) onComplete()
    }
}
