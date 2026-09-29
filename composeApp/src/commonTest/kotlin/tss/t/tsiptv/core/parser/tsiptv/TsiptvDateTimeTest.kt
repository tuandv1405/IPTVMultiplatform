package tss.t.tsiptv.core.parser.tsiptv

import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.docWithChannel
import tss.t.tsiptv.core.parser.tsiptv.TsiptvFixtures.success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PRD Follow-up #3 row 5: RFC 3339 ranges for `meta.updatedAt`. */
class TsiptvDateTimeTest {

    @Test
    fun validDateTimes() {
        for (value in listOf(
            "2026-09-27T10:00:00Z",
            "2026-09-27t10:00:00z",
            "2026-09-27T23:59:59.999+07:00",
            "2026-09-27T00:00:00-23:59",
            "2024-02-29T12:00:00+00:00",
        )) {
            assertTrue(TsiptvRules.isDateTime(value), value)
        }
    }

    @Test
    fun outOfRangeDateTimes() {
        for (value in listOf(
            "2026-09-27T24:00:00Z",
            "2026-09-27T10:60:00Z",
            "2026-09-27T10:00:60Z",
            "2026-09-27T10:00:00+24:00",
            "2026-09-27T10:00:00+07:60",
            "2026-02-30T10:00:00Z",
            "2026-09-27 10:00:00Z",
            "2026-09-27T10:00Z",
        )) {
            assertFalse(TsiptvRules.isDateTime(value), value)
        }
    }

    @Test
    fun outOfRangeUpdatedAtIsDroppedWithWField() {
        val (doc, report) = success(docWithChannel(meta = """{"name":"N","updatedAt":"2026-09-27T25:00:00Z"}"""))
        assertNull(doc.meta.updatedAt)
        assertEquals(listOf(TsiptvIssueCode.W_FIELD to "meta.updatedAt"), report.codes())

        val (trimmed, trimmedReport) = success(docWithChannel(meta = """{"name":"N","updatedAt":"  2026-09-27T10:00:00Z "}"""))
        assertEquals("2026-09-27T10:00:00Z", trimmed.meta.updatedAt)
        assertTrue(trimmedReport.issues.isEmpty())
    }
}
