package tss.t.tsiptv.ui.screens.addons

import kotlin.test.Test
import kotlin.test.assertEquals

class AddonsGuideUrlTest {
    @Test
    fun vietnameseGetsTheDefaultPageOthersTheEnglishOne() {
        val base = "https://tsiptv-8bdd6.web.app/guides/stremio-addons/"
        assertEquals(base, addonsGuideUrl("vi"))
        assertEquals(base, addonsGuideUrl("vi-VN"))
        assertEquals("$base?lang=en", addonsGuideUrl("en"))
        assertEquals("$base?lang=en", addonsGuideUrl("ja"))
        assertEquals("$base?lang=en", addonsGuideUrl(null))
    }
}
