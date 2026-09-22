@file:OptIn(ExperimentalUuidApi::class)

package az.tribe.lifeplanner.testutil

import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.domain.enum.*
import az.tribe.lifeplanner.domain.model.*
import az.tribe.lifeplanner.domain.repository.*
import az.tribe.lifeplanner.domain.enum.AiProvider
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// ─── GoalRepository ──────────────────────────────────────────────────────────

class FakeGoalRepository : GoalRepository {
    private val goals = mutableListOf<Goal>()
    private val _flow = MutableStateFlow<List<Goal>>(emptyList())

    fun setGoals(list: List<Goal>) {
        goals.clear()
        goals.addAll(list)
        _flow.value = goals.toList()
    }

    private fun emit() { _flow.value = goals.toList() }

    override fun observeAllGoals(): Flow<List<Goal>> = _flow
    override suspend fun getAllGoals(): List<Goal> = goals.toList()
    override suspend fun getGoalById(id: String): Goal? = goals.find { it.id == id }
    override suspend fun insertGoal(goal: Goal) { goals.add(goal); emit() }
    override suspend fun insertGoals(goals: List<Goal>) { this.goals.addAll(goals); emit() }
    override suspend fun updateGoal(goal: Goal) {
        val idx = goals.indexOfFirst { it.id == goal.id }
        if (idx >= 0) { goals[idx] = goal; emit() }
    }
    override suspend fun deleteGoalById(id: String) { goals.removeAll { it.id == id }; emit() }
    override suspend fun deleteAllGoals() { goals.clear(); emit() }
    override suspend fun getGoalsByTimeline(timeline: GoalTimeline) = goals.filter { it.timeline == timeline }
    override suspend fun getGoalsByCategory(category: GoalCategory) = goals.filter { it.category == category }
    override suspend fun updateProgress(id: String, progress: Int) {
        val idx = goals.indexOfFirst { it.id == id }
        if (idx >= 0) { goals[idx] = goals[idx].copy(progress = progress.toLong()); emit() }
    }
    override suspend fun updateGoalNotes(id: String, notes: String) {
        val idx = goals.indexOfFirst { it.id == id }
        if (idx >= 0) { goals[idx] = goals[idx].copy(notes = notes); emit() }
    }
    override suspend fun archiveGoal(id: String) {
        val idx = goals.indexOfFirst { it.id == id }
        if (idx >= 0) { goals[idx] = goals[idx].copy(isArchived = true); emit() }
    }
    override suspend fun unarchiveGoal(id: String) {
        val idx = goals.indexOfFirst { it.id == id }
        if (idx >= 0) { goals[idx] = goals[idx].copy(isArchived = false); emit() }
    }
    override suspend fun searchGoals(query: String) = goals.filter { it.title.contains(query, ignoreCase = true) }
    override suspend fun getActiveGoals() = goals.filter { it.status == GoalStatus.IN_PROGRESS }
    override suspend fun getCompletedGoals() = goals.filter { it.status == GoalStatus.COMPLETED }
    override suspend fun getUpcomingDeadlines(days: Int) = goals.take(days)
    override suspend fun getAnalytics() = testGoalAnalytics(totalGoals = goals.size)
    override suspend fun addMilestone(goalId: String, milestone: Milestone) {
        val idx = goals.indexOfFirst { it.id == goalId }
        if (idx >= 0) { goals[idx] = goals[idx].copy(milestones = goals[idx].milestones + milestone); emit() }
    }
    override suspend fun updateMilestone(milestone: Milestone) {
        goals.forEachIndexed { i, g ->
            val mIdx = g.milestones.indexOfFirst { it.id == milestone.id }
            if (mIdx >= 0) {
                val updated = g.milestones.toMutableList().apply { set(mIdx, milestone) }
                goals[i] = g.copy(milestones = updated)
                emit()
                return
            }
        }
    }
    override suspend fun deleteMilestone(milestoneId: String) {
        goals.forEachIndexed { i, g ->
            if (g.milestones.any { it.id == milestoneId }) {
                goals[i] = g.copy(milestones = g.milestones.filter { it.id != milestoneId })
                emit()
                return
            }
        }
    }
    override suspend fun toggleMilestoneCompletion(milestoneId: String, isCompleted: Boolean) {
        goals.forEachIndexed { i, g ->
            val mIdx = g.milestones.indexOfFirst { it.id == milestoneId }
            if (mIdx >= 0) {
                val updated = g.milestones.toMutableList()
                updated[mIdx] = updated[mIdx].copy(isCompleted = isCompleted)
                goals[i] = g.copy(milestones = updated)
                emit()
                return
            }
        }
    }
}

// ─── HabitRepository ─────────────────────────────────────────────────────────

class FakeHabitRepository : HabitRepository {
    private val habits = mutableListOf<Habit>()
    private val checkIns = mutableListOf<HabitCheckIn>()
    private val _flow = MutableStateFlow<List<Pair<Habit, Boolean>>>(emptyList())

    fun setHabits(list: List<Habit>) { habits.clear(); habits.addAll(list); emitFlow() }
    fun setCheckIns(list: List<HabitCheckIn>) { checkIns.clear(); checkIns.addAll(list); emitFlow() }

    private fun emitFlow() {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        _flow.value = habits.map { h -> h to checkIns.any { it.habitId == h.id && it.date == today && it.completed } }
    }

    override fun observeHabitsWithTodayStatus(): Flow<List<Pair<Habit, Boolean>>> = _flow
    override suspend fun getAllHabits() = habits.toList()
    override suspend fun getHabitById(id: String) = habits.find { it.id == id }
    override suspend fun getHabitsByCategory(category: GoalCategory) = habits.filter { it.category == category }
    override suspend fun getHabitsByGoalId(goalId: String) = habits.filter { it.linkedGoalId == goalId }
    override suspend fun insertHabit(habit: Habit) { habits.add(habit); emitFlow() }
    override suspend fun updateHabit(habit: Habit) {
        val idx = habits.indexOfFirst { it.id == habit.id }
        if (idx >= 0) { habits[idx] = habit; emitFlow() }
    }
    override suspend fun deleteHabit(id: String) { habits.removeAll { it.id == id }; emitFlow() }
    override suspend fun deactivateHabit(id: String) {
        val idx = habits.indexOfFirst { it.id == id }
        if (idx >= 0) { habits[idx] = habits[idx].copy(isActive = false); emitFlow() }
    }
    override suspend fun checkIn(habitId: String, date: LocalDate, notes: String): HabitCheckIn {
        val ci = HabitCheckIn(Uuid.random().toString(), habitId, date, true, notes, count = 1)
        checkIns.add(ci)
        emitFlow()
        return ci
    }
    override suspend fun incrementCount(habitId: String, date: LocalDate): HabitCheckIn =
        addCount(habitId, date, delta = 1)

    override suspend fun addCount(habitId: String, date: LocalDate, delta: Int): HabitCheckIn {
        val target = habits.find { it.id == habitId }?.targetCount ?: 1
        val idx = checkIns.indexOfFirst { it.habitId == habitId && it.date == date }
        val newCount = ((if (idx >= 0) checkIns[idx].count else 0) + delta).coerceAtLeast(0)
        val completed = newCount >= target
        val updated = if (idx >= 0) {
            val existing = checkIns[idx].copy(count = newCount, completed = completed)
            checkIns[idx] = existing
            existing
        } else {
            val ci = HabitCheckIn(Uuid.random().toString(), habitId, date, completed, notes = "", count = newCount)
            checkIns.add(ci)
            ci
        }
        emitFlow()
        return updated
    }
    override suspend fun getCheckInsByHabitId(habitId: String) = checkIns.filter { it.habitId == habitId }
    override suspend fun getCheckInsByDate(date: LocalDate) = checkIns.filter { it.date == date }
    override suspend fun getCheckInByHabitAndDate(habitId: String, date: LocalDate) = checkIns.find { it.habitId == habitId && it.date == date }
    override suspend fun getCheckInsInRange(habitId: String, startDate: LocalDate, endDate: LocalDate) =
        checkIns.filter { it.habitId == habitId && it.date >= startDate && it.date <= endDate }
    override suspend fun getAllCheckInsInRange(startDate: LocalDate, endDate: LocalDate) =
        checkIns.filter { it.date >= startDate && it.date <= endDate }
    override suspend fun deleteCheckIn(id: String) { checkIns.removeAll { it.id == id }; emitFlow() }
    override suspend fun calculateStreak(habitId: String) = habits.find { it.id == habitId }?.currentStreak ?: 0
    override suspend fun updateStreakAfterCheckIn(habitId: String) {}
    override suspend fun getHabitsWithTodayStatus(today: LocalDate) =
        habits.map { h -> h to checkIns.any { it.habitId == h.id && it.date == today && it.completed } }
    override suspend fun getHabitCompletionRate(habitId: String, days: Int) = 0.8f
    override suspend fun invalidateCache() {}
}

// ─── JournalRepository ───────────────────────────────────────────────────────

class FakeJournalRepository : JournalRepository {
    private val entries = mutableListOf<JournalEntry>()
    private val _flow = MutableStateFlow<List<JournalEntry>>(emptyList())

    fun setEntries(list: List<JournalEntry>) { entries.clear(); entries.addAll(list); _flow.value = entries.toList() }
    private fun emit() { _flow.value = entries.toList() }

    override fun observeAllEntries(): Flow<List<JournalEntry>> = _flow
    override suspend fun getAllEntries() = entries.toList()
    override suspend fun getEntryById(id: String) = entries.find { it.id == id }
    override suspend fun getEntriesByDate(date: LocalDate) = entries.filter { it.date == date }
    override suspend fun getEntriesByGoalId(goalId: String) = entries.filter { it.linkedGoalId == goalId }
    override suspend fun getEntriesByHabitId(habitId: String) = entries.filter { it.linkedHabitId == habitId }
    override suspend fun getEntriesByMood(mood: Mood) = entries.filter { it.mood == mood }
    override suspend fun getEntriesInRange(startDate: LocalDate, endDate: LocalDate) = entries.filter { it.date in startDate..endDate }
    override suspend fun getRecentEntries(limit: Int) = entries.sortedByDescending { it.createdAt }.take(limit)
    override suspend fun insertEntry(entry: JournalEntry) { entries.add(entry); emit() }
    override suspend fun updateEntry(entry: JournalEntry) {
        val idx = entries.indexOfFirst { it.id == entry.id }
        if (idx >= 0) { entries[idx] = entry; emit() }
    }
    override suspend fun deleteEntry(id: String) { entries.removeAll { it.id == id }; emit() }
    override suspend fun searchEntries(query: String) = entries.filter { it.content.contains(query, ignoreCase = true) }
    override suspend fun getMoodStats(startDate: LocalDate, endDate: LocalDate): Map<Mood, Int> =
        entries.filter { it.date in startDate..endDate }.groupBy { it.mood }.mapValues { it.value.size }
}

// ─── FocusRepository ─────────────────────────────────────────────────────────

class FakeFocusRepository : FocusRepository {
    private val sessions = mutableListOf<FocusSession>()
    private val _flow = MutableStateFlow<List<FocusSession>>(emptyList())

    fun setSessions(list: List<FocusSession>) { sessions.clear(); sessions.addAll(list); _flow.value = sessions.toList() }
    private fun emit() { _flow.value = sessions.toList() }

    override fun observeAllSessions(): Flow<List<FocusSession>> = _flow
    override suspend fun insertSession(session: FocusSession) { sessions.add(session); emit() }
    override suspend fun updateSession(session: FocusSession) {
        val idx = sessions.indexOfFirst { it.id == session.id }
        if (idx >= 0) { sessions[idx] = session; emit() }
    }
    override suspend fun getSessionById(id: String) = sessions.find { it.id == id }
    override suspend fun getSessionsByGoalId(goalId: String) = sessions.filter { it.goalId == goalId }
    override suspend fun getSessionsByMilestoneId(milestoneId: String) = sessions.filter { it.milestoneId == milestoneId }
    override suspend fun getCompletedSessions() = sessions.filter { it.wasCompleted }
    override suspend fun getTotalFocusMinutes() = sessions.sumOf { it.actualDurationSeconds } / 60
    override suspend fun getTotalFocusSeconds() = sessions.sumOf { it.actualDurationSeconds }.toLong()
    override suspend fun getTotalSessionCount() = sessions.size
    override suspend fun getTodaySessions() = sessions // simplified
}

// ─── GamificationRepository ──────────────────────────────────────────────────

class FakeGamificationRepository : GamificationRepository {
    var userProgress: UserProgress = testUserProgress()
    private val badges = mutableListOf<Badge>()
    private val challenges = mutableListOf<Challenge>()
    var streakResult: Pair<Int, Int> = Pair(1, 5)

    fun setBadges(list: List<Badge>) { badges.clear(); badges.addAll(list) }
    fun setChallenges(list: List<Challenge>) { challenges.clear(); challenges.addAll(list) }

    override suspend fun getUserProgress(): Flow<UserProgress> = flowOf(userProgress)
    override suspend fun getAllBadges() = badges.toList()
    override suspend fun getNewBadges() = badges.filter { it.isNew }
    override suspend fun hasBadge(type: BadgeType) = badges.any { it.type == type }
    override suspend fun markBadgeAsSeen(badgeId: String) {
        val idx = badges.indexOfFirst { it.id == badgeId }
        if (idx >= 0) badges[idx] = badges[idx].copy(isNew = false)
    }
    override suspend fun markAllBadgesAsSeen() {
        badges.forEachIndexed { i, b -> badges[i] = b.copy(isNew = false) }
    }
    override suspend fun getBadgeCount() = badges.size
    override suspend fun getActiveChallenges() = challenges.filter { !it.isCompleted }
    override suspend fun getCompletedChallenges() = challenges.filter { it.isCompleted }
    override suspend fun startChallenge(type: ChallengeType): Challenge {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val c = testChallenge(id = Uuid.random().toString(), type = type, targetProgress = ChallengeTargets.getTargetForType(type))
        challenges.add(c)
        return c
    }
    override suspend fun updateChallengeProgress(challengeId: String, progress: Int) {
        val idx = challenges.indexOfFirst { it.id == challengeId }
        if (idx >= 0) challenges[idx] = challenges[idx].copy(currentProgress = progress)
    }
    override suspend fun checkAndCompleteChallenge(challengeId: String): Challenge? {
        val idx = challenges.indexOfFirst { it.id == challengeId }
        if (idx >= 0 && challenges[idx].currentProgress >= challenges[idx].targetProgress) {
            challenges[idx] = challenges[idx].copy(isCompleted = true)
            return challenges[idx]
        }
        return null
    }
    override suspend fun cleanupExpiredChallenges() {}
    override suspend fun getAvailableChallenges(): List<ChallengeType> = ChallengeType.entries.toList()
    override suspend fun updateDailyStreakRemote() = streakResult
    /** Every XP award, in order, so tests can assert what an action actually paid out. */
    val xpAwards = mutableListOf<Long>()
    override suspend fun awardXp(amount: Long) { xpAwards.add(amount) }
    override suspend fun awardBadge(type: BadgeType) {}
}

// ─── CoachRepository ─────────────────────────────────────────────────────────

class FakeCoachRepository : CoachRepository {
    private val coaches = mutableListOf<CustomCoach>()
    private val groups = mutableListOf<CoachGroup>()
    private val members = mutableListOf<Pair<String, CoachGroupMember>>() // groupId to member

    fun setCoaches(list: List<CustomCoach>) { coaches.clear(); coaches.addAll(list) }
    fun setGroups(list: List<CoachGroup>) { groups.clear(); groups.addAll(list) }

    override suspend fun getAllCustomCoaches() = coaches.toList()
    override suspend fun getCustomCoachById(id: String) = coaches.find { it.id == id }
    override suspend fun createCustomCoach(coach: CustomCoach): CustomCoach { coaches.add(coach); return coach }
    override suspend fun updateCustomCoach(coach: CustomCoach) {
        val idx = coaches.indexOfFirst { it.id == coach.id }
        if (idx >= 0) coaches[idx] = coach
    }
    override suspend fun deleteCustomCoach(id: String) { coaches.removeAll { it.id == id } }
    override suspend fun getCustomCoachCount() = coaches.size.toLong()
    override suspend fun getAllCoachGroups() = groups.toList()
    override suspend fun getCoachGroupById(id: String) = groups.find { it.id == id }
    override suspend fun createCoachGroup(group: CoachGroup): CoachGroup { groups.add(group); return group }
    override suspend fun updateCoachGroup(group: CoachGroup) {
        val idx = groups.indexOfFirst { it.id == group.id }
        if (idx >= 0) groups[idx] = group
    }
    override suspend fun deleteCoachGroup(id: String) { groups.removeAll { it.id == id } }
    override suspend fun getCoachGroupCount() = groups.size.toLong()
    override suspend fun getCoachGroupMembers(groupId: String) = members.filter { it.first == groupId }.map { it.second }
    override suspend fun addMemberToGroup(groupId: String, member: CoachGroupMember) { members.add(groupId to member) }
    override suspend fun removeMemberFromGroup(memberId: String) { members.removeAll { it.second.id == memberId } }
    override suspend fun updateMemberOrder(memberId: String, newOrder: Int) {
        val idx = members.indexOfFirst { it.second.id == memberId }
        if (idx >= 0) members[idx] = members[idx].first to members[idx].second.copy(displayOrder = newOrder)
    }
    override suspend fun setGroupMembers(groupId: String, membersList: List<CoachGroupMember>) {
        members.removeAll { it.first == groupId }
        membersList.forEach { members.add(groupId to it) }
    }
    override suspend fun getPersonaOverride(coachId: String): String? = null
    override suspend fun savePersonaOverride(coachId: String, userPersona: String) {}
    override suspend fun deletePersonaOverride(coachId: String) {}
}

// ─── ReminderRepository ──────────────────────────────────────────────────────

class FakeReminderRepository : ReminderRepository {
    private val reminders = mutableListOf<Reminder>()
    var settings = testReminderSettings()
    var activityPattern = UserActivityPattern()
    private val notifications = mutableListOf<ScheduledNotification>()
    var globalEnabled = true

    fun setReminders(list: List<Reminder>) { reminders.clear(); reminders.addAll(list) }

    override suspend fun createReminder(reminder: Reminder): Reminder { reminders.add(reminder); return reminder }
    override suspend fun updateReminder(reminder: Reminder) {
        val idx = reminders.indexOfFirst { it.id == reminder.id }
        if (idx >= 0) reminders[idx] = reminder
    }
    override suspend fun deleteReminder(reminderId: String) { reminders.removeAll { it.id == reminderId } }
    override suspend fun getReminderById(id: String) = reminders.find { it.id == id }
    override suspend fun getAllReminders() = reminders.toList()
    override suspend fun getEnabledReminders() = reminders.filter { it.isEnabled }
    override suspend fun getRemindersByGoal(goalId: String) = reminders.filter { it.linkedGoalId == goalId }
    override suspend fun getRemindersByHabit(habitId: String) = reminders.filter { it.linkedHabitId == habitId }
    override suspend fun getRemindersByType(type: ReminderType) = reminders.filter { it.type == type }
    override suspend fun getUpcomingReminders(limit: Int) = reminders.take(limit)
    override suspend fun getSettings() = settings
    override suspend fun updateSettings(settings: ReminderSettings) { this.settings = settings }
    override suspend fun getUserActivityPattern() = activityPattern
    override suspend fun updateUserActivityPattern(pattern: UserActivityPattern) { activityPattern = pattern }
    override suspend fun calculateOptimalTime(reminderType: ReminderType) = LocalTime(9, 0)
    override suspend fun recordUserActivity(activityTime: LocalDateTime) {}
    override suspend fun scheduleNotification(notification: ScheduledNotification) { notifications.add(notification) }
    override suspend fun cancelScheduledNotification(notificationId: String) { notifications.removeAll { it.id == notificationId } }
    override suspend fun getScheduledNotifications() = notifications.toList()
    override suspend fun markNotificationDelivered(notificationId: String) {
        val idx = notifications.indexOfFirst { it.id == notificationId }
        if (idx >= 0) notifications[idx] = notifications[idx].copy(isDelivered = true)
    }
    override suspend fun snoozeReminder(reminderId: String, snoozeUntil: LocalDateTime) {
        val idx = reminders.indexOfFirst { it.id == reminderId }
        if (idx >= 0) reminders[idx] = reminders[idx].copy(snoozedUntil = snoozeUntil)
    }
    override suspend fun enableAllReminders() {
        reminders.forEachIndexed { i, r -> reminders[i] = r.copy(isEnabled = true) }
        globalEnabled = true
    }
    override suspend fun disableAllReminders() {
        reminders.forEachIndexed { i, r -> reminders[i] = r.copy(isEnabled = false) }
        globalEnabled = false
    }
    override suspend fun rescheduleAllReminders() {}
    override suspend fun findAvailableTimeSlot(preferredTime: LocalTime, excludeReminderId: String?): LocalTime = preferredTime
}

// ─── BackupRepository ────────────────────────────────────────────────────────

class FakeBackupRepository : BackupRepository {
    var exportResult: ExportResult = ExportResult.Success("{}", "backup.json")
    var importResult: ImportResult = ImportResult.Success(0, 0, 0)
    var validationResult: ValidationResult = ValidationResult.Invalid("no data")
    var lastBackupDate: String? = null
    private var _autoBackupEnabled = false

    override suspend fun exportData() = exportResult
    override suspend fun importData(jsonData: String, mergeStrategy: MergeStrategy) = importResult
    override suspend fun validateBackup(jsonData: String) = validationResult
    override suspend fun getLastBackupDate() = lastBackupDate
    override suspend fun saveLastBackupDate(date: String) { lastBackupDate = date }
    override fun isAutoBackupEnabled() = _autoBackupEnabled
    override fun setAutoBackupEnabled(enabled: Boolean) { _autoBackupEnabled = enabled }
}

// ─── RetrospectiveRepository ─────────────────────────────────────────────────

class FakeRetrospectiveRepository : RetrospectiveRepository {
    var snapshotToReturn: DaySnapshot = testDaySnapshot()
    var activeDates: Set<LocalDate> = emptySet()
    private val recaps = mutableMapOf<LocalDate, String>()

    override suspend fun getDaySnapshot(date: LocalDate) = snapshotToReturn
    override fun observeWeeklySnapshots(): kotlinx.coroutines.flow.Flow<List<DaySnapshot>> =
        kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun getDatesWithActivity(start: LocalDate, end: LocalDate) = activeDates
    override suspend fun getDayRecap(date: LocalDate) = recaps[date]
    override suspend fun saveDayRecap(date: LocalDate, recap: String) { recaps[date] = recap }
}

// ─── LifeBalanceRepository ───────────────────────────────────────────────────

class FakeLifeBalanceRepository : LifeBalanceRepository {
    var report: LifeBalanceReport = testLifeBalanceReport()
    var areaScores: List<LifeAreaScore> = emptyList()
    private val assessments = mutableListOf<ManualAssessment>()
    private val reports = mutableListOf<LifeBalanceReport>()

    override suspend fun calculateCurrentBalance(forceRefresh: Boolean) = report
    override suspend fun getAreaScore(area: LifeArea) = areaScores.find { it.area == area } ?: testLifeAreaScore(area = area)
    override suspend fun getAllAreaScores() = areaScores
    override suspend fun generateAIInsights(areaScores: List<LifeAreaScore>) = report
    override suspend fun saveManualAssessment(assessment: ManualAssessment) { assessments.add(assessment) }
    override suspend fun getManualAssessments() = assessments.toList()
    override fun getBalanceHistory(): Flow<List<LifeBalanceReport>> = flowOf(reports.toList())
    override suspend fun saveBalanceReport(report: LifeBalanceReport) { reports.add(report) }
    override suspend fun getLatestReport() = reports.lastOrNull()
    override suspend fun preGenerateGoalsForRecommendations(
        recommendations: List<BalanceRecommendation>,
        areaScores: List<LifeAreaScore>
    ) = recommendations
}

// ─── GoalDependencyRepository ────────────────────────────────────────────────

class FakeGoalDependencyRepository : GoalDependencyRepository {
    private val deps = mutableListOf<GoalDependency>()
    private val _flow = MutableStateFlow<List<GoalDependency>>(emptyList())
    var graphToReturn = DependencyGraph(emptyList(), emptyList())
    var suggestedDeps: List<Pair<Goal, DependencyType>> = emptyList()

    fun setDependencies(list: List<GoalDependency>) { deps.clear(); deps.addAll(list); _flow.value = deps.toList() }
    private fun emit() { _flow.value = deps.toList() }

    override fun getAllDependencies(): Flow<List<GoalDependency>> = _flow
    override suspend fun getDependenciesBySourceGoal(goalId: String) = deps.filter { it.sourceGoalId == goalId }
    override suspend fun getDependenciesByTargetGoal(goalId: String) = deps.filter { it.targetGoalId == goalId }
    override suspend fun getDependenciesForGoal(goalId: String) = deps.filter { it.sourceGoalId == goalId || it.targetGoalId == goalId }
    override suspend fun dependencyExists(goalId1: String, goalId2: String) =
        deps.any { (it.sourceGoalId == goalId1 && it.targetGoalId == goalId2) || (it.sourceGoalId == goalId2 && it.targetGoalId == goalId1) }
    override suspend fun addDependency(sourceGoalId: String, targetGoalId: String, dependencyType: DependencyType): GoalDependency {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val dep = GoalDependency(Uuid.random().toString(), sourceGoalId, targetGoalId, dependencyType, now)
        deps.add(dep)
        emit()
        return dep
    }
    override suspend fun removeDependency(dependencyId: String) { deps.removeAll { it.id == dependencyId }; emit() }
    override suspend fun removeDependenciesForGoal(goalId: String) { deps.removeAll { it.sourceGoalId == goalId || it.targetGoalId == goalId }; emit() }
    override suspend fun getBlockingGoals(goalId: String) = emptyList<Goal>()
    override suspend fun getBlockedGoals(goalId: String) = emptyList<Goal>()
    override suspend fun getChildGoals(goalId: String) = emptyList<Goal>()
    override suspend fun getParentGoals(goalId: String) = emptyList<Goal>()
    override suspend fun getRelatedGoals(goalId: String) = emptyList<Goal>()
    override suspend fun buildDependencyGraph() = graphToReturn
    override suspend fun buildDependencyGraphForGoal(goalId: String) = graphToReturn
    override suspend fun getSuggestedDependencies(goalId: String) = suggestedDeps
    override suspend fun wouldCreateCycle(sourceGoalId: String, targetGoalId: String) = false
}

// ─── AiProxyService ──────────────────────────────────────────────────────────

class FakeAiProxyService : AiProxyService {
    var responseToReturn = "Great day!"
    var errorToThrow: Throwable? = null
    override suspend fun generateText(prompt: String, systemPrompt: String?, provider: AiProvider?): String {
        errorToThrow?.let { throw it }
        return responseToReturn
    }
    override suspend fun generateStructuredJson(prompt: String, responseSchema: JsonObject, systemPrompt: String?, provider: AiProvider?) = "{}"
    override suspend fun chat(messages: List<AiProxyService.ChatMessage>, systemPrompt: String?, responseSchema: JsonObject?, provider: AiProvider?) = responseToReturn
    override fun chatStream(messages: List<AiProxyService.ChatMessage>, systemPrompt: String?, provider: AiProvider?) = flow<AiProxyService.StreamEvent> {}
}

// ─── AbilityRepository ───────────────────────────────────────────────────────

class FakeAbilityRepository : az.tribe.lifeplanner.domain.repository.AbilityRepository {
    /** Abilities this fake holds, keyed by id. Tests seed these and assert on them afterwards. */
    val abilities = mutableMapOf<String, az.tribe.lifeplanner.domain.model.Ability>()

    /** Every awardXp call in order, so a test can assert what was credited and how much. */
    val awards = mutableListOf<Pair<String, Int>>()

    override fun observeAllAbilities(): Flow<List<az.tribe.lifeplanner.domain.model.Ability>> =
        kotlinx.coroutines.flow.flowOf(abilities.values.toList())
    override suspend fun getAbilityById(id: String) = abilities[id]
    override suspend fun createAbility(ability: az.tribe.lifeplanner.domain.model.Ability) { abilities[ability.id] = ability }
    override suspend fun updateAbility(ability: az.tribe.lifeplanner.domain.model.Ability) { abilities[ability.id] = ability }
    override suspend fun deleteAbility(id: String) { abilities.remove(id) }
    override suspend fun linkHabit(abilityId: String, habitId: String, xpWeight: Float) {}
    override suspend fun unlinkHabit(abilityId: String, habitId: String) {}
    override suspend fun getLinksForAbility(abilityId: String) = emptyList<az.tribe.lifeplanner.domain.model.AbilityHabitLink>()
    override suspend fun getLinksForHabit(habitId: String) = emptyList<az.tribe.lifeplanner.domain.model.AbilityHabitLink>()
    override suspend fun awardXpToAbilitiesForHabit(habitId: String, baseXp: Int) {}

    override suspend fun awardXp(abilityId: String, xp: Int): az.tribe.lifeplanner.domain.model.XpAward? {
        if (xp <= 0) return null
        val before = abilities[abilityId] ?: return null
        awards += abilityId to xp
        val newTotal = before.totalXp + xp
        val newLevel = levelFor(newTotal)
        val after = before.copy(totalXp = newTotal, currentLevel = newLevel)
        abilities[abilityId] = after
        return az.tribe.lifeplanner.domain.model.XpAward(after, xp, newLevel > before.currentLevel)
    }

    // Mirrors AbilityRepositoryImpl.calculateLevel: each level costs level*50 XP.
    private fun levelFor(totalXp: Int): Int {
        var level = 1
        var acc = 0
        while (true) {
            acc += level * 50
            if (acc > totalXp) break
            level++
        }
        return maxOf(1, level)
    }

    override suspend fun linkGoal(abilityId: String, goalId: String) {}
    override suspend fun unlinkGoal(abilityId: String, goalId: String) {}
    override suspend fun getGoalLinksForAbility(abilityId: String) = emptyList<az.tribe.lifeplanner.domain.model.AbilityGoalLink>()
    override suspend fun getAbilityLinksForGoal(goalId: String) = emptyList<az.tribe.lifeplanner.domain.model.AbilityGoalLink>()
}

// ─── GoalHistoryRepository ───────────────────────────────────────────────────

class FakeGoalHistoryRepository : GoalHistoryRepository {
    private val changes = mutableListOf<GoalChange>()

    fun setChanges(list: List<GoalChange>) { changes.clear(); changes.addAll(list) }

    override suspend fun insertChange(id: String, goalId: String, field: String, oldValue: String?, newValue: String, changedAt: String) {
        changes.add(GoalChange(id, goalId, field, oldValue, newValue, changedAt))
    }
    override suspend fun getHistoryForGoal(goalId: String) = changes.filter { it.goalId == goalId }
}

// ─── LifeValueRepository ─────────────────────────────────────────────────────

class FakeLifeValueRepository : LifeValueRepository {
    private val values = mutableListOf<LifeValue>()
    private val _flow = MutableStateFlow<List<LifeValue>>(emptyList())

    fun setValues(list: List<LifeValue>) { values.clear(); values.addAll(list); emit() }
    private fun emit() { _flow.value = values.toList() }

    override fun observeAllLifeValues(): Flow<List<LifeValue>> = _flow
    override suspend fun getAllLifeValues() = values.toList()
    override suspend fun getActiveLifeValues() = values.filter { it.isActive }
    override suspend fun getLifeValueById(id: String) = values.find { it.id == id }
    override suspend fun insertLifeValue(value: LifeValue) { values.add(value); emit() }
    override suspend fun insertLifeValues(values: List<LifeValue>) { this.values.addAll(values); emit() }
    override suspend fun updateLifeValue(value: LifeValue) {
        val idx = values.indexOfFirst { it.id == value.id }
        if (idx >= 0) { values[idx] = value; emit() }
    }
    override suspend fun deleteLifeValueById(id: String) { values.removeAll { it.id == id }; emit() }
}

// ─── KnowledgeRepository ─────────────────────────────────────────────────────

class FakeKnowledgeRepository : KnowledgeRepository {
    private val read = mutableSetOf<String>()
    private val _flow = MutableStateFlow<Set<String>>(emptySet())

    fun setRead(ids: Set<String>) { read.clear(); read.addAll(ids); _flow.value = read.toSet() }

    override fun readIds(): Flow<Set<String>> = _flow
    override suspend fun markRead(id: String) { read.add(id); _flow.value = read.toSet() }
    override suspend fun markUnread(id: String) { read.remove(id); _flow.value = read.toSet() }
}

// ─── DecisionRepository ──────────────────────────────────────────────────────

/** In-memory decision log. Seed via [setDecisions]; mutations are reflected in the flow. */
class FakeDecisionRepository(
    initial: List<az.tribe.lifeplanner.domain.model.Decision> = emptyList(),
) : az.tribe.lifeplanner.domain.repository.DecisionRepository {
    private val _flow = MutableStateFlow(initial)

    fun setDecisions(list: List<az.tribe.lifeplanner.domain.model.Decision>) { _flow.value = list }

    override fun observeAllDecisions(): Flow<List<az.tribe.lifeplanner.domain.model.Decision>> = _flow
    override suspend fun getAllDecisions() = _flow.value
    override suspend fun getDecisionById(id: String) = _flow.value.firstOrNull { it.id == id }
    override suspend fun getDecisionsForGoal(goalId: String) = _flow.value.filter { it.relatedGoalId == goalId }
    override suspend fun insertDecision(decision: az.tribe.lifeplanner.domain.model.Decision) {
        _flow.value = _flow.value + decision
    }
    override suspend fun updateDecision(decision: az.tribe.lifeplanner.domain.model.Decision) {
        _flow.value = _flow.value.map { if (it.id == decision.id) decision else it }
    }
    override suspend fun deleteDecisionById(id: String) {
        _flow.value = _flow.value.filterNot { it.id == id }
    }
}

// ─── DecisionProfileRepository ───────────────────────────────────────────────

class FakeDecisionProfileRepository(
    initial: az.tribe.lifeplanner.domain.model.DecisionProfile? = null,
) : az.tribe.lifeplanner.domain.repository.DecisionProfileRepository {
    private val _flow = MutableStateFlow(initial)
    override fun observeProfile(): Flow<az.tribe.lifeplanner.domain.model.DecisionProfile?> = _flow
    override suspend fun getProfile() = _flow.value
    override suspend fun upsertProfile(profile: az.tribe.lifeplanner.domain.model.DecisionProfile) {
        _flow.value = profile
    }
}

// ─── Fakes added for whole-screen previews ───────────────────────────────────
// These back the Present feed, whose builder reaches through a deep graph of services. They are
// deliberately inert: previews want a screen that renders, not a simulation of the app.

class FakeHealthRepository : az.tribe.lifeplanner.domain.repository.HealthRepository {
    var todaySteps: Long? = 6420
    override fun observeMetricsByType(type: az.tribe.lifeplanner.domain.enum.HealthMetricType): Flow<List<az.tribe.lifeplanner.domain.model.HealthMetric>> =
        kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun getMetricsInRange(
        type: az.tribe.lifeplanner.domain.enum.HealthMetricType,
        startDate: kotlinx.datetime.LocalDate,
        endDate: kotlinx.datetime.LocalDate
    ) = emptyList<az.tribe.lifeplanner.domain.model.HealthMetric>()
    override suspend fun getLatestMetric(type: az.tribe.lifeplanner.domain.enum.HealthMetricType) = null
    override suspend fun insertMetric(metric: az.tribe.lifeplanner.domain.model.HealthMetric) {}
    override suspend fun insertMetrics(metrics: List<az.tribe.lifeplanner.domain.model.HealthMetric>) {}
    override suspend fun syncFromPlatform() {}
    override suspend fun getTodaySteps() = todaySteps
    override suspend fun getLatestWeight(): Double? = null
}

class FakeBehaviorRepository : az.tribe.lifeplanner.domain.repository.BehaviorRepository {
    override suspend fun recordScreenEnter(screen: String) {}
    override suspend fun recordScreenExit(screen: String, durationMs: Long) {}
    override suspend fun getPattern() = null
    override suspend fun refreshPattern() {}
    override suspend fun pruneOldEvents(keepDays: Int) {}
}

class FakeIdentityStatementRepository : az.tribe.lifeplanner.domain.repository.IdentityStatementRepository {
    override fun observeAll(): Flow<List<az.tribe.lifeplanner.domain.model.IdentityStatement>> =
        kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun getAll() = emptyList<az.tribe.lifeplanner.domain.model.IdentityStatement>()
    override suspend fun getById(id: String) = null
    override suspend fun getByValue(valueId: String) = emptyList<az.tribe.lifeplanner.domain.model.IdentityStatement>()
    override suspend fun insert(statement: az.tribe.lifeplanner.domain.model.IdentityStatement) {}
    override suspend fun update(statement: az.tribe.lifeplanner.domain.model.IdentityStatement) {}
    override suspend fun deleteById(id: String) {}
}

class FakeUserSituationRepository : az.tribe.lifeplanner.domain.repository.UserSituationRepository {
    private val situation = az.tribe.lifeplanner.domain.model.UserSituation()
    override fun observe(): Flow<az.tribe.lifeplanner.domain.model.UserSituation?> =
        kotlinx.coroutines.flow.flowOf(situation)
    override suspend fun getOrCreate(userId: String) = situation
    override suspend fun upsert(userId: String, situation: az.tribe.lifeplanner.domain.model.UserSituation) {}
    override suspend fun updateMeta(userId: String, meta: az.tribe.lifeplanner.domain.model.MetaSlice) {}
    override suspend fun updateCareer(userId: String, career: az.tribe.lifeplanner.domain.model.CareerSlice) {}
    override suspend fun updateMoney(userId: String, money: az.tribe.lifeplanner.domain.model.MoneySlice) {}
    override suspend fun updateBody(userId: String, body: az.tribe.lifeplanner.domain.model.BodySlice) {}
    override suspend fun updatePeople(userId: String, people: az.tribe.lifeplanner.domain.model.PeopleSlice) {}
    override suspend fun updatePurpose(userId: String, purpose: az.tribe.lifeplanner.domain.model.PurposeSlice) {}
}

/** A populated wheel, so Present renders the chart rather than the "set up your wheel" prompt. */
class FakeWheelRepository(
    private val report: az.tribe.lifeplanner.domain.model.WheelReport = defaultWheel(),
) : az.tribe.lifeplanner.domain.repository.WheelRepository {
    override fun observeWheel(): Flow<az.tribe.lifeplanner.domain.model.WheelReport> =
        kotlinx.coroutines.flow.flowOf(report)
    override suspend fun getWheel() = report
    override suspend fun setScore(area: az.tribe.lifeplanner.domain.model.WheelArea, score: Double, note: String?) {}
    override suspend fun clearScore(area: az.tribe.lifeplanner.domain.model.WheelArea) {}
    override suspend fun captureSnapshot() {}
    override suspend fun compareTo(period: az.tribe.lifeplanner.domain.model.ComparisonPeriod) = null
    override suspend fun compareToDate(date: kotlinx.datetime.LocalDate) = null
    override suspend fun snapshots() = emptyList<az.tribe.lifeplanner.domain.model.WheelSnapshot>()

    companion object {
        private val SCORES = listOf(8.0, 8.0, 9.0, 10.0, 4.0, 7.0, 3.0, 10.0, 7.0, 7.5)

        fun defaultWheel(): az.tribe.lifeplanner.domain.model.WheelReport =
            az.tribe.lifeplanner.domain.model.WheelReport(
                id = "preview-wheel",
                scores = az.tribe.lifeplanner.domain.model.WheelArea.entries.mapIndexed { i, area ->
                    az.tribe.lifeplanner.domain.model.WheelScore(
                        area = area,
                        score = SCORES[i % SCORES.size],
                        source = az.tribe.lifeplanner.domain.model.ScoreSource.USER,
                        confidence = 1.0,
                        basis = "Set by you",
                    )
                },
                generatedAt = kotlinx.datetime.LocalDateTime(2026, 8, 21, 9, 0),
            )
    }
}
