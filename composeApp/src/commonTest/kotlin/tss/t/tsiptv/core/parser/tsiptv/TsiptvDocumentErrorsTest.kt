package tss.t.tsiptv.core.parser.tsiptv

import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.asset
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.channel
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.doc
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.docWithChannel
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.failure
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.success
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_EMPTY
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_ID
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_META
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_NOT_JSON
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_NOT_SOURCE
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_VERSION
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** AC-T2: every document error code of spec §10 (E_TOO_LARGE is in [TsiptvLimitsTest]). */
class TsiptvDocumentErrorsTest {

    private fun primary(json: String) = failure(json).primaryDocumentError

    @Test
    fun notJson() {
        assertEquals(E_NOT_JSON, primary(asset("doc-not-json.tsiptv.json")))
        assertEquals(E_NOT_JSON, primary("{"))
        assertEquals(E_NOT_JSON, primary("not json at all"))
        assertEquals(E_NOT_JSON, primary("""{"format":"tsiptv-source",}"""))
        assertEquals(E_NOT_JSON, primary("{format: 'tsiptv-source'}"))
        assertEquals(E_NOT_JSON, primary(""))
    }

    @Test
    fun rootThatIsNotAnObjectIsNotJson() {
        assertEquals(E_NOT_JSON, primary("""[{"format":"tsiptv-source"}]"""))
        assertEquals(E_NOT_JSON, primary("\"tsiptv-source\""))
        assertEquals(E_NOT_JSON, primary("42"))
    }

    @Test
    fun notSource() {
        assertEquals(E_NOT_SOURCE, primary(asset("doc-not-source.json")))
        assertEquals(E_NOT_SOURCE, primary("""{"version":1}"""))
        assertEquals(E_NOT_SOURCE, primary("""{"format":1}"""))
        assertEquals(E_NOT_SOURCE, primary("""{"format":"TSIPTV-SOURCE"}"""))
    }

    @Test
    fun version() {
        val report = failure(asset("doc-version-2.tsiptv.json"))
        assertEquals(listOf(E_VERSION to "version"), report.codes())
        val base = docWithChannel()
        for (bad in listOf("\"1\"", "1.5", "0", "-1", "null", "true", "99")) {
            assertEquals(E_VERSION, primary(base.replace("\"version\":1", "\"version\":$bad")), "version $bad")
        }
        assertEquals(E_VERSION, primary(base.replace("\"version\":1,", "")))
        // 1.0 is the integer 1 in JSON.
        success(base.replace("\"version\":1", "\"version\":1.0"))
    }

    @Test
    fun idAndMetaAreBothReported() {
        val report = failure(asset("doc-missing-id-and-name.tsiptv.json"))
        assertEquals(listOf(E_ID to "id", E_META to "meta.name"), report.codes())
        assertEquals(E_ID, report.primaryDocumentError)
    }

    @Test
    fun id() {
        for (bad in listOf("\"\"", "\"-leading-dash\"", "\"has space\"", "\"a:b\"", "1", "\"${"a".repeat(129)}\"")) {
            val json = docWithChannel().replace("\"id\":\"qa.test\"", "\"id\":$bad")
            assertEquals(listOf(E_ID to "id"), failure(json).codes(), "id $bad")
        }
        assertEquals(listOf(E_ID to "id"), failure(docWithChannel().replace("\"id\":\"qa.test\",", "")).codes())
        success(docWithChannel().replace("\"id\":\"qa.test\"", "\"id\":\"${"a".repeat(128)}\""))
    }

    @Test
    fun meta() {
        val channels = """"channels":[${channel("a")}]"""
        assertEquals(E_META to "meta", failure("""{"format":"tsiptv-source","version":1,"id":"x",$channels}""").codes().single())
        assertEquals(E_META to "meta", failure(doc(channels, meta = "\"My name\"")).codes().single())
        assertEquals(E_META to "meta.name", failure(doc(channels, meta = "{}")).codes().single())
        assertEquals(E_META to "meta.name", failure(doc(channels, meta = """{"name":""}""")).codes().single())
        assertEquals(E_META to "meta.name", failure(doc(channels, meta = """{"name":"   "}""")).codes().single())
        assertEquals(E_META to "meta.name", failure(doc(channels, meta = """{"name":42}""")).codes().single())
        assertEquals(E_META to "meta.name", failure(doc(channels, meta = """{"name":{}}""")).codes().single())
        // Only invalid language keys: each is a W_TEXT, and nothing is left, so E_META.
        val report = failure(doc(channels, meta = """{"name":{"English":"Name","EN":"Name"}}"""))
        assertEquals(listOf(TsiptvIssueCode.W_TEXT, TsiptvIssueCode.W_TEXT, E_META), report.issues.map { it.code })
    }

    @Test
    fun empty() {
        assertEquals(listOf(E_EMPTY to ""), failure(doc()).codes())
        assertEquals(listOf(E_EMPTY to ""), failure(doc(""""channels":[]""", """"movies":[]""", """"series":[]""", """"includes":[]""")).codes())
        // EPG links alone are not content.
        assertEquals(listOf(E_EMPTY to ""), failure(doc(""""epg":["https://example.com/guide.xml"]""")).codes())
    }

    @Test
    fun emptyAfterEveryItemWasDropped() {
        val report = failure(asset("doc-all-items-invalid.tsiptv.json"))
        assertEquals(E_EMPTY, report.primaryDocumentError)
        assertEquals(
            listOf(
                TsiptvIssueCode.E_NO_STREAM to "channels[0]",
                TsiptvIssueCode.E_URL to "channels[1].url",
                TsiptvIssueCode.E_NO_STREAM to "channels[1]",
                TsiptvIssueCode.E_ITEM_NAME to "movies[0].name",
                TsiptvIssueCode.E_INCLUDE to "includes[0].url",
                E_EMPTY to "",
            ),
            report.codes()
        )
        assertTrue(report.issues.last().message.contains("dropped"))
    }

    @Test
    fun oneItemOrOneIncludeIsEnough() {
        success(doc(""""movies":[{"id":"m","name":"M","url":"https://cdn.example.com/m.mp4"}]"""))
        success(doc(""""includes":[{"id":"i","type":"m3u","url":"https://example.com/a.m3u"}]"""))
        success(
            doc(""""series":[{"id":"s","name":"S","seasons":[{"number":1,"episodes":[{"id":"e","number":1,"name":"E","url":"https://cdn.example.com/e.mp4"}]}]}]""")
        )
    }

    @Test
    fun bomAndLeadingWhitespaceAreTolerated() {
        success("﻿  \n" + docWithChannel())
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + docWithChannel().encodeToByteArray()
        assertTrue(TsiptvSourceParser.parse(bytes) is TsiptvParseResult.Success)
    }

    @Test
    fun documentErrorsNeverLeakValues() {
        val report = failure(doc(""""channels":[{"id":"x","name":"X","url":"rtmp://secret-token@host/app"}]"""))
        assertTrue(report.issues.none { "secret-token" in it.message || "secret-token" in it.path })
    }
}
