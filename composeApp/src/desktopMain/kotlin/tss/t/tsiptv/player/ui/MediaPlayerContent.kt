package tss.t.tsiptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import tss.t.tsiptv.player.DesktopMediaPlayer
import tss.t.tsiptv.player.MediaPlayer
import tss.t.tsiptv.player.MediaPlayerFactory
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JPanel

/**
 * Desktop implementation of MediaPlayerContent.
 * Uses VLCj to display the video content.
 */
@Composable
actual fun MediaPlayerContent(
    player: MediaPlayer,
    modifier: Modifier
) {
    // Cast to DesktopMediaPlayer to access the video surface
    val desktopPlayer = player as? DesktopMediaPlayer
        ?: (MediaPlayerFactory.createDesktopPlayer(kotlinx.coroutines.MainScope()))
    
    // Create a panel to hold the video surface
    val panel = remember { JPanel(BorderLayout()) }
    
    // Get the video surface component
    val videoSurface = remember(desktopPlayer) { desktopPlayer.getVideoSurface() }
    
    // Add the video surface to the panel
    DisposableEffect(videoSurface) {
        panel.add(videoSurface, BorderLayout.CENTER)
        
        onDispose {
            panel.remove(videoSurface)
        }
    }
    
    // Use SwingPanel to embed the JPanel in Compose
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        SwingPanel(
            factory = { panel },
            modifier = Modifier.fillMaxSize(),
            update = { /* Nothing to update */ }
        )
    }
}