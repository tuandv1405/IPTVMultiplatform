package tss.t.tsiptv.feature.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.share_caption_copied
import tss.t.tsiptv.TSAndroidApplication
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Android: the picture goes out as a FileProvider content URI with `ACTION_SEND`, to one app
 * (Facebook, Instagram, Zalo, Telegram) or through the system chooser. Package visibility needs the
 * `<queries>` entries in the manifest.
 */
class AndroidImageSharer : ImageSharer {
    private val context: Context get() = TSAndroidApplication.instance.applicationContext

    override fun targets(): List<ShareTarget> = buildList {
        APPS.forEach { (target, packages) -> if (packages.any(::acceptsImages)) add(target) }
        add(ShareTarget.MORE)
        // Before Android 10, writing to the shared Pictures folder needs a storage permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(ShareTarget.SAVE)
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun share(target: ShareTarget, image: ImageBitmap, text: String): ShareResult {
        return try {
            val bytes = withContext(Dispatchers.Default) { image.encodeJpeg() }
            if (target == ShareTarget.SAVE) return withContext(Dispatchers.IO) { save(bytes) }

            val file = withContext(Dispatchers.IO) { writeShareFile(bytes) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                // The grant travels with the clip data, also through the chooser.
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val packages = APPS[target]
            val intent = if (packages == null) {
                Intent.createChooser(send, null)
            } else {
                send.setPackage(packages.firstOrNull(::acceptsImages) ?: return ShareResult.Failed)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            if (target == ShareTarget.FACEBOOK || target == ShareTarget.INSTAGRAM) {
                // Both ignore EXTRA_TEXT (platform policy): the caption waits on the clipboard.
                val message = getString(Res.string.share_caption_copied)
                withContext(Dispatchers.Main) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("TS IPTV", text))
                    // Android 13+ confirms clipboard writes itself.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
                ShareResult.OpenedCaptionCopied
            } else {
                ShareResult.Opened
            }
        } catch (_: Exception) {
            ShareResult.Failed
        }
    }

    private fun acceptsImages(packageName: String): Boolean {
        val probe = Intent(Intent.ACTION_SEND).setType("image/jpeg").setPackage(packageName)
        return context.packageManager.queryIntentActivities(probe, 0).isNotEmpty()
    }

    /** One file at a time under cache/share (the FileProvider path); older cards are removed. */
    @OptIn(ExperimentalTime::class)
    private fun writeShareFile(bytes: ByteArray): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, "tsiptv-${Clock.System.now().toEpochMilliseconds()}.jpg").apply { writeBytes(bytes) }
    }

    @OptIn(ExperimentalTime::class)
    private fun save(bytes: ByteArray): ShareResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ShareResult.Failed
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "tsiptv-${Clock.System.now().toEpochMilliseconds()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return ShareResult.Failed
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return ShareResult.Saved("${Environment.DIRECTORY_PICTURES}/$ALBUM")
    }

    private companion object {
        const val ALBUM = "TS IPTV"

        /** Main app first, then the lite / alternative clients. */
        val APPS = linkedMapOf(
            ShareTarget.FACEBOOK to listOf("com.facebook.katana", "com.facebook.lite"),
            ShareTarget.INSTAGRAM to listOf("com.instagram.android"),
            ShareTarget.ZALO to listOf("com.zing.zalo"),
            ShareTarget.TELEGRAM to listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram"),
        )
    }
}

actual fun getImageSharer(): ImageSharer = AndroidImageSharer()

actual fun ImageBitmap.encodeJpeg(quality: Int): ByteArray {
    var bitmap = asAndroidBitmap()
    // A layer read back on API 28+ can be a HARDWARE bitmap, which compress() cannot read.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
    }
    return ByteArrayOutputStream().use { out ->
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "JPEG encoding failed" }
        out.toByteArray()
    }
}
