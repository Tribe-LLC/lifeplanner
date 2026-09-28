package az.tribe.lifeplanner.ui.v4.life

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.life.LifeFactsService
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.service.DayFacts
import az.tribe.lifeplanner.domain.service.LifeFacts
import az.tribe.lifeplanner.domain.service.LifeFactsMath
import az.tribe.lifeplanner.domain.service.Pattern
import az.tribe.lifeplanner.domain.service.WeekSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class LifeInsightsState(
    val patterns: List<Pattern> = emptyList(),
    /** Days still needed before the first pattern; 0 once there are patterns or enough days. */
    val daysUntil: Int = 0,
    /** The week to review, shown Saturday evening to Monday until saved. */
    val review: WeekSummary? = null,
    val reviewSaved: Boolean = false,
    /** Monday-first month grid: null cells pad the first week. */
    val month: List<DayFacts?> = emptyList(),
    val monthName: String = "",
    val today: LocalDate? = null,
    val currency: String? = null,
    val loaded: Boolean = false,
)

/**
 * The parts of Life that read across areas: the strongest patterns, the Sunday week review, and a
 * month you can tap into. Read from [LifeFactsService]; the review is the only thing it writes.
 */
@OptIn(ExperimentalUuidApi::class)
class V4LifeInsightsViewModel(
    private val facts: LifeFactsService,
    private val logs: LifeLogRepository,
) : ViewModel() {
    private val tz = TimeZone.currentSystemDefault()
    private val _state = MutableStateFlow(LifeInsightsState())
    val state: StateFlow<LifeInsightsState> = _state.asStateFlow()
    private var last: LifeFacts? = null

    fun refresh() = viewModelScope.launch {
        val f = runCatching { facts.load(62) }.getOrNull() ?: return@launch
        last = f
        val now = Clock.System.now().toLocalDateTime(tz)
        val reviewEnd = reviewWeekEnd(now.date, now.hour)
        val saved = reviewEnd?.let { end -> isSaved(end) } ?: false
        val first = LocalDate(f.today.year, f.today.month, 1)
        val pad = first.dayOfWeek.ordinal
        val monthDays = generateSequence(first) { it.plus(DatePeriod(days = 1)) }.takeWhile { it.month == first.month }
            .map { d -> f.day(d) ?: DayFacts(d) }.toList()
        _state.value = LifeInsightsState(
            patterns = LifeFactsMath.patterns(f),
            daysUntil = LifeFactsMath.daysUntilPatterns(f),
            // Only a week that has something in it; a brand new account has nothing to look back on.
            review = reviewEnd?.let { end -> LifeFactsMath.week(f, end).takeIf { w -> f.days.any { it.date in w.from..w.to && it.hasData } } },
            reviewSaved = saved,
            month = List(pad) { null } + monthDays,
            monthName = first.month.name.lowercase().replaceFirstChar { it.uppercase() },
            today = f.today,
            currency = f.currency,
            loaded = true,
        )
    }

    fun saveReview(mood: Int?, change: String) = viewModelScope.launch {
        val w = _state.value.review ?: return@launch
        runCatching {
            logs.save(
                LifeLog(
                    id = Uuid.random().toString(),
                    area = PlanArea.MIND,
                    kind = LogKind.NOTE,
                    title = "Week of ${w.from.day} ${w.from.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}",
                    category = WEEK_REVIEW,
                    quantity = mood?.toDouble(),
                    occurredAt = Clock.System.now().toLocalDateTime(tz),
                    notes = change.trim().ifEmpty { null },
                    externalId = "week_${w.to}",
                )
            )
        }
        PostHogAnalytics.capture("v4_week_review_saved", mapOf("mood" to (mood ?: 0), "change" to change.isNotBlank()))
        _state.update { it.copy(reviewSaved = true) }
    }

    /** A short prompt for the coach to write a note about the week. */
    fun coachPrompt(): String {
        val w = _state.value.review
        val f = last
        val digest = f?.let { LifeFactsMath.digest(it) }.orEmpty()
        return "Write me a short, kind note about my week (${w?.from} to ${w?.to}). Two or three sentences: what went well, and one small thing to try next week.\n$digest"
    }

    private suspend fun isSaved(end: LocalDate): Boolean = runCatching { logs.hasExternalId("week_$end") }.getOrDefault(false)

    companion object {
        const val WEEK_REVIEW = "week_review"

        /**
         * The Sunday that ends the week to review: this weekend from Saturday 18:00, or last week on a
         * Monday. Null the rest of the week.
         */
        fun reviewWeekEnd(today: LocalDate, hour: Int): LocalDate? = when (today.dayOfWeek) {
            DayOfWeek.SATURDAY -> if (hour >= 18) today.plus(DatePeriod(days = 1)) else null
            DayOfWeek.SUNDAY -> today
            DayOfWeek.MONDAY -> today.minus(DatePeriod(days = 1))
            else -> null
        }
    }
}
