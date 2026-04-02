package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.Story

interface StoryRepository {
    suspend fun getActiveStories(): List<Story>
}
