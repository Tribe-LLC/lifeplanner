package az.tribe.lifeplanner.ui.components

import az.tribe.lifeplanner.domain.model.Story

/**
 * Transient in-memory store for passing story data into StoryReaderScreen via navigation.
 * Populated just before navigating; cleared on dismiss.
 */
object StoryReaderStore {
    var stories: List<Story> = emptyList()
    var initialIndex: Int = 0
    var seenIds: Set<String> = emptySet()
    var onMarkSeen: (String) -> Unit = {}
    var onStoryAction: (String?) -> Unit = {}
}
