package az.tribe.lifeplanner.di

import az.tribe.lifeplanner.BuildKonfig
import az.tribe.lifeplanner.data.sync.SyncManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val supabaseModule = module {

    single<SupabaseClient> {
        createSupabaseClient(
            supabaseUrl = BuildKonfig.SUPABASE_URL,
            supabaseKey = BuildKonfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
                autoSaveToStorage = true
            }
            defaultSerializer = KotlinXSerializer(Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            })
            install(Postgrest)
        }
    }

    single { SyncManager(get(), get(), get()) }
}
