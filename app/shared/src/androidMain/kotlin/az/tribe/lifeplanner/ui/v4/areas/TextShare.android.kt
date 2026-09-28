package az.tribe.lifeplanner.ui.v4.areas

import android.content.ComponentName
import android.content.Intent
import az.tribe.lifeplanner.MainActivity
import az.tribe.lifeplanner.MainApplication
import co.touchlab.kermit.Logger

internal actual fun shareText(text: String, title: String) {
    try {
        val context = MainApplication.appContext
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        val chooser = Intent.createChooser(send, title).apply {
            // LifePlanner takes shared job ads itself; it should not offer itself here.
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Logger.e("TextShare") { "Could not share: ${e.message}" }
    }
}
