package az.tribe.lifeplanner

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import org.koin.mp.KoinPlatform
import platform.Foundation.NSURL

object DeepLinkHandler {
    fun handle(url: NSURL) {
        val supabaseClient = KoinPlatform.getKoin().get<SupabaseClient>()
        supabaseClient.handleDeeplinks(url)
    }
}
