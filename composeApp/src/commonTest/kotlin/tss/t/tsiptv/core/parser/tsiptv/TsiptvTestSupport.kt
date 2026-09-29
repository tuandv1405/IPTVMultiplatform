package tss.t.tsiptv.core.parser.tsiptv

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.SYSTEM
import tss.t.tsiptv.TestAssets
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/** Fixtures and small builders shared by the TS IPTV Source tests. */
internal object TsiptvFixtures {

    fun asset(name: String): String = TestAssets.read("tsiptv/$name")

    /** `web/public/examples`, found by walking up from the working directory (Gradle uses the module dir). */
    private val examplesDir: Path by lazy {
        val fs = FileSystem.SYSTEM
        var dir: Path? = fs.canonicalize(".".toPath())
        while (dir != null) {
            val candidate = dir / "web" / "public" / "examples"
            if (fs.exists(candidate)) return@lazy candidate
            dir = dir.parent
        }
        error("web/public/examples not found")
    }

    fun example(name: String): String = FileSystem.SYSTEM.read(examplesDir / name) { readUtf8() }

    fun exampleBytes(name: String): ByteArray = FileSystem.SYSTEM.read(examplesDir / name) { readByteArray() }

    /** A minimal valid document with [members] appended (each a `"key": value` JSON fragment). */
    fun doc(vararg members: String, meta: String = """{"name":"Test"}""", id: String = "qa.test"): String {
        val extra = if (members.isEmpty()) "" else ",\n" + members.joinToString(",\n")
        return """{"format":"tsiptv-source","version":1,"id":"$id","meta":$meta$extra}"""
    }

    /** A document with one valid channel plus [members]. */
    fun docWithChannel(vararg members: String, meta: String = """{"name":"Test"}"""): String =
        doc(""""channels":[${channel("base")}]""", *members, meta = meta)

    fun channel(id: String, extra: String = "", url: String? = "https://cdn.example.com/$id.m3u8"): String {
        val urlPart = if (url == null) "" else ""","url":"$url""""
        val extraPart = if (extra.isEmpty()) "" else ",$extra"
        return """{"id":"$id","name":"Channel $id"$urlPart$extraPart}"""
    }

    fun parse(json: String, url: String? = null): TsiptvParseResult = TsiptvSourceParser.parse(json, url)

    fun success(json: String, url: String? = null): Pair<TsiptvSourceDocument, TsiptvValidationReport> {
        val result = parse(json, url)
        if (result !is TsiptvParseResult.Success) {
            fail("Expected success, got ${result.report.issues.joinToString { "${it.code} ${it.path}" }}")
        }
        return result.document to result.report
    }

    fun failure(json: String): TsiptvValidationReport {
        val result = parse(json)
        assertIs<TsiptvParseResult.Failure>(result, "expected a document error")
        assertTrue(result.report.isRejected)
        return result.report
    }
}

/** `code path` pairs, for compact assertions. */
internal fun TsiptvValidationReport.codes(): List<Pair<TsiptvIssueCode, String>> = issues.map { it.code to it.path }

internal fun TsiptvValidationReport.assertHas(code: TsiptvIssueCode, path: String) {
    assertTrue(
        issues.any { it.code == code && it.path == path },
        "Expected $code at '$path' in ${codes()}"
    )
}

internal fun TsiptvValidationReport.assertOnly(vararg expected: Pair<TsiptvIssueCode, String>) {
    assertEquals(expected.toList().sortedBy { it.second + it.first }, codes().sortedBy { it.second + it.first })
}
