package az.tribe.lifeplanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import az.tribe.lifeplanner.ui.GoalViewModel
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.mmk.kmpnotifier.extensions.onCreateOrOnNewIntent
import com.mmk.kmpnotifier.notification.NotifierManager
import android.net.Uri
import co.touchlab.kermit.Logger
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
        handleAuthDeeplink(intent)

        // Request POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

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
        handleAuthDeeplink(intent)
    }

    /**
     * Handle auth deep links from both custom scheme (lifeplanner://auth)
     * and HTTPS universal links (https://tribe.az/lifeplanner/auth/callback).
     * Supabase's handleDeeplinks only recognizes the custom scheme,
     * so rewrite HTTPS URLs to the custom scheme before passing them.
     */
    private fun handleAuthDeeplink(intent: Intent) {
        val uri = intent.data
        Logger.d("MainActivity") { "handleAuthDeeplink: $uri" }
        if (uri != null && uri.scheme == "https" && uri.fragment?.contains("access_token") == true) {
            val customUri = Uri.parse("lifeplanner://auth#${uri.fragment}")
            Logger.d("MainActivity") { "Rewriting to custom scheme: $customUri" }
            val rewrittenIntent = Intent(intent).setData(customUri)
            supabaseClient.handleDeeplinks(rewrittenIntent)
        } else {
            supabaseClient.handleDeeplinks(intent)
        }
    }
}
