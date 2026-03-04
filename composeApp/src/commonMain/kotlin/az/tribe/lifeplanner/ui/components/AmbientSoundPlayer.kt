package az.tribe.lifeplanner.ui.components

import androidx.compose.runtime.Composable
import az.tribe.lifeplanner.domain.enum.AmbientSound

expect class AmbientSoundPlayer {
    fun play(sound: AmbientSound)
    fun stop()
    fun release()
}

@Composable
expect fun rememberAmbientSoundPlayer(): AmbientSoundPlayer
