package tss.t.tsiptv.feature.lan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tss.t.tsiptv.core.parser.model.playback.DrmSpec
import tss.t.tsiptv.player.models.SubtitleTrack

/**
 * LAN protocol v1 between a phone (sender) and a TS IPTV TV (receiver).
 * See docs/prd-tv-cast-and-sync.md §2.4. One JSON line per request and per answer.
 *
 * Every `toString()` here leaves out URLs, headers, keys, codes and MACs: they must never reach a log.
 */
object LanProtocol {
    const val VERSION = 1

    /** DNS-SD service type (Android `NsdManager` form). */
    const val SERVICE_TYPE = "_tsiptv._tcp"
    const val TXT_ID = "id"
    const val TXT_VERSION = "v"

    /** Largest request line for anything but a playlist offer with file content. */
    const val MAX_REQUEST_BYTES = 64 * 1024

    /** Largest request line at all (a playlist offer carrying a file). */
    const val MAX_OFFER_BYTES = 1536 * 1024

    /** Largest file content a phone sends (text, UTF-8). */
    const val MAX_FILE_CONTENT_BYTES = 1024 * 1024

    /** Signed requests older or newer than this are refused (clock skew + delivery). */
    const val TIMESTAMP_WINDOW_MS = 120_000L

    const val READ_TIMEOUT_MS = 10_000
    const val CONNECT_TIMEOUT_MS = 5_000

    const val PAIR_CODE_TTL_MS = 120_000L
    const val PAIR_MAX_WRONG_CODES = 3
    const val PAIR_LOCK_AFTER_FAILED_SESSIONS = 5
    const val PAIR_LOCK_WINDOW_MS = 10 * 60_000L
    const val PAIR_LOCK_MS = 10 * 60_000L

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "type"
    }

    /** Signed bodies use their own discriminator so they never parse as a request. */
    val bodyJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        classDiscriminator = "cmd"
    }
}

/** Error codes in answers. Stable strings: older and newer apps must agree on them. */
object LanErrorCode {
    const val BAD_REQUEST = "BAD_REQUEST"
    const val UNSUPPORTED_VERSION = "UNSUPPORTED_VERSION"
    const val TOO_LARGE = "TOO_LARGE"
    const val UNPAIRED = "UNPAIRED"
    const val BAD_SIGNATURE = "BAD_SIGNATURE"
    const val REPLAY = "REPLAY"
    const val EXPIRED = "EXPIRED"
    const val WRONG_CODE = "WRONG_CODE"
    const val BUSY = "BUSY"
    const val LOCKED = "LOCKED"
    const val BAD_URL = "BAD_URL"
    const val NOT_ACCEPTING = "NOT_ACCEPTING"

    /** pair_start while the TV is not on its "TV & devices" screen (pairing mode). */
    const val PAIRING_CLOSED = "PAIRING_CLOSED"
}

// -------------------------------------------------------------------------------------------------
// Requests (phone -> TV)
// -------------------------------------------------------------------------------------------------

@Serializable
sealed interface LanRequest

@Serializable
@SerialName("hello")
data class HelloRequest(val v: Int = LanProtocol.VERSION) : LanRequest

@Serializable
@SerialName("pair_start")
data class PairStartRequest(
    val v: Int = LanProtocol.VERSION,
    val senderId: String,
    val senderName: String,
    /** The sender's ephemeral P-256 public key (X.509, base64). */
    val pub: String,
) : LanRequest {
    override fun toString(): String = "PairStartRequest(v=$v)"
}

@Serializable
@SerialName("pair_confirm")
data class PairConfirmRequest(
    val sessionId: String,
    /** base64 HMAC-SHA256(K, "confirm" ‖ code). */
    val proof: String,
) : LanRequest {
    override fun toString(): String = "PairConfirmRequest()"
}

@Serializable
@SerialName("signed")
data class SignedRequest(
    val v: Int = LanProtocol.VERSION,
    val senderId: String,
    val ts: Long,
    val nonce: String,
    /** A [LanCommand] as JSON. Signed as text so both sides MAC the same bytes. */
    val body: String,
    val mac: String,
) : LanRequest {
    override fun toString(): String = "SignedRequest(v=$v, ts=$ts)"
}

// -------------------------------------------------------------------------------------------------
// Answers (TV -> phone)
// -------------------------------------------------------------------------------------------------

@Serializable
data class LanResponse(
    val ok: Boolean,
    val code: String? = null,
    /** `info` / `pair_challenge` answers. */
    val id: String? = null,
    val name: String? = null,
    val v: Int? = null,
    val sessionId: String? = null,
    val pub: String? = null,
) {
    override fun toString(): String = "LanResponse(ok=$ok, code=$code)"

    companion object {
        fun ok() = LanResponse(ok = true)
        fun error(code: String) = LanResponse(ok = false, code = code)
    }
}

// -------------------------------------------------------------------------------------------------
// Signed commands
// -------------------------------------------------------------------------------------------------

@Serializable
sealed interface LanCommand

@Serializable
@SerialName("ping")
data object PingCommand : LanCommand

@Serializable
@SerialName("cast")
data class CastCommand(val stream: CastStream) : LanCommand

@Serializable
@SerialName("playlist")
data class PlaylistCommand(val playlist: SharedPlaylist) : LanCommand

/**
 * What the TV needs to play the phone's current item. Headers and DRM are included because the
 * stream does not play without them; they only travel on the LAN.
 */
@Serializable
data class CastStream(
    val url: String,
    val title: String = "",
    val logo: String? = null,
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val drm: DrmSpec? = null,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val isLive: Boolean = true,
    /** VOD only: where the phone was, in ms. */
    val positionMs: Long? = null,
) {
    override fun toString(): String = "CastStream(title=$title, live=$isLive, headers=${headers.keys})"
}

/**
 * A playlist definition sent to the TV. [content] is set only for a file playlist (the phone just
 * picked the file; the app never keeps file content after import).
 */
@Serializable
data class SharedPlaylist(
    val name: String,
    val url: String? = null,
    val epgUrls: List<String> = emptyList(),
    /** Reserved: playlists carry no headers today (headers are per channel, inside the list). */
    val headers: Map<String, String> = emptyMap(),
    val fileName: String? = null,
    val content: String? = null,
) {
    override fun toString(): String = "SharedPlaylist(name=$name, file=${content != null})"
}

/** A TV found on the LAN (mDNS) or typed by IP. */
data class LanDevice(
    /** Receiver installation id from the TXT record; empty until `hello` answered (connect by IP). */
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val version: Int = LanProtocol.VERSION,
) {
    override fun toString(): String = "LanDevice(name=$name)"
}
