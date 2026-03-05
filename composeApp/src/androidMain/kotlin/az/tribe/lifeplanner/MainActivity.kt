package az.tribe.lifeplanner

import android.content.Intent
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
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    private val supabaseClient: SupabaseClient by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        NotifierManager.onCreateOrOnNewIntent(intent)
        supabaseClient.handleDeeplinks(intent)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        NotifierManager.onCreateOrOnNewIntent(intent)
        supabaseClient.handleDeeplinks(intent)
    }
}
