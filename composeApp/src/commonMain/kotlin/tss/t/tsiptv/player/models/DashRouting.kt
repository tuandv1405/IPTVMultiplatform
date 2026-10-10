package tss.t.tsiptv.player.models

import tss.t.tsiptv.core.parser.model.playback.DrmSpec
import tss.t.tsiptv.core.parser.model.playback.StreamMimeTypes

/**
 * What the platform player can do beyond its default engine. Set once at start-up by platform code
 * (iOS: when the app registers its DASH engine), read by common UI such as the stream picker.
 */
object PlaybackCapabilities {
    /**
     * iOS: a DASH engine (VLCKit) is registered, so clear MPEG-DASH plays in the app. AVPlayer alone
     * plays HLS only. Always false on Android and desktop, which play DASH natively.
     */
    var iosDashEngineAvailable: Boolean = false
}

/** Which iOS engine plays a stream: AVPlayer (default) or the registered DASH engine. */
object DashRouting {

    /**
     * MPEG-DASH, from the MIME hint or the URL path (`.mpd`). An explicit HLS hint wins over the
     * extension.
     */
    fun isDash(url: String, mimeType: String?): Boolean {
        val mime = mimeType?.trim()?.takeIf { it.isNotEmpty() }
        if (mime != null && StreamMimeTypes.fromMimeType(mime) == StreamMimeTypes.DASH) return true
        if (mime != null && StreamMimeTypes.fromMimeType(mime) == StreamMimeTypes.HLS) return false
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return path.endsWith(".mpd")
    }

    /**
     * iOS: play through the DASH engine when the stream is DASH, has no DRM (neither AVPlayer nor
     * VLCKit has a Widevine / PlayReady / ClearKey CDM for DASH, so DRM keeps the existing refusal),
     * and an engine is registered.
     */
    fun shouldUseDashEngine(url: String, mimeType: String?, drm: DrmSpec?, engineAvailable: Boolean): Boolean =
        engineAvailable && drm == null && isDash(url, mimeType)

    /**
     * The stream picker's "may not play" badge for DASH on iOS: shown when no DASH engine is
     * registered, or when the stream has DRM (refused on iOS either way).
     */
    fun dashMayNotPlayOnIos(engineAvailable: Boolean, hasDrm: Boolean): Boolean = !engineAvailable || hasDrm
}
