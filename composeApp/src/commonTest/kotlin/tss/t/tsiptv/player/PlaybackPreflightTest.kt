package tss.t.tsiptv.player

import tss.t.tsiptv.core.parser.model.playback.ClearKey
import tss.t.tsiptv.core.parser.model.playback.DrmSpec
import tss.t.tsiptv.core.parser.model.playback.DrmSystem
import tss.t.tsiptv.core.parser.model.playback.StreamMimeTypes
import tss.t.tsiptv.player.models.MediaItem
import tss.t.tsiptv.player.models.PlaybackError
import tss.t.tsiptv.player.models.PlaybackPreflight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** AC-K17 (no DRM, no licence) and AC-K19 (refusal before any request) at the decision level. */
class PlaybackPreflightTest {

    private val widevine = DrmSpec(DrmSystem.WIDEVINE, licenseUrl = "https://lic.example.com/wv")
    private val clearKey = DrmSpec(
        DrmSystem.CLEARKEY,
        clearKeys = listOf(ClearKey("000102030405060708090a0b0c0d0e0f", "00112233445566778899aabbccddeeff"))
    )

    private fun item(url: String, drm: DrmSpec?, mime: String? = null) =
        MediaItem(id = "c", uri = url, drm = drm, mimeType = mime)

    @Test
    fun clearStreamPlaysEvenWhenItsUrlSaysDrm() {
        assertNull(PlaybackPreflight.check(item("https://cdn.example.com/drm/widevine/index.m3u8", null), true))
        assertNull(PlaybackPreflight.check(item("https://cdn.example.com/drm/index.m3u8", null), false))
    }

    @Test
    fun drmOnPlatformsWithoutCdmIsRefused() {
        assertEquals(
            PlaybackError.DRM_NOT_SUPPORTED_DEVICE,
            PlaybackPreflight.check(item("https://cdn.example.com/a.mpd", widevine), platformSupportsDrm = false)
        )
    }

    @Test
    fun supportedDrmPlaysOnAndroid() {
        assertNull(PlaybackPreflight.check(item("https://cdn.example.com/a.mpd", widevine), true))
        assertNull(PlaybackPreflight.check(item("https://cdn.example.com/a/manifest", clearKey, StreamMimeTypes.DASH), true))
    }

    @Test
    fun unsupportedSetupAndClearKeyHlsAreRefused() {
        assertEquals(
            PlaybackError.DRM_NOT_SUPPORTED_CONFIG,
            PlaybackPreflight.check(item("https://cdn.example.com/a.mpd", DrmSpec.unsupported("wrapper")), true)
        )
        assertEquals(
            PlaybackError.DRM_NOT_SUPPORTED_CONFIG,
            PlaybackPreflight.check(item("https://cdn.example.com/a/index.m3u8?t=1", clearKey), true)
        )
    }
}
