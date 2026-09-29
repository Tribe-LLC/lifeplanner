package az.tribe.lifeplanner.di

import az.tribe.lifeplanner.ui.v4.life.V4LifeInsightsViewModel
import az.tribe.lifeplanner.data.integrations.IntegrationPrefs
import az.tribe.lifeplanner.data.repository.PlanAreasRepositoryImpl
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.PlanAreasRepository
import az.tribe.lifeplanner.ui.v4.areas.V4AreaViewModel
import az.tribe.lifeplanner.ui.v4.firstrun.V4FirstRunViewModel
import az.tribe.lifeplanner.ui.v4.habits.V4StartersViewModel
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
import az.tribe.lifeplanner.ui.v4.plans.PlanSheetViewModel
import az.tribe.lifeplanner.ui.v4.plans.V4PlanViewModel
import az.tribe.lifeplanner.ui.v4.quickadd.QuickAddViewModel
import az.tribe.lifeplanner.data.travel.TripWeather
import az.tribe.lifeplanner.domain.service.StreakPauses
import az.tribe.lifeplanner.domain.service.TripPlanner
import az.tribe.lifeplanner.ui.v4.travel.V4TravelViewModel
import az.tribe.lifeplanner.ui.v4.travel.V4TripViewModel
import az.tribe.lifeplanner.ui.v4.travel.V4TripRecapViewModel
import az.tribe.lifeplanner.data.meals.MealService
import az.tribe.lifeplanner.data.plans.PlanService
import az.tribe.lifeplanner.data.study.StudyService
import az.tribe.lifeplanner.ui.v4.areas.V4MealsViewModel
import az.tribe.lifeplanner.ui.v4.areas.V4StudyViewModel
import az.tribe.lifeplanner.data.habits.HabitService
import az.tribe.lifeplanner.domain.service.HabitStreakRules
import az.tribe.lifeplanner.ui.v4.areas.V4HabitsViewModel
import az.tribe.lifeplanner.ui.v4.habits.V4CheckInViewModel
import az.tribe.lifeplanner.ui.v4.habits.V4ReviewViewModel
import az.tribe.lifeplanner.data.mind.MindService
import az.tribe.lifeplanner.ui.v4.areas.V4MindViewModel
import az.tribe.lifeplanner.data.career.CareerService
import az.tribe.lifeplanner.ui.v4.areas.V4CareerViewModel
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
    single { az.tribe.lifeplanner.data.fitness.WorkoutWeekService(get(), get(), get(), get()) }
    single { TripWeather(get()) }
    single { PlanService(get(), get(), get(), get()) }
    single { az.tribe.lifeplanner.data.plans.PlanSpecs(get()) }
    single { az.tribe.lifeplanner.data.plans.PlanBoard(get(), get(), get(), get(), get(), get(), get()) }
    single { az.tribe.lifeplanner.data.plans.PlanMaker(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { az.tribe.lifeplanner.data.plans.PlanAutoTick(get(), get()) }
    single { az.tribe.lifeplanner.data.plans.PlanStepSuggester(get(), get()) }
    single { MealService(get(), get(), get(), get(), get(), get(), get()) }
    single { az.tribe.lifeplanner.data.meals.MealCoachService(get(), get()) }
    single { StudyService(get(), get(), get(), get()) }
    // Trip days with travel mode on do not break habit streaks.
    single<StreakPauses> {
        val trips = get<TripRepository>()
        StreakPauses { trips.getAll().filter { it.travelMode }.flatMap { TripPlanner.days(it) }.toSet() }
    }

    single { HabitService(get(), get(), get(), get(), get()) }
    single { az.tribe.lifeplanner.data.habits.NudgePrefs(get()) }
    single { az.tribe.lifeplanner.data.habits.NudgeService(get(), get()) }
    single { az.tribe.lifeplanner.data.life.LifeFactsService(get(), get(), get(), get(), get(), get(), get()) }
    single { MindService(get(), get(), get(), get(), get()) }
    single { CareerService(get(), get(), get(), get()) }
    // Resolved on each call, not here: HabitService needs the habit repository, which needs this.
    single<HabitStreakRules> { HabitStreakRules { habit -> get<HabitService>().rulesFor(habit) } }

    single { az.tribe.lifeplanner.data.habits.SelfTickService(get(), get(), get()) }
    single { az.tribe.lifeplanner.data.money.FxRates(get(), get(), get()) }
    single { az.tribe.lifeplanner.data.money.BillService(get()) }
    single { az.tribe.lifeplanner.ui.v4.today.TodayMoney(get(), get(), get(), get()) }
    single { az.tribe.lifeplanner.data.travel.BookingReader(get()) }
    single { az.tribe.lifeplanner.data.mind.MoodNudges(get(), get()) }

    viewModelOf(::V4FirstRunViewModel)
    // 24 dependencies, more than viewModelOf takes.
    viewModel { V4TodayViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModelOf(::V4CheckInViewModel)
    viewModelOf(::V4ReviewViewModel)
    viewModelOf(::V4LifeViewModel)
    viewModelOf(::QuickAddViewModel)
    viewModelOf(::PlanSheetViewModel)
    viewModel { params -> V4PlanViewModel(params.get<String>(), get(), get(), get(), get()) }
    viewModelOf(::V4MoneyViewModel)
    viewModelOf(::V4FitnessViewModel)
    viewModelOf(::V4TravelViewModel)
    viewModelOf(::V4MealsViewModel)
    viewModelOf(::V4StudyViewModel)
    viewModelOf(::V4HabitsViewModel)
    viewModelOf(::V4MindViewModel)
    viewModelOf(::V4CareerViewModel)
    viewModelOf(::V4StartersViewModel)
    viewModelOf(::V4LifeInsightsViewModel)
    viewModelOf(::V4TripRecapViewModel)
    viewModel { params -> V4TripViewModel(params.get<String>(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { params ->
        V4AreaViewModel(params.get<PlanArea>(), get(), get(), get(), get(), get(), get(), get(), get())
    }
}
