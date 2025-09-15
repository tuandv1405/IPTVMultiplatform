package tss.t.tsiptv.utils

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.char
import kotlinx.datetime.format.optional
import kotlin.test.Test
import kotlin.test.assertTrue

class TestDateTime {

    @Test
    fun testDateTime() {
        val dt = "20240817220303 +0700"
        LocalDateTime.Formats.ISO
        val dateTime = LocalDateTime.parse(
            input = dt,
            format = LocalDateTime.Format {
                year();monthNumber();dayOfMonth();hour();minute();second()
                char(' ')
                optional {
                    char('+');char('0');char('7');char('0');char('0')
                }
            }
        )

        assertTrue(dateTime.year == 2024)
        assertTrue(dateTime.monthNumber == 8)
        assertTrue(dateTime.dayOfMonth == 17)
        assertTrue(dateTime.hour == 22)
        assertTrue(dateTime.minute == 3)
        assertTrue(dateTime.second == 3)
    }
}
