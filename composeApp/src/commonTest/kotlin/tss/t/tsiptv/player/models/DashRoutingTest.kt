package tss.t.tsiptv.player.models

import tss.t.tsiptv.core.parser.model.playback.DrmSpec
import tss.t.tsiptv.core.parser.model.playback.DrmSystem
import tss.t.tsiptv.core.parser.model.playback.StreamMimeTypes
import tss.t.tsiptv.core.stremio.StreamBehaviorHints
import tss.t.tsiptv.core.stremio.StreamClassifier
import tss.t.tsiptv.core.stremio.StremioStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** iOS DASH routing: which streams go to the VLCKit engine, and the picker badge. */
class DashRoutingTest {

    @AfterTest
    fun reset() {
        PlaybackCapabilities.iosDashEngineAvailable = false
    }

    private val widevine = DrmSpec(system = DrmSystem.WIDEVINE, licenseUrl = "https://lic.example.com/")

    @Test
    fun detectsDashByMimeOrMpdPath() {
        assertTrue(DashRouting.isDash("https://cdn.example.com/live/manifest.mpd", null))
        assertTrue(DashRouting.isDash("https://cdn.example.com/live/MANIFEST.MPD?token=a.mpd4#x", null))
        assertTrue(DashRouting.isDash("https://cdn.example.com/stream", StreamMimeTypes.DASH))
        assertTrue(DashRouting.isDash("https://cdn.example.com/stream", " application/dash+xml "))
        assertFalse(DashRouting.isDash("https://cdn.example.com/index.m3u8", null))
        assertFalse(DashRouting.isDash("https://cdn.example.com/a.mp4?f=x.mpd", null)) // query, not path
        assertFalse(DashRouting.isDash("https://cdn.example.com/a.mpd", StreamMimeTypes.HLS)) // explicit HLS wins
        assertFalse(DashRouting.isDash("https://cdn.example.com/a.mpd.mp4", null))
    }

    @Test
    fun onlyClearDashWithAnEngineGoesToTheEngine() {
        val mpd = "https://cdn.example.com/v/manifest.mpd"
        assertTrue(DashRouting.shouldUseDashEngine(mpd, null, drm = null, engineAvailable = true))
        assertFalse(DashRouting.shouldUseDashEngine(mpd, null, drm = null, engineAvailable = false))
        // DASH with DRM keeps the existing refusal (no CDM on iOS for DASH).
        assertFalse(DashRouting.shouldUseDashEngine(mpd, null, drm = widevine, engineAvailable = true))
        // HLS and files stay on AVPlayer.
        assertFalse(DashRouting.shouldUseDashEngine("https://cdn.example.com/i.m3u8", null, null, true))
        assertFalse(DashRouting.shouldUseDashEngine("https://cdn.example.com/f.mp4", null, null, true))
    }

    @Test
    fun pickerBadgesDashOnlyWithoutEngineOrWithDrm() {
        val dash = StremioStream(url = "https://cdn.example.com/v/manifest.mpd")
        val dashDrm = StremioStream(url = "https://cdn.example.com/v/manifest.mpd", behaviorHints = StreamBehaviorHints(hasDrm = true))
        val mkv = StremioStream(url = "https://cdn.example.com/v/film.mkv")

        PlaybackCapabilities.iosDashEngineAvailable = false
        assertTrue(StreamClassifier.classifyAll(listOf(dash)).single().mayNotPlayOnIos)

        PlaybackCapabilities.iosDashEngineAvailable = true
        assertFalse(StreamClassifier.classifyAll(listOf(dash)).single().mayNotPlayOnIos)
        assertTrue(StreamClassifier.classifyAll(listOf(dashDrm)).single().mayNotPlayOnIos)
        // MKV is still left to AVPlayer, which cannot play it.
        assertTrue(StreamClassifier.classifyAll(listOf(mkv)).single().mayNotPlayOnIos)
    }
}
