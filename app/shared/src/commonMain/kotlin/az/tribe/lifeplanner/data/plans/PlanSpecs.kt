package az.tribe.lifeplanner.data.plans

import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.BudgetRepository
import az.tribe.lifeplanner.domain.service.PlanSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Reads and writes the plan settings rows (see [PlanSpec]). */
class PlanSpecs(private val budgets: BudgetRepository) {

    /** Every plan's settings, by goal id. */
    val all: Flow<Map<String, PlanSpec>> = budgets.observeAll().map { PlanSpec.fromBudgets(it) }.distinctUntilChanged()

    suspend fun getAll(): Map<String, PlanSpec> = PlanSpec.fromBudgets(budgets.getAll())

    suspend fun get(goalId: String): PlanSpec? = getAll()[goalId]

    suspend fun save(spec: PlanSpec) = budgets.save(spec.toBudget())

    suspend fun remove(goalId: String) = budgets.delete(PlanSpec.rowId(goalId))

    suspend fun areaOf(goal: Goal): PlanArea = PlanSpec.areaOf(goal, getAll())
}
