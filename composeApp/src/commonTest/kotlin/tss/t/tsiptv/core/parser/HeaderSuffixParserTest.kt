package tss.t.tsiptv.core.parser

import tss.t.tsiptv.core.parser.iptv.m3u.HeaderSuffixParser
import kotlin.test.Test
import kotlin.test.assertEquals

class HeaderSuffixParserTest {

    @Test
    fun splitsAtTheFirstPipeOnly() {
        val split = HeaderSuffixParser.split("https://cdn.example.com/a.m3u8|X-A=1|2")
        assertEquals("https://cdn.example.com/a.m3u8", split.url)
        assertEquals(listOf("X-A" to "1|2"), split.headers)
    }

    @Test
    fun urlWithoutSuffixIsUnchanged() {
        val split = HeaderSuffixParser.split("  rtmp://live.example.com/app/stream  ")
        assertEquals("rtmp://live.example.com/app/stream", split.url)
        assertEquals(emptyList(), split.headers)
    }

    @Test
    fun kodiFieldsAreMapped() {
        assertEquals(
            listOf("X-Custom" to "1", "Cookie" to "a=b", "User-Agent" to "UA"),
            HeaderSuffixParser.parseHeaderList("!X-Custom=1&cookies=a%3Db&seekable=0&user-agent=UA&=novalue&noequals")
        )
    }

    @Test
    fun percentDecoding() {
        assertEquals("Mozilla/5.0 (X11)", HeaderSuffixParser.percentDecode("Mozilla%2F5.0%20(X11)"))
        assertEquals("héllo", HeaderSuffixParser.percentDecode("h%C3%A9llo"))
        // Plus is data (base64 tokens), not a space.
        assertEquals("a+b", HeaderSuffixParser.percentDecode("a+b"))
        // A malformed escape keeps the raw text.
        assertEquals("100%zz", HeaderSuffixParser.percentDecode("100%zz"))
        assertEquals("tail%4", HeaderSuffixParser.percentDecode("tail%4"))
    }
}
