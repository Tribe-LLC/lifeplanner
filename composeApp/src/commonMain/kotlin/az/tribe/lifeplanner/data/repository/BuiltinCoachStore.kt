package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.model.CoachPersona

object BuiltinCoachStore {
    @Volatile private var _coaches: List<CoachPersona> = emptyList()

    fun update(coaches: List<CoachPersona>) { _coaches = coaches }

    fun getAll(): List<CoachPersona> = _coaches.ifEmpty { CoachPersona.ALL_COACHES }

    fun getById(id: String): CoachPersona =
        getAll().find { it.id == id } ?: CoachPersona.ALL_COACHES.first()

    fun getByCategory(category: GoalCategory): CoachPersona =
        getAll().find { it.category == category } ?: CoachPersona.ALL_COACHES.first()
}
