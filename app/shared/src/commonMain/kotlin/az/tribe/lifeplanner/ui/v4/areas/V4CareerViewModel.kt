package az.tribe.lifeplanner.ui.v4.areas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.career.CareerService
import az.tribe.lifeplanner.data.plans.PlanService
import az.tribe.lifeplanner.data.study.StudyService
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.FocusRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.service.CareerKind
import az.tribe.lifeplanner.domain.service.CareerPlanner
import az.tribe.lifeplanner.domain.service.Stage
import az.tribe.lifeplanner.domain.service.StudyPlanner
import az.tribe.lifeplanner.domain.service.StudyTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** A skill with what the page says about it. */
data class SkillRow(val log: LifeLog, val level: Int, val want: Int, val practiceMin: Int)

data class CareerState(
    val searching: Boolean = false,
    val plan: Goal? = null,
    val actions: List<CareerPlanner.Action> = emptyList(),
    val applications: List<LifeLog> = emptyList(),
    val stageCounts: Map<Stage, Int> = emptyMap(),
    val wins: List<LifeLog> = emptyList(),
    val review: String = "",
    val skills: List<SkillRow> = emptyList(),
    val people: List<LifeLog> = emptyList(),
    /** Each person's catch-ups, newest first, by person id. */
    val talks: Map<String, List<LifeLog>> = emptyMap(),
    /** How the search is going. Null until an application has been sent. */
    val funnel: CareerPlanner.Funnel? = null,
    val interviews: Map<String, LifeLog> = emptyMap(),
    val canCalendar: Boolean = false,
    val loaded: Boolean = false,
)

class V4CareerViewModel(
    private val career: CareerService,
    private val study: StudyService,
    private val plans: PlanService,
    logs: LifeLogRepository,
    goals: GoalRepository,
    focus: FocusRepository,
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)
    private val canCalendar = MutableStateFlow(false)

    private val focusTimes = combine(focus.observeAllSessions(), goals.observeAllGoals()) { sessions, gs ->
        val names = gs.associate { it.id to it.title }
        sessions.filter { it.wasCompleted || it.actualMinutes > 0 }.mapNotNull { s -> s.completedAt?.let { StudyTime(it.date, s.actualMinutes, names[s.goalId]) } }
    }

    val state: StateFlow<CareerState> = combine(
        logs.observeInRange(today().minus(DatePeriod(days = 3650)), today().plus(DatePeriod(days = 3650))),
        goals.observeAllGoals(),
        career.searching,
        focusTimes,
        canCalendar,
    ) { all, gs, searching, focusRows, cal ->
        val today = today()
        val rows = all.filter { CareerKind.of(it) != null }
        val apps = rows.filter { CareerKind.of(it) == CareerKind.APPLICATION }
        val times = StudyPlanner.times(all, focusRows)
        CareerState(
            searching = searching,
            plan = gs.filter { !it.isArchived && it.status != GoalStatus.COMPLETED && PlanArea.forCategory(it.category) == PlanArea.CAREER }.sortedWith(compareBy { it.dueDate }).firstOrNull(),
            actions = CareerPlanner.actions(rows, today).filter { it.due <= today || it.timed },
            applications = apps.sortedWith(compareBy({ CareerPlanner.stage(it) == Stage.CLOSED }, { it.occurredAt })),
            stageCounts = apps.groupingBy { CareerPlanner.stage(it) }.eachCount(),
            wins = CareerPlanner.winsInQuarter(rows, today),
            review = CareerPlanner.reviewText(CareerPlanner.winsInQuarter(rows, today), today),
            skills = rows.filter { CareerKind.of(it) == CareerKind.SKILL }.sortedBy { it.occurredAt }.map { s ->
                SkillRow(s, s.quantity?.toInt() ?: 1, CareerPlanner.wantLevel(s) ?: 4, CareerPlanner.practiceMinutes(s.title, times, today))
            },
            people = rows.filter { CareerKind.of(it) == CareerKind.CONTACT }.sortedBy { it.occurredAt },
            talks = rows.filter { CareerKind.of(it) == CareerKind.TALK }.groupBy { it.externalId ?: "" }
                .mapValues { (_, v) -> v.sortedByDescending { it.occurredAt } },
            funnel = CareerPlanner.funnel(rows).takeIf { it.applied > 0 },
            interviews = rows.filter { CareerKind.of(it) == CareerKind.INTERVIEW && it.date >= today }.mapNotNull { i -> i.externalId?.let { it to i } }.toMap(),
            canCalendar = cal,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CareerState())

    init {
        viewModelScope.launch { canCalendar.value = plans.canAddToCalendar() }
    }

    fun setSearching(on: Boolean) {
        career.setSearching(on)
        PostHogAnalytics.capture("v4_career_mode", mapOf("searching" to on))
    }

    fun complete(a: CareerPlanner.Action) = launch { career.complete(a.log) }
    fun logWin(what: String, impact: String?, date: LocalDate) = launch {
        career.logWin(what, impact, date)
        PostHogAnalytics.capture("v4_career_win")
    }
    fun remove(l: LifeLog) = launch { career.remove(l) }
    fun addApplication(role: String, company: String, link: String?, stage: Stage, location: String? = null, closes: LocalDate? = null, from: String = "manual") = launch {
        career.addApplication(role, company, link, stage, location, closes)
        // Saving a job is looking for one: the page opens on applications from now on.
        if (!career.searching.value) career.setSearching(true)
        PostHogAnalytics.capture("v4_career_application", mapOf("stage" to stage.name, "from" to from))
    }
    fun updateApplication(app: LifeLog, role: String, company: String, link: String?, location: String?, closes: LocalDate?) =
        launch { career.updateApplication(app, role, company, link, location, closes) }

    /** Reads a pasted or shared job ad; [onResult] gets the draft, or null when it could not be read. */
    fun readJob(text: String, shared: Boolean, onResult: (CareerPlanner.JobDraft?) -> Unit) {
        viewModelScope.launch {
            val draft = career.readJob(text)
            PostHogAnalytics.capture("v4_career_job_read", mapOf("ok" to (draft != null), "shared" to shared, "found_role" to (draft?.role != null)))
            onResult(draft ?: CareerPlanner.firstUrl(text)?.let { CareerPlanner.JobDraft(null, null, it, null, null) })
        }
    }

    fun sharedFunnel() = PostHogAnalytics.capture("v4_career_funnel_shared")
    fun sharedWins() = PostHogAnalytics.capture("v4_career_wins_shared")
    fun setStage(app: LifeLog, stage: Stage, reason: String? = null) = launch { career.setStage(app, stage, reason) }
    fun scheduleInterview(app: LifeLog, date: LocalDate, time: LocalTime, minutes: Int, calendar: Boolean) = launch { career.scheduleInterview(app, date, time, minutes, calendar) }
    fun addContact(name: String, about: String?, every: Int) = launch { career.addContact(name, about, every) }
    fun talked(c: LifeLog, about: String? = null) = launch {
        career.talked(c, about)
        PostHogAnalytics.capture("v4_career_talked", mapOf("with_note" to !about.isNullOrBlank()))
    }
    fun setCadence(c: LifeLog, every: Int) = launch { career.setCadence(c, every) }
    fun addSkill(name: String, level: Int, want: Int) = launch { career.addSkill(name, level, want) }
    fun setSkill(s: LifeLog, level: Int, want: Int) = launch { career.setSkill(s, level, want) }

    /** Starts the Study timer on this skill, so practice counts toward it. */
    fun practise(s: LifeLog) = study.start(s.title.trim(), null, 25)

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch { runCatching { block() } }
}
