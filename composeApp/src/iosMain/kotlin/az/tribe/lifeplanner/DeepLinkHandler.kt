package az.tribe.lifeplanner

import az.tribe.lifeplanner.ui.viewmodel.AuthViewModel
import co.touchlab.kermit.Logger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import org.koin.mp.KoinPlatform
import platform.Foundation.NSURL

object DeepLinkHandler {
    fun handle(url: NSURL) {
        try {
            Logger.d("DeepLinkHandler") { "Received URL: ${url.absoluteString}" }
            val supabaseClient = KoinPlatform.getKoin().get<SupabaseClient>()

            val urlString = url.absoluteString ?: return
            val fragment = url.fragment

            // Check for error in the callback fragment (e.g. expired magic link)
            if (fragment != null && "error=" in fragment) {
                val params = fragment.split("&").associate {
                    val parts = it.split("=", limit = 2)
                    parts[0] to (parts.getOrNull(1)?.replace("+", " ") ?: "")
                }
                val errorDesc = params["error_description"] ?: params["error"] ?: "Authentication failed"
                Logger.w("DeepLinkHandler") { "Auth callback error: $errorDesc" }
                AuthViewModel.reportDeepLinkError(errorDesc)
                return
            }

            // Validate the URL is from an expected domain before processing
            val host = url.host
            val isExpectedDomain = host == "tribe.az" || host == "auth" || url.scheme == "lifeplanner"
            if (!isExpectedDomain) {
                Logger.w("DeepLinkHandler") { "Ignoring deep link from unexpected host: $host" }
                return
            }

            // Universal links (HTTPS) won't match the custom scheme handler.
            // If the URL contains an access_token fragment, rewrite to the custom scheme
            // so supabase-kt's handleDeeplinks can parse it.
            if (fragment != null && "access_token" in fragment && url.scheme == "https") {
                val customUrl = NSURL(string = "lifeplanner://auth#$fragment")
                if (customUrl != null) {
                    Logger.d("DeepLinkHandler") { "Rewriting to custom scheme: ${customUrl.absoluteString}" }
                    supabaseClient.handleDeeplinks(customUrl)
                } else {
                    Logger.e("DeepLinkHandler") { "Failed to construct custom scheme URL from fragment" }
                }
            } else if (urlString.contains("access_token") || urlString.contains("refresh_token") ||
                       urlString.contains("type=magiclink") || urlString.contains("type=signup")) {
                val customSchemeUrl = urlString
                    .replace("https://tribe.az/lifeplanner/auth/callback", "lifeplanner://auth")
                val customUrl = NSURL(string = customSchemeUrl)
                if (customUrl != null) {
                    Logger.d("DeepLinkHandler") { "Rewriting callback to custom scheme: ${customUrl.absoluteString}" }
                    supabaseClient.handleDeeplinks(customUrl)
                } else {
                    Logger.e("DeepLinkHandler") { "Failed to rewrite callback URL" }
                }
            } else {
                supabaseClient.handleDeeplinks(url)
            }
        } catch (e: Exception) {
            Logger.e("DeepLinkHandler", e) { "Error handling deep link: ${e.message}" }
        }
    }
}
