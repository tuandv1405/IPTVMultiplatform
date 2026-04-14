package tss.t.tsiptv.player.models

import kotlinx.serialization.Serializable
import tss.t.tsiptv.core.model.Channel

/**
 * Represents a media item that can be played
 */
@Serializable
data class MediaItem(
    val id: String,
    val uri: String,
    val description: String = "",
    val title: String = "",
    val artist: String = "",
    val artworkUri: String? = null,
    val mimeType: String? = null,
    /**
     * DRM license key/URL from the playlist's #KODIPROP lines.
     * - ClearKey format: "kid_hex:key_hex"  (e.g. "a7c94277...:6d54358...")
     * - Widevine format: HTTP/HTTPS license server URL
     * Null for streams that have no DRM or whose DRM URL is embedded in the manifest.
     */
    val drmLicenseKey: String? = null,
) {
    companion object {
        val EMPTY = MediaItem("", "")
    }
}

fun Channel.toMediaItem() = MediaItem(
    id = id,
    uri = url,
    title = name,
    description = categoryId ?: "",
    artist = categoryId ?: "",
    artworkUri = logoUrl,
    mimeType = null
)

fun Channel.toMediaItem(groupTitle: String) = MediaItem(
    id = id,
    uri = url,
    title = name,
    artist = groupTitle,
    artworkUri = logoUrl,
    mimeType = null
)
