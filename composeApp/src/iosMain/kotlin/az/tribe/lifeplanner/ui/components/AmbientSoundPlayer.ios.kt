@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package az.tribe.lifeplanner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import az.tribe.lifeplanner.domain.enum.AmbientSound
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle
import platform.Foundation.NSURL

actual class AmbientSoundPlayer {
    private var player: AVAudioPlayer? = null
    private var currentSound: AmbientSound = AmbientSound.NONE

    actual fun play(sound: AmbientSound) {
        if (sound == AmbientSound.NONE) {
            stop()
            return
        }

        if (sound == currentSound && player?.isPlaying() == true) return

        stop()

        // Use Ambient category so other audio (Spotify, podcasts) keeps playing
        try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(
                AVAudioSessionCategoryAmbient,
                error = null
            )
            session.setActive(true, error = null)
        } catch (_: Exception) { }

        val fileName = when (sound) {
            AmbientSound.RAIN -> "ambient_rain"
            AmbientSound.FOREST -> "ambient_forest"
            AmbientSound.LOFI -> "ambient_lofi"
            AmbientSound.WHITE_NOISE -> "ambient_white_noise"
            AmbientSound.OCEAN -> "ambient_ocean"
            AmbientSound.FIREPLACE -> "ambient_fireplace"
            AmbientSound.NIGHT -> "ambient_night"
            AmbientSound.CAFE -> "ambient_cafe"
            AmbientSound.BIRDS -> "ambient_birds"
            AmbientSound.NONE -> return
        }

        val path = NSBundle.mainBundle.pathForResource(fileName, ofType = "wav") ?: return
        val url = NSURL.fileURLWithPath(path)
        val audioPlayer = AVAudioPlayer(contentsOfURL = url, error = null) ?: return
        audioPlayer.numberOfLoops = -1 // Infinite loop
        audioPlayer.volume = 0.3f
        audioPlayer.prepareToPlay()
        audioPlayer.play()
        player = audioPlayer
        currentSound = sound
    }

    actual fun stop() {
        player?.stop()
        player = null
        currentSound = AmbientSound.NONE
        // Deactivate audio session so other apps can reclaim audio
        try {
            AVAudioSession.sharedInstance().setActive(false, error = null)
        } catch (_: Exception) { }
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
