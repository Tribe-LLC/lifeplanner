package az.tribe.lifeplanner.data.network

import az.tribe.lifeplanner.data.repository.SystemPromptStore
import co.touchlab.kermit.Logger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable

class SystemPromptFetcher(private val supabase: SupabaseClient) {

    @Serializable
    private data class SystemPromptDto(val key: String, val content: String)

    suspend fun fetch() {
        try {
            val prompts = supabase.postgrest["system_prompts"]
                .select()
                .decodeList<SystemPromptDto>()
            SystemPromptStore.update(prompts.associate { it.key to it.content })
            Logger.d("SystemPromptFetcher") { "Loaded ${prompts.size} system prompts" }
        } catch (e: Exception) {
            Logger.w("SystemPromptFetcher") { "Using default prompts: ${e.message}" }
        }
    }
}
