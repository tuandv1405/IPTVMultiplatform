package tss.t.tsiptv.core.parser.tsiptv

import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.doc
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.success
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_INCLUDE
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_NO_STREAM
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_URL
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.W_FIELD
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regression tests for QC round 4. */
class TsiptvQcRound4Test {

    // 1: ECMA whitespace inside a URL is invalid in every URL field; outer whitespace is trimmed.
    @Test
    fun ecmaWhitespaceInsideUrlsIsRejected() {
        for (ws in listOf('\u00A0', '\uFEFF', '\u2028', '\u2029', '\u3000', '\u2003', '\u1680')) {
            assertFalse(TsiptvRules.isHttpUrl("https://cdn.example.com/a${ws}b.m3u8"), "U+" + ws.code.toString(16))
        }
        assertTrue(TsiptvRules.isHttpUrl("https://cdn.example.com/a.m3u8"))

        val bad = "https://cdn.example.com/a\\u00A0b" // a JSON \u00A0 escape
        val (document, report) = success(
            doc(
                """"channels":[
                  {"id":"a","name":"A","url":"$bad"},
                  {"id":"b","name":"B","logo":"https://img.example.com/\u3000.png","url":"\u00A0https://cdn.example.com/b.m3u8\uFEFF"},
                  {"id":"c","name":"C","streams":[{"url":"https://cdn.example.com/c.mpd","drm":{"system":"widevine","licenseUrl":"https://lic.example.com/a\u2028b"}},{"url":"https://cdn.example.com/c.m3u8"}]}
                ]""",
                """"movies":[{"id":"m","name":"M","url":"https://cdn.example.com/m.mp4","subtitles":[{"url":"https://cdn.example.com/\u2029.vtt","language":"en"}]}]""",
                """"includes":[{"id":"i","type":"m3u","url":"https://example.com/\u00A0.m3u"}]""",
                """"epg":["https://example.com/\uFEFF.xml"]""",
                """"layout":{"home":[{"type":"hero","items":[{"image":"https://img.example.com/\u00A0.jpg"},{"image":"https://img.example.com/ok.jpg"}]}]}""",
                meta = """{"name":"N","homepage":"https://example.com/a\u3000b"}""",
            )
        )
        assertEquals(
            listOf(
                E_URL to "meta.homepage",
                E_URL to "channels[0].url",
                E_NO_STREAM to "channels[0]",
                E_URL to "channels[1].logo",
                E_URL to "channels[2].streams[0].drm.licenseUrl",
                E_URL to "movies[0].subtitles[0].url",
                E_INCLUDE to "includes[0].url",
                E_URL to "epg[0]",
                E_URL to "layout.home[0].items[0].image",
            ),
            report.codes()
        )
        // Leading/trailing ECMA whitespace is trimmed first, so this URL is fine.
        assertEquals("https://cdn.example.com/b.m3u8", document.channels.first { it.id == "b" }.streams.single().url)
    }

    // 2: C0 controls in short strings → spaces, then trimmed, with W_FIELD; the value stays.
    @Test
    fun controlCharactersInShortStrings() {
        val (document, report) = success(
            doc(
                """"channels":[{"id":"a","name":"A","url":"https://cdn.example.com/a.m3u8","epgId":"News\tUS",
                   "groups":["\u0007News\n","\tHD"],"tags":["a\u0000b"],"streams":null}]""",
                """"movies":[{"id":"m","name":"M","url":"https://cdn.example.com/m.mp4","originalName":"Orig\r\nName",
                   "ageRating":"PG\u001F13","genres":["Drama\t"],"cast":["A\nB"],"directors":["\u0001D"]}]""",
                """"includes":[{"id":"addon","type":"stremio","url":"https://addon.example.com/manifest.json"}]""",
                """"layout":{"home":[{"type":"row","query":{"from":"catalog","include":"addon","catalog":{"type":"mo\u0009vie","id":"top\n"}}}]}""",
                meta = """{"name":"N","license":"CC0\n1.0","author":{"name":"\tAuthor\u0002"}}""",
            )
        )
        assertEquals(
            listOf(
                W_FIELD to "meta.author.name",
                W_FIELD to "meta.license",
                W_FIELD to "channels[0].groups[0]",
                W_FIELD to "channels[0].groups[1]",
                W_FIELD to "channels[0].epgId",
                W_FIELD to "channels[0].tags[0]",
                W_FIELD to "movies[0].originalName",
                W_FIELD to "movies[0].genres[0]",
                W_FIELD to "movies[0].cast[0]",
                W_FIELD to "movies[0].directors[0]",
                W_FIELD to "movies[0].ageRating",
                W_FIELD to "layout.home[0].query.catalog.type",
                W_FIELD to "layout.home[0].query.catalog.id",
            ),
            report.codes()
        )
        val ch = document.channels.single()
        assertEquals(listOf("News", "HD"), ch.groups)
        assertEquals("News US", ch.epgId)
        assertEquals(listOf("a b"), ch.tags)
        val m = document.movies.single()
        assertEquals("Orig  Name", m.originalName)
        assertEquals("PG 13", m.ageRating)
        assertEquals(listOf("Drama"), m.genres)
        assertEquals(listOf("A B"), m.cast)
        assertEquals(listOf("D"), m.directors)
        assertEquals("Author", document.meta.author?.name)
        assertEquals("CC0 1.0", document.meta.license)
        assertEquals(TsiptvCatalogRef("mo vie", "top"), document.layout?.home?.single()?.query?.catalog)

        // A value made only of controls and whitespace is still empty after trimming → invalid.
        val (d2, r2) = success(doc(""""channels":[{"id":"a","name":"A","url":"https://cdn.example.com/a.m3u8","epgId":"\t\n"}]"""))
        assertNull(d2.channels.single().epgId)
        assertEquals(listOf(W_FIELD to "channels[0].epgId", W_FIELD to "channels[0].epgId"), r2.codes())
    }
}
