package az.tribe.lifeplanner.ui.components

import androidx.compose.runtime.Composable

expect class CelebrationSoundPlayer {
    fun play(type: CelebrationType)
    fun release()
}

@Composable
expect fun rememberCelebrationSoundPlayer(): CelebrationSoundPlayer
