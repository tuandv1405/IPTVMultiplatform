package tss.t.tsiptv.player.ui

import androidx.compose.runtime.Composable
import tss.t.tsiptv.ui.screens.player.PlayerEvent

/**
 * Keyboard control of the open player while it is on screen. Desktop: Space plays / pauses, Esc
 * leaves full screen, F toggles it, M mutes, Left / Right seek 10 s in a video with a duration.
 * Android and iOS: nothing (remotes and TV layouts have their own handling).
 */
@Composable
expect fun PlayerKeyboardShortcuts(
    isPlaying: Boolean,
    isFullScreen: Boolean,
    isLive: Boolean,
    onPlayerControl: (PlayerEvent) -> Unit,
)
