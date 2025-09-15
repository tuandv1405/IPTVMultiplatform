package tss.t.tsiptv.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringExtTest {
    
    @Test
    fun testIsValidUrl() {
        // Valid URLs
        assertTrue("https://m.cnbuy.app/pages/system/down-load".isValidUrl())
        assertTrue("https://m.cnbuy.app/".isValidUrl())
        assertTrue("https://m.cnbuy.app/parse".isValidUrl())
        assertTrue("http://m.cnbuy.app/parse".isValidUrl())
        assertTrue("https://m.cnbuy.app/parse?q=a".isValidUrl())
        assertTrue("http://m.cnbuy.app/parse?q=a".isValidUrl())
        assertTrue("http://tth.vn/parse".isValidUrl())
        assertTrue("http://tth.vn".isValidUrl())

        // Invalid URLs
        assertFalse("https:/m.cnbuy.app/parse".isValidUrl())
        assertFalse("http:/m.cnbuy.app/parse".isValidUrl())
        
        // Additional test cases
        assertFalse("invalid-url".isValidUrl())
        assertFalse("http:m.cnbuy.app".isValidUrl())
        assertFalse("https:m.cnbuy.app".isValidUrl())
    }
}