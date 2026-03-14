package az.tribe.lifeplanner.ui.backup

import app.cash.turbine.test
import az.tribe.lifeplanner.domain.model.ExportResult
import az.tribe.lifeplanner.domain.model.ImportResult
import az.tribe.lifeplanner.domain.repository.MergeStrategy
import az.tribe.lifeplanner.domain.repository.ValidationResult
import az.tribe.lifeplanner.testutil.FakeBackupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    private lateinit var viewModel: BackupViewModel
    private lateinit var fakeRepository: FakeBackupRepository
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeBackupRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): BackupViewModel {
        return BackupViewModel(backupRepository = fakeRepository)
    }

    @Test
    fun `init loads last backup date`() = runTest(testDispatcher) {
        fakeRepository.lastBackupDate = "2026-03-06T10:00:00"
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("2026-03-06T10:00:00", viewModel.uiState.value.lastBackupDate)
    }

    @Test
    fun `init loads auto backup setting`() = runTest(testDispatcher) {
        fakeRepository.setAutoBackupEnabled(true)
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAutoBackupEnabled)
    }

    @Test
    fun `init with no last backup date`() = runTest(testDispatcher) {
        fakeRepository.lastBackupDate = null
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.lastBackupDate)
    }

    @Test
    fun `exportData success sets exported data and filename`() = runTest(testDispatcher) {
        fakeRepository.exportResult = ExportResult.Success(
            jsonData = """{"goals":[]}""",
            fileName = "backup-2026-03-06.json"
        )
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.exportData()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("""{"goals":[]}""", state.exportedData)
        assertEquals("backup-2026-03-06.json", state.exportFileName)
        assertTrue(state.showExportSuccess)
    }

    @Test
    fun `exportData error sets error message`() = runTest(testDispatcher) {
        fakeRepository.exportResult = ExportResult.Error("Export failed")
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.exportData()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Export failed", state.error)
        assertNull(state.exportedData)
    }

    @Test
    fun `prepareImport with valid data shows merge strategy dialog`() = runTest(testDispatcher) {
        fakeRepository.validationResult = ValidationResult.Valid(
            az.tribe.lifeplanner.domain.model.BackupData(
                createdAt = "2026-03-06T10:00:00",
                goals = emptyList(),
                milestones = emptyList(),
                habits = emptyList(),
                habitCompletions = emptyList(),
                journalEntries = emptyList(),
                userProgress = null,
                badges = emptyList(),
                challenges = emptyList(),
                settings = null
            )
        )
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.prepareImport("""{"valid":"json"}""")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.showMergeStrategyDialog)
        assertNotNull(state.pendingImportData)
    }

    @Test
    fun `prepareImport with invalid data sets error`() = runTest(testDispatcher) {
        fakeRepository.validationResult = ValidationResult.Invalid("Invalid format")
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.prepareImport("bad json")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Invalid format", state.error)
        assertFalse(state.showMergeStrategyDialog)
    }

    @Test
    fun `importData success sets import result`() = runTest(testDispatcher) {
        fakeRepository.validationResult = ValidationResult.Valid(
            az.tribe.lifeplanner.domain.model.BackupData(
                createdAt = "2026-03-06T10:00:00",
                goals = emptyList(),
                milestones = emptyList(),
                habits = emptyList(),
                habitCompletions = emptyList(),
                journalEntries = emptyList(),
                userProgress = null,
                badges = emptyList(),
                challenges = emptyList(),
                settings = null
            )
        )
        fakeRepository.importResult = ImportResult.Success(
            goalsImported = 5,
            habitsImported = 3,
            journalEntriesImported = 10
        )
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.prepareImport("""{"valid":"json"}""")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.importData(MergeStrategy.SKIP_EXISTING)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.importResult)
        assertTrue(state.importResult is ImportResult.Success)
        assertNull(state.pendingImportData)
        assertFalse(state.showMergeStrategyDialog)
    }

    @Test
    fun `importData error sets error message`() = runTest(testDispatcher) {
        fakeRepository.validationResult = ValidationResult.Valid(
            az.tribe.lifeplanner.domain.model.BackupData(
                createdAt = "2026-03-06T10:00:00",
                goals = emptyList(),
                milestones = emptyList(),
                habits = emptyList(),
                habitCompletions = emptyList(),
                journalEntries = emptyList(),
                userProgress = null,
                badges = emptyList(),
                challenges = emptyList(),
                settings = null
            )
        )
        fakeRepository.importResult = ImportResult.Error("Import failed")
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.prepareImport("""{"valid":"json"}""")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.importData(MergeStrategy.OVERWRITE_EXISTING)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Import failed", state.error)
        assertNull(state.pendingImportData)
    }

    @Test
    fun `importData version mismatch sets descriptive error`() = runTest(testDispatcher) {
        fakeRepository.validationResult = ValidationResult.Valid(
            az.tribe.lifeplanner.domain.model.BackupData(
                createdAt = "2026-03-06T10:00:00",
                goals = emptyList(),
                milestones = emptyList(),
                habits = emptyList(),
                habitCompletions = emptyList(),
                journalEntries = emptyList(),
                userProgress = null,
                badges = emptyList(),
                challenges = emptyList(),
                settings = null
            )
        )
        fakeRepository.importResult = ImportResult.VersionMismatch(
            backupVersion = 5,
            currentVersion = 1
        )
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.prepareImport("""{"valid":"json"}""")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.importData(MergeStrategy.KEEP_NEWEST)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("5"))
        assertTrue(state.error!!.contains("1"))
    }

    @Test
    fun `importData does nothing when no pending import data`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.importData(MergeStrategy.SKIP_EXISTING)
        testDispatcher.scheduler.advanceUntilIdle()

        // Should not crash, state should remain unchanged
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.importResult)
    }

    @Test
    fun `setAutoBackupEnabled updates repository and state`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAutoBackupEnabled)

        viewModel.setAutoBackupEnabled(true)
        assertTrue(viewModel.uiState.value.isAutoBackupEnabled)
        assertTrue(fakeRepository.isAutoBackupEnabled())

        viewModel.setAutoBackupEnabled(false)
        assertFalse(viewModel.uiState.value.isAutoBackupEnabled)
        assertFalse(fakeRepository.isAutoBackupEnabled())
    }

    @Test
    fun `showImportDialog sets flag to true`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showImportDialog)
        viewModel.showImportDialog()
        assertTrue(viewModel.uiState.value.showImportDialog)
    }

    @Test
    fun `hideImportDialog sets flag to false`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.showImportDialog()
        viewModel.hideImportDialog()
        assertFalse(viewModel.uiState.value.showImportDialog)
    }

    @Test
    fun `hideMergeStrategyDialog clears pending data and hides dialog`() = runTest(testDispatcher) {
        fakeRepository.validationResult = ValidationResult.Valid(
            az.tribe.lifeplanner.domain.model.BackupData(
                createdAt = "2026-03-06T10:00:00",
                goals = emptyList(),
                milestones = emptyList(),
                habits = emptyList(),
                habitCompletions = emptyList(),
                journalEntries = emptyList(),
                userProgress = null,
                badges = emptyList(),
                challenges = emptyList(),
                settings = null
            )
        )
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.prepareImport("""{"data":"here"}""")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showMergeStrategyDialog)

        viewModel.hideMergeStrategyDialog()
        assertFalse(viewModel.uiState.value.showMergeStrategyDialog)
        assertNull(viewModel.uiState.value.pendingImportData)
    }

    @Test
    fun `clearExportData resets export state`() = runTest(testDispatcher) {
        fakeRepository.exportResult = ExportResult.Success("{}", "backup.json")
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.exportData()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.exportedData)

        viewModel.clearExportData()
        assertNull(viewModel.uiState.value.exportedData)
        assertNull(viewModel.uiState.value.exportFileName)
        assertFalse(viewModel.uiState.value.showExportSuccess)
    }

    @Test
    fun `clearImportResult resets import result`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.clearImportResult()
        assertNull(viewModel.uiState.value.importResult)
    }

    @Test
    fun `clearError resets error`() = runTest(testDispatcher) {
        fakeRepository.exportResult = ExportResult.Error("some error")
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.exportData()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)

        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `setError sets error message`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setError("Custom error")
        assertEquals("Custom error", viewModel.uiState.value.error)
    }
}
