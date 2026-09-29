package tss.t.tsiptv.core.parser.tsiptv

import kotlinx.coroutines.runBlocking
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.asset
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.channel
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.doc
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.docWithChannel
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.success
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_INCLUDE
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_ITEM_NAME
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_URL
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.W_FIELD
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.W_TEXT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regression tests for QC round 3 (numbers as in the QC report). */
class TsiptvQcRound3Test {

    // 1: size cap and corrupt gzip are fetch failures (keep stale); a rejected document is dropped.
    @Test
    fun oversizedOrCorruptIncludesAreFetchFailuresNotRejections() = runBlocking {
        val tooLarge = docWithChannel(""""x-pad":"${"p".repeat(6 * 1024 * 1024)}"""").encodeToByteArray()
        val corruptGzip = byteArrayOf(0x1f, 0x8b.toByte(), 8, 0, 1, 2, 3, 4)
        val version2 = asset("doc-version-2.tsiptv.json").encodeToByteArray()

        assertEquals(TsiptvFailureCause.TOO_LARGE, assertIs<TsiptvParseResult.Failure>(TsiptvSourceParser.parse(tooLarge)).cause)
        assertEquals(TsiptvFailureCause.CORRUPT_COMPRESSION, assertIs<TsiptvParseResult.Failure>(TsiptvSourceParser.parse(corruptGzip)).cause)
        assertEquals(TsiptvFailureCause.INVALID_DOCUMENT, assertIs<TsiptvParseResult.Failure>(TsiptvSourceParser.parse(version2)).cause)

        val bodies = mapOf("big" to tooLarge, "gz" to corruptGzip, "v2" to version2)
        val includes = bodies.keys.joinToString(",") { """{"id":"$it","type":"tsiptv-source","url":"https://x.example.com/$it.json"}""" }
        val (root, _) = success(docWithChannel(""""includes":[$includes]"""))
        val tree = TsiptvIncludeTreePlanner.plan(root, null) { include, _ ->
            val bytes = bodies.getValue(include.id)
            TsiptvFetchedDocument(TsiptvSourceParser.parse(bytes, include.url), bytes.size.toLong())
        }
        assertEquals(
            listOf(TsiptvIncludeNode.Status.FETCH_FAILED, TsiptvIncludeNode.Status.FETCH_FAILED, TsiptvIncludeNode.Status.REJECTED),
            tree.nodes.map { it.status }
        )
        assertEquals(listOf(E_INCLUDE to "includes[0]", E_INCLUDE to "includes[1]", E_INCLUDE to "includes[2]"), tree.report.codes())
        val (fetch0, fetch1, rejected) = tree.report.issues
        assertTrue("could not be loaded" in fetch0.message && "could not be loaded" in fetch1.message)
        assertEquals(0, fetch0.droppedItems + fetch1.droppedItems, "stale copies are kept, nothing is dropped")
        assertTrue("rejected" in rejected.message)
        assertEquals(1, rejected.droppedItems)
    }

    // 3: trimming uses the ECMA-262 whitespace set of the schema's \S.
    @Test
    fun trimmingMatchesTheSchemaWhitespaceSet() {
        assertEquals("x", TsiptvRules.trimEcma("﻿   　  x \t\u000B\u000C\r\n"))
        assertEquals("\u001Cx\u001F", TsiptvRules.trimEcma("\u001Cx\u001F"))
        assertTrue(TsiptvRules.isEcmaWhitespace('﻿'))
        assertTrue(!TsiptvRules.isEcmaWhitespace('\u001C') && !TsiptvRules.isEcmaWhitespace('​'))

        val (doc, report) = success(
            doc(
                """"channels":[
                  {"id":"a","name":"﻿ News　","groups":[" Sport "," "],"url":"https://cdn.example.com/a.m3u8"},
                  {"id":"b","name":" ﻿ ","url":"https://cdn.example.com/b.m3u8"},
                  {"id":"c","name":"\u001CFS","url":"https://cdn.example.com/c.m3u8"}
                ]""",
            )
        )
        assertEquals(
            listOf(
                W_FIELD to "channels[0].groups[1]",
                E_ITEM_NAME to "channels[1].name",
                W_TEXT to "channels[2].name",
            ),
            report.codes()
        )
        val a = doc.channels.first { it.id == "a" }
        assertEquals("News", a.name.resolve(null))
        assertEquals(listOf("Sport"), a.groups)
        // U+001C is a C0 control, not ECMA whitespace: replaced by a space, then trimmed.
        assertEquals("FS", doc.channels.first { it.id == "c" }.name.resolve(null))
    }

    // 3: C0 controls in Text/LongText; C1 in URLs. Addendum: LongText CRLF / CR → LF.
    @Test
    fun controlCharactersAndLineBreaks() {
        val (doc, report) = success(
            doc(
                """"channels":[
                  {"id":"a","name":"A\u0007B","description":"one\r\ntwo\rthree\nfour","url":"https://cdn.example.com/a.m3u8"},
                  {"id":"b","name":"Tab\tName","description":"tab\there\u0000","url":"https://cdn.example.com/b.m3u8"},
                  {"id":"c","name":"C","url":"https://cdn.example.com/c\u0085.m3u8"}
                ]""",
            )
        )
        assertEquals(
            listOf(
                W_TEXT to "channels[0].name",
                W_TEXT to "channels[1].name",
                W_TEXT to "channels[1].description",
                E_URL to "channels[2].url",
                TsiptvIssueCode.E_NO_STREAM to "channels[2]",
            ),
            report.codes()
        )
        val a = doc.channels.first { it.id == "a" }
        assertEquals("A B", a.name.resolve(null))
        assertEquals("one\ntwo\nthree\nfour", a.description?.resolve(null), "CRLF and lone CR become LF, silently")
        val b = doc.channels.first { it.id == "b" }
        assertEquals("Tab Name", b.name.resolve(null))
        assertEquals("tab here", b.description?.resolve(null))
        assertTrue(!TsiptvRules.isHttpUrl("https://x.example.com/\u0085") && !TsiptvRules.isHeaderValue("a\u0085"))
    }

    // 4: catch-up `source` is not trimmed; any whitespace drops catch-up.
    @Test
    fun catchupSourceIsNotTrimmed() {
        for (source in listOf(" ?utc={utc}", "?utc={utc} ", "?utc={utc}\\u00A0", "\\uFEFF?utc={utc}", "?utc={utc}\\u2028")) {
            val (doc, report) = success(doc(""""channels":[${channel("a", """"catchup":{"mode":"append","source":"$source"}""")}]"""))
            assertNull(doc.channels.single().catchup, source)
            assertEquals(listOf(W_FIELD to "channels[0].catchup.source"), report.codes(), source)
        }
    }
}
