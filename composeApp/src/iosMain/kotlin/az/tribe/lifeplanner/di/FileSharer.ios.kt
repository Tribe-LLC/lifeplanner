package az.tribe.lifeplanner.di

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard

@OptIn(ExperimentalForeignApi::class)
class IOSFileSharer : FileSharer {

    override fun shareFile(content: String, fileName: String, mimeType: String) {
        try {
            // Write to temp file
            val tempDir = NSTemporaryDirectory()
            val filePath = "$tempDir$fileName"

            (content as NSString).writeToFile(
                filePath,
                atomically = true,
                encoding = NSUTF8StringEncoding,
                error = null
            )

            val fileUrl = NSURL.fileURLWithPath(filePath)

            // Create activity view controller
            val activityViewController = UIActivityViewController(
                activityItems = listOf(fileUrl),
                applicationActivities = null
            )

            // Present the share sheet
            val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
            rootViewController?.presentViewController(
                activityViewController,
                animated = true,
                completion = null
            )
        } catch (e: Exception) {
            println("Error sharing file: ${e.message}")
        }
    }

    override fun copyToClipboard(content: String) {
        UIPasteboard.generalPasteboard.string = content
    }
}

actual fun createFileSharer(): FileSharer {
    return IOSFileSharer()
}
