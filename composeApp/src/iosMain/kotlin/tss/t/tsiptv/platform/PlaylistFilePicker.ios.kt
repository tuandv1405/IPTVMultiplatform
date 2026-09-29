package tss.t.tsiptv.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerMode
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject
import platform.posix.memcpy

// Not compiled on the Windows build machine (no Kotlin/Native Apple toolchain); see
// docs/handoff-f1.md. Kept to the smallest UIKit surface on purpose.

@Composable
actual fun rememberPlaylistFilePicker(onResult: (PickedPlaylistFile) -> Unit): PlaylistFilePicker {
    val latestOnResult by rememberUpdatedState(onResult)
    // UIKit holds its delegate weakly; remembering it keeps it alive while the picker is open.
    val delegate = remember { DocumentPickerDelegate { latestOnResult(it) } }
    return remember {
        object : PlaylistFilePicker {
            override val isAvailable: Boolean = true
            override fun launch() {
                val picker = UIDocumentPickerViewController(
                    documentTypes = listOf("public.data", "public.plain-text"),
                    inMode = UIDocumentPickerMode.UIDocumentPickerModeImport,
                )
                picker.delegate = delegate
                UIApplication.sharedApplication.keyWindow?.rootViewController
                    ?.presentViewController(picker, animated = true, completion = null)
            }
        }
    }
}

private class DocumentPickerDelegate(
    private val onResult: (PickedPlaylistFile) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) {
            onResult(PickedPlaylistFile.Cancelled)
            return
        }
        // Read off the main thread: a 20 MiB file would stall the UI.
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            val picked = readPicked(url)
            dispatch_async(dispatch_get_main_queue()) { onResult(picked) }
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onResult(PickedPlaylistFile.Cancelled)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun readPicked(url: NSURL): PickedPlaylistFile {
    val name = url.lastPathComponent ?: "playlist"
    val scoped = url.startAccessingSecurityScopedResource()
    try {
        // Size first, so an oversized file is refused without being read into memory.
        val size = url.path
            ?.let { NSFileManager.defaultManager.attributesOfItemAtPath(it, null) }
            ?.get(NSFileSize) as? NSNumber
        if (size != null && size.longLongValue > MAX_PLAYLIST_FILE_BYTES) return PickedPlaylistFile.TooLarge(name)
        val data: NSData = NSData.dataWithContentsOfURL(url) ?: return PickedPlaylistFile.ReadFailed
        val length = data.length.toLong()
        if (length > MAX_PLAYLIST_FILE_BYTES) return PickedPlaylistFile.TooLarge(name)
        val bytes = ByteArray(length.toInt())
        if (length > 0) {
            bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
        }
        return PickedPlaylistFile.Picked(name, bytes)
    } finally {
        if (scoped) url.stopAccessingSecurityScopedResource()
    }
}
