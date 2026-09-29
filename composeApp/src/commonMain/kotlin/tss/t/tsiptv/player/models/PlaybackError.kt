package tss.t.tsiptv.player.models

import tss.t.tsiptv.core.parser.model.playback.DrmSystem
import tss.t.tsiptv.core.parser.model.playback.StreamMimeTypes

/**
 * Playback failures the player overlay explains with its own message. Other failures keep the
 * player's generic behaviour.
 */
enum class PlaybackError {
    /** The platform has no CDM for the channel's DRM (iOS, desktop), or rejected the scheme. */
    DRM_NOT_SUPPORTED_DEVICE,

    /** The playlist's DRM setup is one TS IPTV cannot configure (yet). */
    DRM_NOT_SUPPORTED_CONFIG,

    /** The licence server refused, or the device could not be provisioned. */
    DRM_LICENSE_FAILED,

    /** HTTP 401/403 on a channel that sends its own headers: they or the token likely expired. */
    FORBIDDEN_HEADERS,

    /** Any other failure (DNS, 404, unreadable source): the stream is offline or its link moved. */
    STREAM_FAILED,
}

/**
 * Checks a channel before any network request is made for it, so a channel that cannot play
 * fails at once with a precise message and zapping on to the next one keeps working.
 */
object PlaybackPreflight {

    /**
     * @param platformSupportsDrm Whether the player can use Widevine / PlayReady / ClearKey at all
     * @return why [item] must not be played, or null to play it
     */
    fun check(item: MediaItem, platformSupportsDrm: Boolean): PlaybackError? {
        val drm = item.drm ?: return null
        if (!platformSupportsDrm) return PlaybackError.DRM_NOT_SUPPORTED_DEVICE
        if (!drm.isSupported) return PlaybackError.DRM_NOT_SUPPORTED_CONFIG
        // Media3 supports ClearKey for DASH only.
        if (drm.system == DrmSystem.CLEARKEY && StreamMimeTypes.isHls(item.uri, item.mimeType)) {
            return PlaybackError.DRM_NOT_SUPPORTED_CONFIG
        }
        return null
    }
}
