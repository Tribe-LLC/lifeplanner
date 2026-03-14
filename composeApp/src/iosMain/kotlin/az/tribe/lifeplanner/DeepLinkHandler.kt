package az.tribe.lifeplanner

import co.touchlab.kermit.Logger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import org.koin.mp.KoinPlatform
import platform.Foundation.NSURL

object DeepLinkHandler {
    fun handle(url: NSURL) {
        Logger.d("DeepLinkHandler") { "Received URL: ${url.absoluteString}" }
        val supabaseClient = KoinPlatform.getKoin().get<SupabaseClient>()

        val urlString = url.absoluteString ?: return

        // Universal links (HTTPS) won't match the custom scheme handler.
        // If the URL contains an access_token fragment, rewrite to the custom scheme
        // so supabase-kt's handleDeeplinks can parse it.
        val fragment = url.fragment
        if (fragment != null && "access_token" in fragment && url.scheme == "https") {
            val customUrl = NSURL(string = "lifeplanner://auth#$fragment")
            Logger.d("DeepLinkHandler") { "Rewriting to custom scheme: ${customUrl.absoluteString}" }
            supabaseClient.handleDeeplinks(customUrl)
        } else {
            supabaseClient.handleDeeplinks(url)
        }
    }
}
