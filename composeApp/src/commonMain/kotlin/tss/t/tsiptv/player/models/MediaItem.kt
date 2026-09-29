package tss.t.tsiptv.player.models

import kotlinx.serialization.Serializable
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.parser.model.playback.DrmSpec

/**
 * Represents a media item that can be played
 *
 * @property headers HTTP headers for every request of this item (manifest, variants, segments, keys)
 * @property drm DRM setup declared by the playlist; null plays without any DRM configuration
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
    val headers: Map<String, String> = emptyMap(),
    val drm: DrmSpec? = null,
    val isRadio: Boolean = false,
    /** F3: side-loaded subtitles (TS IPTV Source movies and episodes; Android and desktop). */
    val subtitles: List<SubtitleTrack> = emptyList(),
) {
    /** Never the URL or header values (tokens). */
    override fun toString(): String = "MediaItem(id=$id, title=$title, headers=${headers.keys}, subtitles=${subtitles.size})"

    companion object {
        val EMPTY = MediaItem("", "")
    }
}

/**
 * A side-loaded subtitle file (WebVTT or SubRip).
 *
 * @property mimeType [MIME_VTT] or [MIME_SRT]
 */
@Serializable
data class SubtitleTrack(
    val url: String,
    val language: String,
    val label: String? = null,
    val mimeType: String = MIME_VTT,
) {
    override fun toString(): String = "SubtitleTrack(language=$language, mimeType=$mimeType)"

    companion object {
        const val MIME_VTT = "text/vtt"
        const val MIME_SRT = "application/x-subrip"
    }
}

fun Channel.toMediaItem() = MediaItem(
    id = id,
    uri = url,
    title = name,
    description = categoryId ?: "",
    artist = categoryId ?: "",
    artworkUri = logoUrl,
    mimeType = mimeType,
    headers = headers,
    drm = drm,
    isRadio = isRadio,
)

fun Channel.toMediaItem(groupTitle: String) = MediaItem(
    id = id,
    uri = url,
    title = name,
    artist = groupTitle,
    artworkUri = logoUrl,
    mimeType = mimeType,
    headers = headers,
    drm = drm,
    isRadio = isRadio,
)
