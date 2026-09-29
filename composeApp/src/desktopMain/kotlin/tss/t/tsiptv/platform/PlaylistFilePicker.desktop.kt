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

@Composable
actual fun rememberPlaylistFilePicker(onResult: (PickedPlaylistFile) -> Unit): PlaylistFilePicker {
    val scope = rememberCoroutineScope()
    val latestOnResult by rememberUpdatedState(onResult)
    return remember {
        object : PlaylistFilePicker {
            override val isAvailable: Boolean = true
            override fun launch() {
                scope.launch { latestOnResult(pickFile()) }
            }
        }
    }
}

/**
 * The native dialog rather than Swing's JFileChooser, so it looks like the platform's own.
 * It is modal and pumps events itself, so opening it from the UI thread is fine.
 */
private suspend fun pickFile(): PickedPlaylistFile {
    val dialog = FileDialog(null as Frame?, "", FileDialog.LOAD).apply {
        // macOS and Linux filter with this; Windows ignores it and uses the pattern in `file`.
        setFilenameFilter { _, name ->
            PLAYLIST_FILE_EXTENSIONS.any { name.endsWith(".$it", ignoreCase = true) }
        }
        file = PLAYLIST_FILE_EXTENSIONS.joinToString(";") { "*.$it" }
        isMultipleMode = false
    }
    dialog.isVisible = true
    val file = dialog.files.firstOrNull() ?: return PickedPlaylistFile.Cancelled
    if (file.length() > MAX_PLAYLIST_FILE_BYTES) return PickedPlaylistFile.TooLarge(file.name)
    return withContext(Dispatchers.IO) {
        runCatching { PickedPlaylistFile.Picked(file.name, file.readBytes()) }
            .getOrDefault(PickedPlaylistFile.ReadFailed)
    }
}
