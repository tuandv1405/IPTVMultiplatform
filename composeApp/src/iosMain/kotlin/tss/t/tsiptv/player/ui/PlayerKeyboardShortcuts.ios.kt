package tss.t.tsiptv.player.ui

import androidx.compose.runtime.Composable
import tss.t.tsiptv.ui.screens.player.PlayerEvent

@Composable
actual fun PlayerKeyboardShortcuts(
    isPlaying: Boolean,
    isFullScreen: Boolean,
    isLive: Boolean,
    onPlayerControl: (PlayerEvent) -> Unit,
) = Unit
