package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.domain.model.CoachPost
import az.tribe.lifeplanner.domain.repository.CoachPostRepository
import co.touchlab.kermit.Logger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

class CoachPostRepositoryImpl(
    private val supabase: SupabaseClient
) : CoachPostRepository {

    override suspend fun getPostsForCoach(coachId: String): List<CoachPost> {
        return try {
            supabase.postgrest["coach_posts"]
                .select {
                    filter {
                        eq("coach_id", coachId)
                        eq("is_published", true)
                    }
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                }
                .decodeList<CoachPost>()
        } catch (e: Exception) {
            Logger.w("CoachPostRepo") { "Failed to fetch posts for $coachId: ${e.message}" }
            emptyList()
        }
    }
}
