package az.tribe.lifeplanner.ui.components

import az.tribe.lifeplanner.MainApplication
import java.io.File

actual fun getVideoCacheDir(): String {
    val dir = File(MainApplication.appContext.cacheDir, "video")
    if (!dir.exists()) dir.mkdirs()
    return dir.absolutePath
}

actual fun fileExists(path: String): Boolean = File(path).exists()

actual fun writeBytes(path: String, bytes: ByteArray) {
    File(path).writeBytes(bytes)
}
