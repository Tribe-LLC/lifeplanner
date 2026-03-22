@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package az.tribe.lifeplanner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle
import platform.Foundation.NSURL

actual class CelebrationSoundPlayer {
    private var players = mutableMapOf<CelebrationType, AVAudioPlayer?>()
    private var loaded = false

    private val soundFiles = mapOf(
        CelebrationType.GOAL_COMPLETED to "celebration_goal_complete",
        CelebrationType.BADGE_UNLOCKED to "celebration_badge_unlock",
        CelebrationType.STREAK_MILESTONE to "celebration_streak",
        CelebrationType.LEVEL_UP to "celebration_level_up"
    )

    // Load sounds lazily — only when first celebration plays, not on app launch
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        soundFiles.forEach { (type, fileName) ->
            val path = NSBundle.mainBundle.pathForResource(fileName, ofType = "wav")
            if (path != null) {
                val url = NSURL.fileURLWithPath(path)
                val player = AVAudioPlayer(contentsOfURL = url, error = null)
                player?.prepareToPlay()
                players[type] = player
            }
        }
    }

    actual fun play(type: CelebrationType) {
        // Use Ambient category — mixes with Spotify/podcasts instead of stopping them
        try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryAmbient, error = null)
            session.setActive(true, error = null)
        } catch (_: Exception) { }

        ensureLoaded()
        val player = players[type] ?: return
        if (player.isPlaying()) {
            player.currentTime = 0.0
        }
        player.play()
    }

    actual fun release() {
        players.values.forEach { it?.stop() }
        players.clear()
        loaded = false
    }
}

@Composable
actual fun rememberCelebrationSoundPlayer(): CelebrationSoundPlayer {
    val player = remember { CelebrationSoundPlayer() }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    return player
}
