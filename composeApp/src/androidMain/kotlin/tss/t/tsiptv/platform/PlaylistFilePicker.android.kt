package tss.t.tsiptv.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
actual fun rememberPlaylistFilePicker(onResult: (PickedPlaylistFile) -> Unit): PlaylistFilePicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnResult by rememberUpdatedState(onResult)
    // Phones always have DocumentsUI. Android TV images answer ACTION_OPEN_DOCUMENT with a
    // do-nothing stub (com.android.tv.frameworkpackagestubs), so "something resolves" is not
    // enough: only a real handler counts, otherwise the button would open nothing.
    val available = remember(context) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        context.packageManager.queryIntentActivities(intent, 0)
            .any { it.activityInfo?.packageName?.let { pkg -> pkg !in STUB_PACKAGES } == true }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            latestOnResult(PickedPlaylistFile.Cancelled)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val picked = withContext(Dispatchers.IO) {
                runCatching { readDocument(context, uri) }.getOrDefault(PickedPlaylistFile.ReadFailed)
            }
            latestOnResult(picked)
        }
    }
    return remember(launcher, available) {
        object : PlaylistFilePicker {
            override val isAvailable: Boolean = available
            // "*/*": providers label .m3u and .strm inconsistently, so filtering by MIME type
            // would hide exactly the files users are looking for.
            override fun launch() = launcher.launch(arrayOf("*/*"))
        }
    }
}

/** Packages that register document intents only to swallow them. */
private val STUB_PACKAGES = setOf("com.android.tv.frameworkpackagestubs")

private fun readDocument(context: Context, uri: Uri): PickedPlaylistFile {
    var displayName = uri.lastPathSegment ?: "playlist"
    var size = -1L
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }
                ?.let { cursor.getString(it) }?.let { displayName = it }
            cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }
                ?.let { size = cursor.getLong(it) }
        }
    }
    if (size > MAX_PLAYLIST_FILE_BYTES) return PickedPlaylistFile.TooLarge(displayName)

    // Providers may not report a size; stop reading one byte past the limit either way.
    val input = context.contentResolver.openInputStream(uri) ?: return PickedPlaylistFile.ReadFailed
    val out = ByteArrayOutputStream()
    input.use { stream ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > MAX_PLAYLIST_FILE_BYTES) return PickedPlaylistFile.TooLarge(displayName)
        }
    }
    return PickedPlaylistFile.Picked(displayName, out.toByteArray())
}
