package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.domain.model.Story
import az.tribe.lifeplanner.domain.repository.StoryRepository
import co.touchlab.kermit.Logger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

class StoryRepositoryImpl(
    private val supabase: SupabaseClient
) : StoryRepository {

    override suspend fun getActiveStories(): List<Story> {
        return try {
            supabase.postgrest["stories"]
                .select {
                    order("sort_order", Order.ASCENDING)
                }
                .decodeList<Story>()
        } catch (e: Exception) {
            Logger.w("StoryRepository") { "Failed to fetch stories: ${e.message}" }
            emptyList()
        }
    }
}
