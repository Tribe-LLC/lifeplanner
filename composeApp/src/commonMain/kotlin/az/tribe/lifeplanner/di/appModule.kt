package az.tribe.lifeplanner.di

import az.tribe.lifeplanner.data.network.FirebaseTokenProvider
import az.tribe.lifeplanner.data.network.GeminiService
import az.tribe.lifeplanner.data.network.GeminiServiceImpl
import az.tribe.lifeplanner.data.repository.BackupRepositoryImpl
import az.tribe.lifeplanner.data.repository.ChatRepositoryImpl
import az.tribe.lifeplanner.data.repository.CoachRepositoryImpl
import az.tribe.lifeplanner.di.FileSharer
import az.tribe.lifeplanner.di.createFileSharer
import az.tribe.lifeplanner.data.repository.GamificationRepositoryImpl
import az.tribe.lifeplanner.data.repository.GeminiRepositoryImp
import az.tribe.lifeplanner.data.repository.GoalDependencyRepositoryImpl
import az.tribe.lifeplanner.data.repository.GoalHistoryRepositoryImpl
import az.tribe.lifeplanner.data.repository.GoalRepositoryImpl
import az.tribe.lifeplanner.data.repository.HabitRepositoryImpl
import az.tribe.lifeplanner.data.repository.JournalRepositoryImpl
import az.tribe.lifeplanner.data.repository.LifeBalanceRepositoryImpl
import az.tribe.lifeplanner.data.repository.ReminderRepositoryImpl
import az.tribe.lifeplanner.data.repository.ReviewRepositoryImpl
import az.tribe.lifeplanner.data.repository.UserRepositoryImpl
import az.tribe.lifeplanner.domain.repository.BackupRepository
import az.tribe.lifeplanner.domain.repository.ChatRepository
import az.tribe.lifeplanner.domain.repository.CoachRepository
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import az.tribe.lifeplanner.domain.repository.GeminiRepository
import az.tribe.lifeplanner.domain.repository.GoalDependencyRepository
import az.tribe.lifeplanner.domain.repository.GoalHistoryRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.JournalRepository
import az.tribe.lifeplanner.domain.repository.LifeBalanceRepository
import az.tribe.lifeplanner.domain.repository.ReminderRepository
import az.tribe.lifeplanner.domain.repository.ReviewRepository
import az.tribe.lifeplanner.domain.repository.UserRepository
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import az.tribe.lifeplanner.widget.WidgetDataSyncService
import az.tribe.lifeplanner.ui.GoalViewModel
import az.tribe.lifeplanner.ui.chat.ChatViewModel
import az.tribe.lifeplanner.ui.dependency.GoalDependencyViewModel
import az.tribe.lifeplanner.ui.gamification.GamificationViewModel
import az.tribe.lifeplanner.ui.habit.HabitViewModel
import az.tribe.lifeplanner.ui.journal.JournalViewModel
import az.tribe.lifeplanner.ui.backup.BackupViewModel
import az.tribe.lifeplanner.ui.balance.LifeBalanceViewModel
import az.tribe.lifeplanner.ui.coach.CoachViewModel
import az.tribe.lifeplanner.ui.reminder.ReminderViewModel
import az.tribe.lifeplanner.ui.review.ReviewViewModel
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
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val DB_NAME = "LifePlannerDB.db"

/**
 * Platform-specific function to create FirebaseTokenProvider
 */
expect fun createFirebaseTokenProvider(): FirebaseTokenProvider

/**
 * Platform-specific function to create AuthService
 */
expect fun createAuthService(): az.tribe.lifeplanner.data.auth.AuthService

val appModule = module {

    single { DatabaseDriverFactory() }
    single { SharedDatabase(get()) }
    single { Settings() }
    single<FileSharer> { createFileSharer() }
    single { WidgetDataSyncService() }

    // Firebase Token Provider (platform-specific)
    single<FirebaseTokenProvider> { createFirebaseTokenProvider() }

    // Auth Service (platform-specific)
    single<az.tribe.lifeplanner.data.auth.AuthService> { createAuthService() }

    // Repositories
    single<GeminiService> { GeminiServiceImpl(get(named("gemini"))) }
    single<GeminiRepository> { GeminiRepositoryImp(get()) }

    single<GoalRepository> { GoalRepositoryImpl(get(), get()) }
    single<GoalHistoryRepository> { GoalHistoryRepositoryImpl(get()) }
    single<GamificationRepository> { GamificationRepositoryImpl(get(), get()) }
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<HabitRepository> { HabitRepositoryImpl(get(), get()) }
    single<JournalRepository> { JournalRepositoryImpl(get()) }
    single<GoalDependencyRepository> { GoalDependencyRepositoryImpl(get()) }
    single<CoachRepository> { CoachRepositoryImpl(get()) }
    single<ChatRepository> { ChatRepositoryImpl(get(), get(named("gemini")), get()) }
    single<ReviewRepository> { ReviewRepositoryImpl(get(), get(named("gemini"))) }
    single<ReminderRepository> { ReminderRepositoryImpl(get()) }

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
    single<LifeBalanceRepository> { LifeBalanceRepositoryImpl(get(), get(), get(named("gemini"))) }

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
    viewModelOf(::ReviewViewModel)
    viewModelOf(::ReminderViewModel)
    viewModelOf(::LifeBalanceViewModel)
    viewModelOf(::BackupViewModel)
}