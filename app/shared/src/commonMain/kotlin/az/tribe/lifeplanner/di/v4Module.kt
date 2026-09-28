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
import az.tribe.lifeplanner.ui.v4.quickadd.QuickAddViewModel
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

    viewModelOf(::V4FirstRunViewModel)
    viewModelOf(::V4TodayViewModel)
    viewModelOf(::V4LifeViewModel)
    viewModelOf(::QuickAddViewModel)
    viewModelOf(::V4MoneyViewModel)
    viewModel { params ->
        V4AreaViewModel(params.get<PlanArea>(), get(), get(), get(), get(), get(), get())
    }
}
