package az.tribe.lifeplanner.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

@Composable
actual fun InAppUpdateEffect(enabled: Boolean) {
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        val url = NSURL.URLWithString(getStoreUrl()) ?: return@LaunchedEffect
        UIApplication.sharedApplication.openURL(url)
    }
}
