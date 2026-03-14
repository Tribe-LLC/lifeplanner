package az.tribe.lifeplanner.di

import az.tribe.lifeplanner.data.auth.AuthService
import az.tribe.lifeplanner.data.auth.SupabaseAuthService
import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.data.network.AiProxyServiceImpl
import az.tribe.lifeplanner.data.network.AuthTokenProvider
import az.tribe.lifeplanner.data.network.GeminiService
import az.tribe.lifeplanner.data.network.GeminiServiceImpl
import az.tribe.lifeplanner.data.repository.AiUsageRepositoryImpl
import az.tribe.lifeplanner.data.repository.BackupRepositoryImpl
import az.tribe.lifeplanner.data.repository.ChatRepositoryImpl
import az.tribe.lifeplanner.data.repository.CoachRepositoryImpl
import az.tribe.lifeplanner.di.FileSharer
import az.tribe.lifeplanner.di.createFileSharer
import az.tribe.lifeplanner.data.repository.GamificationRepositoryImpl
import az.tribe.lifeplanner.data.repository.GeminiRepositoryImp
import az.tribe.lifeplanner.data.repository.GoalDependencyRepositoryImpl
import az.tribe.lifeplanner.data.repository.GoalHistoryRepositoryImpl
import az.tribe.lifeplanner.data.repository.FocusRepositoryImpl
import az.tribe.lifeplanner.data.repository.RetrospectiveRepositoryImpl
import az.tribe.lifeplanner.data.repository.GoalRepositoryImpl
import az.tribe.lifeplanner.data.repository.HabitRepositoryImpl
import az.tribe.lifeplanner.data.repository.JournalRepositoryImpl
import az.tribe.lifeplanner.data.repository.LifeBalanceRepositoryImpl
import az.tribe.lifeplanner.data.repository.ReminderRepositoryImpl
import az.tribe.lifeplanner.data.review.ReviewMessageBuilder
import az.tribe.lifeplanner.data.repository.UserRepositoryImpl
import az.tribe.lifeplanner.domain.repository.AiUsageRepository
import az.tribe.lifeplanner.domain.repository.BackupRepository
import az.tribe.lifeplanner.domain.repository.ChatRepository
import az.tribe.lifeplanner.domain.repository.CoachRepository
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import az.tribe.lifeplanner.domain.repository.GeminiRepository
import az.tribe.lifeplanner.domain.repository.GoalDependencyRepository
import az.tribe.lifeplanner.domain.repository.GoalHistoryRepository
import az.tribe.lifeplanner.domain.repository.FocusRepository
import az.tribe.lifeplanner.domain.repository.RetrospectiveRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.JournalRepository
import az.tribe.lifeplanner.domain.repository.LifeBalanceRepository
import az.tribe.lifeplanner.domain.repository.ReminderRepository
import az.tribe.lifeplanner.domain.repository.UserRepository
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import az.tribe.lifeplanner.notification.NotificationSchedulerInterface
import az.tribe.lifeplanner.notification.getNotificationScheduler
import az.tribe.lifeplanner.util.NetworkConnectivityObserver
import az.tribe.lifeplanner.widget.WidgetDataSyncService
import az.tribe.lifeplanner.ui.GoalViewModel
import az.tribe.lifeplanner.ui.chat.ChatViewModel
import az.tribe.lifeplanner.ui.dependency.GoalDependencyViewModel
import az.tribe.lifeplanner.ui.gamification.GamificationViewModel
import az.tribe.lifeplanner.ui.habit.HabitViewModel
import az.tribe.lifeplanner.ui.journal.JournalViewModel
import az.tribe.lifeplanner.ui.backup.BackupViewModel
import az.tribe.lifeplanner.ui.focus.FocusViewModel
import az.tribe.lifeplanner.ui.retrospective.RetrospectiveViewModel
import az.tribe.lifeplanner.ui.balance.LifeBalanceViewModel
import az.tribe.lifeplanner.ui.coach.CoachViewModel
import az.tribe.lifeplanner.ui.reminder.ReminderViewModel
import az.tribe.lifeplanner.ui.viewmodel.AuthViewModel
import az.tribe.lifeplanner.usecases.journal.CreateJournalEntryUseCase
import az.tribe.lifeplanner.usecases.journal.DeleteJournalEntryUseCase
import az.tribe.lifeplanner.usecases.journal.GetAllJournalEntriesUseCase
import az.tribe.lifeplanner.usecases.journal.GetJournalEntriesByGoalUseCase
import az.tribe.lifeplanner.usecases.journal.GetRecentJournalEntriesUseCase
import az.tribe.lifeplanner.usecases.journal.UpdateJournalEntryUseCase
import az.tribe.lifeplanner.usecases.habit.CheckInHabitUseCase
import az.tribe.lifeplanner.usecases.habit.CreateHabitUseCase
import az.tribe.lifeplanner.usecases.habit.DeleteHabitUseCase
import az.tribe.lifeplanner.usecases.habit.GetAllHabitsUseCase
import az.tribe.lifeplanner.usecases.habit.GetHabitsByGoalUseCase
import az.tribe.lifeplanner.usecases.habit.GetHabitsWithTodayStatusUseCase
import az.tribe.lifeplanner.usecases.habit.UncheckHabitUseCase
import az.tribe.lifeplanner.usecases.habit.UpdateHabitUseCase
import az.tribe.lifeplanner.usecases.AddMilestoneUseCase
import az.tribe.lifeplanner.usecases.ArchiveGoalUseCase
import az.tribe.lifeplanner.usecases.CalculateGoalCompletionRateUseCase
import az.tribe.lifeplanner.usecases.CreateGoalUseCase
import az.tribe.lifeplanner.usecases.DeleteGoalUseCase
import az.tribe.lifeplanner.usecases.DeleteMilestoneUseCase
import az.tribe.lifeplanner.usecases.FilterGoalsByStatusUseCase
import az.tribe.lifeplanner.usecases.GenerateAiGoalsUseCase
import az.tribe.lifeplanner.usecases.GenerateAiQuestionnaireUseCase
import az.tribe.lifeplanner.usecases.GetActiveGoalsUseCase
import az.tribe.lifeplanner.usecases.GetAllGoalsUseCase
import az.tribe.lifeplanner.usecases.GetCompletedGoalsUseCase
import az.tribe.lifeplanner.usecases.GetGoalAnalyticsUseCase
import az.tribe.lifeplanner.usecases.GetGoalByIdUseCase
import az.tribe.lifeplanner.usecases.GetGoalHistoryUseCase
import az.tribe.lifeplanner.usecases.GetGoalStatisticsUseCase
import az.tribe.lifeplanner.usecases.GetGoalsByCategoryUseCase
import az.tribe.lifeplanner.usecases.GetGoalsByTimelineUseCase
import az.tribe.lifeplanner.usecases.GetUpcomingDeadlinesUseCase
import az.tribe.lifeplanner.usecases.LogGoalChangeUseCase
import az.tribe.lifeplanner.usecases.SearchGoalsUseCase
import az.tribe.lifeplanner.usecases.ToggleMilestoneCompletionUseCase
import az.tribe.lifeplanner.usecases.UnarchiveGoalUseCase
import az.tribe.lifeplanner.usecases.UpdateGoalNotesUseCase
import az.tribe.lifeplanner.usecases.UpdateGoalProgressUseCase
import az.tribe.lifeplanner.usecases.UpdateGoalStatusUseCase
import az.tribe.lifeplanner.usecases.UpdateGoalUseCase
import az.tribe.lifeplanner.usecases.UpdateMilestoneUseCase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.auth.auth
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

const val DB_NAME = "LifePlannerDB.db"

val appModule = module {

    single { DatabaseDriverFactory() }
    single { SharedDatabase(get()) }
    single { Settings() }
    single<FileSharer> { createFileSharer() }
    single { WidgetDataSyncService() }
    single { NetworkConnectivityObserver() }
    single<NotificationSchedulerInterface> { getNotificationScheduler() }

    // Auth Service (Supabase — multiplatform, no platform-specific needed)
    single<AuthService> { SupabaseAuthService(get()) }

    // Auth token provider (Supabase session → JWT, with auto-refresh)
    single<AuthTokenProvider> {
        val supabase: io.github.jan.supabase.SupabaseClient = get()
        AuthTokenProvider {
            // Try current session first
            val session = supabase.auth.currentSessionOrNull()
            if (session != null) {
                // Check if the access token is expired or about to expire (within 30s)
                val now = kotlinx.datetime.Clock.System.now()
                val timeUntilExpiry = session.expiresAt - now
                if (timeUntilExpiry.inWholeSeconds <= 30) {
                    // Token expired or about to — force refresh with retry
                    var lastException: Exception? = null
                    for (attempt in 1..3) {
                        try {
                            supabase.auth.refreshCurrentSession()
                            val refreshed = supabase.auth.currentSessionOrNull()?.accessToken
                            if (refreshed != null) return@AuthTokenProvider refreshed
                        } catch (e: Exception) {
                            lastException = e
                            co.touchlab.kermit.Logger.w("AuthTokenProvider") {
                                "Token refresh attempt $attempt failed: ${e.message}"
                            }
                            if (attempt < 3) kotlinx.coroutines.delay(500L * attempt)
                        }
                    }
                    co.touchlab.kermit.Logger.e("AuthTokenProvider") {
                        "Token refresh failed after 3 attempts: ${lastException?.message}"
                    }
                    throw IllegalStateException("Authentication expired. Please sign in again.")
                } else {
                    session.accessToken
                }
            } else {
                throw IllegalStateException("Not authenticated. Please sign in.")
            }
        }
    }

    // AI Proxy Service
    single<AiProxyService> { AiProxyServiceImpl(get(), get(), get()) }

    // Repositories
    single<GeminiService> { GeminiServiceImpl(get<AiProxyService>()) }
    single<GeminiRepository> { GeminiRepositoryImp(get()) }

    single<GoalRepository> { GoalRepositoryImpl(get(), get(), get()) }
    single<GoalHistoryRepository> { GoalHistoryRepositoryImpl(get(), get()) }
    single<GamificationRepository> { GamificationRepositoryImpl(get(), get(), get(), get()) }
    single<UserRepository> { UserRepositoryImpl(get(), get()) }
    single<HabitRepository> { HabitRepositoryImpl(get(), get(), get()) }
    single<JournalRepository> { JournalRepositoryImpl(get(), get()) }
    single<GoalDependencyRepository> { GoalDependencyRepositoryImpl(get(), get()) }
    single<CoachRepository> { CoachRepositoryImpl(get(), get()) }
    single<ChatRepository> { ChatRepositoryImpl(get(), get<AiProxyService>(), get(), get()) }
    single { ReviewMessageBuilder(get()) }
    single<ReminderRepository> { ReminderRepositoryImpl(get(), get(), get()) }
    single<FocusRepository> { FocusRepositoryImpl(get(), get()) }
    single<AiUsageRepository> { AiUsageRepositoryImpl(get()) }
    single<RetrospectiveRepository> { RetrospectiveRepositoryImpl(get()) }

    // Existing Use Cases
    factory { GetAllGoalsUseCase(get()) }
    factory { GetGoalsByTimelineUseCase(get()) }
    factory { GetGoalsByCategoryUseCase(get()) }
    factory { CreateGoalUseCase(get()) }
    factory { DeleteGoalUseCase(get()) }
    factory { UpdateGoalUseCase(get()) }
    factory { UpdateGoalProgressUseCase(get()) }
    factory { LogGoalChangeUseCase(get()) }
    factory { GetGoalHistoryUseCase(get()) }
    factory { GetGoalAnalyticsUseCase(get()) }

    // New Search and Filter Use Cases
    factory { SearchGoalsUseCase(get()) }
    factory { GetActiveGoalsUseCase(get()) }
    factory { GetCompletedGoalsUseCase(get()) }
    factory { GetUpcomingDeadlinesUseCase(get()) }

    // Goal Management Use Cases
    factory { UpdateGoalStatusUseCase(get()) }
    factory { UpdateGoalNotesUseCase(get()) }
    factory { ArchiveGoalUseCase(get()) }
    factory { UnarchiveGoalUseCase(get()) }

    // Milestone Management Use Cases
    factory { AddMilestoneUseCase(get()) }
    factory { UpdateMilestoneUseCase(get()) }
    factory { DeleteMilestoneUseCase(get()) }
    factory { ToggleMilestoneCompletionUseCase(get()) }

    // Utility Use Cases
    factory { GetGoalByIdUseCase(get()) }
    factory { FilterGoalsByStatusUseCase(get()) }
    factory { CalculateGoalCompletionRateUseCase() }
    factory { GetGoalStatisticsUseCase(get()) }

    factory { GenerateAiQuestionnaireUseCase(get()) }
    factory { GenerateAiGoalsUseCase(get()) }

    // Habit Use Cases
    factory { GetAllHabitsUseCase(get()) }
    factory { CreateHabitUseCase(get()) }
    factory { UpdateHabitUseCase(get()) }
    factory { DeleteHabitUseCase(get()) }
    factory { CheckInHabitUseCase(get()) }
    factory { UncheckHabitUseCase(get()) }
    factory { GetHabitsWithTodayStatusUseCase(get()) }
    factory { GetHabitsByGoalUseCase(get()) }

    // Journal Use Cases
    factory { GetAllJournalEntriesUseCase(get()) }
    factory { CreateJournalEntryUseCase(get()) }
    factory { UpdateJournalEntryUseCase(get()) }
    factory { DeleteJournalEntryUseCase(get()) }
    factory { GetRecentJournalEntriesUseCase(get()) }
    factory { GetJournalEntriesByGoalUseCase(get()) }

    // Life Balance Repository
    single<LifeBalanceRepository> { LifeBalanceRepositoryImpl(get(), get(), get<AiProxyService>()) }

    // Backup Repository
    single<BackupRepository> { BackupRepositoryImpl(get(), get()) }

    // ViewModels
    viewModelOf(::GoalViewModel)
    viewModelOf(::GamificationViewModel)
    viewModelOf(::AuthViewModel)
    viewModelOf(::HabitViewModel)
    viewModelOf(::JournalViewModel)
    viewModelOf(::GoalDependencyViewModel)
    viewModelOf(::ChatViewModel)
    viewModelOf(::CoachViewModel)
    viewModelOf(::ReminderViewModel)
    viewModelOf(::LifeBalanceViewModel)
    viewModelOf(::BackupViewModel)
    viewModelOf(::FocusViewModel)
    viewModelOf(::RetrospectiveViewModel)
}