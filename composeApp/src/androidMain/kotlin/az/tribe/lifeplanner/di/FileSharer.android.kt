package az.tribe.lifeplanner.di

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

class AndroidFileSharer(private val context: Context) : FileSharer {

    override fun shareFile(content: String, fileName: String, mimeType: String) {
        try {
            // Write content to a temp file in cache directory
            val cacheDir = context.cacheDir
            val file = File(cacheDir, fileName)
            file.writeText(content)

            // Get URI using FileProvider
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            // Create share intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "LifePlanner Backup")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooserIntent = Intent.createChooser(shareIntent, "Share Backup")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun copyToClipboard(content: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("LifePlanner Backup", content)
        clipboard.setPrimaryClip(clip)
    }
}

private var appContext: Context? = null

fun initFileSharer(context: Context) {
    appContext = context.applicationContext
}

actual fun createFileSharer(): FileSharer {
    return AndroidFileSharer(appContext ?: throw IllegalStateException("FileSharer not initialized. Call initFileSharer() first."))
}
