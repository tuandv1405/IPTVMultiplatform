package tss.t.tsiptv.core.parser.tsiptv

import okio.Buffer
import okio.GzipSink
import okio.buffer
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.channel
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.doc
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.docWithChannel
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.success
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_NOT_JSON
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.E_TOO_LARGE
import tss.t.tsiptv.core.parser.tsiptv.TsiptvIssueCode.W_LIMIT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * Size and count limits (§2, §4, §8, §9) with fixtures generated in the test (nothing large is
 * committed). The largest allowed document is also the performance fixture of AC-T25.
 */
class TsiptvLimitsTest {

    /** A document with exactly these counts; ids are short so that the maximum stays below 5 MiB. */
    private fun generated(
        channels: Int = 0,
        movies: Int = 0,
        series: Int = 0,
        seasonsPerSeries: Int = 1,
        episodesPerSeason: Int = 0,
        extraSeries: String? = null,
    ): String = buildString {
        append("""{"format":"tsiptv-source","version":1,"id":"qa.large","meta":{"name":"Large"}""")
        append(""","channels":[""")
        for (i in 0 until channels) {
            if (i > 0) append(',')
            append("""{"id":"c$i","name":"C$i","number":$i,"url":"https://e.x/c$i.m3u8"}""")
        }
        append("""],"movies":[""")
        for (i in 0 until movies) {
            if (i > 0) append(',')
            append("""{"id":"m$i","name":"M$i","year":2000,"url":"https://e.x/m$i.mp4"}""")
        }
        append("""],"series":[""")
        var episodeId = 0
        for (s in 0 until series) {
            if (s > 0) append(',')
            append("""{"id":"s$s","name":"S$s","seasons":[""")
            for (season in 1..seasonsPerSeries) {
                if (season > 1) append(',')
                append("""{"number":$season,"episodes":[""")
                for (e in 1..episodesPerSeason) {
                    if (e > 1) append(',')
                    val id = episodeId++
                    append("""{"id":"e$id","number":$e,"name":"E$e","url":"https://e.x/e$id.mp4"}""")
                }
                append("]}")
            }
            append("]}")
        }
        if (extraSeries != null) {
            if (series > 0) append(',')
            append(extraSeries)
        }
        append("]}")
    }

    // AC-T25 fixture: 10,000 channels, 5,000 movies, 1,000 series with 20,000 episodes.
    @Test
    fun theLargestAllowedDocumentParsesWithoutIssues() {
        val text = generated(channels = 10_000, movies = 5_000, series = 1_000, episodesPerSeason = 20)
        val size = TsiptvSourceParser.utf8Size(text)
        assertTrue(size <= TsiptvLimits.MAX_DOCUMENT_BYTES, "fixture is $size bytes")

        val (result, elapsed) = measureTimedValue { TsiptvSourceParser.parse(text.encodeToByteArray()) }
        println("TsiptvLimitsTest: parsed ${size / 1024} KiB (10,000 channels, 5,000 movies, 1,000 series, 20,000 episodes) in $elapsed")
        assertIs<TsiptvParseResult.Success>(result)
        assertTrue(result.report.issues.isEmpty(), result.report.codes().take(5).toString())
        val doc = result.document
        assertEquals(10_000, doc.channels.size)
        assertEquals(5_000, doc.movies.size)
        assertEquals(1_000, doc.series.size)
        assertEquals(20_000, doc.episodeCount)
    }

    @Test
    fun onePastEachDocumentCountLimitIsDroppedWithWLimit() {
        val (doc, report) = success(generated(channels = 10_001, movies = 5_001, series = 1_001, episodesPerSeason = 1))
        assertEquals(listOf(W_LIMIT to "channels", W_LIMIT to "movies", W_LIMIT to "series"), report.codes())
        assertEquals(3, report.skippedItemCount)
        assertEquals(10_000, doc.channels.size)
        assertEquals("c9999", doc.channels.last().id)
        assertEquals(5_000, doc.movies.size)
        assertEquals(1_000, doc.series.size)
    }

    @Test
    fun episodesPastTwentyThousandAreDropped() {
        val late = """{"id":"late","name":"Late","seasons":[{"number":1,"episodes":[""" +
                (1..10).joinToString(",") { """{"id":"late$it","number":$it,"name":"L","url":"https://e.x/l$it.mp4"}""" } + "]}]}"
        val (doc, report) = success(
            generated(movies = 1, series = 1, seasonsPerSeries = 41, episodesPerSeason = 500, extraSeries = late)
        )
        assertEquals(listOf(W_LIMIT to "series"), report.codes())
        // 500 episodes of season 41 and the 10 of "late", plus "late" itself.
        assertEquals(510 + 1, report.skippedItemCount)
        assertEquals(20_000, doc.episodeCount)
        assertEquals(40, doc.series.single().seasons.size)
    }

    @Test
    fun perSeriesAndPerSeasonLimits() {
        val (doc, report) = success(generated(series = 1, seasonsPerSeries = 101, episodesPerSeason = 1))
        assertEquals(listOf(W_LIMIT to "series[0].seasons"), report.codes())
        assertEquals(100, doc.series.single().seasons.size)

        val (doc2, report2) = success(generated(series = 1, seasonsPerSeries = 1, episodesPerSeason = 501))
        assertEquals(listOf(W_LIMIT to "series[0].seasons[0].episodes"), report2.codes())
        assertEquals(500, doc2.episodeCount)
    }

    @Test
    fun includesEpgAndSubtitleLimits() {
        val includes = (1..21).joinToString(",", "[", "]") { """{"id":"i$it","type":"m3u","url":"https://e.x/$it.m3u"}""" }
        val epg = (1..11).joinToString(",", "[", "]") { "\"https://e.x/$it.xml\"" }
        val subtitles = (1..31).joinToString(",", "[", "]") { """{"url":"https://e.x/$it.vtt","language":"en"}""" }
        val (doc, report) = success(
            doc(""""includes":$includes""", """"epg":$epg""", """"movies":[{"id":"m","name":"M","url":"https://e.x/m.mp4","subtitles":$subtitles}]""")
        )
        assertEquals(
            listOf(W_LIMIT to "movies[0].subtitles", W_LIMIT to "includes", W_LIMIT to "epg"),
            report.codes()
        )
        assertEquals(20, doc.includes.size)
        assertEquals(10, doc.epg.size)
        assertEquals(30, doc.movies.single().subtitles.size)
    }

    // AC-T2 E_TOO_LARGE with a 6 MiB file.
    @Test
    fun sixMebibytesIsTooLarge() {
        val text = padded(6L * 1024 * 1024)
        assertEquals(E_TOO_LARGE, TsiptvSourceParser.parse(text).report.primaryDocumentError)
        assertEquals(E_TOO_LARGE, TsiptvSourceParser.parse(text.encodeToByteArray()).report.primaryDocumentError)
    }

    @Test
    fun exactlyFiveMebibytesIsAllowed() {
        val text = padded(TsiptvLimits.MAX_DOCUMENT_BYTES)
        assertEquals(TsiptvLimits.MAX_DOCUMENT_BYTES, TsiptvSourceParser.utf8Size(text))
        assertIs<TsiptvParseResult.Success>(TsiptvSourceParser.parse(text))
        assertIs<TsiptvParseResult.Success>(TsiptvSourceParser.parse(text.encodeToByteArray()))
        val oneMore = padded(TsiptvLimits.MAX_DOCUMENT_BYTES + 1)
        assertEquals(E_TOO_LARGE, TsiptvSourceParser.parse(oneMore).report.primaryDocumentError)
    }

    @Test
    fun theLimitCountsUtf8Bytes() {
        // 2 bytes per "é": 3 MiB of characters is 6 MiB of UTF-8.
        val text = docWithChannel(""""x-pad":"${"é".repeat(3 * 1024 * 1024)}"""")
        assertEquals(E_TOO_LARGE, TsiptvSourceParser.parse(text).report.primaryDocumentError)
    }

    @Test
    fun gzipIsDecompressedAndTheLimitAppliesAfterDecompression() {
        val small = gzip(docWithChannel().encodeToByteArray())
        assertIs<TsiptvParseResult.Success>(TsiptvSourceParser.parse(small))

        val bomb = gzip(padded(6L * 1024 * 1024).encodeToByteArray())
        assertTrue(bomb.size < 100 * 1024, "compresses well: ${bomb.size}")
        assertEquals(E_TOO_LARGE, TsiptvSourceParser.parse(bomb).report.primaryDocumentError)

        val corrupt = byteArrayOf(0x1f, 0x8b.toByte(), 8, 0, 1, 2, 3, 4)
        assertEquals(E_NOT_JSON, TsiptvSourceParser.parse(corrupt).report.primaryDocumentError)
    }

    @Test
    fun aCustomLimitForCallersWithTighterBudgets() {
        val text = docWithChannel(""""x-pad":"${"p".repeat(2_000)}"""")
        assertEquals(E_TOO_LARGE, TsiptvSourceParser.parse(text, maxBytes = 1_000).report.primaryDocumentError)
        assertTrue(TsiptvSourceParser.parse(text.encodeToByteArray(), maxBytes = 1_000).report.primaryDocumentError == E_TOO_LARGE)
        assertIs<TsiptvParseResult.Success>(TsiptvSourceParser.parse(docWithChannel(), maxBytes = 1_000))
    }

    /** A valid document of exactly [bytes] UTF-8 bytes, padded with an `x-` member (which readers ignore). */
    private fun padded(bytes: Long): String {
        val empty = docWithChannel(""""x-pad":""""")
        val pad = (bytes - empty.length).toInt()
        return docWithChannel(""""x-pad":"${"p".repeat(pad)}"""")
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val buffer = Buffer()
        GzipSink(buffer).buffer().use { it.write(bytes) }
        return buffer.readByteArray()
    }
}
