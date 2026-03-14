@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package az.tribe.lifeplanner.ui.components

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToFile

actual fun getVideoCacheDir(): String {
    val paths = NSSearchPathForDirectoriesInDomains(
        NSCachesDirectory,
        NSUserDomainMask,
        true
    )
    val cacheDir = (paths.firstOrNull() as? String) ?: "/tmp"
    val videoDir = "$cacheDir/video"
    val fm = NSFileManager.defaultManager
    if (!fm.fileExistsAtPath(videoDir)) {
        fm.createDirectoryAtPath(videoDir, withIntermediateDirectories = true, attributes = null, error = null)
    }
    return videoDir
}

actual fun fileExists(path: String): Boolean =
    NSFileManager.defaultManager.fileExistsAtPath(path)

actual fun writeBytes(path: String, bytes: ByteArray) {
    val data = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    data.writeToFile(path, atomically = true)
}
