package az.tribe.lifeplanner.util

import androidx.compose.runtime.Composable

/**
 * Triggers platform-specific in-app update flow.
 * On Android: uses Google Play In-App Update API (flexible flow).
 * On iOS: opens App Store URL.
 */
@Composable
expect fun InAppUpdateEffect(enabled: Boolean)
