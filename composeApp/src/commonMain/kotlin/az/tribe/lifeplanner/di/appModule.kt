package az.tribe.lifeplanner.di

import az.tribe.lifeplanner.data.network.GeminiService
import az.tribe.lifeplanner.data.network.GeminiServiceImpl
import az.tribe.lifeplanner.data.repository.GamificationRepositoryImpl
import az.tribe.lifeplanner.data.repository.GeminiRepositoryImp
import az.tribe.lifeplanner.data.repository.GoalHistoryRepositoryImpl
import az.tribe.lifeplanner.data.repository.GoalRepositoryImpl
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import az.tribe.lifeplanner.domain.repository.GeminiRepository
import az.tribe.lifeplanner.domain.repository.GoalHistoryRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import az.tribe.lifeplanner.ui.GoalViewModel
import az.tribe.lifeplanner.ui.gamification.GamificationViewModel
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
import org.koin.compose.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

const val DB_NAME = "LifePlannerDB.db"

val appModule = module {

    single { DatabaseDriverFactory() }
    single { SharedDatabase(get()) }

    // Repositories
    single<GeminiService> { GeminiServiceImpl(get()) }
    single<GeminiRepository> { GeminiRepositoryImp(get()) }

    single<GoalRepository> { GoalRepositoryImpl(get()) }
    single<GoalHistoryRepository> { GoalHistoryRepositoryImpl(get()) }
    single<GamificationRepository> { GamificationRepositoryImpl(get()) }

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


    // ViewModels

    viewModelOf(::GoalViewModel)
    viewModelOf(::GamificationViewModel)
}