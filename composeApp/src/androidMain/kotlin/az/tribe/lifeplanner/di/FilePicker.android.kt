package az.tribe.lifeplanner.di

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger

@Composable
actual fun rememberFilePicker(
    onResult: (FilePickerResult) -> Unit
): FilePicker {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            onResult(FilePickerResult.Cancelled)
            return@rememberLauncherForActivityResult
        }

        try {
            val content = readFileContent(context, uri)
            val fileName = getFileName(context, uri) ?: "backup.json"

            if (content != null) {
                onResult(FilePickerResult.Success(content, fileName))
            } else {
                onResult(FilePickerResult.Error("Failed to read file content"))
            }
        } catch (e: Exception) {
            onResult(FilePickerResult.Error(e.message ?: "Unknown error"))
        }
    }

    return remember(launcher) {
        object : FilePicker {
            override fun launchFilePicker() {
                launcher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
        }
    }
}

private fun readFileContent(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader().readText()
        }
    } catch (e: Exception) {
        Logger.e("FilePicker") { "Failed to read file content: ${e.message}" }
        null
    }
}

private fun getFileName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    cursor.getString(nameIndex)
                } else null
            } else null
        }
    } catch (e: Exception) {
        null
    }
}
