package tss.t.tsiptv.feature.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShareTest {

    @Test
    fun blackAndNearBlackPicturesAreBlank() {
        assertTrue(FrameCheck.isBlank(IntArray(64) { 0xFF000000.toInt() }))
        // Encoder noise in a black frame.
        assertTrue(FrameCheck.isBlank(IntArray(64) { 0xFF0C0A0E.toInt() }))
    }

    @Test
    fun aDarkSceneWithAnyContentIsNotBlank() {
        val pixels = IntArray(64) { 0xFF050505.toInt() }
        pixels[40] = 0xFF606060.toInt()
        assertFalse(FrameCheck.isBlank(pixels))
        assertFalse(FrameCheck.isBlank(IntArray(64) { 0xFF2050A0.toInt() }))
    }

    @Test
    fun shareLinksCarryOnlyTheStoreLinkAndCaption() {
        assertEquals(
            "https://www.facebook.com/sharer/sharer.php?u=https%3A%2F%2Fplay.google.com%2Fstore%2Fapps%2Fdetails%3Fid%3Dtss.t.tsiptv",
            ShareLinks.facebook(),
        )
        val telegram = ShareLinks.telegram("Đang xem VTV1 & HTV7")
        assertTrue(telegram.startsWith("https://t.me/share/url?url=https%3A%2F%2Fplay.google.com%2F"))
        assertTrue(telegram.endsWith("&text=%C4%90ang%20xem%20VTV1%20%26%20HTV7"))
    }
}
