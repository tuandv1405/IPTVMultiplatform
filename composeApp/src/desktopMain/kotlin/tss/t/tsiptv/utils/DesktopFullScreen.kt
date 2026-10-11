package tss.t.tsiptv.utils

import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import java.awt.Frame
import java.awt.Rectangle
import java.awt.Window

/**
 * Puts the app window in and out of full screen for the player.
 *
 * Windows: a borderless window over its whole monitor. Compose's own [WindowPlacement.Fullscreen]
 * goes through Skiko's native full screen, which on Windows drops the display scale to 100 %: at
 * 125 % or more the controls turned small, and after leaving full screen the content stayed at
 * 100 % and filled only part of the window.
 * macOS / Linux: [WindowPlacement.Fullscreen] (native full screen on macOS).
 */
internal class DesktopFullScreen(private val window: Window, private val state: WindowState) {

    private val isWindows = System.getProperty("os.name").startsWith("Windows")

    private var active = false
    private var savedStyle = 0
    private var savedBounds: Rectangle? = null
    private var savedFrameState = Frame.NORMAL
    private var savedPlacement = WindowPlacement.Floating

    fun set(fullScreen: Boolean) {
        if (fullScreen == active) return
        active = fullScreen
        if (!isWindows) {
            if (fullScreen) {
                savedPlacement = state.placement
                state.placement = WindowPlacement.Fullscreen
            } else {
                state.placement = savedPlacement
            }
            return
        }
        runCatching { if (fullScreen) enterWindows() else exitWindows() }
            .onFailure { println("Full screen failed: $it") }
    }

    private fun enterWindows() {
        val hwnd = HWND(Native.getWindowPointer(window))
        val frame = window as? Frame
        savedFrameState = frame?.extendedState ?: Frame.NORMAL
        // A maximized window keeps its maximized size when moved: restore it first.
        if (frame != null && savedFrameState and Frame.MAXIMIZED_BOTH != 0) {
            frame.extendedState = Frame.NORMAL
        }
        savedBounds = window.bounds
        savedStyle = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE)
        User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_STYLE, savedStyle and (WS_CAPTION or WS_THICKFRAME).inv())

        val info = WinUser.MONITORINFO()
        User32.INSTANCE.GetMonitorInfo(User32.INSTANCE.MonitorFromWindow(hwnd, WinUser.MONITOR_DEFAULTTONEAREST), info)
        val r = info.rcMonitor
        User32.INSTANCE.SetWindowPos(
            hwnd, null, r.left, r.top, r.right - r.left, r.bottom - r.top,
            SWP_NOZORDER or SWP_NOOWNERZORDER or SWP_FRAMECHANGED,
        )
    }

    private fun exitWindows() {
        val hwnd = HWND(Native.getWindowPointer(window))
        User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_STYLE, savedStyle)
        User32.INSTANCE.SetWindowPos(
            hwnd, null, 0, 0, 0, 0,
            SWP_NOZORDER or SWP_NOOWNERZORDER or SWP_NOMOVE or SWP_NOSIZE or SWP_FRAMECHANGED,
        )
        savedBounds?.let { window.bounds = it }
        (window as? Frame)?.extendedState = savedFrameState
    }

    private companion object {
        const val WS_CAPTION = 0x00C00000
        const val WS_THICKFRAME = 0x00040000
        const val SWP_NOSIZE = 0x0001
        const val SWP_NOMOVE = 0x0002
        const val SWP_NOZORDER = 0x0004
        const val SWP_FRAMECHANGED = 0x0020
        const val SWP_NOOWNERZORDER = 0x0200
    }
}
