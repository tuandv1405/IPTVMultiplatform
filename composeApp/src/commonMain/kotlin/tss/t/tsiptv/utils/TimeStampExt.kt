package tss.t.tsiptv.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import tsiptv.composeapp.generated.resources.Res
import tsiptv.composeapp.generated.resources.now_playing_title
import tsiptv.composeapp.generated.resources.today_format
import tsiptv.composeapp.generated.resources.yesterday_format

enum class TimeStampFormat(val formatStr: String) {
    yyyyMMdd("yyyy-MM-dd"),
    HHmmss("HH:mm:ss"),
    yyyyMMdd_HHmmss("yyyy-MM-dd HH:mm:ss"),
    mmss("mm:ss"),
    HHmm("HH:mm"),
    MMddHHmm("MM-dd HH:mm"),
    HHmmddMM("HH:mm dd/MM"),
}

fun Long.formatMinuteSecond(): String {
    val timeMs = this
    val totalSeconds = timeMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

fun Long.formatHourMinute(): String {
    val timeMs = this
    val totalMinutes = timeMs / 1000 / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return "${hours.toString().padStart(2, '0')}:" +
            minutes.toString().padStart(2, '0')
}

fun Long.formatDate(): String {
    val instant = Instant.fromEpochMilliseconds(this)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDateTime.year}-${
        localDateTime.monthNumber.toString().padStart(2, '0')
    }-${localDateTime.dayOfMonth.toString().padStart(2, '0')}"
}


fun Long.formatDateTime(): String {
    val instant = Instant.fromEpochMilliseconds(this)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDateTime.year}-${
        localDateTime.monthNumber.toString().padStart(2, '0')
    }-${localDateTime.dayOfMonth.toString().padStart(2, '0')} " +
            "${localDateTime.hour.toString().padStart(2, '0')}:${
                localDateTime.minute.toString().padStart(2, '0')
            }:${localDateTime.second.toString().padStart(2, '0')}"
}

fun Long.isToday(): Boolean {
    val instant = Instant.fromEpochMilliseconds(this)
    val now = Clock.System.now()
    val timeZone = TimeZone.currentSystemDefault()
    val thisDate = instant.toLocalDateTime(timeZone)
    val currentDate = now.toLocalDateTime(timeZone)
    return thisDate.year == currentDate.year &&
            thisDate.monthNumber == currentDate.monthNumber &&
            thisDate.dayOfMonth == currentDate.dayOfMonth
}

/**
 * Checks if the timestamp is from yesterday
 * @return true if the timestamp is from yesterday, false otherwise
 */
fun Long.isYesterday(): Boolean {
    val instant = Instant.fromEpochMilliseconds(this)
    val now = Clock.System.now()
    val timeZone = TimeZone.currentSystemDefault()
    val thisDate = instant.toLocalDateTime(timeZone)
    val currentDate = now.toLocalDateTime(timeZone)

    return thisDate.year == currentDate.year &&
            thisDate.monthNumber == currentDate.monthNumber &&
            thisDate.dayOfMonth == currentDate.dayOfMonth - 1
}

/**
 * Dynamic extension format function that formats timestamp based on provided pattern
 * Supported patterns:
 * - "yyyy-MM-dd" for date only
 * - "HH:mm:ss" for time only
 * - "yyyy-MM-dd HH:mm:ss" for full datetime
 * - "mm:ss" for minute:second
 * - "HH:mm" for hour:minute
 * - Custom patterns using y(year), M(month), d(day), H(hour), m(minute), s(second)
 */
fun Long.formatDynamic(pattern: String): String {
    val instant = Instant.fromEpochMilliseconds(this)
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())

    return when (pattern) {
        "yyyy-MM-dd" -> formatDate()
        "HH:mm:ss" -> "${
            localDateTime.hour.toString().padStart(2, '0')
        }:${localDateTime.minute.toString().padStart(2, '0')}:${
            localDateTime.second.toString().padStart(2, '0')
        }"

        "yyyy-MM-dd HH:mm:ss" -> formatDateTime()
        "mm:ss" -> formatMinuteSecond()
        else -> {
            // Custom pattern parsing
            var result = pattern
            result = result.replace("yyyy", localDateTime.year.toString())
            result = result.replace("MM", localDateTime.monthNumber.toString().padStart(2, '0'))
            result = result.replace("dd", localDateTime.dayOfMonth.toString().padStart(2, '0'))
            result = result.replace("HH", localDateTime.hour.toString().padStart(2, '0'))
            result = result.replace("mm", localDateTime.minute.toString().padStart(2, '0'))
            result = result.replace("ss", localDateTime.second.toString().padStart(2, '0'))
            result
        }
    }
}

@Composable
fun Long.formatToday(): String {

    return when {
        this.isToday() -> stringResource(
            Res.string.today_format,
            this.formatDynamic("HH:mm")
        )

        this.isYesterday() -> stringResource(
            Res.string.yesterday_format,
            this.formatDynamic("HH:mm")
        )

        else -> this.formatDynamic(
            "dd-MM, HH:mm"
        )
    }
}
