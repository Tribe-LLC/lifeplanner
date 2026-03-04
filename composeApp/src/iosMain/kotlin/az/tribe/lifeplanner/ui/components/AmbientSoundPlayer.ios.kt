@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package az.tribe.lifeplanner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import az.tribe.lifeplanner.domain.enum.AmbientSound
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle
import platform.Foundation.NSURL

actual class AmbientSoundPlayer {
    private var player: AVAudioPlayer? = null
    private var currentSound: AmbientSound = AmbientSound.NONE

    init {
        try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, error = null)
            session.setActive(true, error = null)
        } catch (_: Exception) { }
    }

    actual fun play(sound: AmbientSound) {
        if (sound == AmbientSound.NONE) {
            stop()
            return
        }

        if (sound == currentSound && player?.isPlaying() == true) return

        stop()

        val fileName = when (sound) {
            AmbientSound.RAIN -> "ambient_rain"
            AmbientSound.FOREST -> "ambient_forest"
            AmbientSound.LOFI -> "ambient_lofi"
            AmbientSound.WHITE_NOISE -> "ambient_white_noise"
            AmbientSound.NONE -> return
        }

        val path = NSBundle.mainBundle.pathForResource(fileName, ofType = "wav") ?: return
        val url = NSURL.fileURLWithPath(path)
        val audioPlayer = AVAudioPlayer(contentsOfURL = url, error = null) ?: return
        audioPlayer.numberOfLoops = -1 // Infinite loop
        audioPlayer.volume = 0.5f
        audioPlayer.prepareToPlay()
        audioPlayer.play()
        player = audioPlayer
        currentSound = sound
    }

    actual fun stop() {
        player?.stop()
        player = null
        currentSound = AmbientSound.NONE
    }

    actual fun release() {
        stop()
    }
}

@Composable
actual fun rememberAmbientSoundPlayer(): AmbientSoundPlayer {
    val player = remember { AmbientSoundPlayer() }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    return player
}
