package az.tribe.lifeplanner.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import az.tribe.lifeplanner.R

actual class CelebrationSoundPlayer(private val context: Context) {
    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<CelebrationType, Int>()
    private var loaded = false

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(audioAttributes)
            .build().also { pool ->
                pool.setOnLoadCompleteListener { _, _, status ->
                    if (status == 0) loaded = true
                }
                soundIds[CelebrationType.GOAL_COMPLETED] = pool.load(context, R.raw.celebration_goal_complete, 1)
                soundIds[CelebrationType.BADGE_UNLOCKED] = pool.load(context, R.raw.celebration_badge_unlock, 1)
                soundIds[CelebrationType.STREAK_MILESTONE] = pool.load(context, R.raw.celebration_streak, 1)
                soundIds[CelebrationType.LEVEL_UP] = pool.load(context, R.raw.celebration_level_up, 1)
            }
    }

    actual fun play(type: CelebrationType) {
        val soundId = soundIds[type] ?: return
        soundPool?.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    actual fun release() {
        soundPool?.release()
        soundPool = null
        soundIds.clear()
    }
}

@Composable
actual fun rememberCelebrationSoundPlayer(): CelebrationSoundPlayer {
    val context = LocalContext.current
    val player = remember { CelebrationSoundPlayer(context) }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    return player
}
