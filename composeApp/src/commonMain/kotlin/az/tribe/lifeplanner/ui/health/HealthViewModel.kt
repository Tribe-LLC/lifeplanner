package az.tribe.lifeplanner.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.health.HealthDataManager
import az.tribe.lifeplanner.data.mapper.createManualHealthMetric
import az.tribe.lifeplanner.domain.enum.HealthMetricType
import az.tribe.lifeplanner.domain.model.HealthMetric
import az.tribe.lifeplanner.domain.repository.HealthRepository
import az.tribe.lifeplanner.usecases.health.SyncHealthDataUseCase
import az.tribe.lifeplanner.data.analytics.Analytics
import az.tribe.lifeplanner.di.getPlatform
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class HealthViewModel(
    private val healthRepository: HealthRepository,
    private val syncHealthDataUseCase: SyncHealthDataUseCase,
    private val healthDataManager: HealthDataManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _permissionState = MutableStateFlow(HealthPermissionState.UNKNOWN)
    val permissionState: StateFlow<HealthPermissionState> = _permissionState.asStateFlow()

    private val _showWeightDialog = MutableStateFlow(false)
    val showWeightDialog: StateFlow<Boolean> = _showWeightDialog.asStateFlow()

    val todaySteps: StateFlow<Long?> = healthRepository.observeMetricsByType(HealthMetricType.STEPS)
        .map { metrics ->
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            metrics.find { it.date == today }?.value?.toLong()
        }
        .catch { e ->
            Logger.w("HealthViewModel") { "Steps flow error: ${e.message}" }
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val stepsHistory: StateFlow<List<HealthMetric>> = healthRepository.observeMetricsByType(HealthMetricType.STEPS)
        .map { metrics -> metrics.sortedBy { it.date }.takeLast(7) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weightHistory: StateFlow<List<HealthMetric>> = healthRepository.observeMetricsByType(HealthMetricType.WEIGHT)
        .map { metrics -> metrics.sortedBy { it.date } }
        .onEach { _isLoading.value = false }
        .catch { e ->
            Logger.w("HealthViewModel") { "Weight flow error: ${e.message}" }
            _isLoading.value = false
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestWeight: StateFlow<Double?> = healthRepository.observeMetricsByType(HealthMetricType.WEIGHT)
        .map { metrics -> metrics.maxByOrNull { it.date }?.value }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val heartRateHistory: StateFlow<List<HealthMetric>> = healthRepository.observeMetricsByType(HealthMetricType.HEART_RATE)
        .map { metrics -> metrics.sortedBy { it.date }.takeLast(7) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestHeartRate: StateFlow<Double?> = healthRepository.observeMetricsByType(HealthMetricType.HEART_RATE)
        .map { metrics -> metrics.maxByOrNull { it.date }?.value }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val sleepHistory: StateFlow<List<HealthMetric>> = healthRepository.observeMetricsByType(HealthMetricType.SLEEP)
        .map { metrics -> metrics.sortedBy { it.date }.takeLast(7) }
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestSleep: StateFlow<Double?> = healthRepository.observeMetricsByType(HealthMetricType.SLEEP)
        .map { metrics -> metrics.maxByOrNull { it.date }?.value }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        Analytics.healthDashboardViewed()
        checkPermissionsAndSync()
    }

    private fun checkPermissionsAndSync() {
        viewModelScope.launch {
            try {
                val available = healthDataManager.isAvailable()
                Logger.d("HealthViewModel") { "Health available: $available" }
                if (!available) {
                    _permissionState.value = HealthPermissionState.NOT_AVAILABLE
                    _isLoading.value = false
                    return@launch
                }

                val hasPerms = healthDataManager.hasPermissions()
                Logger.d("HealthViewModel") { "Has permissions: $hasPerms" }
                _permissionState.value = if (hasPerms) HealthPermissionState.GRANTED else HealthPermissionState.DENIED

                if (hasPerms) {
                    syncHealth()
                } else {
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                Logger.w("HealthViewModel") { "Permission check failed: ${e.message}" }
                _permissionState.value = HealthPermissionState.NOT_AVAILABLE
                _isLoading.value = false
            }
        }
    }

    fun syncHealth() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                syncHealthDataUseCase()
            } catch (e: Exception) {
                _error.value = "Failed to sync health data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onPermissionsGranted() {
        _permissionState.value = HealthPermissionState.GRANTED
        Analytics.healthPermissionGranted(getPlatform().name)
        syncHealth()
    }

    fun showAddWeightDialog() {
        _showWeightDialog.value = true
    }

    fun dismissWeightDialog() {
        _showWeightDialog.value = false
    }

    fun addManualWeight(weightKg: Double) {
        viewModelScope.launch {
            try {
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                val metric = createManualHealthMetric(
                    metricType = HealthMetricType.WEIGHT,
                    value = weightKg,
                    date = today
                )
                healthRepository.insertMetric(metric)
                Analytics.healthWeightAdded()
                _showWeightDialog.value = false
            } catch (e: Exception) {
                _error.value = "Failed to save weight: ${e.message}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

enum class HealthPermissionState {
    UNKNOWN,
    GRANTED,
    DENIED,
    NOT_AVAILABLE
}
