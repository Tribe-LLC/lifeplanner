package az.tribe.lifeplanner.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import az.tribe.lifeplanner.R
import az.tribe.lifeplanner.domain.enum.AmbientSound

actual class AmbientSoundPlayer(private val context: Context) {
    private var currentPlayer: MediaPlayer? = null
    private var nextPlayer: MediaPlayer? = null
    private var currentSound: AmbientSound = AmbientSound.NONE

    actual fun play(sound: AmbientSound) {
        if (sound == AmbientSound.NONE) {
            stop()
            return
        }

        // If already playing the same sound, do nothing
        if (sound == currentSound && currentPlayer?.isPlaying == true) return

        // Stop any current playback
        stop()

        val resId = soundResId(sound) ?: return

        try {
            currentPlayer = createPlayer(resId)?.apply {
                // Prepare next player for gapless looping
                val next = createPlayer(resId)
                nextPlayer = next
                if (next != null) setNextMediaPlayer(next)
                start()
            }
            currentSound = sound

            // Chain: when the next player finishes, swap and prepare another
            setupLoopChain(resId)
        } catch (_: Exception) {
            // Audio playback failed silently
        }
    }

    /**
     * Create a MediaPlayer that mixes with other audio (won't pause Spotify, podcasts, etc.)
     */
    private fun createPlayer(resId: Int): MediaPlayer? {
        return MediaPlayer.create(context, resId)?.apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setVolume(0.3f, 0.3f)
        }
    }

    private fun setupLoopChain(resId: Int) {
        nextPlayer?.setOnCompletionListener {
            try {
                val old = currentPlayer
                currentPlayer = nextPlayer
                old?.release()

                val fresh = createPlayer(resId)
                nextPlayer = fresh
                if (fresh != null) currentPlayer?.setNextMediaPlayer(fresh)
                setupLoopChain(resId)
            } catch (_: Exception) { }
        }
        currentPlayer?.setOnCompletionListener {
            try {
                val old = currentPlayer
                currentPlayer = nextPlayer
                old?.release()

                val fresh = createPlayer(resId)
                nextPlayer = fresh
                if (fresh != null) currentPlayer?.setNextMediaPlayer(fresh)
                setupLoopChain(resId)
            } catch (_: Exception) { }
        }
    }

    private fun soundResId(sound: AmbientSound): Int? = when (sound) {
        AmbientSound.RAIN -> R.raw.ambient_rain
        AmbientSound.FOREST -> R.raw.ambient_forest
        AmbientSound.LOFI -> R.raw.ambient_lofi
        AmbientSound.WHITE_NOISE -> R.raw.ambient_white_noise
        AmbientSound.OCEAN -> R.raw.ambient_ocean
        AmbientSound.FIREPLACE -> R.raw.ambient_fireplace
        AmbientSound.NIGHT -> R.raw.ambient_night
        AmbientSound.CAFE -> R.raw.ambient_cafe
        AmbientSound.BIRDS -> R.raw.ambient_birds
        AmbientSound.NONE -> null
    }

    actual fun stop() {
        try {
            currentPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) { }
        try {
            nextPlayer?.release()
        } catch (_: Exception) { }
        currentPlayer = null
        nextPlayer = null
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
