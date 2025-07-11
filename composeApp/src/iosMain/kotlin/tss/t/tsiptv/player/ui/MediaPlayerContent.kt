package tss.t.tsiptv.player.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVPlayerLayer
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import tss.t.tsiptv.player.IOSMediaPlayer
import tss.t.tsiptv.player.MediaPlayer

/**
 * iOS implementation of MediaPlayerContent.
 * Uses AVPlayerViewController to display the video content.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun MediaPlayerContent(
    player: MediaPlayer,
    modifier: Modifier,
) {
    // Cast to IOSMediaPlayer to access the AVPlayer
    val iosPlayer = player as? IOSMediaPlayer ?: return

    // Create a player view controller to display the video
    val playerViewController = remember { AVPlayerViewController() }

    // Set up the player view controller
    DisposableEffect(iosPlayer) {
        // Get the AVPlayer from the IOSMediaPlayer
        playerViewController.setPlayer(iosPlayer.getAVPlayer())

        onDispose {
            // Clean up
            playerViewController.setPlayer(null)
        }
    }

    // Use UIKitView to embed the AVPlayerViewController's view in Compose
    UIKitView(
        modifier = modifier.fillMaxSize(),
        factory = {
            // Create a container view
            val containerView = UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))

            // Add the player view controller's view to the container
            val playerView = playerViewController.view
            containerView.addSubview(playerView)

            // Configure the player layer
            (playerView.layer as? AVPlayerLayer)?.videoGravity = AVLayerVideoGravityResizeAspect

            // Position the player view to fill the container
            playerView.setFrame(containerView.bounds)
            playerView.setAutoresizingMask(UIViewAutoresizingFlexibleWidth.toULong() or UIViewAutoresizingFlexibleHeight.toULong())

            containerView
        },
        update = { view ->
            playerViewController.view.setFrame(view.bounds)
        },
        properties = remember {
            UIKitInteropProperties(
                isInteractive = true,
                isNativeAccessibilityEnabled = true
            )
        },
        onRelease = {
            playerViewController.setPlayer(null)
        }
    )
}
