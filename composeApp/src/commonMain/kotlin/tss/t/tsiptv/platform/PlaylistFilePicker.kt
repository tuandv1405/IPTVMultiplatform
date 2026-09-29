package tss.t.tsiptv.platform

import androidx.compose.runtime.Composable
import tss.t.tsiptv.usecase.playlist.PlaylistImporter

/** What the platform file picker handed back. */
sealed interface PickedPlaylistFile {
    /** The file was read once, whole; it is not kept after import. */
    class Picked(val displayName: String, val bytes: ByteArray) : PickedPlaylistFile

    /** Larger than [MAX_PLAYLIST_FILE_BYTES]; refused before it is read into memory. */
    data class TooLarge(val displayName: String) : PickedPlaylistFile

    /** The user picked a file but it could not be read; unlike [Cancelled], this is reported. */
    data object ReadFailed : PickedPlaylistFile

    data object Cancelled : PickedPlaylistFile
}

const val MAX_PLAYLIST_FILE_BYTES = PlaylistImporter.MAX_FILE_BYTES

/** Extensions offered by pickers that filter by name (desktop). */
val PLAYLIST_FILE_EXTENSIONS = listOf("m3u", "m3u8", "strm", "json", "xspf", "xml")

interface PlaylistFilePicker {
    /**
     * False where the device has no document picker at all (most Android TVs ship without
     * DocumentsUI); the import screen then hides the action and explains why.
     */
    val isAvailable: Boolean

    fun launch()
}

/**
 * The platform's own picker: Storage Access Framework on Android, UIDocumentPicker on iOS,
 * the native file dialog on desktop. [onResult] runs on the main thread.
 */
@Composable
expect fun rememberPlaylistFilePicker(onResult: (PickedPlaylistFile) -> Unit): PlaylistFilePicker
