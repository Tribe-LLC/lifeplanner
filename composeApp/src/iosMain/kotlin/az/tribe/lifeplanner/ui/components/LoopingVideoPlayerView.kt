@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package az.tribe.lifeplanner.ui.components

import co.touchlab.kermit.Logger
import platform.AVFoundation.*
import platform.Foundation.NSURL
import platform.Foundation.NSKeyValueObservingOptionNew
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView

class LoopingVideoPlayerView(private val url: NSURL) {
    private val player = AVQueuePlayer()
    private val templateItem = AVPlayerItem(uRL = url)

    @Suppress("unused") // must be retained to keep the loop alive
    private val looper = AVPlayerLooper.playerLooperWithPlayer(player, templateItem = templateItem)

    private val playerLayer = AVPlayerLayer.playerLayerWithPlayer(player).apply {
        setVideoGravity(AVLayerVideoGravityResizeAspectFill)
    }

    val view: UIView = UIView().apply {
        clipsToBounds = true
        backgroundColor = platform.UIKit.UIColor.blackColor
        layer.addSublayer(playerLayer)
    }

    fun play() {
        player.setMuted(true)
        player.setAutomaticallyWaitsToMinimizeStalling(false)
        player.play()
        Logger.d("VideoPlayer") { "iOS play() called, url=$url, items=${player.items().size}, status=${player.status}" }
    }

    fun stop() {
        player.pause()
    }

    fun updateLayout() {
        CATransaction.begin()
        CATransaction.setValue(true, forKey = kCATransactionDisableActions)
        playerLayer.setFrame(view.bounds)
        CATransaction.commit()
    }
}
