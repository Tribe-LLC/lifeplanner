@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package az.tribe.lifeplanner.ui.health

import androidx.compose.runtime.Composable

/**
 * Platform-specific health permission request.
 * On Android, uses Health Connect permission contract via ActivityResultLauncher.
 * On iOS, permissions are requested during hasPermissions() call (HealthKit dialog).
 */
@Composable
expect fun rememberHealthPermissionLauncher(onResult: (Boolean) -> Unit): () -> Unit
