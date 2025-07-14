package tss.t.tsiptv.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class ManualUrlTest {

    @Test
    fun testUrlValidation() {
        // Valid URLs
        assertEquals(true, "https://m.cnbuy.app/pages/system/down-load".isValidUrl(), "https://m.cnbuy.app/pages/system/down-load should be valid")
        assertEquals(true, "https://m.cnbuy.app/".isValidUrl(), "https://m.cnbuy.app/ should be valid")
        assertEquals(true, "https://m.cnbuy.app/parse".isValidUrl(), "https://m.cnbuy.app/parse should be valid")
        assertEquals(true, "http://m.cnbuy.app/parse".isValidUrl(), "http://m.cnbuy.app/parse should be valid")
        assertEquals(true, "https://m.cnbuy.app/parse?q=a".isValidUrl(), "https://m.cnbuy.app/parse?q=a should be valid")
        assertEquals(true, "http://m.cnbuy.app/parse?q=a".isValidUrl(), "http://m.cnbuy.app/parse?q=a should be valid")
        assertEquals(true, "http://tth.vn/parse".isValidUrl(), "http://tth.vn/parse should be valid")
        assertEquals(true, "http://tth.vn".isValidUrl(), "http://tth.vn should be valid")

        // Invalid URLs
        assertEquals(false, "https:/m.cnbuy.app/parse".isValidUrl(), "https:/m.cnbuy.app/parse should be invalid")
        assertEquals(false, "http:/m.cnbuy.app/parse".isValidUrl(), "http:/m.cnbuy.app/parse should be invalid")
        assertEquals(false, "invalid-url".isValidUrl(), "invalid-url should be invalid")
        assertEquals(false, "http:m.cnbuy.app".isValidUrl(), "http:m.cnbuy.app should be invalid")
        assertEquals(false, "https:m.cnbuy.app".isValidUrl(), "https:m.cnbuy.app should be invalid")
    }
}
