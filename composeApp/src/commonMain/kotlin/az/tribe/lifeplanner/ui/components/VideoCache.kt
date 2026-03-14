package az.tribe.lifeplanner.ui.components

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

expect fun getVideoCacheDir(): String
expect fun fileExists(path: String): Boolean
expect fun writeBytes(path: String, bytes: ByteArray)

fun String.toFileUri(): String = if (startsWith("/")) "file://$this" else this

object VideoCache {

    /** Returns the local file path if the video is already cached, null otherwise. */
    fun getCachedPath(url: String): String? {
        val filename = url.substringAfterLast('/').ifBlank { return null }
        val path = "${getVideoCacheDir()}/$filename"
        return if (fileExists(path)) path else null
    }

    /** Downloads a single video to local cache. Returns local path on success. */
    suspend fun cacheVideo(url: String, httpClient: HttpClient): String? {
        getCachedPath(url)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val filename = url.substringAfterLast('/')
                val targetPath = "${getVideoCacheDir()}/$filename"
                val bytes = httpClient.get(url).bodyAsBytes()
                writeBytes(targetPath, bytes)
                Logger.i("VideoCache") { "Cached video (${bytes.size} bytes) to $targetPath" }
                targetPath
            } catch (e: Exception) {
                Logger.e("VideoCache") { "Failed to cache video: ${e.message}" }
                null
            }
        }
    }

    /**
     * Resolves a list of remote URLs to local file:// URIs where possible.
     * Downloads any that aren't cached yet. Returns the resolved list.
     */
    /**
     * Resolves remote URLs to local file paths where possible.
     * Returns raw paths (not file:// URIs) — the video player library handles them correctly.
     * Falls back to remote URL if download fails (works on Android, may fail on iOS due to TLS).
     */
    suspend fun resolveUrls(urls: List<String>, httpClient: HttpClient): List<String> {
        return urls.map { url ->
            val cached = getCachedPath(url)
            if (cached != null) {
                Logger.d("VideoCache") { "Using cached: $cached" }
                cached
            } else {
                Logger.d("VideoCache") { "Downloading: $url" }
                val downloaded = cacheVideo(url, httpClient)
                if (downloaded != null) {
                    Logger.d("VideoCache") { "Downloaded to: $downloaded" }
                    downloaded
                } else {
                    Logger.w("VideoCache") { "Download failed, falling back to remote: $url" }
                    url
                }
            }
        }
    }
}
