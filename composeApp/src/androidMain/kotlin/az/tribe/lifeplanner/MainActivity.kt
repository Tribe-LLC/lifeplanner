package az.tribe.lifeplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import az.tribe.lifeplanner.ui.GoalViewModel
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.mmk.kmpnotifier.extensions.onCreateOrOnNewIntent
import com.mmk.kmpnotifier.notification.NotifierManager
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        NotifierManager.onCreateOrOnNewIntent(intent)

        setContent {
            val systemUiController = rememberSystemUiController()

            val isDark = isSystemInDarkTheme()


            SideEffect {
                systemUiController.setStatusBarColor(
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    darkIcons = !isDark
                )
                systemUiController.setNavigationBarColor(
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    darkIcons = !isDark, navigationBarContrastEnforced = false
                )

            }
            val mainViewModel = koinInject<GoalViewModel>()

            App(viewModel = mainViewModel)
        }
    }
}
