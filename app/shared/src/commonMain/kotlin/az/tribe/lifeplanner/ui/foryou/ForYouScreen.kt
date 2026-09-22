package az.tribe.lifeplanner.ui.foryou

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import az.tribe.lifeplanner.ui.components.BreathingCard
import az.tribe.lifeplanner.domain.model.CalendarEvent
import az.tribe.lifeplanner.ui.calendar.CalendarPermissionState
import az.tribe.lifeplanner.ui.calendar.CalendarViewModel
import az.tribe.lifeplanner.ui.calendar.rememberCalendarPermission
import az.tribe.lifeplanner.ui.components.rememberHapticManager
import androidx.compose.foundation.background
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import org.koin.compose.koinInject
import com.russhwolf.settings.Settings
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.FeedItem
import az.tribe.lifeplanner.domain.model.FeedKind
import az.tribe.lifeplanner.domain.model.HourlyBrief
import az.tribe.lifeplanner.domain.model.TodayWeather
import az.tribe.lifeplanner.domain.model.WeatherCondition
import az.tribe.lifeplanner.domain.model.UserProgress
import az.tribe.lifeplanner.ui.components.AppButton
import az.tribe.lifeplanner.ui.components.AppButtonVariant
import az.tribe.lifeplanner.ui.components.GradientHero
import az.tribe.lifeplanner.ui.components.IconChip
import az.tribe.lifeplanner.ui.components.ProgressRing
import az.tribe.lifeplanner.ui.intro.FeatureIntroHost
import az.tribe.lifeplanner.location.LocationPermissionState
import az.tribe.lifeplanner.location.rememberLocationPermission
import az.tribe.lifeplanner.ui.navigation.Screen
import az.tribe.lifeplanner.domain.service.BreathMoment
import az.tribe.lifeplanner.domain.service.PresentMoment
import az.tribe.lifeplanner.domain.model.WheelArea
import az.tribe.lifeplanner.domain.model.ScoreSource
import az.tribe.lifeplanner.ui.components.todayBreathKey
import az.tribe.lifeplanner.ui.onboarding.CoachOnboardingViewModel
import az.tribe.lifeplanner.ui.wheel.WheelStripCard
import az.tribe.lifeplanner.domain.service.KnowledgeLibrary
import az.tribe.lifeplanner.domain.service.StepDuration
import az.tribe.lifeplanner.ui.today.PlanItem
import az.tribe.lifeplanner.ui.today.TodayWeatherViewModel
import az.tribe.lifeplanner.ui.intro.rememberFeatureIntroGate
import androidx.compose.runtime.LaunchedEffect
import az.tribe.lifeplanner.usecases.health.GetHealthHabitProgressUseCase
import az.tribe.lifeplanner.domain.enum.HealthMetricType
import com.adamglin.phosphoricons.regular.Heartbeat
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign
import az.tribe.lifeplanner.ui.theme.bouncyClickable
import az.tribe.lifeplanner.ui.theme.gradientColors
import az.tribe.lifeplanner.ui.theme.modernColors
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.CheckCircle
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowsOutSimple
import com.adamglin.phosphoricons.regular.Brain
import com.adamglin.phosphoricons.regular.CalendarBlank
import com.adamglin.phosphoricons.regular.CaretDown
import com.adamglin.phosphoricons.regular.CaretRight
import com.adamglin.phosphoricons.regular.Circle
import com.adamglin.phosphoricons.regular.ClockCounterClockwise
import com.adamglin.phosphoricons.regular.Fire
import com.adamglin.phosphoricons.regular.Flag
import com.adamglin.phosphoricons.regular.Lightning
import com.adamglin.phosphoricons.regular.Sparkle
import com.adamglin.phosphoricons.regular.X
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * The new home: a "For You" feed. A calm gradient header (greeting + level/streak ring), filter
 * chips (All / Right now / Reflect / Learn), then a ranked stream of cards the app fills for you,
 * the single best next action, insights about you, who you are becoming, and leveled knowledge.
 * Reflect cards deep-link into the full You screens so those functions live on the front door.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForYouScreen(
    onOpenRoute: (String) -> Unit,
    onOpenWeather: (TodayWeather) -> Unit,
    viewModel: ForYouViewModel = koinViewModel(),
) {
    val feed by viewModel.feed.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val plan by viewModel.todayPlan.collectAsState()
    val healthHabits by viewModel.healthHabits.collectAsState()
    val learning by viewModel.learning.collectAsState()
    val zones by viewModel.zones.collectAsState()
    val zonesShown by viewModel.zonesShown.collectAsState()
    val readLessonIds by viewModel.readLessonIds.collectAsState()
    val wheel by viewModel.wheel.collectAsState()
    val checkinPulse by viewModel.checkinPulse.collectAsState()
    val c = MaterialTheme.modernColors

    // Today's device-calendar events, folded into "Today's plan" so the plan reflects real
    // appointments, not just goal milestones. Read-only; empty (and silent) without permission.
    val calendarViewModel: CalendarViewModel = koinViewModel()
    val calendarPermission = rememberCalendarPermission()
    val todayEvents by calendarViewModel.dayEvents.collectAsState()
    val tz = remember { TimeZone.currentSystemDefault() }
    val feedNowHour = remember { Clock.System.now().toLocalDateTime(tz).hour }
    // Read once per open. The card hides itself the moment a breath is finished, so this only has
    // to be right at the point we decide whether to bring it up at all.
    val feedSettings: Settings = koinInject()
    val breathsToday = remember { feedSettings.getInt(todayBreathKey(), 0) }
    // The coach's questions are no longer a gate before the app, so they are offered here instead.
    // Hidden once answered or declined; an offer that keeps returning is not an offer.
    var coachSetupDone by remember {
        mutableStateOf(
            az.tribe.lifeplanner.ui.onboarding.AboutYouViewModel.isHandled(feedSettings) ||
                CoachOnboardingViewModel.isComplete(feedSettings)
        )
    }
    LaunchedEffect(calendarPermission.state) {
        if (calendarPermission.state == CalendarPermissionState.GRANTED) {
            calendarViewModel.loadDay(Clock.System.todayIn(tz))
        }
    }
    val calendarConnected = calendarPermission.state == CalendarPermissionState.GRANTED

    // Today's weather (Open-Meteo). The permission lives in Compose; feed its state to the VM.
    val weatherViewModel: TodayWeatherViewModel = koinViewModel()
    val weatherState by weatherViewModel.state.collectAsState()
    val locationPermission = rememberLocationPermission()
    LaunchedEffect(locationPermission.state) {
        weatherViewModel.onPermissionState(locationPermission.state == LocationPermissionState.GRANTED)
    }
    // The present is the one thing on this screen that goes stale on its own: an event that was
    // twenty minutes away when the screen opened is not, ten minutes later. A minute of granularity
    // is as fine as anything here is phrased.
    var nowMillis by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMillis = Clock.System.now().toEpochMilliseconds()
        }
    }
    // What is happening in this hour, above the day it belongs to. Falls back through the feed's
    // top habit card so the slot holds something doable rather than something to read.
    val topHabitCard = remember(feed) { feed.firstOrNull { it.actionHabitId != null } }
    val presentMoment = remember(nowMillis, todayEvents, plan, topHabitCard, calendarConnected) {
        PresentMoment.of(
            nowEpochMillis = nowMillis,
            events = if (calendarConnected) todayEvents else emptyList(),
            steps = plan.map {
                PresentMoment.Step(
                    goalId = it.goalId,
                    milestoneId = it.milestoneId,
                    title = it.title,
                    goalTitle = it.goalTitle,
                    overdue = it.overdue,
                )
            },
            nudge = topHabitCard,
        )
    }
    // Whatever the now card is holding is not printed again further down the same screen: the
    // habit loses its full card in the feed, the step loses its row in the plan. It has not gone
    // anywhere, it is at the top, and the day's list is a page long by the afternoon.
    val promotedCardId = if (presentMoment?.kind == PresentMoment.Kind.HABIT) topHabitCard?.id else null
    val promotedMilestoneId = presentMoment?.milestoneId
    val planRows = remember(plan, promotedMilestoneId) {
        plan.filterNot { it.milestoneId == promotedMilestoneId }
    }

    var filter by remember { mutableStateOf<FeedSection?>(null) }
    // The path card opens in place like the feed's lessons do, but keeps its own flag: it is not
    // one of the ranked cards and must not close because the feed re-ranked underneath it.
    // Which stop on the map is open, and which cleared zones have been unfolded to walk again.
    var openLessonId by rememberSaveable { mutableStateOf<String?>(null) }
    var unfoldedZones by rememberSaveable { mutableStateOf(emptySet<String>()) }
    val introGate = rememberFeatureIntroGate()
    val visible = remember(feed, filter, promotedCardId) {
        val f = filter
        feed.filter { (f == null || it.kind.section() == f) && it.id != promotedCardId }
    }
    val grouped = remember(visible) { visible.groupBy { it.kind.section() } }
    // Only sections that actually have cards earn a chip. A week-one feed is DO_NEXT and
    // KNOWLEDGE only, and tapping "Reflect on today" used to render a feed of nothing at all
    // (same rule as the wheel history's offered periods).
    val offeredSections = remember(feed, promotedCardId) {
        FeedSection.entries.filter { sec ->
            feed.any { it.kind.section() == sec && it.id != promotedCardId }
        }
    }
    LaunchedEffect(offeredSections) {
        // A selected section can empty out from under the filter (last card acted on).
        if (filter != null && filter !in offeredSections) filter = null
    }

    // No snackbar here. This screen has a floating bottom bar, and a Scaffold snackbar docks
    // beneath it, so the "+XP" confirmation appeared half-covered and unreadable. XP still lands;
    // it is visible on the You tab. If this ever needs confirming inline again, it wants to be
    // part of the card that earned it, not a bar hidden under the navigation.

    Scaffold(
        containerColor = c.background,
        topBar = {
            TopAppBar(
                title = { Text("Present", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.background, titleContentColor = c.textPrimary),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + LifePlannerDesign.Spacing.xs,
                bottom = padding.calculateBottomPadding() + 84.dp,
                start = LifePlannerDesign.Padding.screenHorizontal,
                end = LifePlannerDesign.Padding.screenHorizontal,
            ),
            verticalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.md),
        ) {
            // The header is now weather-forward when we have it: one understandable widget instead of
            // a big greeting. Falls back to the greeting hero when weather isn't available yet.
            val loadedWeather = (weatherState as? TodayWeatherViewModel.State.Loaded)?.weather
            item { TodayHero(progress = progress, weather = loadedWeather, onExpand = { loadedWeather?.let(onOpenWeather) }) }

            // Hourly detail lives inside the tap-to-expand weather popup, not as a second feed card.
            if (weatherState is TodayWeatherViewModel.State.NeedsPermission) {
                item(key = "weather_prompt") { WeatherPromptCard(onEnable = locationPermission.request) }
            }

            // Then the hour itself. The plan, the wheel and the feed are all true of the day or the
            // life; this one line is true of right now, which is the question the tab is named after.
            presentMoment?.let { m ->
                val milestoneId = m.milestoneId
                val habitId = m.habitId
                val goalId = m.goalId
                item(key = "present_now") {
                    PresentNowCard(
                        moment = m,
                        tz = tz,
                        onAct = when {
                            milestoneId != null -> ({ viewModel.completePlanItem(milestoneId) })
                            habitId != null -> ({ viewModel.checkInHabit(habitId) })
                            else -> null
                        },
                        onOpen = goalId?.let { g -> ({ onOpenRoute("goal_detail/$g") }) },
                    )
                }
            }

            // A breath, still only on a day that gives us a reason to mention it, but now beside the
            // rest of the present rather than under the day's list: on the days it has something to
            // say, this minute is the thing to do with this minute.
            val breathMoment = BreathMoment.of(
                hourOfDay = feedNowHour,
                planItems = plan.size + healthHabits.size + todayEvents.size,
                mentalScore = wheel?.scores
                    ?.firstOrNull { it.area == WheelArea.MENTAL && it.source != ScoreSource.ESTIMATED }
                    ?.score,
                breathsToday = breathsToday,
                overdueItems = plan.count { it.overdue },
            )
            if (breathMoment != null) {
                item(key = "breath") { BreathingCard(reason = breathMoment.reason) }
            }

            if (!coachSetupDone) {
                item(key = "coach_setup") {
                    CoachSetupCard(
                        onStart = { onOpenRoute(Screen.AboutYou.route) },
                        onDismiss = {
                            feedSettings.putBoolean(
                                az.tribe.lifeplanner.ui.onboarding.AboutYouViewModel.KEY_HANDLED,
                                true,
                            )
                            coachSetupDone = true
                        },
                    )
                }
            }

            // The wheel is the one view that shows a whole life at once, so it gets the space to
            // be looked at rather than a status strip beside two lines of text.
            item(key = "balance") {
                WheelStripCard(
                    wheel,
                    onOpen = { onOpenRoute(Screen.WheelOfLife.route) },
                    prominent = true,
                )
            }

            // Today's plan, planner-style: tick items off right here instead of jumping to Goals.
            // Only rendered when the day actually has something in it. A header over a "you're all
            // caught up" line is just chrome on an empty day, and it pushes the feed down.
            val hasCalendarToday = calendarConnected && todayEvents.isNotEmpty()
            if (planRows.isNotEmpty() || hasCalendarToday || healthHabits.isNotEmpty()) {
                item(key = "plan_header") { SectionHeader(label = "Today's plan", onSeeAll = null) }
                // Calendar events lead the plan: they are time-anchored, so they read top-of-day.
                if (hasCalendarToday) {
                    items(todayEvents, key = { "cal_${it.id}" }) { event ->
                        CalendarPlanRow(event = event, tz = tz)
                    }
                }
                // Health-linked habits next. They can't be ticked by hand — health data completes
                // them — so the row carries the live number instead of a checkbox that does nothing.
                items(healthHabits, key = { "health_${it.habitId}" }) { habit ->
                    HealthHabitRow(progress = habit)
                }
                items(planRows, key = { "plan_${it.milestoneId}" }) { p ->
                    PlanRow(
                        item = p,
                        onComplete = { viewModel.completePlanItem(p.milestoneId) },
                        onOpen = { onOpenRoute("goal_detail/${p.goalId}") },
                    )
                }
            }

            // With one section there is nothing to filter between, so the row would be All plus
            // one chip showing the identical list.
            if (offeredSections.size >= 2) {
                item { FilterRow(selected = filter, offered = offeredSections, onSelect = { filter = it }) }
            }

            if (feed.isEmpty()) {
                item {
                    Hint(
                        if (isLoading) "Gathering today for you..."
                        else "Add a goal or a habit and your feed fills up with insights, nudges, and ideas.",
                    )
                }
            } else {
                FeedSection.entries.forEach { sec ->
                    val cards = grouped[sec].orEmpty()
                    if (cards.isNotEmpty()) {
                        item(key = "h_${sec.name}") {
                            SectionHeader(
                                label = sec.label,
                                onSeeAll = if (sec == FeedSection.LEARN) {
                                    { onOpenRoute(Screen.LearnHub.route) }
                                } else null,
                            )
                        }
                        items(cards, key = { it.id }) { fi ->
                            val accent = accentFor(fi)
                            // A card opens the place to act (a feature the user hasn't met explains
                            // itself first via the intro gate). Lessons are no longer feed cards:
                            // they are whole pages in the stream below.
                            val open: () -> Unit = {
                                fi.route?.let { route -> introGate.open(fi.introId, accent) { onOpenRoute(route) } }
                            }
                            FeedCard(
                                item = fi,
                                accent = accent,
                                pulse = fi.actionHabitId?.let { checkinPulse[it] },
                                // Habit cards check in inline; every other action button opens the
                                // place to act, so the labeled button always does what it says.
                                onAction = { fi.actionHabitId?.let(viewModel::checkInHabit) ?: open() },
                                onOpen = open,
                            )
                        }
                    }
                }
            }

            // ── The map, which does not end ──────────────────────────────────
            // Everything above is finite by design: the hour, the day, the life. This is the part
            // that keeps going, and it is the same trail the Learn hub draws rather than a flat list
            // of lessons wearing its name. The zone you are walking comes first, the next unrolls
            // when you reach the end of this one, and a stop opens where you tapped it.
            if (zones.isNotEmpty()) {
                item(key = "map_header") { SectionHeader(label = "Your map", onSeeAll = null) }
                // One line of where you are, from the same numbers the trail is drawn from.
                learning?.let { l ->
                    item(key = "map_line") {
                        Text(
                            l.line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary,
                        )
                    }
                }

                zones.take(zonesShown).forEach { cui ->
                    val zoneId = cui.collection.id
                    // Cleared ground folds to a line. Walking past finished zones to reach the one
                    // you are on is the main thing wrong with a long map.
                    val unfolded = !cui.isComplete || zoneId in unfoldedZones
                    item(key = "zone_$zoneId") {
                        if (cui.isComplete) {
                            ClearedZoneRow(
                                cui = cui,
                                expanded = unfolded,
                                onToggle = {
                                    unfoldedZones = if (zoneId in unfoldedZones) {
                                        unfoldedZones - zoneId
                                    } else {
                                        unfoldedZones + zoneId
                                    }
                                },
                            )
                        } else {
                            ZoneHeader(cui)
                        }
                    }
                    if (unfolded) {
                        item(key = "trail_$zoneId") {
                            ZoneTrail(
                                cui = cui,
                                readIds = readLessonIds,
                                onOpen = { id -> openLessonId = if (openLessonId == id) null else id },
                            )
                        }
                    }
                }

                item(key = "map_more") {
                    // Composing this footer is the signal: the reader has walked off the end of the
                    // map they were given, so unroll the next zone. It stops asking when the library
                    // runs out, and scrolls out of view once the new ground pushes it down.
                    // The pause matters: without it the effect re-fires faster than the list can
                    // push the footer out of view, and the whole map unrolls in one go.
                    LaunchedEffect(zonesShown) {
                        delay(350)
                        viewModel.showMoreZones()
                    }
                    StreamFooter(atEnd = zonesShown >= zones.size)
                }
            }
        }
    }

    // A stop opens over the map rather than under it. The lesson can be several nodes up the trail
    // from where the list is scrolled, and a card that appears somewhere off screen has not opened
    // at all; a sheet arrives where the reader is looking, with the trail still visible behind it.
    openLessonId?.let { id ->
        val zone = zones.firstOrNull { z -> z.lessons.any { it.id == id } }
        val lesson = zone?.lessons?.firstOrNull { it.id == id }
        if (zone != null && lesson != null) {
            LessonSheet(
                lesson = lesson,
                pathTitle = zone.collection.title,
                position = zone.lessons.indexOfFirst { it.id == id } + 1,
                total = zone.total,
                read = id in readLessonIds,
                onComplete = {
                    viewModel.completeLesson(id)
                    // Closed on finish, so the eye goes back to the trail and sees the stop it lit.
                    openLessonId = null
                },
                onDismiss = { openLessonId = null },
            )
        }
    }

    FeatureIntroHost(introGate)
}

@Composable
private fun accentFor(item: FeedItem): Color {
    val c = MaterialTheme.modernColors
    return when (item.kind) {
        FeedKind.DO_NEXT -> item.category?.gradientColors()?.firstOrNull() ?: c.primary
        FeedKind.INSIGHT -> c.secondary
        FeedKind.BECOMING -> c.success
        FeedKind.PATTERN -> c.accent
        FeedKind.MOMENTUM -> c.warning
        FeedKind.KNOWLEDGE -> c.primary
        FeedKind.POSSIBILITY -> c.secondary
    }
}

@Composable
private fun Header(progress: UserProgress?) {
    val p = progress
    // Level/streak live on the You tab; keep the greeting hero calm when weather isn't available.
    GradientHero(
        eyebrow = today(),
        title = greeting(),
        subtitle = when {
            p == null -> "Here is your day, your way."
            p.currentStreak > 0 -> "${p.currentStreak} day streak. Let's build on it."
            else -> "Let's build today."
        },
    )
}

@Composable
private fun FilterRow(
    selected: FeedSection?,
    offered: List<FeedSection>,
    onSelect: (FeedSection?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.xs)) {
        FilterChip("All", selected == null) { onSelect(null) }
        offered.forEach { sec ->
            FilterChip(sec.label, selected == sec) { onSelect(sec) }
        }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.modernColors
    Surface(
        modifier = Modifier.bouncyClickable(onClick = onClick),
        color = if (active) c.primary else c.cardBackground,
        shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.full),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (active) Color.White else c.textSecondary,
            modifier = Modifier.padding(horizontal = LifePlannerDesign.Spacing.sm, vertical = LifePlannerDesign.Spacing.xs),
        )
    }
}

@Composable
private fun FeedCard(
    item: FeedItem,
    accent: Color,
    pulse: ForYouViewModel.CheckinPulse? = null,
    onAction: () -> Unit,
    onOpen: () -> Unit,
) {
    val c = MaterialTheme.modernColors
    Surface(
        modifier = Modifier.fillMaxWidth().bouncyClickable(enabled = item.route != null, onClick = onOpen),
        color = c.cardBackground,
        shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.large),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(LifePlannerDesign.Padding.cardContent),
            verticalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.sm), verticalAlignment = Alignment.Top) {
                LeadingVisual(item, accent)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = accent)
                    Text(item.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                    Text(item.body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
                if (item.route != null && item.actionLabel == null) {
                    Icon(PhosphorIcons.Regular.CaretRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(LifePlannerDesign.IconSize.small))
                }
            }
            if (item.actionLabel != null) {
                val haptic = rememberHapticManager()
                AnimatedContent(
                    targetState = when {
                        pulse?.done == true -> 2
                        pulse != null -> 1
                        else -> 0
                    },
                    transitionSpec = {
                        (scaleIn(initialScale = 0.9f) + fadeIn()) togetherWith fadeOut()
                    },
                    label = "checkin_cta",
                ) { state ->
                    when (state) {
                        // Fully done: a bold green confirmation with a check that springs in, held a beat.
                        2 -> DoneConfirm(target = pulse?.target ?: 1)
                        // Counting up: progress on the button; each tap adds one more with a tactile tick.
                        1 -> AppButton(
                            text = "Add one more · ${pulse?.count ?: 0}/${pulse?.target ?: 1}",
                            onClick = { haptic.success(); onAction() },
                            variant = AppButtonVariant.PRIMARY,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        else -> {
                            // Habit check-in is the primary act (filled + success buzz); navigation
                            // actions (Open goal / step / focus) are secondary with a lighter tap.
                            val isCheckIn = item.actionHabitId != null
                            AppButton(
                                text = item.actionLabel,
                                onClick = { if (isCheckIn) haptic.success() else haptic.click(); onAction() },
                                variant = if (isCheckIn) AppButtonVariant.PRIMARY else AppButtonVariant.SECONDARY,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The satisfying "you did it" state: a filled green bar with a check that springs in. */
@Composable
private fun DoneConfirm(target: Int) {
    val c = MaterialTheme.modernColors
    val scale = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = spring(dampingRatio = 0.42f, stiffness = 520f))
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = c.success,
        shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                PhosphorIcons.Fill.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
            )
            Spacer(Modifier.size(8.dp))
            Text(
                if (target > 1) "All $target done!" else "Checked in!",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun LeadingVisual(item: FeedItem, accent: Color) {
    val emoji = item.emoji
    if (item.kind == FeedKind.KNOWLEDGE && emoji != null) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(color = accent.copy(alpha = 0.12f), shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.medium), modifier = Modifier.fillMaxSize()) {}
            Text(emoji, style = MaterialTheme.typography.titleLarge)
        }
    } else {
        val icon = when (item.kind) {
            FeedKind.DO_NEXT -> PhosphorIcons.Regular.Lightning
            FeedKind.INSIGHT -> PhosphorIcons.Regular.Brain
            FeedKind.BECOMING -> PhosphorIcons.Regular.Sparkle
            FeedKind.PATTERN -> PhosphorIcons.Regular.ClockCounterClockwise
            FeedKind.MOMENTUM -> PhosphorIcons.Regular.Fire
            FeedKind.KNOWLEDGE -> PhosphorIcons.Regular.Sparkle
            FeedKind.POSSIBILITY -> PhosphorIcons.Regular.Sparkle
        }
        IconChip(icon, tint = accent)
    }
}

/**
 * The Today header. When weather is available it becomes one compact widget, place + conditions as
 * the headline (no oversized greeting), the alert or high/low + streak as the subtitle, and the
 * level ring kept on the right. Falls back to the plain greeting hero otherwise.
 */
@Composable
private fun TodayHero(progress: UserProgress?, weather: TodayWeather?, onExpand: () -> Unit) {
    if (weather == null) {
        Header(progress)
        return
    }
    val subtitle = weather.alert ?: buildString {
        append("H ${weather.highC}°  L ${weather.lowC}°")
        if (progress != null && progress.currentStreak > 0) append(" · ${progress.currentStreak} day streak")
    }
    val place = weather.placeName
    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    // Level lives on the You tab; the Today hero is a clean weather widget whose background reflects
    // the day's mood (condition + time), and taps open the full details.
    Box(Modifier.clickable(onClick = onExpand)) {
        GradientHero(
            eyebrow = if (place != null) "${today()} · $place" else today(),
            title = "${weather.condition.emoji}  ${weather.temperatureC}°  ${weather.condition.label}",
            subtitle = subtitle,
            gradient = heroMoodGradient(weather.condition, hour),
            trailing = {
                Icon(PhosphorIcons.Regular.ArrowsOutSimple, contentDescription = "Expand weather", tint = Color.White.copy(alpha = 0.85f))
            },
        )
    }
}

/**
 * A deterministic "day mood" gradient for the hero, dynamic through the day but predictable: the same
 * condition + time band always yields the same look. Night and rain read cool and calm; clear days
 * read bright; sunset reads warm.
 */
private fun heroMoodGradient(condition: WeatherCondition, hour: Int): Brush {
    val night = hour < 6 || hour >= 20
    val evening = hour in 17..19
    val rainy = condition == WeatherCondition.RAIN || condition == WeatherCondition.HEAVY_RAIN ||
        condition == WeatherCondition.SHOWERS || condition == WeatherCondition.DRIZZLE
    val cloudy = condition == WeatherCondition.CLOUDY || condition == WeatherCondition.PARTLY_CLOUDY || condition == WeatherCondition.FOG
    val colors = when {
        night -> listOf(Color(0xFF1E1B4B), Color(0xFF312E81))            // deep indigo night
        condition == WeatherCondition.THUNDERSTORM -> listOf(Color(0xFF2E3B4E), Color(0xFF465A73))
        rainy -> listOf(Color(0xFF41556B), Color(0xFF5B7089))            // calm slate
        evening -> listOf(Color(0xFFF6836B), Color(0xFF9B5DE5))          // sunset coral → violet
        cloudy -> listOf(Color(0xFF5E6E86), Color(0xFF8090A8))           // muted grey-blue
        else -> listOf(Color(0xFF3B82F6), Color(0xFF6366F1))             // bright clear day
    }
    return Brush.linearGradient(colors)
}

/**
 * Fullscreen weather detail. Opens over the whole screen showing the mood gradient with a big
 * current-conditions headline; as the user scrolls, the backdrop washes to the theme surface
 * ("turns white") and the top toolbar gains a solid background so the close (X) stays legible.
 */
@Composable
fun WeatherDetailFullScreen(weather: TodayWeather, onDismiss: () -> Unit) {
    val c = MaterialTheme.modernColors
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val nowHour = now.hour
    val nowLabel = timeLabel(now.hour, now.minute)
    val gradient = heroMoodGradient(weather.condition, nowHour)

    val listState = rememberLazyListState()
    // 0f at the very top (hero gradient in full) → 1f once the headline scrolls away.
    val collapse by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 420f).coerceIn(0f, 1f)
        }
    }
    val toolbarTint = lerp(Color.White, c.textPrimary, collapse)

    Box(Modifier.fillMaxSize().background(gradient)) {
        // The theme surface wash that fades in over the gradient as you scroll.
        Box(Modifier.fillMaxSize().background(c.background.copy(alpha = collapse)))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 104.dp,
                bottom = 40.dp,
                start = LifePlannerDesign.Padding.screenHorizontal,
                end = LifePlannerDesign.Padding.screenHorizontal,
            ),
            verticalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.md),
        ) {
            // Big current-conditions headline, over the gradient (white text throughout).
            item(key = "wx_head") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        weather.placeName ?: today(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    Text(weather.condition.emoji, style = MaterialTheme.typography.displayMedium)
                    Text(
                        "${weather.temperatureC}°",
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    Text(
                        weather.condition.label,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.95f),
                    )
                    Text(
                        "H ${weather.highC}°    L ${weather.lowC}°",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }

            weather.alert?.let { alert ->
                item(key = "wx_alert") {
                    Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)) {
                        Text(
                            alert,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = LifePlannerDesign.Spacing.sm, vertical = LifePlannerDesign.Spacing.xs),
                        )
                    }
                }
            }

            if (weather.hourly.isNotEmpty()) {
                item(key = "wx_hourly") {
                    Surface(color = c.cardBackground, shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.large)) {
                        Column(
                            Modifier.padding(LifePlannerDesign.Padding.cardContent),
                            verticalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.sm),
                        ) {
                            Text("Weather by the hour", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.sm)) {
                                weather.hourly.forEach { h -> HourCell(h, isNow = h.hour == nowHour) }
                            }
                        }
                    }
                }
            }

            val d = weather.details
            val metrics = buildList {
                d?.feelsLikeC?.let { add("Feels like" to "$it°") }
                d?.humidityPct?.let { add("Humidity" to "$it%") }
                d?.windSpeedKmh?.let { add("Wind" to "$it km/h") }
                d?.rainChanceMaxPct?.let { add("Rain chance" to "$it%") }
                d?.uvIndexMax?.let { add("UV index" to "$it") }
                d?.sunrise?.let { add("Sunrise" to it) }
                d?.sunset?.let { add("Sunset" to it) }
            }
            metrics.chunked(2).forEach { row ->
                item(key = "wx_m_${row.first().first}") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.sm)) {
                        row.forEach { (label, value) -> MetricTile(label, value, Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            item(key = "wx_src") {
                Text("As of $nowLabel · Source: ${weather.source}", style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            }
        }

        // Toolbar: close (X) on the left. Transparent over the gradient, solid once it turns white.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.background.copy(alpha = collapse))
                .statusBarsPadding()
                .padding(horizontal = LifePlannerDesign.Spacing.xs, vertical = LifePlannerDesign.Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(PhosphorIcons.Regular.X, contentDescription = "Close", tint = toolbarTint)
            }
            if (collapse > 0.5f) {
                Text(
                    "${weather.temperatureC}°  ${weather.condition.label}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = toolbarTint,
                    modifier = Modifier.padding(start = LifePlannerDesign.Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.modernColors
    Surface(modifier = modifier, color = c.cardBackground, shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.medium)) {
        Column(Modifier.padding(LifePlannerDesign.Padding.cardContent), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = c.textTertiary)
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = c.textPrimary)
        }
    }
}

@Composable
private fun HourCell(h: HourlyBrief, isNow: Boolean = false) {
    val c = MaterialTheme.modernColors
    val wet = h.precipitationProbability in 1..100
    val heavyNow = isNow && wet && h.precipitationProbability >= 50
    // The current hour is highlighted so "what's happening now" is easy to catch at a glance.
    val bg = when {
        heavyNow -> Color(0xFFE53935).copy(alpha = 0.18f) // rain right now: warm alert tint
        isNow -> c.primary.copy(alpha = 0.16f)
        else -> Color.Transparent
    }
    Column(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            if (isNow) "Now" else clockShort(h.hour),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal),
            color = if (isNow) c.primary else c.textSecondary,
        )
        Text(h.condition.emoji, style = MaterialTheme.typography.titleMedium)
        Text("${h.temperatureC}°", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = if (isNow) c.primary else c.textPrimary)
        Text(
            if (wet) "${h.precipitationProbability}%" else " ",
            style = MaterialTheme.typography.labelSmall,
            color = if (wet && h.precipitationProbability >= 50) c.primary else c.textTertiary,
        )
    }
}

private fun clockShort(hour24: Int): String {
    val h = ((hour24 % 24) + 24) % 24
    val period = if (h < 12) "AM" else "PM"
    val h12 = when (h % 12) { 0 -> 12; else -> h % 12 }
    return "$h12$period"
}

/** Device-local time like "1:57 PM". */
private fun timeLabel(hour24: Int, minute: Int): String {
    val h = ((hour24 % 24) + 24) % 24
    val period = if (h < 12) "AM" else "PM"
    val h12 = when (h % 12) { 0 -> 12; else -> h % 12 }
    val mm = minute.toString().padStart(2, '0')
    return "$h12:$mm $period"
}

@Composable
private fun WeatherPromptCard(onEnable: () -> Unit) {
    val c = MaterialTheme.modernColors
    Surface(Modifier.fillMaxWidth(), color = c.cardBackground, shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.large)) {
        Row(
            Modifier.fillMaxWidth().padding(LifePlannerDesign.Padding.cardContent),
            horizontalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🌤️", style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("See today's weather here", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary)
                Text("Turn on location to plan around conditions like incoming rain.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            AppButton(text = "Enable", onClick = onEnable, variant = AppButtonVariant.SECONDARY)
        }
    }
}

/** Planner-style row: a tappable check circle ticks the milestone done in place (no navigation). */
@Composable
private fun CalendarPlanRow(event: CalendarEvent, tz: TimeZone) {
    val c = MaterialTheme.modernColors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = c.cardBackground,
        shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.large),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(LifePlannerDesign.Padding.cardContent),
            horizontalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = PhosphorIcons.Regular.CalendarBlank,
                contentDescription = null,
                tint = c.primary,
                modifier = Modifier.padding(4.dp).size(LifePlannerDesign.IconSize.large),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(event.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary, maxLines = 1)
                event.location?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1)
                }
            }
            Surface(color = c.primary.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                Text(
                    eventTimeLabel(event, tz),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = c.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * A habit that health data completes: shows today's reading against the target, with a progress
 * track, instead of a check circle the user has no way to tick.
 */
@Composable
private fun HealthHabitRow(progress: GetHealthHabitProgressUseCase.Progress) {
    val c = MaterialTheme.modernColors
    val done = progress.done
    val accent = if (done) Color(0xFF28C76F) else c.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = c.cardBackground,
        shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.large),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(LifePlannerDesign.Padding.cardContent),
            horizontalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (done) PhosphorIcons.Fill.CheckCircle else PhosphorIcons.Regular.Heartbeat,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.padding(4.dp).size(LifePlannerDesign.IconSize.large),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    progress.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textPrimary,
                    maxLines = 1,
                )
                val fraction = progress.fraction
                if (fraction != null) {
                    val animated by animateFloatAsState(targetValue = fraction, label = "healthHabitProgress")
                    Box(
                        Modifier.fillMaxWidth().height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(c.primary.copy(alpha = 0.12f)),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(animated).height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accent),
                        )
                    }
                }
                Text(
                    healthProgressLabel(progress),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** "6,240 / 8,000 steps", or just the reading for habits that only need one. */
private fun healthProgressLabel(p: GetHealthHabitProgressUseCase.Progress): String {
    val unit = when (p.metricType) {
        HealthMetricType.STEPS -> "steps"
        HealthMetricType.SLEEP -> "h"
        HealthMetricType.WEIGHT -> "kg"
        HealthMetricType.HEART_RATE -> "bpm"
    }
    val current = formatHealthValue(p.current, p.metricType)
    val target = p.target?.let { formatHealthValue(it, p.metricType) }
    return when {
        target != null && p.metricType == HealthMetricType.SLEEP -> "$current$unit of $target$unit"
        target != null -> "$current / $target $unit"
        p.current <= 0.0 -> "No reading yet today"
        else -> "$current $unit today"
    }
}

private fun formatHealthValue(value: Double, type: HealthMetricType): String = when (type) {
    // Steps read as whole numbers with a thousands separator; the rest keep one decimal.
    HealthMetricType.STEPS -> value.toLong().toString()
        .reversed().chunked(3).joinToString(",").reversed()
    else -> {
        val rounded = (value * 10).toLong() / 10.0
        if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString()
    }
}

private fun eventTimeLabel(event: CalendarEvent, tz: TimeZone): String {
    if (event.allDay) return "All day"
    val dt = Instant.fromEpochMilliseconds(event.startEpochMillis).toLocalDateTime(tz)
    val hour12 = when (val h = dt.hour % 12) {
        0 -> 12
        else -> h
    }
    val minute = dt.minute.toString().padStart(2, '0')
    val meridiem = if (dt.hour < 12) "AM" else "PM"
    return "$hour12:$minute $meridiem"
}

@Composable
private fun PlanRow(item: PlanItem, onComplete: () -> Unit, onOpen: () -> Unit) {
    val c = MaterialTheme.modernColors
    val overdueColor = Color(0xFFE53935)
    val totalSeconds = remember(item.title) { StepDuration.secondsIn(item.title) }
    val timer = totalSeconds?.let { rememberStepTimer(item.milestoneId, it, onComplete) }
    val running = timer?.running == true

    // Completion is deliberate: only the check circle (or a countdown the user started and let run)
    // marks the step done. Tapping the row body opens the goal for context, so a stray tap can
    // never silently complete a milestone.
    Surface(
        modifier = Modifier.fillMaxWidth().bouncyClickable(onClick = onOpen),
        color = c.cardBackground,
        shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.large),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(LifePlannerDesign.Padding.cardContent),
            horizontalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (timer != null) {
                StepTimerControl(timer, accent = if (item.overdue) overdueColor else c.primary)
            } else {
                Icon(
                    imageVector = PhosphorIcons.Regular.Circle,
                    contentDescription = "Mark done",
                    tint = if (item.overdue) overdueColor else c.textTertiary,
                    modifier = Modifier
                        .clip(CircleShape)
                        .bouncyClickable(onClick = onComplete)
                        .padding(4.dp)
                        .size(LifePlannerDesign.IconSize.large),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary, maxLines = 1)
                Text(
                    if (running) "Keep going. Ticks itself at zero." else item.goalTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (running) c.primary else c.textSecondary,
                    maxLines = 1,
                )
            }
            if (!running) DueChip(overdue = item.overdue)
        }
    }
}

@Composable
private fun DueChip(overdue: Boolean) {
    val c = MaterialTheme.modernColors
    val accent = if (overdue) Color(0xFFE53935) else c.primary
    Surface(color = accent.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
        Text(
            if (overdue) "Overdue" else "Today",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SectionHeader(label: String, onSeeAll: (() -> Unit)?) {
    val c = MaterialTheme.modernColors
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = LifePlannerDesign.Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = c.textPrimary,
        )
        if (onSeeAll != null) {
            Row(
                modifier = Modifier.bouncyClickable(onClick = onSeeAll),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "See all",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = c.primary,
                )
                Icon(PhosphorIcons.Regular.CaretRight, contentDescription = null, tint = c.primary, modifier = Modifier.size(LifePlannerDesign.IconSize.small))
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.modernColors.cardBackground,
        shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.large),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.modernColors.textSecondary,
            modifier = Modifier.padding(LifePlannerDesign.Padding.cardContent),
        )
    }
}

private fun greeting(): String {
    val hour = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Hello"
    }
}

private fun today(): String {
    val d = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return "${d.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}, ${d.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${d.dayOfMonth}"
}
