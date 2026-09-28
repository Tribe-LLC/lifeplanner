package az.tribe.lifeplanner.di

import az.tribe.lifeplanner.data.integrations.IntegrationPrefs
import az.tribe.lifeplanner.data.repository.PlanAreasRepositoryImpl
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.PlanAreasRepository
import az.tribe.lifeplanner.ui.v4.areas.V4AreaViewModel
import az.tribe.lifeplanner.ui.v4.firstrun.V4FirstRunViewModel
import az.tribe.lifeplanner.ui.v4.life.V4LifeViewModel
import az.tribe.lifeplanner.ui.v4.today.V4TodayViewModel
import az.tribe.lifeplanner.core.CurrencyPrefs
import az.tribe.lifeplanner.data.repository.BudgetRepositoryImpl
import az.tribe.lifeplanner.data.repository.LifeLogRepositoryImpl
import az.tribe.lifeplanner.data.repository.TripRepositoryImpl
import az.tribe.lifeplanner.domain.repository.BudgetRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.repository.TripRepository
import az.tribe.lifeplanner.ui.v4.areas.V4MoneyViewModel
import az.tribe.lifeplanner.ui.v4.areas.V4FitnessViewModel
import az.tribe.lifeplanner.data.fitness.WorkoutService
import az.tribe.lifeplanner.ui.v4.quickadd.QuickAddViewModel
import az.tribe.lifeplanner.data.travel.TripWeather
import az.tribe.lifeplanner.domain.service.StreakPauses
import az.tribe.lifeplanner.domain.service.TripPlanner
import az.tribe.lifeplanner.ui.v4.travel.V4TravelViewModel
import az.tribe.lifeplanner.ui.v4.travel.V4TripViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Everything v4 adds. Kept apart from appModule so the redesign is easy to find and to switch off. */
val v4Module = module {
    single<PlanAreasRepository> { PlanAreasRepositoryImpl(get()) }
    single { IntegrationPrefs(get()) }
    single { CurrencyPrefs(get()) }
    single<LifeLogRepository> { LifeLogRepositoryImpl(get(), get()) }
    single<BudgetRepository> { BudgetRepositoryImpl(get(), get()) }
    single<TripRepository> { TripRepositoryImpl(get(), get()) }
    single { WorkoutService(get(), get(), get(), get(), get()) }
    single { TripWeather(get()) }
    // Trip days with travel mode on do not break habit streaks.
    single<StreakPauses> {
        val trips = get<TripRepository>()
        StreakPauses { trips.getAll().filter { it.travelMode }.flatMap { TripPlanner.days(it) }.toSet() }
    }

    viewModelOf(::V4FirstRunViewModel)
    viewModelOf(::V4TodayViewModel)
    viewModelOf(::V4LifeViewModel)
    viewModelOf(::QuickAddViewModel)
    viewModelOf(::V4MoneyViewModel)
    viewModelOf(::V4FitnessViewModel)
    viewModelOf(::V4TravelViewModel)
    viewModel { params -> V4TripViewModel(params.get<String>(), get(), get(), get(), get(), get(), get()) }
    viewModel { params ->
        V4AreaViewModel(params.get<PlanArea>(), get(), get(), get(), get(), get(), get())
    }
}
