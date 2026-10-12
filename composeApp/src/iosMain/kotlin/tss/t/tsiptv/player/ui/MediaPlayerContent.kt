package tss.t.tsiptv.player.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import platform.AVKit.AVPlayerViewControllerDelegateProtocol
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.darwin.NSObject
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
    val iosPlayer = remember(player) {
        player as IOSMediaPlayer
    }

    // Get the current playback state
    val playbackState by iosPlayer.playbackState.collectAsState()
    val mediaItem by iosPlayer.currentMedia.collectAsState()
    // Clear DASH plays on the registered engine (VLCKit): show its view instead of AVPlayer's.
    val engineView by iosPlayer.engineView.collectAsState()

    // Track whether the app is in background
    var isInBackground by remember { mutableStateOf(false) }

    val pipDelegate = remember { PictureInPictureDelegate() }

    // Video surface only: the shared Compose controls drive playback, as on Android.
    val playerViewController = remember {
        AVPlayerViewController().apply {
            delegate = pipDelegate
            showsPlaybackControls = false
            updatesNowPlayingInfoCenter = true
            // Leaving the app continues in Picture in Picture, like Android's PiP.
            canStartPictureInPictureAutomaticallyFromInline = true
        }
    }

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

    val dashView = engineView
    if (!isInBackground && dashView != null) {
        // A new engine view (first DASH item) gets a new interop view; the same engine reuses it.
        key(dashView) {
            UIKitView(
                modifier = modifier.fillMaxSize(),
                factory = {
                    PlayerSurfaceContainer(dashView).apply { setBackgroundColor(UIColor.blackColor) }
                },
                properties = remember {
                    UIKitInteropProperties(
                        // Taps go to the Compose controls drawn over the video.
                        isInteractive = false,
                        isNativeAccessibilityEnabled = true
                    )
                },
                onRelease = { container ->
                    // The engine keeps its view. A newer container may already hold it.
                    if (dashView.superview == container) dashView.removeFromSuperview()
                }
            )
        }
        return
    }

    // The AVPlayer surface stays attached in the background: Picture in Picture restores into it
    // when the app comes back, and the player's background policy keeps the audio going.
    UIKitView(
        modifier = modifier.fillMaxSize(),
        factory = {
            // onRelease cleared the player when the DASH engine's view replaced this one:
            // attach it again whenever this surface is (re)created.
            playerViewController.setPlayer(iosPlayer.getAVPlayer())
            playerViewController.title = mediaItem?.title
            (playerViewController.view.layer as? AVPlayerLayer)?.videoGravity = AVLayerVideoGravityResizeAspect
            PlayerSurfaceContainer(playerViewController.view)
        },
        properties = remember {
            UIKitInteropProperties(
                // Taps go to the Compose controls drawn over the video.
                isInteractive = false,
                isNativeAccessibilityEnabled = true
            )
        },
        onRelease = { container ->
            // Entering or leaving full screen builds the new surface before releasing this one.
            if (playerViewController.view.superview == container) playerViewController.setPlayer(null)
        }
    )
}

/**
 * Holds the video view and keeps it at its own size on every layout. Compose resizes the
 * interop view without recomposing (rotation into full screen), so `update` alone left the
 * video at its old frame: black in landscape until the next recomposition.
 */
@OptIn(ExperimentalForeignApi::class)
private class PlayerSurfaceContainer(private val content: UIView) : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    init {
        content.removeFromSuperview()
        addSubview(content)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        content.setFrame(bounds)
    }
}

/**
 * Lets Picture in Picture restore into the app: as in Apple's own apps, the floating window
 * stays when the app comes back, and its restore button brings the video back here.
 */
private class PictureInPictureDelegate : NSObject(), AVPlayerViewControllerDelegateProtocol {
    override fun playerViewController(
        playerViewController: AVPlayerViewController,
        restoreUserInterfaceForPictureInPictureStopWithCompletionHandler: (Boolean) -> Unit,
    ) {
        // The surface stays attached, so there is always a place to restore into.
        restoreUserInterfaceForPictureInPictureStopWithCompletionHandler(true)
    }
}
