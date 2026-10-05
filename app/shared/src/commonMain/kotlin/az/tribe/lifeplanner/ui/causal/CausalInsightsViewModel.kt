package az.tribe.lifeplanner.ui.causal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.core.PremiumGate
import az.tribe.lifeplanner.domain.model.CausalInsight
import az.tribe.lifeplanner.domain.service.CalibrationProvider
import az.tribe.lifeplanner.domain.service.Calibration
import az.tribe.lifeplanner.domain.service.CausalInsightProvider
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Pillar 4, drives the Causal Insights screen: correlations + spirals + the calibration stat. */
class CausalInsightsViewModel(
    private val causalProvider: CausalInsightProvider,
    private val calibrationProvider: CalibrationProvider,
    private val premiumGate: PremiumGate,
) : ViewModel() {

    data class State(
        val isLoading: Boolean = true,
        val isPremium: Boolean = true,
        val insights: List<CausalInsight> = emptyList(),
        val calibration: Calibration? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val premium = premiumGate.isPremium()
            if (!premium) {
                _state.value = State(isLoading = false, isPremium = false)
                return@launch
            }
            _state.value = State(isLoading = true, isPremium = true)
            val insights = runCatching { causalProvider.insights() }
                .getOrElse { Logger.w("CausalInsightsVM") { "insights failed: ${it.message}" }; emptyList() }
            val calibration = runCatching { calibrationProvider.calibration() }
                .getOrElse { Logger.w("CausalInsightsVM") { "calibration failed: ${it.message}" }; null }
            _state.value = State(
                isLoading = false,
                isPremium = true,
                insights = insights,
                calibration = calibration
            )
        }
    }
}
