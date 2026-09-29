package az.tribe.lifeplanner

import androidx.compose.ui.window.ComposeUIViewController
import az.tribe.lifeplanner.di.initKoin
import az.tribe.lifeplanner.ui.goal.GoalViewModel
import org.koin.compose.koinInject

fun MainViewController() = ComposeUIViewController (
    configure = {
        initKoin()
        // The app keeps fields above the keyboard itself (imePadding at the root, as on Android).
        // The default also slides the whole view up, which doubled the jump and left gaps.
        onFocusBehavior = androidx.compose.ui.uikit.OnFocusBehavior.DoNothing
    }
){
    val mainViewModel =  koinInject<GoalViewModel>()
    App(mainViewModel)
}