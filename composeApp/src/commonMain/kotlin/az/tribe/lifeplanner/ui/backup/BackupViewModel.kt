package az.tribe.lifeplanner.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.domain.model.ExportResult
import az.tribe.lifeplanner.domain.model.ImportResult
import az.tribe.lifeplanner.domain.repository.BackupRepository
import az.tribe.lifeplanner.domain.repository.MergeStrategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BackupUiState(
    val isLoading: Boolean = false,
    val lastBackupDate: String? = null,
    val exportedData: String? = null,
    val exportFileName: String? = null,
    val importResult: ImportResult? = null,
    val error: String? = null,
    val showExportSuccess: Boolean = false,
    val showImportDialog: Boolean = false,
    val showMergeStrategyDialog: Boolean = false,
    val pendingImportData: String? = null,
    val isAutoBackupEnabled: Boolean = false
)

class BackupViewModel(
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        loadLastBackupDate()
        loadAutoBackupSetting()
    }

    private fun loadLastBackupDate() {
        viewModelScope.launch {
            val lastBackup = backupRepository.getLastBackupDate()
            _uiState.value = _uiState.value.copy(lastBackupDate = lastBackup)
        }
    }

    private fun loadAutoBackupSetting() {
        val isEnabled = backupRepository.isAutoBackupEnabled()
        _uiState.value = _uiState.value.copy(isAutoBackupEnabled = isEnabled)
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        backupRepository.setAutoBackupEnabled(enabled)
        _uiState.value = _uiState.value.copy(isAutoBackupEnabled = enabled)
    }

    fun exportData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val result = backupRepository.exportData()) {
                is ExportResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        exportedData = result.jsonData,
                        exportFileName = result.fileName,
                        showExportSuccess = true
                    )
                    loadLastBackupDate()
                }
                is ExportResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message
                    )
                }
            }
        }
    }

    fun prepareImport(jsonData: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            when (val validation = backupRepository.validateBackup(jsonData)) {
                is az.tribe.lifeplanner.domain.repository.ValidationResult.Valid -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        pendingImportData = jsonData,
                        showMergeStrategyDialog = true
                    )
                }
                is az.tribe.lifeplanner.domain.repository.ValidationResult.Invalid -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = validation.reason
                    )
                }
            }
        }
    }

    fun importData(mergeStrategy: MergeStrategy) {
        val jsonData = _uiState.value.pendingImportData ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                showMergeStrategyDialog = false,
                error = null
            )

            when (val result = backupRepository.importData(jsonData, mergeStrategy)) {
                is ImportResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        importResult = result,
                        pendingImportData = null
                    )
                }
                is ImportResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message,
                        pendingImportData = null
                    )
                }
                is ImportResult.VersionMismatch -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Backup version (${result.backupVersion}) is newer than app version (${result.currentVersion}). Please update the app.",
                        pendingImportData = null
                    )
                }
            }
        }
    }

    fun showImportDialog() {
        _uiState.value = _uiState.value.copy(showImportDialog = true)
    }

    fun hideImportDialog() {
        _uiState.value = _uiState.value.copy(showImportDialog = false)
    }

    fun hideMergeStrategyDialog() {
        _uiState.value = _uiState.value.copy(
            showMergeStrategyDialog = false,
            pendingImportData = null
        )
    }

    fun clearExportData() {
        _uiState.value = _uiState.value.copy(
            exportedData = null,
            exportFileName = null,
            showExportSuccess = false
        )
    }

    fun clearImportResult() {
        _uiState.value = _uiState.value.copy(importResult = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun setError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }
}
