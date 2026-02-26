package az.tribe.lifeplanner.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.lastPathComponent
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberFilePicker(
    onResult: (FilePickerResult) -> Unit
): FilePicker {
    return remember {
        IOSFilePicker(onResult)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IOSFilePicker(
    private val onResult: (FilePickerResult) -> Unit
) : FilePicker {

    private var delegate: DocumentPickerDelegate? = null

    override fun launchFilePicker() {
        delegate = DocumentPickerDelegate(onResult)

        val documentPicker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypeJSON, UTTypePlainText)
        )
        documentPicker.delegate = delegate
        documentPicker.allowsMultipleSelection = false

        val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootViewController?.presentViewController(
            documentPicker,
            animated = true,
            completion = null
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private class DocumentPickerDelegate(
    private val onResult: (FilePickerResult) -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) {
            onResult(FilePickerResult.Cancelled)
            return
        }

        try {
            // Start accessing security-scoped resource
            val accessing = url.startAccessingSecurityScopedResource()

            val content = NSString.create(contentsOfURL = url, encoding = NSUTF8StringEncoding, error = null)
            val fileName = url.lastPathComponent ?: "backup.json"

            // Stop accessing security-scoped resource
            if (accessing) {
                url.stopAccessingSecurityScopedResource()
            }

            if (content != null) {
                onResult(FilePickerResult.Success(content.toString(), fileName))
            } else {
                onResult(FilePickerResult.Error("Failed to read file content"))
            }
        } catch (e: Exception) {
            onResult(FilePickerResult.Error(e.message ?: "Unknown error"))
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onResult(FilePickerResult.Cancelled)
    }
}
