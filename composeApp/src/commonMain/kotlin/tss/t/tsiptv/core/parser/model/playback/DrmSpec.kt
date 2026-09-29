package tss.t.tsiptv.core.parser.model.playback

import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Key systems TS IPTV can hand to a player. WisePlay and FairPlay are deliberately absent. */
@Serializable
enum class DrmSystem {
    WIDEVINE,
    PLAYREADY,
    CLEARKEY,
}

/**
 * One ClearKey key pair, both as 32 lower-case hex characters (16 bytes).
 */
@Serializable
data class ClearKey(
    val kid: String,
    val key: String,
)

/**
 * A channel's DRM setup as declared by the playlist (`#KODIPROP:inputstream.adaptive.*`).
 *
 * A spec is kept even when TS IPTV cannot honour it: [unsupportedReason] is then set, the
 * channel is still imported, and playback refuses it with a clear message instead of
 * requesting the stream and failing somewhere inside the player.
 *
 * @property system The key system, or null when the playlist named one TS IPTV does not support
 * @property licenseUrl Licence server; null for ClearKey with local keys, or to let the manifest decide
 * @property licenseHeaders Headers sent with every licence request
 * @property clearKeys Local ClearKey keys (ClearKey only)
 * @property unsupportedReason Why this setup cannot be played; for logs and QC, never shown verbatim
 */
@Serializable
data class DrmSpec(
    val system: DrmSystem?,
    val licenseUrl: String? = null,
    val licenseHeaders: Map<String, String> = emptyMap(),
    val clearKeys: List<ClearKey> = emptyList(),
    val unsupportedReason: String? = null,
) {
    val isSupported: Boolean
        get() = unsupportedReason == null && system != null

    companion object {
        fun unsupported(reason: String, system: DrmSystem? = null) =
            DrmSpec(system = system, unsupportedReason = reason)
    }
}

/**
 * The W3C Clear Key licence (a JSON Web Key Set) for [DrmSpec.clearKeys], in the exact form
 * Media3's `LocalMediaDrmCallback` returns to the CDM: `kid` and `k` in base64url without padding.
 */
@OptIn(ExperimentalEncodingApi::class)
fun DrmSpec.clearKeyJwks(): String {
    val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    val keys = clearKeys.joinToString(",") { pair ->
        val kid = base64Url.encode(hexToBytes(pair.kid))
        val k = base64Url.encode(hexToBytes(pair.key))
        """{"kty":"oct","kid":"$kid","k":"$k"}"""
    }
    return """{"keys":[$keys],"type":"temporary"}"""
}

internal fun hexToBytes(hex: String): ByteArray =
    ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }

internal fun bytesToHex(bytes: ByteArray): String =
    bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
