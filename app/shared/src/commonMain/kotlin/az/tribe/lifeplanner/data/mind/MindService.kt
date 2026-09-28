package az.tribe.lifeplanner.data.mind

import az.tribe.lifeplanner.data.health.HealthDataManager
import az.tribe.lifeplanner.data.integrations.DataFlow
import az.tribe.lifeplanner.data.integrations.IntegrationPrefs
import az.tribe.lifeplanner.domain.model.Budget
import az.tribe.lifeplanner.domain.model.BudgetPeriod
import az.tribe.lifeplanner.domain.model.JournalEntry
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.BudgetRepository
import az.tribe.lifeplanner.domain.repository.JournalRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.service.MindCheckIns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Mind's own writes: mood check-ins (a log here, State of Mind in Health on iPhone), breathing
 * sessions (a log here, mindful minutes in Health), the sleep goal, and journal entries.
 */
@OptIn(ExperimentalUuidApi::class)
class MindService(
    private val logs: LifeLogRepository,
    private val budgets: BudgetRepository,
    private val journal: JournalRepository,
    private val health: HealthDataManager,
    private val prefs: IntegrationPrefs,
) {
    private val tz = TimeZone.currentSystemDefault()

    /** Health writes outlive the page that asked for them. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun healthOn(flow: DataFlow) = prefs.state.value.let { it.health && it.isOn(flow) }

    suspend fun supportsMindful(): Boolean = runCatching { health.supportsMindful() }.getOrDefault(false)
    suspend fun canWriteHealth(): Boolean = runCatching { health.canWriteMind() }.getOrDefault(false)
    suspend fun requestHealth(): Boolean = runCatching { health.requestMind() }.getOrDefault(false)

    /** Saves the level straight away; tags, feelings and a note can follow with [update]. */
    suspend fun checkIn(score: Int): LifeLog {
        val log = LifeLog(
            id = Uuid.random().toString(), area = PlanArea.MIND, kind = LogKind.MOOD,
            title = MindCheckIns.label(score), quantity = score.toDouble(),
            occurredAt = Clock.System.now().toLocalDateTime(tz),
        )
        logs.save(log)
        return log
    }

    suspend fun update(log: LifeLog, score: Int, tags: List<String>, feelings: List<String>, note: String?): LifeLog {
        val updated = log.copy(
            title = MindCheckIns.label(score), quantity = score.toDouble(),
            category = tags.joinToString(", ").ifEmpty { null },
            notes = MindCheckIns.encode(feelings, note),
        )
        logs.save(updated)
        return updated
    }

    /** Sends a finished check-in to Health, once. Called when the user is done with it. */
    fun sendToHealth(log: LifeLog) {
        if (!healthOn(DataFlow.MOOD)) return
        val score = MindCheckIns.score(log) ?: return
        scope.launch {
            runCatching {
                health.writeMood(
                    MindCheckIns.valence(score), MindCheckIns.feelings(log), MindCheckIns.tags(log),
                    log.occurredAt.toInstant(tz).toEpochMilliseconds(), "mood:${log.id}",
                )
            }
        }
    }

    suspend fun remove(log: LifeLog) = logs.delete(log.id)

    /** A finished breathing session: a log here, and mindful minutes in Health when allowed. */
    suspend fun breathed(startEpochMs: Long, endEpochMs: Long) {
        val start = kotlin.time.Instant.fromEpochMilliseconds(startEpochMs)
        val minutes = ((endEpochMs - startEpochMs) / 60_000.0).let { kotlin.math.ceil(it).toInt() }.coerceAtLeast(1)
        val id = Uuid.random().toString()
        logs.save(
            LifeLog(
                id = id, area = PlanArea.MIND, kind = LogKind.NOTE, title = "Breathing", category = MindCheckIns.MINDFUL,
                durationMin = minutes, occurredAt = start.toLocalDateTime(tz), source = LifeLog.SOURCE_TIMER,
            )
        )
        if (healthOn(DataFlow.MINDFUL)) scope.launch { runCatching { health.writeMindful(startEpochMs, endEpochMs, "mindful:$id") } }
    }

    suspend fun sleepGoalHours(): Double? =
        runCatching { budgets.getAll() }.getOrDefault(emptyList()).firstOrNull { it.area == PlanArea.MIND && it.metric == METRIC_SLEEP }?.amount

    suspend fun setSleepGoal(hours: Double?) {
        budgets.getAll().filter { it.area == PlanArea.MIND && it.metric == METRIC_SLEEP }.forEach { budgets.delete(it.id) }
        if (hours != null) budgets.save(Budget(Uuid.random().toString(), PlanArea.MIND, METRIC_SLEEP, null, hours, null, BudgetPeriod.WEEK))
    }

    /** A journal entry, with the mood of today's latest check-in when there is one. */
    suspend fun write(title: String, text: String, score: Int?, prompt: String?, tags: List<String> = emptyList()) {
        val now = Clock.System.now().toLocalDateTime(tz)
        journal.insertEntry(
            JournalEntry(
                id = Uuid.random().toString(),
                title = title.trim().ifEmpty { text.trim().lineSequence().first().take(60) },
                content = text.trim(),
                mood = MindCheckIns.mood(score ?: 3),
                promptUsed = prompt,
                tags = tags,
                date = now.date,
                createdAt = now,
            )
        )
    }

    companion object {
        const val METRIC_SLEEP = "sleep_hours"
    }
}
