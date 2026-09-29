package az.tribe.lifeplanner.data.plans

import co.touchlab.kermit.Logger
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/**
 * Ticks plan steps that the data says are done (a run long enough, money put aside, a weight
 * reached), whichever screen logged it. Runs while the app does, next to the habit self-ticks.
 * Each tick is tried once per piece of evidence, so a failed write never loops, while a new run
 * after an untick still ticks the step again.
 */
class PlanAutoTick(private val board: PlanBoard, private val maker: PlanMaker) {

    private val tried = mutableSetOf<String>()

    @OptIn(FlowPreview::class)
    suspend fun run() {
        board.plans.debounce(800).collect { plans ->
            plans.filter { it.state == PlanState.ACTIVE || it.state == PlanState.PAUSED }.forEach { p ->
                p.steps.filter { !it.isCompleted && it.id in p.progress.ticks }.forEach { m ->
                    val key = "${m.id}@${p.progress.ticks.getValue(m.id).at}"
                    if (!tried.add(key)) return@forEach
                    runCatching { maker.setStep(p.id, m.id, done = true, byData = true) }
                        .onFailure { Logger.w("PlanAutoTick") { "${p.title}: ${it.message}" } }
                }
            }
        }
    }
}
