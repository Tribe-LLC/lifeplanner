package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.CoachPost

interface CoachPostRepository {
    suspend fun getPostsForCoach(coachId: String): List<CoachPost>
}
