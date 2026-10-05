package az.tribe.lifeplanner

import androidx.compose.ui.window.ComposeUIViewController
import az.tribe.lifeplanner.di.initKoin
import az.tribe.lifeplanner.ui.goal.GoalViewModel
import com.revenuecat.purchases.kmp.LogLevel
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration
import org.koin.compose.koinInject

fun MainViewController() = ComposeUIViewController (
    configure = {
        initKoin()
        // RevenueCat, same rule as Android: no key, no billing, app stays free.
        if (BuildKonfig.REVENUECAT_IOS_API_KEY.isNotBlank() && !Purchases.isConfigured) {
            Purchases.logLevel = if (BuildKonfig.isDebug) LogLevel.DEBUG else LogLevel.INFO
            Purchases.configure(PurchasesConfiguration(apiKey = BuildKonfig.REVENUECAT_IOS_API_KEY))
        }
        // The app keeps fields above the keyboard itself (imePadding at the root, as on Android).
        // The default also slides the whole view up, which doubled the jump and left gaps.
        onFocusBehavior = androidx.compose.ui.uikit.OnFocusBehavior.DoNothing
    }
){
    val mainViewModel =  koinInject<GoalViewModel>()
    App(mainViewModel)
}