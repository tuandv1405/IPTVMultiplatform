package tss.t.tsiptv.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import tss.t.tsiptv.ui.screens.player.PlayerEvent

/**
 * Window-level key handling (main.kt's onPreviewKeyEvent), so the shortcuts work wherever the focus
 * is. The most recently shown player gets the keys.
 */
object DesktopKeyRouter {
    private val handlers = mutableListOf<(KeyEvent) -> Boolean>()

    fun dispatch(event: KeyEvent): Boolean = handlers.lastOrNull()?.invoke(event) ?: false

    internal fun add(handler: (KeyEvent) -> Boolean) {
        handlers += handler
    }

    internal fun remove(handler: (KeyEvent) -> Boolean) {
        handlers -= handler
    }
}

private const val SEEK_STEP_MS = 10_000L

@Composable
actual fun PlayerKeyboardShortcuts(
    isPlaying: Boolean,
    isFullScreen: Boolean,
    isLive: Boolean,
    onPlayerControl: (PlayerEvent) -> Unit,
) {
    val playing by rememberUpdatedState(isPlaying)
    val fullScreen by rememberUpdatedState(isFullScreen)
    val live by rememberUpdatedState(isLive)
    val onEvent by rememberUpdatedState(onPlayerControl)

    DisposableEffect(Unit) {
        val handler: (KeyEvent) -> Boolean = handler@{ event ->
            if (event.type != KeyEventType.KeyDown) return@handler false
            if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return@handler false
            val playerEvent = when (event.key) {
                Key.Spacebar -> if (playing) PlayerEvent.Pause else PlayerEvent.Play
                Key.Escape -> if (fullScreen) PlayerEvent.OnExitFullScreen else return@handler false
                Key.F -> if (fullScreen) PlayerEvent.OnExitFullScreen else PlayerEvent.OnEnterFullScreen
                Key.M -> PlayerEvent.ToggleMute
                Key.DirectionLeft -> if (live) return@handler false else PlayerEvent.SeekBy(-SEEK_STEP_MS)
                Key.DirectionRight -> if (live) return@handler false else PlayerEvent.SeekBy(SEEK_STEP_MS)
                else -> return@handler false
            }
            onEvent(playerEvent)
            true
        }
        DesktopKeyRouter.add(handler)
        onDispose { DesktopKeyRouter.remove(handler) }
    }
}
