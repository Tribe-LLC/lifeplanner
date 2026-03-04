package az.tribe.lifeplanner.ui.components

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import az.tribe.lifeplanner.R
import az.tribe.lifeplanner.domain.enum.AmbientSound

actual class AmbientSoundPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentSound: AmbientSound = AmbientSound.NONE

    actual fun play(sound: AmbientSound) {
        if (sound == AmbientSound.NONE) {
            stop()
            return
        }

        // If already playing the same sound, do nothing
        if (sound == currentSound && mediaPlayer?.isPlaying == true) return

        // Stop any current playback
        stop()

        val resId = when (sound) {
            AmbientSound.RAIN -> R.raw.ambient_rain
            AmbientSound.FOREST -> R.raw.ambient_forest
            AmbientSound.LOFI -> R.raw.ambient_lofi
            AmbientSound.WHITE_NOISE -> R.raw.ambient_white_noise
            AmbientSound.NONE -> return
        }

        try {
            mediaPlayer = MediaPlayer.create(context, resId)?.apply {
                isLooping = true
                setVolume(0.5f, 0.5f)
                start()
            }
            currentSound = sound
        } catch (_: Exception) {
            // Audio playback failed silently
        }
    }

    actual fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                reset()
                release()
            }
        } catch (_: Exception) { }
        mediaPlayer = null
        currentSound = AmbientSound.NONE
    }

    actual fun release() {
        stop()
    }
}

@Composable
actual fun rememberAmbientSoundPlayer(): AmbientSoundPlayer {
    val context = LocalContext.current
    val player = remember { AmbientSoundPlayer(context) }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    return player
}
