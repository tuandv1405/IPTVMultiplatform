package tss.t.tsiptv.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

@Composable
actual fun rememberPlaylistFilePicker(onResult: (PickedPlaylistFile) -> Unit): PlaylistFilePicker {
    val scope = rememberCoroutineScope()
    val latestOnResult by rememberUpdatedState(onResult)
    return remember {
        object : PlaylistFilePicker {
            private val open = AtomicBoolean(false)
            override val isAvailable: Boolean = true
            override fun launch() {
                if (!open.compareAndSet(false, true)) return
                // The dialog is modal: it runs its own event loop until it closes, and Compose keeps
                // rendering inside that loop. Opened from a coroutine of the composition, Compose
                // re-entered the dispatcher running that coroutine and broke it (ClassCastException,
                // "Error" window). A plain Swing event of its own keeps the loop outside coroutines.
                SwingUtilities.invokeLater {
                    val file = try {
                        chooseFile()
                    } finally {
                        open.set(false)
                    }
                    scope.launch { latestOnResult(read(file)) }
                }
            }
        }
    }
}

/** The native dialog rather than Swing's JFileChooser, so it looks like the platform's own. */
private fun chooseFile(): File? {
    // Owned by the app window: centred on it and kept in front of it.
    val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow as? Frame
    val dialog = FileDialog(owner, "", FileDialog.LOAD).apply {
        // macOS and Linux filter with this; Windows ignores it and uses the pattern in `file`.
        setFilenameFilter { _, name ->
            PLAYLIST_FILE_EXTENSIONS.any { name.endsWith(".$it", ignoreCase = true) }
        }
        file = PLAYLIST_FILE_EXTENSIONS.joinToString(";") { "*.$it" }
        isMultipleMode = false
    }
    dialog.isVisible = true
    return dialog.files.firstOrNull().also { dialog.dispose() }
}

private suspend fun read(file: File?): PickedPlaylistFile {
    if (file == null) return PickedPlaylistFile.Cancelled
    return withContext(Dispatchers.IO) {
        if (file.length() > MAX_PLAYLIST_FILE_BYTES) return@withContext PickedPlaylistFile.TooLarge(file.name)
        runCatching { PickedPlaylistFile.Picked(file.name, file.readBytes()) }
            .getOrDefault(PickedPlaylistFile.ReadFailed)
    }
}
