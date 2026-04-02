package az.tribe.lifeplanner.util

import androidx.compose.runtime.Composable

/**
 * Silently saves an email+password pair to the device credential manager
 * (Android Keystore / iCloud Keychain) right after a successful sign-in or sign-up.
 *
 * Set [trigger] to true to fire the save. Reset it to false via [onComplete] so it
 * doesn't re-fire on recomposition.
 */
@Composable
expect fun SaveCredentialEffect(
    email: String,
    password: String,
    trigger: Boolean,
    onComplete: () -> Unit
)

/**
 * Shows the platform credential chooser (Android: CredentialManager bottom sheet;
 * iOS: no-op — the system keyboard suggestion bar handles this natively).
 *
 * Set [trigger] to true to open the chooser. On success [onCredentialReceived] is
 * called with the email and password. [onComplete] is always called afterwards so
 * the caller can reset [trigger].
 */
@Composable
expect fun GetCredentialEffect(
    trigger: Boolean,
    onCredentialReceived: (email: String, password: String) -> Unit,
    onComplete: () -> Unit
)
