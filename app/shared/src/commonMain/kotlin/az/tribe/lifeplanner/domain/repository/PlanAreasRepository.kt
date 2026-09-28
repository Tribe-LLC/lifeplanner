package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.PlanArea
import kotlinx.coroutines.flow.StateFlow

/**
 * Which life areas the user has switched on, and whether they have been through v4's first run.
 * Local to the device for now; the choice is cheap to make again on a second device.
 */
interface PlanAreasRepository {
    /** The areas that appear in the app. Never empty: Habits is always on. */
    val enabledAreas: StateFlow<Set<PlanArea>>

    fun setEnabledAreas(areas: Set<PlanArea>)

    /** False until the user (or the update screen, from their data) has picked areas once. */
    fun hasChosenAreas(): Boolean

    /** True once the user has finished (or skipped through) v4's first run on this device. */
    fun isFirstRunDone(): Boolean

    fun markFirstRunDone()
}
