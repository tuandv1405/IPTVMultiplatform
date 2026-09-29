package tss.t.tsiptv.core.parser.tsiptv

import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.asset
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.channel
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.doc
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.success
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_DRM
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_DUPLICATE_ID
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_HEADER_FORBIDDEN
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_INCLUDE
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_INCLUDE_CYCLE
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_ITEM_ID
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_ITEM_NAME
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_NO_STREAM
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_URL
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.W_FIELD
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.W_UNKNOWN_TYPE
import tss.t.tsiptv.core.parser.model.playback.DrmSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** AC-T3 (one item per item error code), AC-T4 (forward compatibility), AC-T6 (http(s) only). */
class TsiptvItemErrorsTest {

    // AC-T3
    @Test
    fun everyItemErrorCodeIsListedWithItsPathAndTheValidItemsAreKept() {
        val (doc, report) = success(asset("item-errors.tsiptv.json"), url = "https://example.com/item-errors.tsiptv.json")
        assertEquals(
            listOf(
                E_URL to "channels[1].url",
                E_NO_STREAM to "channels[1]",
                E_ITEM_ID to "channels[2].id",
                E_ITEM_ID to "channels[3].id",
                E_DUPLICATE_ID to "channels[4].id",
                E_ITEM_NAME to "channels[5].name",
                E_NO_STREAM to "channels[6]",
                E_HEADER_FORBIDDEN to "channels[7].headers.Host",
                E_DRM to "channels[8].drm.licenseUrl",
                E_NO_STREAM to "channels[8]",
                E_DRM to "channels[9].streams[0].drm.keys",
                E_URL to "movies[0].poster",
                E_INCLUDE to "includes[0].type",
                E_INCLUDE to "includes[2].url",
                E_INCLUDE_CYCLE to "includes[3].url",
            ),
            report.codes()
        )
        assertTrue(report.issues.all { it.level == TsiptvIssueLevel.ITEM })
        assertEquals(listOf("ok-1", "forbidden-header", "one-bad-stream"), doc.channels.map { it.id })
        assertEquals(7 + 3, report.skippedItemCount)

        val hostless = doc.channels.first { it.id == "forbidden-header" }.streams.single()
        assertEquals(mapOf("Referer" to "https://example.com/"), hostless.headers)
        val backup = doc.channels.first { it.id == "one-bad-stream" }.streams.single()
        assertEquals("https://cdn.example.com/clear.m3u8", backup.url)
        assertEquals("Backup", backup.name?.resolve(null))

        val movie = doc.movies.single()
        assertNull(movie.poster)
        assertEquals(listOf("live"), doc.includes.map { it.id })

        // Details lines: `path — message`.
        assertTrue(report.details().first().detailLine().startsWith("channels[1].url — "))
    }

    @Test
    fun withoutADocumentUrlASelfIncludeIsNotDetectable() {
        val (doc, report) = success(asset("item-errors.tsiptv.json"))
        assertFalse(report.has(E_INCLUDE_CYCLE))
        assertEquals(listOf("live", "self"), doc.includes.map { it.id })
    }

    @Test
    fun uniquenessSpansChannelsMoviesSeriesEpisodesAndIncludes() {
        val movie = """{"id":"shared","name":"M","url":"https://cdn.example.com/m.mp4"}"""
        val series = """{"id":"s","name":"S","seasons":[{"number":1,"episodes":[
            {"id":"shared","number":1,"name":"E1","url":"https://cdn.example.com/1.mp4"},
            {"id":"e2","number":2,"name":"E2","url":"https://cdn.example.com/2.mp4"}]}]}"""
        val (doc, report) = success(
            doc(
                """"channels":[${channel("shared")}]""",
                """"movies":[$movie]""",
                """"series":[$series]""",
                """"includes":[{"id":"e2","type":"m3u","url":"https://example.com/a.m3u"},{"id":"s","type":"m3u","url":"https://example.com/b.m3u"}]""",
            )
        )
        assertEquals(
            listOf(
                E_DUPLICATE_ID to "movies[0].id",
                E_DUPLICATE_ID to "series[0].seasons[0].episodes[0].id",
                E_DUPLICATE_ID to "includes[0].id",
                E_DUPLICATE_ID to "includes[1].id",
            ),
            report.codes()
        )
        assertEquals(1, doc.channels.size)
        assertTrue(doc.movies.isEmpty())
        assertEquals(listOf("e2"), doc.series.single().seasons.single().episodes.map { it.id })
        assertTrue(doc.includes.isEmpty())
    }

    @Test
    fun aDroppedItemDoesNotClaimItsId() {
        val (doc, report) = success(
            doc(
                """"channels":[${channel("x", url = "rtmp://host/app")}, ${channel("x")}]""",
            )
        )
        assertEquals(listOf(E_URL to "channels[0].url", E_NO_STREAM to "channels[0]"), report.codes())
        assertEquals("https://cdn.example.com/x.m3u8", doc.channels.single().streams.single().url)
    }

    @Test
    fun anItemThatIsNotAnObjectIsAnIdError() {
        val (doc, report) = success(doc(""""channels":["https://cdn.example.com/a.m3u8", ${channel("b")}]"""))
        assertEquals(listOf(E_ITEM_ID to "channels[0]"), report.codes())
        assertEquals(1, doc.channels.size)
    }

    // AC-T4
    @Test
    fun forwardCompatibility() {
        val (doc, report) = success(asset("forward-compat.tsiptv.json"))
        report.assertOnly(
            // Keys of a localized object are data (§4): `x-note` is an invalid language key.
            TsiptvIssueCode.W_TEXT to "meta.name[1]",
            W_UNKNOWN_TYPE to "channels[0].mimeType",
            W_UNKNOWN_TYPE to "channels[0].type",
            W_UNKNOWN_TYPE to "channels[0].catchup.mode",
            W_UNKNOWN_TYPE to "channels[1].streams[0].drm.system",
            E_INCLUDE to "includes[0].type",
            W_UNKNOWN_TYPE to "appearance.card.style",
            W_UNKNOWN_TYPE to "appearance.card.corner",
            W_UNKNOWN_TYPE to "layout.home[0].type",
            W_UNKNOWN_TYPE to "layout.home[1].query.from",
            W_UNKNOWN_TYPE to "layout.home[2].query.sort",
            W_UNKNOWN_TYPE to "layout.home[3].card.style",
        )
        assertEquals(mapOf("en" to "Forward compatibility"), doc.meta.name.values)
        assertEquals("QA", doc.meta.author?.name)

        // Unknown card style / corner → auto / medium.
        assertEquals(TsiptvCardStyle(TsiptvCardKind.AUTO, TsiptvCorner.MEDIUM), doc.appearance?.card)

        // Unknown section type and query source → skipped; unknown sort → source.
        val sections = assertNotNull(doc.layout).home
        assertEquals(listOf("Random order", "All"), sections.map { it.title?.resolve("en") })
        assertEquals(TsiptvQuerySort.SOURCE, sections[0].query?.sort)
        assertEquals(TsiptvCardKind.AUTO, sections[1].card?.style)

        // Unknown channel type → tv; unknown MIME hint ignored; unknown catch-up → none, live stays.
        val podcast = doc.channels.first { it.id == "future-type" }
        assertEquals(TsiptvChannelType.TV, podcast.type)
        assertNull(podcast.streams.single().mimeType)
        assertNull(podcast.catchup)

        // Unknown DRM system → kept, but never playable without DRM.
        val fairplay = doc.channels.first { it.id == "future-drm" }.streams.single()
        val drm = assertNotNull(fairplay.drm)
        assertFalse(drm.isSupported)
        assertNull(drm.system)
        assertEquals("https://cdn.example.com/fp.m3u8", fairplay.url)
        assertEquals(drm, doc.channels.first { it.id == "future-drm" }.toIPTVChannel().drm)

        // Unknown include type → skipped (E_INCLUDE); the xmltv include with unknown members stays.
        assertEquals(listOf("guide"), doc.includes.map { it.id })
    }

    // AC-T6
    @Test
    fun onlyHttpAndHttpsUrlsAnywhere() {
        val (doc, report) = success(asset("bad-urls.tsiptv.json"))
        report.assertOnly(
            E_URL to "meta.logo",
            E_URL to "meta.homepage",
            E_URL to "channels[0].url",
            E_NO_STREAM to "channels[0]",
            E_URL to "channels[1].url",
            E_NO_STREAM to "channels[1]",
            E_URL to "channels[2].streams[0].url",
            E_URL to "channels[2].streams[1].drm.licenseUrl",
            E_URL to "channels[2].logo",
            E_URL to "movies[0].poster",
            E_URL to "movies[0].backdrop",
            E_URL to "movies[0].subtitles[0].url",
            E_INCLUDE to "includes[0].url",
            E_INCLUDE to "includes[1].url",
            E_URL to "epg[0]",
            E_URL to "appearance.background.image",
            E_URL to "layout.home[0].items[0].image",
        )
        assertNull(doc.meta.logo)
        assertNull(doc.meta.homepage)
        val mixed = doc.channels.single()
        assertEquals(listOf("https://cdn.example.com/clear.m3u8"), mixed.streams.map { it.url })
        assertNull(mixed.logo)
        val movie = doc.movies.single()
        assertNull(movie.poster)
        assertNull(movie.backdrop)
        assertEquals(listOf("vi"), movie.subtitles.map { it.language })
        assertEquals(TsiptvSubtitleFormat.SRT, movie.subtitles.single().format)
        assertEquals(listOf("ok"), doc.includes.map { it.id })
        assertEquals(listOf(1), doc.epg.map { it.index })
        assertEquals(12, doc.epg.single().refreshHours)
        assertEquals("epg-1", doc.epgIncludes().single().id)
        assertNull(doc.appearance?.background?.image)
        assertEquals(listOf("HTTPS://images.example.com/ok.jpg"), doc.layout?.home?.single()?.items?.map { it.image })
    }

    @Test
    fun urlLengthLimit() {
        val longUrl = "https://cdn.example.com/" + "a".repeat(TsiptvLimits.MAX_URL_CHARS - 24)
        assertEquals(TsiptvLimits.MAX_URL_CHARS, longUrl.length)
        success(doc(""""channels":[${channel("a", url = longUrl)}]"""))
        val (_, report) = success(doc(""""channels":[${channel("a", url = longUrl + "b")}, ${channel("b")}]"""))
        assertEquals(listOf(E_URL to "channels[0].url", E_NO_STREAM to "channels[0]"), report.codes())
    }

    @Test
    fun drmValidation() {
        fun drmOf(drm: String): Pair<TsiptvSourceDocument, TsiptvValidationReport> =
            success(doc(""""channels":[${channel("a", """"drm":$drm""")}, ${channel("b")}]"""))

        val (wv, wvReport) = drmOf("""{"system":"widevine","licenseUrl":"https://lic.example.com/wv","licenseHeaders":{"X-Token":"t"},"keys":{"000102030405060708090a0b0c0d0e0f":"00112233445566778899aabbccddeeff"}}""")
        val wvSpec = assertNotNull(wv.channels.first().streams.single().drm)
        assertEquals(DrmSystem.WIDEVINE, wvSpec.system)
        assertEquals("https://lic.example.com/wv", wvSpec.licenseUrl)
        assertEquals(mapOf("X-Token" to "t"), wvSpec.licenseHeaders)
        assertTrue(wvSpec.clearKeys.isEmpty())
        assertEquals(listOf(W_FIELD to "channels[0].drm.keys"), wvReport.codes())

        val (pr, _) = drmOf("""{"system":"PlayReady","licenseUrl":"https://lic.example.com/pr"}""")
        assertEquals(DrmSystem.PLAYREADY, pr.channels.first().streams.single().drm?.system)

        val (ck, ckReport) = drmOf("""{"system":"clearkey","keys":{"000102030405060708090A0B0C0D0E0F":"00112233445566778899AABBCCDDEEFF"}}""")
        assertTrue(ckReport.issues.isEmpty())
        val ckSpec = assertNotNull(ck.channels.first().streams.single().drm)
        assertEquals("000102030405060708090a0b0c0d0e0f", ckSpec.clearKeys.single().kid)
        assertEquals("00112233445566778899aabbccddeeff", ckSpec.clearKeys.single().key)
        assertTrue(ckSpec.isSupported)

        val (ckUrl, _) = drmOf("""{"system":"clearkey","licenseUrl":"https://lic.example.com/ck"}""")
        assertEquals("https://lic.example.com/ck", ckUrl.channels.first().streams.single().drm?.licenseUrl)

        for ((drm, path) in listOf(
            """"widevine"""" to "channels[0].drm",
            """{}""" to "channels[0].drm.system",
            """{"system":"playready"}""" to "channels[0].drm.licenseUrl",
            """{"system":"clearkey"}""" to "channels[0].drm",
            """{"system":"clearkey","keys":{}}""" to "channels[0].drm.keys",
            """{"system":"clearkey","keys":{"0001":"0011"}}""" to "channels[0].drm.keys",
            """{"system":"clearkey","keys":{"000102030405060708090a0b0c0d0e0f":42}}""" to "channels[0].drm.keys",
            """{"system":"clearkey","keys":{${(1..21).joinToString(",") { "\"${it.toString().padStart(32, '0')}\":\"00112233445566778899aabbccddeeff\"" }}}}""" to "channels[0].drm.keys",
        )) {
            val (d, r) = drmOf(drm)
            assertEquals(listOf(E_DRM to path, E_NO_STREAM to "channels[0]"), r.codes(), drm)
            assertEquals(listOf("b"), d.channels.map { it.id })
        }
    }

    @Test
    fun headerRules() {
        val headers = """{"host":"h","CONTENT-LENGTH":"1","Connection":"close","Transfer-Encoding":"chunked",
            "Bad Header":"x","Referer":"https://example.com/","X-Enc":"a%20b","X-Api-Key":"k","X-Multi":"a\nb","X-Num":5}"""
        val (doc, report) = success(doc(""""channels":[${channel("a", """"headers":$headers""")}]"""))
        report.assertOnly(
            E_HEADER_FORBIDDEN to "channels[0].headers.host",
            E_HEADER_FORBIDDEN to "channels[0].headers.CONTENT-LENGTH",
            E_HEADER_FORBIDDEN to "channels[0].headers.Connection",
            E_HEADER_FORBIDDEN to "channels[0].headers.Transfer-Encoding",
            E_HEADER_FORBIDDEN to "channels[0].headers[4]",
            W_FIELD to "channels[0].headers.X-Multi",
            W_FIELD to "channels[0].headers.X-Num",
        )
        // Sent as written: not URL-decoded, x- names are headers, not extension members.
        assertEquals(
            mapOf("Referer" to "https://example.com/", "X-Enc" to "a%20b", "X-Api-Key" to "k"),
            doc.channels.single().streams.single().headers
        )
    }

    @Test
    fun atMostTwentyHeaders() {
        val headers = (1..21).joinToString(",", "{", "}") { "\"X-H$it\":\"$it\"" }
        val (doc, report) = success(doc(""""channels":[${channel("a", """"headers":$headers""")}]"""))
        assertEquals(listOf(TsiptvIssueCode.W_LIMIT to "channels[0].headers"), report.codes())
        assertEquals(20, doc.channels.single().streams.single().headers.size)
        assertFalse("X-H21" in doc.channels.single().streams.single().headers)
    }

    @Test
    fun includeValidation() {
        val includes = """[
            {"id":"a","type":"m3u","url":"https://example.com/a.m3u","headers":{"User-Agent":"UA"},"refreshHours":6,"name":{"en":"A","vi":"Á"}},
            {"id":"b","type":"stremio","url":"https://addon.example.com/path/manifest.json?token=1","headers":{"X":"y"}},
            {"id":"c","type":"XMLTV","url":"https://example.com/g.xml","refreshHours":0},
            {"id":"d:e","type":"m3u","url":"https://example.com/d.m3u"},
            {"type":"m3u","url":"https://example.com/d.m3u"},
            {"id":"f","url":"https://example.com/f.m3u"},
            {"id":"g","type":"m3u"},
            {"id":"h","type":"stremio","url":"https://addon.example.com/manifest.json.bak"},
            "https://example.com/i.m3u"
        ]"""
        val (doc, report) = success(doc(""""includes":$includes"""))
        assertEquals(
            listOf(
                W_FIELD to "includes[1].headers",
                W_FIELD to "includes[2].refreshHours",
                E_INCLUDE to "includes[3].id",
                E_INCLUDE to "includes[4].id",
                E_INCLUDE to "includes[5].type",
                E_INCLUDE to "includes[6].url",
                E_INCLUDE to "includes[7].url",
                E_INCLUDE to "includes[8]",
            ),
            report.codes()
        )
        assertEquals(listOf("a", "b", "c"), doc.includes.map { it.id })
        val a = doc.includes[0]
        assertEquals(mapOf("User-Agent" to "UA"), a.headers)
        assertEquals(6, a.refreshHours)
        assertEquals("Á", a.name?.resolve("vi"))
        assertTrue(doc.includes[1].headers.isEmpty())
        assertEquals(TsiptvIncludeType.XMLTV, doc.includes[2].type)
        assertEquals(24, doc.includes[2].refreshHours)
    }
}
