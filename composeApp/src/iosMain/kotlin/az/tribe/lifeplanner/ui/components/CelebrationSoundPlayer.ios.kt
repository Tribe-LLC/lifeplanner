@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package az.tribe.lifeplanner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle
import platform.Foundation.NSURL

actual class CelebrationSoundPlayer {
    private var players = mutableMapOf<CelebrationType, AVAudioPlayer?>()

    init {
        // Configure audio session
        try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, error = null)
            session.setActive(true, error = null)
        } catch (_: Exception) {
            // Audio session setup failed, sounds won't play
        }

        // Pre-load all celebration sounds
        val soundFiles = mapOf(
            CelebrationType.GOAL_COMPLETED to "celebration_goal_complete",
            CelebrationType.BADGE_UNLOCKED to "celebration_badge_unlock",
            CelebrationType.STREAK_MILESTONE to "celebration_streak",
            CelebrationType.LEVEL_UP to "celebration_level_up"
        )

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
        val player = players[type] ?: return
        if (player.isPlaying()) {
            player.currentTime = 0.0
        }
        player.play()
    }

    actual fun release() {
        players.values.forEach { it?.stop() }
        players.clear()
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
