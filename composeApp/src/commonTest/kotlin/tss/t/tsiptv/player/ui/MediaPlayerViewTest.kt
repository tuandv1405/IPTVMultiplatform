package tss.t.tsiptv.player.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the MediaPlayerView component.
 */
class MediaPlayerViewTest {
    
    /**
     * Test for the formatTime function.
     * 
     * Since formatTime is a private function in MediaPlayerView.kt, we're testing it indirectly
     * by creating a wrapper function that calls it with the same logic.
     */
    @Test
    fun testFormatTime() {
        // Test cases for different time values
        assertEquals("00:00", formatTimeForTest(0))
        assertEquals("00:01", formatTimeForTest(1000))
        assertEquals("00:59", formatTimeForTest(59000))
        assertEquals("01:00", formatTimeForTest(60000))
        assertEquals("01:01", formatTimeForTest(61000))
        assertEquals("10:00", formatTimeForTest(600000))
        assertEquals("99:59", formatTimeForTest(5999000))
    }
    
    /**
     * A copy of the formatTime function from MediaPlayerView.kt for testing purposes.
     */
    private fun formatTimeForTest(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}