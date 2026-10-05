package az.tribe.lifeplanner.ui.v4.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import az.tribe.lifeplanner.ui.v4.theme.V4
import com.revenuecat.purchases.kmp.models.CustomerInfo
import com.revenuecat.purchases.kmp.models.StoreTransaction
import com.revenuecat.purchases.kmp.ui.revenuecatui.CustomerCenter
import com.revenuecat.purchases.kmp.ui.revenuecatui.Paywall
import com.revenuecat.purchases.kmp.ui.revenuecatui.PaywallListener
import com.revenuecat.purchases.kmp.ui.revenuecatui.PaywallOptions

/**
 * LifePlanner Plus. The paywall itself (plans, prices, copy, restore button) is designed in the
 * RevenueCat dashboard and rendered here, so it can change without an app release. A purchase or a
 * restore closes it; the You screen re-reads the entitlement when it comes back.
 */
@Composable
fun V4PlusScreen(onDone: () -> Unit) {
    val options = remember {
        PaywallOptions(dismissRequest = onDone) {
            shouldDisplayDismissButton = true
            listener = object : PaywallListener {
                override fun onPurchaseCompleted(customerInfo: CustomerInfo, storeTransaction: StoreTransaction) = onDone()
                override fun onRestoreCompleted(customerInfo: CustomerInfo) = onDone()
            }
        }
    }
    Box(Modifier.fillMaxSize().background(V4.colors.background).statusBarsPadding().navigationBarsPadding()) {
        Paywall(options)
    }
}

/**
 * Manage a subscription: plan, cancel, restore, refund requests. RevenueCat's Customer Center, also
 * configured from the dashboard, so the store rules for cancelling are not ours to keep up with.
 */
@Composable
fun V4SubscriptionScreen(onDone: () -> Unit) {
    CustomerCenter(modifier = Modifier.fillMaxSize(), onDismiss = onDone)
}
