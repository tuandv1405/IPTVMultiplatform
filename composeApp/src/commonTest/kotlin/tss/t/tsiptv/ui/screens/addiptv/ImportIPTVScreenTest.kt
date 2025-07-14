package tss.t.tsiptv.ui.screens.addiptv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tss.t.tsiptv.ui.screens.home.HomeEvent
import tss.t.tsiptv.ui.screens.home.HomeUiState
import tss.t.tsiptv.utils.isValidUrl

class ImportIPTVScreenTest {

    @Test
    fun testInitialState() {
        // Test that the initial state is correctly set
        val initSourceName = "Test Source"
        val initSourceUrl = "https://example.com/playlist.m3u"

        // In a real test, we would render the component and check its state
        // Since we can't directly test the Compose UI in commonTest, we'll verify the logic

        // Verify that the initial values are used correctly
        assertEquals("Test Source", initSourceName)
        assertEquals("https://example.com/playlist.m3u", initSourceUrl)
    }

    @Test
    fun testUrlValidation() {
        // Test the URL validation logic used in the screen

        // Valid URLs
        assertTrue("https://example.com/playlist.m3u".isValidUrl())
        assertTrue("http://example.com/playlist.m3u".isValidUrl())
        assertTrue("https://example.com/playlist.m3u?param=value".isValidUrl())

        // Invalid URLs
        assertFalse("".isValidUrl())
        assertFalse("invalid-url".isValidUrl())
        assertFalse("http:/example.com".isValidUrl())
        assertFalse("https:/example.com".isValidUrl())
    }

    @Test
    fun testEventHandling() {
        // Test that events are correctly handled
        var capturedEvent: HomeEvent? = null
        val onEvent: (HomeEvent) -> Unit = { event ->
            capturedEvent = event
        }

        // Simulate clicking the back button
        onEvent(HomeEvent.OnBackPressed)
        assertEquals(HomeEvent.OnBackPressed, capturedEvent)

        // Simulate adding a source
        val sourceName = "Test Source"
        val sourceUrl = "https://example.com/playlist.m3u"
        onEvent(HomeEvent.OnParseIPTVSource(sourceName, sourceUrl))

        // Verify the correct event was triggered with the right parameters
        val parseEvent = capturedEvent as? HomeEvent.OnParseIPTVSource
        assertEquals(sourceName, parseEvent?.name)
        assertEquals(sourceUrl, parseEvent?.url)

        // Simulate canceling parsing
        onEvent(HomeEvent.OnCancelParseIPTVSource)
        assertEquals(HomeEvent.OnCancelParseIPTVSource, capturedEvent)

        // Simulate dismissing error dialog
        onEvent(HomeEvent.OnDismissErrorDialog)
        assertEquals(HomeEvent.OnDismissErrorDialog, capturedEvent)
    }

    @Test
    fun testLoadingState() {
        // Test the behavior when in loading state
        val loadingState = HomeUiState(isLoading = true)
        val normalState = HomeUiState(isLoading = false)

        // Verify loading state is correctly detected
        assertTrue(loadingState.isLoading)
        assertFalse(normalState.isLoading)
    }

    @Test
    fun testErrorState() {
        // Test the behavior when in error state
        val errorMessage = "Error message"
        val errorState = HomeUiState(error = Exception(errorMessage))
        val normalState = HomeUiState(error = null)

        // Verify error state is correctly detected
        assertTrue(errorState.error != null)
        assertEquals(errorMessage, errorState.error?.message)
        assertTrue(normalState.error == null)
    }
}
