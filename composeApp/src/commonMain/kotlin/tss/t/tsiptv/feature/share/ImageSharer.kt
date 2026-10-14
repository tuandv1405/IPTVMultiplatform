package tss.t.tsiptv.feature.share

import androidx.compose.ui.graphics.ImageBitmap

/** Where the share card can go. Each platform offers the ones it can reach ([ImageSharer.targets]). */
enum class ShareTarget {
    FACEBOOK,
    INSTAGRAM,
    ZALO,
    TELEGRAM,

    /** The system share sheet (Android chooser, iOS activity sheet). */
    MORE,

    /** Desktop: the picture on the clipboard. */
    COPY,

    /** Into the device's pictures. */
    SAVE,
}

sealed interface ShareResult {
    /** Another app or the system sheet took over. */
    data object Opened : ShareResult

    /** Android, Facebook and Instagram drop the text of a shared picture: it was put on the clipboard. */
    data object OpenedCaptionCopied : ShareResult

    /** Desktop: the picture is on the clipboard and the site's share page is open, to paste it there. */
    data object OpenedImageCopied : ShareResult

    data object ImageCopied : ShareResult

    /** [where] is shown to the viewer: a folder path or the album name. */
    data class Saved(val where: String) : ShareResult

    data object Failed : ShareResult
}

interface ImageSharer {
    /** In the order shown. Apps that are not installed are left out. */
    fun targets(): List<ShareTarget>

    /** [image] is the share card; [text] the caption with the Play Store link. */
    suspend fun share(target: ShareTarget, image: ImageBitmap, text: String): ShareResult
}

expect fun getImageSharer(): ImageSharer

/** JPEG bytes of [this], for files and other apps. */
expect fun ImageBitmap.encodeJpeg(quality: Int = 90): ByteArray

object ShareLinks {
    /** Shared captions link here, never to the stream (its URL can carry the viewer's tokens). */
    const val PLAY_STORE = "https://play.google.com/store/apps/details?id=tss.t.tsiptv"

    fun facebook(): String = "https://www.facebook.com/sharer/sharer.php?u=${encode(PLAY_STORE)}"

    fun telegram(text: String): String = "https://t.me/share/url?url=${encode(PLAY_STORE)}&text=${encode(text)}"

    /** Percent-encodes everything but RFC 3986 unreserved characters (UTF-8). */
    internal fun encode(value: String): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val c = byte.toInt().toChar()
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-._~") {
                append(c)
            } else {
                append('%')
                append(HEX[(byte.toInt() shr 4) and 0xF])
                append(HEX[byte.toInt() and 0xF])
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
}
