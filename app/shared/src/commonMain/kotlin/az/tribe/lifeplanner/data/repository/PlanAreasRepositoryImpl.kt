package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.PlanAreasRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlanAreasRepositoryImpl(
    private val settings: Settings,
) : PlanAreasRepository {

    private val _enabledAreas = MutableStateFlow(read())
    override val enabledAreas: StateFlow<Set<PlanArea>> = _enabledAreas.asStateFlow()

    override fun setEnabledAreas(areas: Set<PlanArea>) {
        val withHabits = areas + PlanArea.HABITS
        settings.putString(KEY_AREAS, withHabits.joinToString(",") { it.key })
        _enabledAreas.value = withHabits
    }

    override fun hasChosenAreas(): Boolean = settings.getStringOrNull(KEY_AREAS) != null

    override fun isFirstRunDone(): Boolean = settings.getBoolean(KEY_FIRST_RUN_DONE, false)

    override fun markFirstRunDone() {
        settings.putBoolean(KEY_FIRST_RUN_DONE, true)
    }

    private fun read(): Set<PlanArea> {
        val stored = settings.getStringOrNull(KEY_AREAS)
            ?.split(",")
            ?.mapNotNull { PlanArea.fromKey(it) }
            ?.toSet()
            .orEmpty()
        return if (stored.isEmpty()) PlanArea.DEFAULTS else stored + PlanArea.HABITS
    }

    private companion object {
        const val KEY_AREAS = "v4_enabled_areas"
        const val KEY_FIRST_RUN_DONE = "v4_first_run_done"
    }
}
