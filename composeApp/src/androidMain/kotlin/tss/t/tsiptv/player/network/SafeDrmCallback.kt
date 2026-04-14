package tss.t.tsiptv.player.network

import android.util.Base64
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallback
import java.util.UUID

/**
 * DRM callback with two modes:
 *
 * 1. **Inline key** ([clearKeyHex] is set) — builds a ClearKey JSON license response
 *    directly from the `kid_hex:key_hex` pair and returns it without any network request.
 *
 * 2. **Remote license** ([clearKeyHex] is null) — resolves the URL from the key request
 *    (set by [MediaItem.DrmConfiguration.licenseUri] or from the manifest's `<laurl>`) or
 *    falls back to [overrideLicenseUrl], then fetches the license over HTTPS using
 *    [dataSourceFactory].  Throws with a clear message when no URL can be found.
 */
@UnstableApi
class SafeDrmCallback(
    /** Inline ClearKey in "kid_hex:key_hex" format (32-char hex each, separated by ':'). */
    private val clearKeyHex: String? = null,
    /** HTTP/HTTPS license server URL used when the manifest does not embed one. */
    private val overrideLicenseUrl: String? = null,
    private val dataSourceFactory: HttpDataSource.Factory,
) : MediaDrmCallback {

    override fun executeProvisionRequest(
        uuid: UUID,
        request: ExoMediaDrm.ProvisionRequest,
    ): ByteArray {
        val url = request.defaultUrl.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("Widevine provisioning failed: no provisioning URL.")
        return httpDelegate(url).executeProvisionRequest(uuid, request)
    }

    override fun executeKeyRequest(
        uuid: UUID,
        request: ExoMediaDrm.KeyRequest,
    ): ByteArray {
        // Mode 1: inline ClearKey — no network needed
        if (clearKeyHex != null) {
            Log.d(TAG, "Returning inline ClearKey response for kid=${clearKeyHex.substringBefore(':')}")
            return buildClearKeyResponse(clearKeyHex)
        }

        // Mode 2: fetch license from URL
        val url = request.licenseServerUrl?.takeIf { it.isNotEmpty() }
            ?: overrideLicenseUrl?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException(
                "No DRM license URL for this stream. " +
                    "Provide it via channel metadata (drmLicenseKey) or embed " +
                    "<dashif:laurl> in the MPD manifest."
            )

        Log.d(TAG, "Fetching DRM license from: $url")
        return httpDelegate(url).executeKeyRequest(uuid, request)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a ClearKey JSON license response from [clearKeyHex] (`kid_hex:key_hex`).
     *
     * Response format (W3C ClearKey):
     * `{"keys":[{"kty":"oct","k":"<base64url_key>","kid":"<base64url_kid>"}],"type":"temporary"}`
     */
    private fun buildClearKeyResponse(clearKeyHex: String): ByteArray {
        val colon = clearKeyHex.indexOf(':')
        check(colon == 32) { "Expected 'kid_hex:key_hex' (32 chars each), got: $clearKeyHex" }
        val kidHex = clearKeyHex.substring(0, colon)
        val keyHex = clearKeyHex.substring(colon + 1)
        val kidB64 = hexToBase64Url(kidHex)
        val keyB64 = hexToBase64Url(keyHex)
        val json =
            """{"keys":[{"kty":"oct","k":"$keyB64","kid":"$kidB64"}],"type":"temporary"}"""
        return json.toByteArray(Charsets.UTF_8)
    }

    /** Converts a lowercase hex string to URL-safe base64 without padding. */
    private fun hexToBase64Url(hex: String): String {
        val bytes = ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return Base64.encodeToString(
            bytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
    }

    /** Creates a single-use [HttpMediaDrmCallback] pinned to [licenseUrl]. */
    private fun httpDelegate(licenseUrl: String): HttpMediaDrmCallback =
        HttpMediaDrmCallback(licenseUrl, true, dataSourceFactory)

    companion object {
        private const val TAG = "SafeDrmCallback"
    }
}
