package tss.t.tsiptv.core.parser.model.playback

import kotlinx.serialization.Serializable

/**
 * Catch-up modes of PVR IPTV Simple Client (`catchup=` / `catchup-type=`).
 *
 * Stored in F1 and played in F1b; see docs/prd-kodi-m3u-compat.md, "Decision: catch-up".
 */
@Serializable
enum class CatchupMode {
    DEFAULT,
    APPEND,
    SHIFT,
    FLUSSONIC,
    FLUSSONIC_HLS,
    FLUSSONIC_TS,
    XC,
    VOD;

    companion object {
        /** Kodi's mode names, case-insensitive. Unknown names give null: no catch-up. */
        fun fromKodi(value: String?): CatchupMode? = when (value?.trim()?.lowercase()) {
            "default" -> DEFAULT
            "append" -> APPEND
            "shift", "timeshift" -> SHIFT
            "flussonic" -> FLUSSONIC
            "flussonic-hls" -> FLUSSONIC_HLS
            "flussonic-ts", "fs" -> FLUSSONIC_TS
            "xc" -> XC
            "vod" -> VOD
            else -> null
        }
    }
}

/**
 * A channel's catch-up template, exactly as the playlist declared it.
 *
 * @property source `catchup-source`: a full URL template (default) or a query to append (append)
 * @property days Length of the archive in days, if given
 * @property correctionHours `catchup-correction`, added to every time substituted into [source]
 */
@Serializable
data class CatchupSpec(
    val mode: CatchupMode,
    val source: String? = null,
    val days: Int? = null,
    val correctionHours: Double = 0.0,
)
