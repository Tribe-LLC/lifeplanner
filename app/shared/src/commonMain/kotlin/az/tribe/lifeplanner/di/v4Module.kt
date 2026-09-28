package az.tribe.lifeplanner.di

import az.tribe.lifeplanner.data.integrations.IntegrationPrefs
import az.tribe.lifeplanner.data.repository.PlanAreasRepositoryImpl
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.PlanAreasRepository
import az.tribe.lifeplanner.ui.v4.areas.V4AreaViewModel
import az.tribe.lifeplanner.ui.v4.firstrun.V4FirstRunViewModel
import az.tribe.lifeplanner.ui.v4.life.V4LifeViewModel
import az.tribe.lifeplanner.ui.v4.today.V4TodayViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Everything v4 adds. Kept apart from appModule so the redesign is easy to find and to switch off. */
val v4Module = module {
    single<PlanAreasRepository> { PlanAreasRepositoryImpl(get()) }
    single { IntegrationPrefs(get()) }

    viewModelOf(::V4FirstRunViewModel)
    viewModelOf(::V4TodayViewModel)
    viewModelOf(::V4LifeViewModel)
    viewModel { params ->
        V4AreaViewModel(params.get<PlanArea>(), get(), get(), get(), get(), get(), get())
    }
}
