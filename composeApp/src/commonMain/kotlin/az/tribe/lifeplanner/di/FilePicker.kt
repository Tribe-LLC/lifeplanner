package az.tribe.lifeplanner.di

import androidx.compose.runtime.Composable

/**
 * Platform-specific file picker result
 */
sealed class FilePickerResult {
    data class Success(val content: String, val fileName: String) : FilePickerResult()
    data class Error(val message: String) : FilePickerResult()
    data object Cancelled : FilePickerResult()
}

/**
 * Platform-specific file picker interface
 */
interface FilePicker {
    /**
     * Launch the file picker for JSON files
     */
    fun launchFilePicker()
}

/**
 * Creates a file picker and returns a launcher function.
 * Call the returned function to open the file picker.
 * Results will be delivered via the onResult callback.
 */
@Composable
expect fun rememberFilePicker(
    onResult: (FilePickerResult) -> Unit
): FilePicker
