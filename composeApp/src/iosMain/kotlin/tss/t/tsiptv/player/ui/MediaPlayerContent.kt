package tss.t.tsiptv.player.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVPlayerLayer
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import tss.t.tsiptv.player.IOSMediaPlayer
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.models.PlaybackState

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

    // Get the current playback state
    val playbackState by iosPlayer.playbackState.collectAsState()

    // Track whether the app is in background
    var isInBackground by remember { mutableStateOf(false) }

    // Create a player view controller to display the video
    val playerViewController = remember { AVPlayerViewController() }

    // Set up app lifecycle observers
    DisposableEffect(Unit) {
        val notificationCenter = NSNotificationCenter.defaultCenter

        // Observer for when app enters background
        val backgroundObserver = notificationCenter.addObserverForName(
            UIApplicationDidEnterBackgroundNotification,
            null,
            null
        ) { _ ->
            println("MediaPlayerContent: App entered background")
            isInBackground = true
        }

        // Observer for when app enters foreground
        val foregroundObserver = notificationCenter.addObserverForName(
            UIApplicationWillEnterForegroundNotification,
            null,
            null
        ) { _ ->
            println("MediaPlayerContent: App will enter foreground")
            isInBackground = false
        }

        onDispose {
            // Remove observers
            notificationCenter.removeObserver(backgroundObserver)
            notificationCenter.removeObserver(foregroundObserver)
        }
    }

    // Set up the player view controller
    DisposableEffect(iosPlayer) {
        // Get the AVPlayer from the IOSMediaPlayer
        playerViewController.setPlayer(iosPlayer.getAVPlayer())

        onDispose {
            // Clean up
            playerViewController.setPlayer(null)
        }
    }

    // Only show the player UI when the app is in foreground
    if (!isInBackground) {
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
}
