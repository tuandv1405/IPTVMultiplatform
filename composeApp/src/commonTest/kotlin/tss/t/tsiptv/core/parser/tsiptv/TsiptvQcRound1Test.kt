package tss.t.tsiptv.core.parser.tsiptv

import kotlinx.coroutines.runBlocking
import tss.t.tsiptv.core.parser.model.playback.CatchupMode
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.channel
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.doc
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.docWithChannel
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.failure
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.success
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_INCLUDE
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_URL
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_VERSION
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.W_FIELD
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.W_LIMIT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regression tests for the QC round 1 findings on F3 step 1 (numbers as in the QC report). */
class TsiptvQcRound1Test {

    // 1: a Stremio manifest URL needs a real path ending in /manifest.json.
    @Test
    fun stremioManifestUrlNeedsARealPath() {
        for (url in listOf(
            "https://addon.example.com?x=/manifest.json",
            "https://addon.example.com#/manifest.json",
            "https://addon.example.com",
            "https://addon.example.com/manifest.json.bak",
            "https://addon.example.com/notmanifest.json",
        )) {
            assertFalse(TsiptvRules.isStremioManifestUrl(url), url)
            val (_, report) = success(docWithChannel(""""includes":[{"id":"a","type":"stremio","url":"$url"}]"""))
            assertEquals(listOf(E_INCLUDE to "includes[0].url"), report.codes(), url)
        }
        for (url in listOf(
            "https://addon.example.com/manifest.json",
            "https://addon.example.com/a/b/manifest.json?x=1#top",
            "HTTPS://addon.example.com:8443/manifest.json",
        )) {
            assertTrue(TsiptvRules.isStremioManifestUrl(url), url)
        }
    }

    // 2 / PO decision (j): catch-up `source` scheme rules of §8.6.
    @Test
    fun catchupSourceRules() {
        fun catchup(json: String) = success(doc(""""channels":[${channel("a", """"catchup":$json""")}]"""))
            .let { (d, r) -> d.channels.single() to r.codes() }

        // append is query text: nested URLs of any scheme are data.
        for (source in listOf(
            "?redirect=https://cdn.example.com/x&utc={utc}&f={utc:Y-m-d}",
            "&back=rtmp://live.example.com/x&u=javascript:void(0)",
        )) {
            val (ch, codes) = catchup("""{"mode":"append","source":"$source"}""")
            assertEquals(source, ch.catchup?.source)
            assertEquals(emptyList(), codes, source)
        }

        // default must start with http(s); the substituted URL is checked at play time.
        val (template, templateCodes) = catchup("""{"mode":"default","source":"HTTPS://arc.example.com/{utc}?via=udp://x"}""")
        assertEquals(CatchupMode.DEFAULT, template.catchup?.mode)
        assertEquals(emptyList(), templateCodes)
        for (bad in listOf("javascript:alert(1)", "rtmp://arc.example.com/{utc}", "https://arc.example.com/{utc} x", "https://a/\\u0007")) {
            val (ch, codes) = catchup("""{"mode":"default","source":"$bad"}""")
            assertNull(ch.catchup, bad)
            assertEquals(listOf(W_FIELD to "channels[0].catchup.source"), codes, bad)
            assertEquals(1, ch.streams.size, "the channel still plays live")
        }

        // Other modes ignore `source` silently, whatever it contains.
        for (mode in listOf("shift", "flussonic", "flussonic-ts", "xc")) {
            for (any in listOf("\"javascript:alert(1)\"", "\"https://xc.example.com/{Y}\"", "42", "\"a b\"")) {
                val (ch, codes) = catchup("""{"mode":"$mode","source":$any,"days":2}""")
                assertEquals(2, ch.catchup?.days, "$mode keeps catch-up")
                assertNull(ch.catchup?.source, "$mode ignores $any")
                assertEquals(emptyList(), codes, "$mode $any")
            }
        }
        assertTrue(TsiptvRules.isCatchupSource("?utc={utc}&lutc={lutc}"))
        assertFalse(TsiptvRules.isCatchupSource("a\tb"))
        assertFalse(TsiptvRules.isCatchupSource(""))
    }

    // 3: hostile keys and values never reach messages or paths.
    @Test
    fun noUrlSecretOrHeaderValueInAnyMessageOrPath() {
        val secretKey = "https://secret.example.com/?token=SECRET1"
        val json = doc(
            """"channels":[
              {"id":"a","name":{"$secretKey":"x","en":"A"},"url":"https://cdn.example.com/a.m3u8",
               "headers":{"https://evil.example.com/SECRET2":"v","X-Ok":"SECRET3\nline","X-Long":"${"S".repeat(4097)}"},
               "description":{"javascript:SECRET4":"d"}},
              {"id":"b","name":"B","url":"ftp://SECRET5.example.com/b",
               "logo":"file:///SECRET6","drm":{"system":"widevine","licenseUrl":"rtmp://SECRET7"}},
              {"id":"c","name":"C","streams":[{"url":"https://cdn.example.com/c.mpd","drm":{"system":"clearkey","keys":{"SECRET8":"SECRET9"}}}, {"url":"https://cdn.example.com/c2.m3u8"}]}
            ]""",
            """"includes":[{"id":"i","type":"https://SECRET10","url":"https://example.com/i"},{"id":"j","type":"stremio","url":"https://SECRET11.example.com/?x=/manifest.json"}]""",
            """"layout":{"home":[{"type":"https://SECRET12","query":{"from":"channels"}},{"type":"row","query":{"from":"https://SECRET13","include":"https://SECRET14"}}]}""",
            meta = """{"name":{"https://SECRET15":"x","en":"N"},"homepage":"javascript:SECRET16"}""",
        )
        val (_, report) = success(json)
        assertTrue(report.issues.size >= 12, report.codes().toString())
        for (issue in report.issues) {
            for (text in listOf(issue.path, issue.message)) {
                assertFalse("://" in text, "'$text' contains a URL")
                assertFalse("SECRET" in text, "'$text' leaks a value")
            }
        }
        report.assertHas(TsiptvIssueCode.W_TEXT, "channels[0].name[0]")
        report.assertHas(TsiptvIssueCode.E_HEADER_FORBIDDEN, "channels[0].headers[0]")
        report.assertHas(TsiptvIssueCode.W_TEXT, "meta.name[0]")
    }

    // 4: every plain-string length counts code points, not UTF-16 units.
    @Test
    fun plainStringLengthsCountCodePoints() {
        val smile = "😀"
        val (doc, report) = success(
            doc(
                """"movies":[{"id":"m","name":"M","url":"https://cdn.example.com/m.mp4",
                   "ageRating":"${smile.repeat(16)}","genres":["${smile.repeat(50)}"],"originalName":"${smile.repeat(200)}"}]""",
                """"includes":[{"id":"addon","type":"stremio","url":"https://addon.example.com/manifest.json"}]""",
                """"layout":{"home":[{"type":"row","query":{"from":"catalog","include":"addon","catalog":{"type":"${smile.repeat(50)}","id":"${smile.repeat(200)}"}}}]}""",
                meta = """{"name":"N","author":{"name":"${smile.repeat(100)}"}}""",
            )
        )
        assertEquals(emptyList(), report.codes())
        val movie = doc.movies.single()
        assertEquals(smile.repeat(16), movie.ageRating)
        assertEquals(listOf(smile.repeat(50)), movie.genres)
        assertEquals(smile.repeat(100), doc.meta.author?.name)
        assertEquals(smile.repeat(200), doc.layout?.home?.single()?.query?.catalog?.id)
        assertEquals(3, TsiptvRules.codePointCount("a${smile}b"))

        val (tooLong, r2) = success(doc(""""movies":[{"id":"m","name":"M","url":"https://cdn.example.com/m.mp4","ageRating":"${smile.repeat(17)}"}]"""))
        assertNull(tooLong.movies.single().ageRating)
        assertEquals(listOf(W_FIELD to "movies[0].ageRating"), r2.codes())
    }

    // 6: the planner enforces the fetch budget and merged item limits and reports failed fetches.
    @Test
    fun plannerEnforcesTheFetchBudget() = runBlocking {
        val includes = (1..3).joinToString(",") { """{"id":"n$it","type":"tsiptv-source","url":"https://x.example.com/$it.json"}""" }
        val (root, _) = success(docWithChannel(""""includes":[$includes]"""))
        val loaded = mutableListOf<String>()
        val tree = TsiptvIncludeTreePlanner.plan(root, null) { include, _ ->
            loaded += include.id
            val text = doc(""""channels":[${channel(include.id + "-c")}]""", id = include.id)
            // The first include alone is reported as 60 MiB.
            TsiptvFetchedDocument(TsiptvSourceParser.parse(text), if (include.id == "n1") 60L * 1024 * 1024 else text.length.toLong())
        }
        assertEquals(listOf("n1"), loaded, "nothing is fetched once the budget is spent")
        // PO decision (e): over the budget is a fetch failure, E_INCLUDE on the declaring entry.
        assertEquals(listOf(E_INCLUDE to "includes[0]", E_INCLUDE to "includes[1]", E_INCLUDE to "includes[2]"), tree.report.codes())
        assertTrue(tree.nodes.all { it.status == TsiptvIncludeNode.Status.FETCH_FAILED && it.document == null })
        assertFalse(tree.guard.canFetch)
    }

    @Test
    fun plannerAppliesMergedItemLimitsRootFirst() = runBlocking {
        fun channels(prefix: String, n: Int) = (0 until n).joinToString(",") { """{"id":"$prefix$it","name":"C","url":"https://e.x/$prefix$it.m3u8"}""" }
        val includes = """[{"id":"big","type":"tsiptv-source","url":"https://x.example.com/big.json"},{"id":"late","type":"tsiptv-source","url":"https://x.example.com/late.json"}]"""
        val (root, _) = success(doc(""""channels":[${channels("r", 10_000)}]""", """"includes":$includes"""))
        val files = mapOf(
            "https://x.example.com/big.json" to doc(""""channels":[${channels("b", 9_998)}]""", id = "big"),
            "https://x.example.com/late.json" to doc(""""channels":[${channels("l", 5)}]""", """"movies":[{"id":"m","name":"M","url":"https://e.x/m.mp4"}]""", id = "late"),
        )
        val tree = TsiptvIncludeTreePlanner.plan(root, null) { include, _ ->
            val text = files.getValue(include.url)
            TsiptvFetchedDocument(TsiptvSourceParser.parse(text, include.url), text.length.toLong())
        }
        assertEquals(10_000, tree.root.channels.size)
        assertEquals(9_998, tree.nodes[0].document?.channels?.size)
        val late = assertNotNull(tree.nodes[1].document)
        assertEquals(listOf("l0", "l1"), late.channels.map { it.id })
        assertEquals(1, late.movies.size)
        assertEquals(listOf(W_LIMIT to "[late]"), tree.report.codes())
        assertEquals(3, tree.report.skippedItemCount)
        // Step 2 goes on with the same guard for the includes it fetches itself.
        assertEquals(0, tree.guard.admitItems(TsiptvPoolKind.CHANNEL, 7, tree.nodes[1].context!!))
    }

    // 7: warnings about the ignored parts of an included document are not reported.
    @Test
    fun nestedMetaAppearanceAndLayoutWarningsAreFilteredOut() = runBlocking {
        val nested = doc(
            """"channels":[${channel("ok")}, ${channel("bad", url = "rtmp://x/y")}]""",
            """"appearance":{"background":{"color":"#DDDDDD"},"accent":"orange"}""",
            """"layout":{"home":[{"type":"carousel","query":{"from":"channels"}}]}""",
            meta = """{"name":{"EN":"bad","en":"N"},"language":"English"}""",
            id = "nested",
        )
        assertTrue(TsiptvSourceParser.parse(nested).report.issues.size > 5)
        val (root, _) = success(docWithChannel(""""includes":[{"id":"n","type":"tsiptv-source","url":"https://x.example.com/n.json"}]"""))
        val tree = TsiptvIncludeTreePlanner.plan(root, null) { include, _ ->
            TsiptvFetchedDocument(TsiptvSourceParser.parse(nested, include.url), nested.length.toLong())
        }
        assertEquals(listOf(E_URL to "[n] channels[1].url", TsiptvIssueCode.E_NO_STREAM to "[n] channels[1]"), tree.report.codes())
    }

    // 8: the 20-header cap applies after merging item and stream headers; the stream's own win.
    @Test
    fun mergedHeadersAreCappedAtTwenty() {
        val itemHeaders = (1..15).joinToString(",", "{", "}") { "\"X-Item$it\":\"$it\"" }
        val ownHeaders = (1..10).joinToString(",", "{", "}") { "\"X-Own$it\":\"$it\"" }
        val (doc, report) = success(
            doc(""""channels":[{"id":"a","name":"A","headers":$itemHeaders,"streams":[{"url":"https://cdn.example.com/a.m3u8","headers":$ownHeaders}]}]""")
        )
        assertEquals(listOf(W_LIMIT to "channels[0].streams[0].headers"), report.codes())
        val headers = doc.channels.single().streams.single().headers
        assertEquals(20, headers.size)
        // Stream headers first, then the item's in document order.
        assertEquals((1..10).map { "X-Own$it" } + (1..10).map { "X-Item$it" }, headers.keys.toList())

        // Names are distinct case-insensitively: an override does not take a slot twice.
        val (doc2, report2) = success(
            doc(""""channels":[{"id":"a","name":"A","headers":$itemHeaders,"streams":[{"url":"https://cdn.example.com/a.m3u8","headers":{"x-item1":"own"}}]}]""")
        )
        assertEquals(emptyList(), report2.codes())
        val h2 = doc2.channels.single().streams.single().headers
        assertEquals(15, h2.size)
        assertEquals("own", h2["x-item1"])
        assertFalse("X-Item1" in h2)

        // licenseHeaders have their own limit and are never merged with the stream's.
        val license = (1..20).joinToString(",", "{", "}") { "\"X-Lic$it\":\"$it\"" }
        val (doc3, report3) = success(
            doc(""""channels":[{"id":"a","name":"A","headers":$itemHeaders,"url":"https://cdn.example.com/a.mpd","drm":{"system":"widevine","licenseUrl":"https://lic.example.com/wv","licenseHeaders":$license}}]""")
        )
        assertEquals(emptyList(), report3.codes())
        assertEquals(20, doc3.channels.single().streams.single().drm?.licenseHeaders?.size)
    }

    // PO decision (c): the `epg-` prefix is reserved for implicit EPG include ids.
    @Test
    fun includeIdsStartingWithEpgAreReserved() {
        val (doc, report) = success(
            doc(
                """"channels":[${channel("epg-1")}]""",
                """"includes":[{"id":"epg-0","type":"xmltv","url":"https://example.com/g.xml"},{"id":"EPG-1","type":"m3u","url":"https://example.com/a.m3u"},{"id":"guide-epg-2","type":"xmltv","url":"https://example.com/h.xml"}]""",
                """"epg":["https://example.com/e.xml"]""",
            )
        )
        assertEquals(listOf(E_INCLUDE to "includes[0].id"), report.codes())
        assertEquals(listOf("EPG-1", "guide-epg-2"), doc.includes.map { it.id })
        assertEquals(listOf("epg-1"), doc.channels.map { it.id }, "item ids may start with epg-")
        assertEquals(1, report.skippedItemCount)
    }

    // PO decision (a): null is absent, silently; null entries are "not an object"; null map values are invalid.
    @Test
    fun nullValues() {
        val (doc, report) = success(
            doc(
                """"channels":[
                  {"id":"a","name":"A","url":"https://cdn.example.com/a.m3u8","logo":null,"number":null,"headers":null,"drm":null,"catchup":null,"groups":null,"type":null,"streams":null},
                  {"id":"b","name":"B","url":"https://cdn.example.com/b.m3u8","headers":{"X-A":null,"X-B":"b"}},
                  {"id":"c","name":{"en":null,"vi":"C"},"url":"https://cdn.example.com/c.m3u8"},
                  {"id":"d","name":null,"url":"https://cdn.example.com/d.m3u8"},
                  {"id":"e","name":"E","streams":[null,{"url":"https://cdn.example.com/e.m3u8","drm":{"system":"clearkey","keys":{"000102030405060708090a0b0c0d0e0f":null}}},{"url":"https://cdn.example.com/e2.m3u8"}]},
                  null
                ]""",
                """"movies":null""",
                """"includes":[null]""",
                """"epg":[null]""",
                """"appearance":null""",
                """"layout":{"home":[null,{"type":"row","query":{"from":"channels","sort":null,"limit":null},"title":null,"seeAll":null}]}""",
                """"revision":null""",
                meta = """{"name":"N","adult":null,"logo":null,"language":null}""",
            )
        )
        assertEquals(
            listOf(
                W_FIELD to "channels[1].headers.X-A",
                TsiptvIssueCode.W_TEXT to "channels[2].name.en",
                TsiptvIssueCode.E_ITEM_NAME to "channels[3].name",
                E_URL to "channels[4].streams[0]",
                TsiptvIssueCode.E_DRM to "channels[4].streams[1].drm.keys",
                TsiptvIssueCode.E_ITEM_ID to "channels[5]",
                E_INCLUDE to "includes[0]",
                E_URL to "epg[0]",
                W_FIELD to "layout.home[0]",
            ),
            report.codes()
        )
        assertEquals(listOf("a", "b", "c", "e"), doc.channels.map { it.id })
        assertEquals(TsiptvChannelType.TV, doc.channels[0].type)
        assertFalse(doc.meta.adult)
        assertEquals("en", doc.meta.language)
        val row = assertNotNull(doc.layout).home.single()
        assertTrue(row.seeAll)
        assertEquals(TsiptvQuerySort.SOURCE, row.query?.sort)
        assertEquals(E_META_MISSING, failure(docWithChannel().replace("\"meta\":{\"name\":\"Test\"}", "\"meta\":null")).codes())
        assertEquals(listOf(E_VERSION to "version"), failure(docWithChannel().replace("\"version\":1", "\"version\":null")).codes())
    }

    // PO decision (d): adult content through includes.
    @Test
    fun adultRuleCoversIncludedSourcesAndStremioManifests() = runBlocking {
        val includes = """[
            {"id":"a","type":"tsiptv-source","url":"https://x.example.com/a.json"},
            {"id":"addon","type":"stremio","url":"https://addon.example.com/manifest.json"},
            {"id":"live","type":"m3u","url":"https://x.example.com/live.m3u"}
        ]"""
        val (root, _) = success(docWithChannel(""""includes":$includes"""))
        val files = mapOf(
            "https://x.example.com/a.json" to doc(""""channels":[${channel("a1")}]""", """"includes":[{"id":"deep","type":"tsiptv-source","url":"https://x.example.com/deep.json"}]""", id = "a"),
            "https://x.example.com/deep.json" to doc(""""channels":[${channel("d1")}]""", meta = """{"name":"Deep","adult":"yes"}""", id = "deep"),
        )
        val tree = TsiptvIncludeTreePlanner.plan(root, "https://x.example.com/root.json", rootBytes = 1_000) { include, _ ->
            val text = files.getValue(include.url)
            TsiptvFetchedDocument(TsiptvSourceParser.parse(text, include.url), text.length.toLong())
        }
        // The nested meta warning (non-boolean adult) is filtered, but the flag itself counts.
        assertEquals(emptyList(), tree.report.codes())
        assertFalse(tree.root.meta.adult)
        assertEquals(TsiptvAdultCheck(rootIsAdult = false, adultIncludePaths = listOf("a:deep")), tree.adultCheck())
        assertTrue(tree.requiresAdultConfirmation)
        assertEquals(listOf("a:deep", "addon"), tree.adultCheck(stremioAdultIncludePaths = setOf("addon", "live")).adultIncludePaths)
        assertTrue(tree.guard.fetchedBytes > 1_000, "the root counts towards the budget")

        val (plain, _) = success(docWithChannel())
        val plainTree = TsiptvIncludeTreePlanner.plan(plain, null) { _, _ -> null }
        assertFalse(plainTree.requiresAdultConfirmation)
        val (adultRoot, _) = success(docWithChannel(meta = """{"name":"N","adult":true}"""))
        assertTrue(TsiptvIncludeTreePlanner.plan(adultRoot, null) { _, _ -> null }.adultCheck().rootIsAdult)
    }

    // Spec §10: a file that passes the (tightened) schema is always accepted, without item errors.
    @Test
    fun schemaValidDocumentsAreAccepted() {
        val smile = "😀"
        val edgeDocs = listOf(
            // Every limit at its maximum on one item, headers named x-…, ids starting with epg-.
            doc(
                """"channels":[{"id":"epg-${"a".repeat(124)}","name":" ${smile.repeat(198)} ","number":99999,"type":"radio",
                   "groups":${(1..10).joinToString(",", "[", "]") { "\"g$it\"" }},"tags":${(1..20).joinToString(",", "[", "]") { "\"t$it\"" }},
                   "epgShiftHours":14,"headers":${(1..20).joinToString(",", "{", "}") { "\"x-h$it\":\"v\\tv\"" }},
                   "streams":[{"url":"HTTPS://cdn.example.com/${"p".repeat(2000)}","headers":${(1..20).joinToString(",", "{", "}") { "\"X-S$it\":\"$it\"" }}}],
                   "catchup":{"mode":"append","source":"?utc={utc}&back=https://example.com/x","days":60,"correctionHours":-24}}]""",
                meta = """{"name":{"en":"N","zh-CN":"源"},"language":"zh-CN","adult":false,"license":"CC0-1.0","updatedAt":"2026-09-28T00:00:00+07:00"}""",
            ),
            // Hero with items, catalog query, default catch-up, ClearKey keys, subtitles, seasons 0.
            doc(
                """"channels":[{"id":"c","name":"C","url":"https://cdn.example.com/c.mpd","drm":{"system":"clearkey","keys":{"000102030405060708090a0b0c0d0e0f":"00112233445566778899aabbccddeeff"}},
                   "catchup":{"mode":"default","source":"https://arc.example.com/{utc}/{duration:60}.m3u8"}}]""",
                """"series":[{"id":"s","name":"S","year":1870,"endYear":2100,"seasons":[{"number":0,"episodes":[{"id":"e","number":0,"name":"E","url":"https://cdn.example.com/e.mp4","subtitles":[{"url":"https://cdn.example.com/e.srt","language":"pt-BR","format":"srt"}]}]}]}]""",
                """"includes":[{"id":"addon","type":"stremio","url":"https://addon.example.com/path/manifest.json?key=1"}]""",
                """"epg":["https://example.com/g.xml",{"url":"https://example.com/h.xml","refreshHours":720}]""",
                """"layout":{"home":[{"type":"hero","items":[{"image":"https://img.example.com/1.jpg","target":"addon:tt1:x","autoplay":true}]},
                   {"type":"row","query":{"from":"catalog","include":"addon","catalog":{"type":"movie","id":"top","genre":"Comedy"}}},
                   {"type":"grid","query":{"from":"radio","groups":["News"],"limit":500},"groupChips":true,"card":{"style":"list","corner":"none","showTitles":false}}]}""",
                """"appearance":{"accent":"#FFB300","background":{"color":"#101014","image":"https://img.example.com/bg.jpg","imageDim":0.3}}""",
            ),
        )
        val examples = listOf("minimal-live.tsiptv.json", "vod-catalog.tsiptv.json", "composed-includes.tsiptv.json").map(TsiptvFixtures::example)
        for (json in examples + edgeDocs) {
            val result = TsiptvSourceParser.parse(json)
            val (document, report) = success(json)
            assertTrue(result is TsiptvParseResult.Success)
            assertEquals(emptyList(), report.documentErrors + report.itemErrors, report.codes().toString())
            assertTrue(document.channels.isNotEmpty() || document.movies.isNotEmpty() || document.series.isNotEmpty() || document.includes.isNotEmpty())
        }
        // The only warning the first edge document may produce is the merged-header limit (§10).
        assertEquals(listOf(W_LIMIT to "channels[0].streams[0].headers"), success(edgeDocs[0]).second.codes())
    }

    private companion object {
        val E_META_MISSING = listOf(TsiptvIssueCode.E_META to "meta")
    }

    // 9: with a legible gradient, an illegible colour is not returned as the background colour.
    @Test
    fun anIllegibleColourIsNotUsedNextToALegibleGradient() {
        val (doc, report) = success(docWithChannel(""""appearance":{"background":{"color":"#DDDDDD","gradient":["#101014","#1C1C24"]}}"""))
        assertTrue(report.issues.isEmpty())
        val (effective, _) = TsiptvAppearanceResolver.resolve(doc.appearance)
        assertEquals("#101014", effective.backgroundColor)
        assertEquals(listOf("#101014", "#1C1C24"), effective.backgroundGradient)

        val (legible, _) = TsiptvAppearanceResolver.resolve(
            TsiptvAppearance(background = TsiptvBackground(color = "#06121C", gradient = listOf("#101014", "#1C1C24")))
        )
        assertEquals("#06121C", legible.backgroundColor)
    }

    // 10: `items: []` on a hero is treated as absent.
    @Test
    fun heroWithEmptyItemsUsesItsQuery() {
        val (doc, report) = success(docWithChannel(""""layout":{"home":[{"type":"hero","items":[],"query":{"from":"movies","limit":3}}]}"""))
        assertEquals(emptyList(), report.codes())
        val hero = assertNotNull(doc.layout).home.single()
        assertNull(hero.items)
        assertEquals(TsiptvQuerySource.MOVIES, hero.query?.from)
        assertEquals(3, hero.effectiveLimit)
    }

    // 11: integers too large for a Long still mean "a newer version".
    @Test
    fun hugeVersionsNeedANewerApp() {
        for (huge in listOf("100000000000000000000", "1e20", "9223372036854775808")) {
            val report = failure(docWithChannel().replace("\"version\":1", "\"version\":$huge"))
            assertEquals(listOf(E_VERSION to "version"), report.codes(), huge)
            assertTrue(report.issues.single().message.contains("newer app"), huge)
        }
        val negative = failure(docWithChannel().replace("\"version\":1", "\"version\":-100000000000000000000"))
        assertTrue(negative.issues.single().message.contains("does not exist"))
        assertTrue(failure(docWithChannel().replace("\"version\":1", "\"version\":1.5")).issues.single().message.contains("integer"))
    }
}
