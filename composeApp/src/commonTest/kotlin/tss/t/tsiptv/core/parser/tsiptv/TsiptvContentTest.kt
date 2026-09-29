package tss.t.tsiptv.core.parser.tsiptv

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tss.t.tsiptv.core.parser.model.playback.CatchupMode
import tss.t.tsiptv.core.parser.model.playback.CatchupSpec
import tss.t.tsiptv.core.parser.model.playback.DrmSystem
import tss.t.tsiptv.core.parser.model.playback.StreamMimeTypes
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.channel
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.doc
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.docWithChannel
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.success
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_DRM
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_DUPLICATE_ID
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_ITEM_ID
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_ITEM_NAME
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_NO_STREAM
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_URL
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.W_FIELD
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.W_LIMIT
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.W_TEXT
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.W_UNKNOWN_TYPE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Shorthands and defaults of §8.0 (AC-T5), content fields of §8.1–§8.7, Text rules of §5.2. */
class TsiptvContentTest {

    private fun oneChannel(extra: String, url: String? = "https://cdn.example.com/a.m3u8"): Pair<TsiptvChannel, TsiptvValidationReport> {
        val (doc, report) = success(doc(""""channels":[${channel("a", extra, url)}]"""))
        return doc.channels.single() to report
    }

    // AC-T5
    @Test
    fun urlShorthandBecomesOneStreamWithTheItemDefaults() {
        val (ch, report) = oneChannel(
            """"headers":{"User-Agent":"UA"},"mimeType":"application/vnd.apple.mpegurl",
               "drm":{"system":"widevine","licenseUrl":"https://lic.example.com/wv"}"""
        )
        assertTrue(report.issues.isEmpty())
        val stream = ch.streams.single()
        assertEquals("https://cdn.example.com/a.m3u8", stream.url)
        assertEquals(mapOf("User-Agent" to "UA"), stream.headers)
        assertEquals(StreamMimeTypes.HLS, stream.mimeType)
        assertEquals(DrmSystem.WIDEVINE, stream.drm?.system)
    }

    // AC-T5
    @Test
    fun itemHeadersAreMergedUnderStreamHeadersStreamWinsPerName() {
        val (ch, report) = oneChannel(
            """"headers":{"User-Agent":"item-UA","Referer":"https://item.example.com/"},
               "mimeType":"application/dash+xml",
               "drm":{"system":"widevine","licenseUrl":"https://lic.example.com/item"},
               "streams":[
                 {"url":"https://cdn.example.com/1.mpd","headers":{"user-agent":"stream-UA","X-Extra":"1"}},
                 {"url":"https://cdn.example.com/2.m3u8","mimeType":"application/x-mpegURL",
                  "drm":{"system":"clearkey","keys":{"000102030405060708090a0b0c0d0e0f":"00112233445566778899aabbccddeeff"}}}
               ]""",
            url = null,
        )
        assertTrue(report.issues.isEmpty(), report.codes().toString())
        val (first, second) = ch.streams
        assertEquals(
            mapOf("Referer" to "https://item.example.com/", "user-agent" to "stream-UA", "X-Extra" to "1"),
            first.headers
        )
        assertEquals(StreamMimeTypes.DASH, first.mimeType)
        assertEquals("https://lic.example.com/item", first.drm?.licenseUrl)

        assertEquals(mapOf("User-Agent" to "item-UA", "Referer" to "https://item.example.com/"), second.headers)
        assertEquals(StreamMimeTypes.HLS, second.mimeType)
        // A stream's drm replaces the item's entirely.
        val drm = assertNotNull(second.drm)
        assertEquals(DrmSystem.CLEARKEY, drm.system)
        assertNull(drm.licenseUrl)

        // What step 3 stores as streamsJson carries the merged values.
        val json = Json.encodeToString(ch.streams)
        assertTrue("stream-UA" in json && "item.example.com" in json)
        assertEquals(ch.streams, Json.decodeFromString<List<TsiptvStream>>(json))
    }

    @Test
    fun anInvalidItemDrmDropsOnlyTheStreamsThatInheritIt() {
        val (ch, report) = oneChannel(
            """"drm":{"system":"widevine"},
               "streams":[
                 {"url":"https://cdn.example.com/inherits.mpd"},
                 {"url":"https://cdn.example.com/own.mpd","drm":{"system":"playready","licenseUrl":"https://lic.example.com/pr"}}
               ]""",
            url = null,
        )
        assertEquals(listOf(E_DRM to "channels[0].drm.licenseUrl"), report.codes())
        assertEquals(listOf("https://cdn.example.com/own.mpd"), ch.streams.map { it.url })
    }

    @Test
    fun streamsLimitsAndShapes() {
        val eleven = (1..11).joinToString(",", "[", "]") { """{"url":"https://cdn.example.com/$it.m3u8","quality":"HD","language":"vi"}""" }
        val (ch, report) = oneChannel(""""streams":$eleven""", url = null)
        assertEquals(listOf(W_LIMIT to "channels[0].streams"), report.codes())
        assertEquals(10, ch.streams.size)
        assertEquals("HD", ch.streams.first().quality)
        assertEquals("vi", ch.streams.first().language)

        val (doc, r2) = success(
            doc(""""channels":[${channel("empty", """"streams":[]""", url = null)}, ${channel("str", """"streams":"https://x.example.com/a"""", url = null)}, ${channel("ok")}]""")
        )
        assertEquals(listOf(E_NO_STREAM to "channels[0].streams", E_NO_STREAM to "channels[1].streams"), r2.codes())
        assertEquals(listOf("ok"), doc.channels.map { it.id })

        val (ch3, r3) = oneChannel(""""streams":[{"url":"https://cdn.example.com/a.m3u8","quality":"${"q".repeat(33)}","language":"English","name":{"en":"1080p"},"mimeType":"not a mime"}]""", url = null)
        assertEquals(
            listOf(W_FIELD to "channels[0].streams[0].mimeType", W_FIELD to "channels[0].streams[0].quality", W_FIELD to "channels[0].streams[0].language"),
            r3.codes()
        )
        assertEquals("1080p", ch3.streams.single().name?.resolve("fr"))
    }

    @Test
    fun mimeTypeHints() {
        for ((given, expected) in listOf(
            "application/x-mpegURL" to StreamMimeTypes.HLS,
            "application/vnd.apple.mpegurl" to StreamMimeTypes.HLS,
            "application/dash+xml" to StreamMimeTypes.DASH,
            "video/mp2t" to StreamMimeTypes.MPEG_TS,
            "video/webm" to "video/webm",
            "video/x-matroska" to "video/x-matroska",
            "audio/mpeg" to "audio/mpeg",
            "audio/ogg" to "audio/ogg",
        )) {
            assertEquals(expected, oneChannel(""""mimeType":"$given"""").first.streams.single().mimeType, given)
        }
        val (ch, report) = oneChannel(""""mimeType":"application/vnd.ms-sstr+xml"""")
        assertNull(ch.streams.single().mimeType)
        assertEquals(listOf(W_UNKNOWN_TYPE to "channels[0].mimeType"), report.codes())
        assertEquals(listOf(W_FIELD to "channels[0].mimeType"), oneChannel(""""mimeType":"Video/MP4"""").second.codes())
    }

    @Test
    fun channelFields() {
        val (ch, report) = oneChannel(
            """"type":"radio","number":7,"logo":"https://img.example.com/l.png","groups":["News","HD"],
               "epgId":"News.us","epgShiftHours":-2.5,"description":"Line one\nLine two","tags":["a","b"]"""
        )
        assertTrue(report.issues.isEmpty())
        assertEquals(TsiptvChannelType.RADIO, ch.type)
        assertEquals(7, ch.number)
        assertEquals(-2.5, ch.epgShiftHours)
        assertEquals("Line one\nLine two", ch.description?.resolve(null))
        val mapped = ch.toIPTVChannel(id = "ts:p1:a", uiLanguage = "vi")
        assertEquals("ts:p1:a", mapped.id)
        assertEquals("Channel a", mapped.name)
        assertEquals("News", mapped.groupTitle)
        assertEquals(listOf("News", "HD"), mapped.groups)
        assertEquals("News.us", mapped.epgId)
        assertEquals(7, mapped.number)
        assertTrue(mapped.isRadio)
        assertEquals("https://img.example.com/l.png", mapped.logoUrl)
    }

    @Test
    fun wrongOptionalFieldsAreDroppedAndTheItemStays() {
        val (ch, report) = oneChannel(
            """"number":-5,"epgShiftHours":15,"epgId":"","tags":"news",
               "groups":["A","","${"g".repeat(101)}",3,"B","C","D","E","F","G","H","I","J","K"]"""
        )
        assertEquals(
            listOf(
                W_FIELD to "channels[0].number",
                W_FIELD to "channels[0].groups[1]",
                W_FIELD to "channels[0].groups[2]",
                W_FIELD to "channels[0].groups[3]",
                W_LIMIT to "channels[0].groups",
                W_FIELD to "channels[0].epgId",
                W_FIELD to "channels[0].epgShiftHours",
                W_FIELD to "channels[0].tags",
            ),
            report.codes()
        )
        assertNull(ch.number)
        assertEquals(listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J"), ch.groups)
        assertNull(ch.epgShiftHours)
        assertTrue(ch.tags.isEmpty())
    }

    @Test
    fun catchup() {
        assertEquals(
            CatchupSpec(CatchupMode.SHIFT, days = 3),
            oneChannel(""""catchup":{"mode":"shift","days":3}""").first.catchup
        )
        assertEquals(
            CatchupSpec(CatchupMode.DEFAULT, source = "https://arc.example.com/{utc}/{duration}.m3u8", days = 7, correctionHours = 1.5),
            oneChannel(""""catchup":{"mode":"default","source":"https://arc.example.com/{utc}/{duration}.m3u8","correctionHours":1.5}""").first.catchup
        )
        assertEquals(
            CatchupSpec(CatchupMode.APPEND, source = "?utc={utc}&lutc={lutc}", days = 7),
            oneChannel(""""catchup":{"mode":"append","source":"?utc={utc}&lutc={lutc}"}""").first.catchup
        )
        assertEquals(CatchupMode.FLUSSONIC_TS, oneChannel(""""catchup":{"mode":"flussonic-ts"}""").first.catchup?.mode)
        assertEquals(CatchupMode.XC, oneChannel(""""catchup":{"mode":"xc"}""").first.catchup?.mode)

        for ((catchup, expected) in listOf(
            """{"mode":"default"}""" to (W_FIELD to "channels[0].catchup.source"),
            """{"mode":"append"}""" to (W_FIELD to "channels[0].catchup.source"),
            """{"mode":"default","source":"rtmp://arc.example.com/{utc}"}""" to (W_FIELD to "channels[0].catchup.source"),
            """{"mode":"default","source":"/relative/{utc}"}""" to (W_FIELD to "channels[0].catchup.source"),
            """{"mode":"append","source":"?utc={utc} &x=1"}""" to (W_FIELD to "channels[0].catchup.source"),
            """{"mode":"default","source":42}""" to (W_FIELD to "channels[0].catchup.source"),
            """{"days":3}""" to (W_FIELD to "channels[0].catchup.mode"),
            """"shift"""" to (W_FIELD to "channels[0].catchup"),
        )) {
            val (ch, report) = oneChannel(""""catchup":$catchup""")
            assertNull(ch.catchup, catchup)
            assertEquals(listOf(expected), report.codes(), catchup)
            assertEquals(1, ch.streams.size, "live playback unaffected")
        }

        val (ch, report) = oneChannel(""""catchup":{"mode":"shift","days":61,"correctionHours":25}""")
        assertEquals(CatchupSpec(CatchupMode.SHIFT, days = 7, correctionHours = 0.0), ch.catchup)
        assertEquals(listOf(W_FIELD to "channels[0].catchup.days", W_FIELD to "channels[0].catchup.correctionHours"), report.codes())
    }

    @Test
    fun movieFields() {
        val movie = """{"id":"m","name":"M","originalName":"Original","year":3000,"releaseDate":"2026-02-30",
            "runtimeMinutes":0,"countries":["US","us","USA"],"languages":["en","EN"],"ageRating":"PG-13",
            "cast":["A","B"],"directors":["D"],"genres":["Drama"],"url":"https://cdn.example.com/m.mp4",
            "subtitles":[
              {"url":"https://cdn.example.com/m.vtt","language":"en","label":"English"},
              {"url":"https://cdn.example.com/m.srt?x=1","language":"vi","format":"vtt"},
              {"url":"https://cdn.example.com/m.sub","language":"fr","format":"ass"},
              {"url":"https://cdn.example.com/m2.vtt"}
            ]}"""
        val (doc, report) = success(doc(""""movies":[$movie]"""))
        assertEquals(
            listOf(
                W_FIELD to "movies[0].year",
                W_FIELD to "movies[0].releaseDate",
                W_FIELD to "movies[0].runtimeMinutes",
                W_FIELD to "movies[0].countries[1]",
                W_FIELD to "movies[0].countries[2]",
                W_FIELD to "movies[0].languages[1]",
                W_FIELD to "movies[0].subtitles[2].format",
                W_FIELD to "movies[0].subtitles[3].language",
            ),
            report.codes()
        )
        val m = doc.movies.single()
        assertNull(m.year)
        assertNull(m.releaseDate)
        assertEquals("Original", m.originalName)
        assertEquals(listOf("US"), m.countries)
        assertEquals(listOf("en"), m.languages)
        assertEquals(listOf("A", "B"), m.cast)
        assertEquals(
            listOf(TsiptvSubtitleFormat.VTT, TsiptvSubtitleFormat.VTT, TsiptvSubtitleFormat.VTT),
            m.subtitles.map { it.format }
        )
        assertEquals("English", m.subtitles.first().label?.resolve("en"))
    }

    @Test
    fun seriesSeasonsAndEpisodes() {
        fun ep(id: String, number: Int, extra: String = "") =
            """{"id":"$id","number":$number,"name":"E$number","url":"https://cdn.example.com/$id.mp4"$extra}"""
        val series = """[
          {"id":"s1","name":"S1","year":2001,"endYear":1999,"seasons":[
            {"number":0,"name":"Specials","episodes":[${ep("s1-sp", 1)}]},
            {"number":2,"episodes":[${ep("s1e3", 3)}, ${ep("s1e1", 1, ""","releaseDate":"2099-01-01"""")}, ${ep("s1e1b", 1)}]},
            {"number":2,"episodes":[${ep("dup-season", 1)}]},
            {"number":1000,"episodes":[${ep("bad-season", 1)}]},
            {"number":1,"episodes":[]},
            {"number":3,"episodes":[{"id":"no-number","name":"N","url":"https://cdn.example.com/n.mp4"}, {"id":"no-name","number":2,"url":"https://cdn.example.com/n.mp4"}]}
          ]},
          {"id":"s2","name":"S2","seasons":[{"number":1,"episodes":[{"id":"s2e1","number":1,"name":"E1","url":"rtmp://host/x"}]}]},
          {"id":"s3","name":"S3"},
          {"id":"s4","name":"S4","seasons":[{"number":1,"episodes":[${ep("s2e1", 1)}]}]}
        ]"""
        val (doc, report) = success(doc(""""series":$series"""))
        assertEquals(
            listOf(
                E_DUPLICATE_ID to "series[0].seasons[1].episodes[2].number",
                E_DUPLICATE_ID to "series[0].seasons[2].number",
                E_ITEM_ID to "series[0].seasons[3].number",
                E_NO_STREAM to "series[0].seasons[4].episodes",
                E_ITEM_ID to "series[0].seasons[5].episodes[0].number",
                E_ITEM_NAME to "series[0].seasons[5].episodes[1].name",
                E_NO_STREAM to "series[0].seasons[5]",
                W_FIELD to "series[0].endYear",
                E_URL to "series[1].seasons[0].episodes[0].url",
                E_NO_STREAM to "series[1].seasons[0].episodes[0]",
                E_NO_STREAM to "series[1].seasons[0]",
                E_NO_STREAM to "series[1]",
                E_NO_STREAM to "series[2].seasons",
            ),
            report.codes()
        )
        // s2 was dropped, so its episode id "s2e1" was never claimed and s4 may use it.
        assertEquals(listOf("s1", "s4"), doc.series.map { it.id })
        val s1 = doc.series.first()
        assertNull(s1.endYear)
        assertEquals(listOf(2, 0), s1.orderedSeasons().map { it.number })
        val season2 = s1.seasons.first { it.number == 2 }
        assertEquals(listOf("s1e1", "s1e3"), season2.orderedEpisodes().map { it.id })
        assertTrue(season2.episodes.first { it.id == "s1e1" }.isUpcoming("2026-09-28"))
        assertEquals("Specials", s1.seasons.first().name?.resolve(null))
        assertEquals(4, doc.episodeCount)
    }

    // §5.2
    @Test
    fun textRules() {
        val long = "x".repeat(201)
        val emoji = "😀".repeat(201)
        val (doc, report) = success(
            doc(
                """"channels":[
                  {"id":"a","name":"$long","url":"https://cdn.example.com/a.m3u8","description":"${"d".repeat(5001)}"},
                  {"id":"b","name":"Two\nlines","url":"https://cdn.example.com/b.m3u8"},
                  {"id":"c","name":{"vi":"Kênh","EN":"bad key","zh-CN":"","fr":7,"x-note":"skip"},"url":"https://cdn.example.com/c.m3u8"},
                  {"id":"d","name":"$emoji","url":"https://cdn.example.com/d.m3u8"},
                  {"id":"e","name":{"en":"E"},"description":42,"url":"https://cdn.example.com/e.m3u8"}
                ]""",
                meta = """{"name":"Nguồn","language":"vi"}""",
            )
        )
        assertEquals(
            listOf(
                W_TEXT to "channels[0].name",
                W_TEXT to "channels[0].description",
                W_TEXT to "channels[1].name",
                W_TEXT to "channels[2].name[1]",
                W_TEXT to "channels[2].name.zh-CN",
                W_TEXT to "channels[2].name.fr",
                W_TEXT to "channels[2].name[4]",
                W_TEXT to "channels[3].name",
                W_FIELD to "channels[4].description",
            ),
            report.codes()
        )
        val byId = doc.channels.associateBy { it.id }
        assertEquals(200, byId.getValue("a").name.resolve(null).length)
        assertEquals(5000, byId.getValue("a").description?.resolve(null)?.length)
        assertEquals("Two lines", byId.getValue("b").name.resolve(null))
        assertEquals(mapOf("vi" to "Kênh"), byId.getValue("c").name.values)
        // 200 code points = 400 UTF-16 units, never a split surrogate pair.
        assertEquals(400, byId.getValue("d").name.resolve(null).length)
        // A plain string is in meta.language.
        assertEquals(mapOf("vi" to "Nguồn"), doc.meta.name.values)
        assertEquals("vi", doc.meta.name.defaultLanguage)
        assertNull(byId.getValue("e").description)
    }

    @Test
    fun atMostThirtyLanguagesPerText() {
        val tags = (0 until 31).map { "a" + ('a' + it / 26) + ('a' + it % 26) }
        val name = tags.joinToString(",", "{", "}") { "\"$it\":\"$it\"" }
        val (doc, report) = success(doc(""""channels":[{"id":"a","name":$name,"url":"https://cdn.example.com/a.m3u8"}]"""))
        assertEquals(listOf(W_TEXT to "channels[0].name.${tags.last()}"), report.codes())
        assertEquals(30, doc.channels.single().name.values.size)
    }

    @Test
    fun metaFields() {
        val meta = """{"name":"N","language":"EN","languages":["en","vi","bad tag"],"updatedAt":"yesterday",
            "adult":"yes","license":"CC0-1.0","author":{"url":"https://example.com"},"logo":"https://img.example.com/l.png",
            "description":{"en":"Line\nbreak ok"}}"""
        val (doc, report) = success(docWithChannel(meta = meta))
        assertEquals(
            listOf(
                W_FIELD to "meta.language",
                W_FIELD to "meta.author",
                W_FIELD to "meta.updatedAt",
                W_FIELD to "meta.adult",
                W_FIELD to "meta.languages[2]",
            ),
            report.codes()
        )
        val m = doc.meta
        assertEquals("en", m.language)
        assertTrue(m.adult, "a malformed adult flag is treated as adult")
        assertNull(m.author)
        assertEquals(listOf("en", "vi"), m.languages)
        assertEquals("Line\nbreak ok", m.description?.resolve("en"))
        assertEquals("CC0-1.0", m.license)

        val (ok, okReport) = success(
            docWithChannel(meta = """{"name":"N","adult":true,"updatedAt":"2026-09-27T10:00:00.5+07:00","author":{"name":"A","email":"a@example.com"}}""")
        )
        assertTrue(okReport.issues.isEmpty(), okReport.codes().toString())
        assertTrue(ok.meta.adult)
        assertEquals("a@example.com", ok.meta.author?.email)
        assertEquals(false, success(docWithChannel()).first.meta.adult)
    }

    @Test
    fun revision() {
        assertEquals(3, success(docWithChannel(""""revision":3""")).first.revision)
        val (doc, report) = success(docWithChannel(""""revision":-1"""))
        assertEquals(0, doc.revision)
        assertEquals(listOf(W_FIELD to "revision"), report.codes())
    }

    @Test
    fun topLevelCollectionsOfTheWrongTypeAreIgnored() {
        val (doc, report) = success(docWithChannel(""""movies":{"id":"m"}""", """"epg":"https://example.com/g.xml""""))
        assertEquals(listOf(W_FIELD to "movies", W_FIELD to "epg"), report.codes())
        assertTrue(doc.movies.isEmpty())
    }
}
