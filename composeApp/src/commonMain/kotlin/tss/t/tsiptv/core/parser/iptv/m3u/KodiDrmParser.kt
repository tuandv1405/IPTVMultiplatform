package tss.t.tsiptv.core.parser.iptv.m3u

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tss.t.tsiptv.core.parser.model.playback.ClearKey
import tss.t.tsiptv.core.parser.model.playback.DrmSpec
import tss.t.tsiptv.core.parser.model.playback.DrmSystem
import tss.t.tsiptv.core.parser.model.playback.bytesToHex
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Reads inputstream.adaptive's three DRM syntaxes (docs/research/kodi.md §4.2) into a [DrmSpec].
 *
 * Only what Media3's default licence path can do is accepted: a POST of the raw challenge to a
 * URL with extra headers, or local ClearKey keys. Anything that needs the challenge wrapped or
 * the response unwrapped is reported as unsupported rather than half-applied, because a
 * half-applied setup fails later inside the CDM with an error nobody can act on.
 */
object KodiDrmParser {

    const val PROP_DRM = "inputstream.adaptive.drm"
    const val PROP_DRM_LEGACY = "inputstream.adaptive.drm_legacy"
    const val PROP_LICENSE_TYPE = "inputstream.adaptive.license_type"
    const val PROP_LICENSE_KEY = "inputstream.adaptive.license_key"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * @param kodiProps `#KODIPROP` values by lower-cased key
     * @return null when the stanza declares no DRM
     */
    fun parse(kodiProps: Map<String, String>): DrmSpec? {
        // One malformed channel must never abort the import of the whole playlist.
        return try {
            parseOrThrow(kodiProps)
        } catch (e: Exception) {
            DrmSpec.unsupported("malformed DRM properties: ${e::class.simpleName}")
        }
    }

    private fun parseOrThrow(kodiProps: Map<String, String>): DrmSpec? {
        // Newest syntax wins, as in Kodi 22.
        kodiProps[PROP_DRM]?.let { return parseDrmJson(it) }
        kodiProps[PROP_DRM_LEGACY]?.let { return parseDrmLegacy(it) }
        val licenseType = kodiProps[PROP_LICENSE_TYPE] ?: return null
        return parseLicenseType(licenseType, kodiProps[PROP_LICENSE_KEY])
    }

    fun keySystem(name: String): DrmSystem? = when (name.trim().lowercase()) {
        "com.widevine.alpha", "widevine" -> DrmSystem.WIDEVINE
        "com.microsoft.playready", "com.microsoft.playready.recommendation", "playready" -> DrmSystem.PLAYREADY
        "org.w3.clearkey", "clearkey" -> DrmSystem.CLEARKEY
        else -> null
    }

    // --- A. license_type + license_key --------------------------------------------------------

    private fun parseLicenseType(licenseType: String, licenseKey: String?): DrmSpec {
        val system = keySystem(licenseType)
            ?: return DrmSpec.unsupported("key system '$licenseType'")
        val value = licenseKey?.trim().orEmpty()

        // Kodi rejects ClearKey here; lists use it anyway, with keys or a URL in license_key.
        if (system == DrmSystem.CLEARKEY) {
            if (value.isEmpty()) return DrmSpec.unsupported("ClearKey without keys or licence URL", system)
            clearKeyMaterial(value)?.let { return it }
        }
        return parseLicenseKey(system, value)
    }

    /** `URL|headers|body|response`. */
    private fun parseLicenseKey(system: DrmSystem, value: String): DrmSpec {
        if (value.isEmpty()) return DrmSpec(system = system)
        val fields = value.split('|')
        if (fields.size > 4) return DrmSpec.unsupported("license_key has ${fields.size} fields", system)

        val url = fields[0].trim()
        if ("{SSM}" in url || "{HASH}" in url) {
            return DrmSpec.unsupported("GET licence request", system)
        }
        // Anything else in the URL field (keys meant for another system, a typo) must not be
        // requested as if it were a licence server.
        if (url.isNotEmpty() && !isUrl(url)) {
            return DrmSpec.unsupported("licence URL is not an http(s) URL", system)
        }
        val headers = fields.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?.let { HeaderSuffixParser.parseHeaderList(it).toMap() }
            .orEmpty()
        val body = fields.getOrNull(2)?.trim().orEmpty()
        if (body.isNotEmpty() && body != "R{SSM}") {
            return DrmSpec.unsupported("licence body '$body'", system)
        }
        val response = fields.getOrNull(3)?.trim().orEmpty()
        if (response.isNotEmpty() && response != "R") {
            return DrmSpec.unsupported("licence response '$response'", system)
        }
        if (system == DrmSystem.CLEARKEY && url.isEmpty()) {
            return DrmSpec.unsupported("ClearKey without keys or licence URL", system)
        }
        return DrmSpec(
            system = system,
            licenseUrl = url.ifEmpty { null },
            licenseHeaders = headers,
        )
    }

    // --- B. drm_legacy ------------------------------------------------------------------------

    /** `keysystem|licence URL or kid:key list|headers`, one to three fields. */
    private fun parseDrmLegacy(value: String): DrmSpec {
        val fields = value.split('|')
        if (fields.size > 3) return DrmSpec.unsupported("drm_legacy has ${fields.size} fields")
        val system = keySystem(fields[0])
            ?: return DrmSpec.unsupported("key system '${fields[0].trim()}'")
        val second = fields.getOrNull(1)?.trim().orEmpty()
        val headers = fields.getOrNull(2)?.takeIf { it.isNotBlank() }
            ?.let { HeaderSuffixParser.parseHeaderList(it).toMap() }
            .orEmpty()

        return when {
            second.isEmpty() -> if (system == DrmSystem.CLEARKEY) {
                DrmSpec.unsupported("ClearKey without keys or licence URL", system)
            } else {
                DrmSpec(system = system, licenseHeaders = headers)
            }

            second.startsWith("data:", ignoreCase = true) -> if (system == DrmSystem.CLEARKEY) {
                clearKeyMaterial(second) ?: DrmSpec.unsupported("unreadable ClearKey data URI", system)
            } else {
                DrmSpec.unsupported("data URI licence for $system", system)
            }

            isUrl(second) -> DrmSpec(system = system, licenseUrl = second, licenseHeaders = headers)

            system == DrmSystem.CLEARKEY ->
                clearKeyMaterial(second) ?: DrmSpec.unsupported("unreadable ClearKey keys", system)

            else -> DrmSpec.unsupported("local keys for $system", system)
        }
    }

    // --- C. drm JSON --------------------------------------------------------------------------

    private val FORBIDDEN_CONFIG_KEYS = setOf("init_data", "pre_init_data")
    private val ALLOWED_LICENSE_KEYS = setOf("server_url", "req_headers", "keyids")

    private fun parseDrmJson(value: String): DrmSpec? {
        val root = runCatching { json.parseToJsonElement(value).jsonObject }.getOrNull()
            ?: return DrmSpec.unsupported("drm is not a JSON object")

        // "none" configures clear streams (HLS AES-128), which Media3 plays without help,
        // so a document with nothing else declares no DRM at all.
        val candidates = root.entries
            .filter { !it.key.equals("none", ignoreCase = true) }
            .mapIndexed { index, (name, config) -> Triple(index, name, config as? JsonObject) }
        if (candidates.isEmpty()) return null

        val ordered = candidates.sortedWith(
            compareBy<Triple<Int, String, JsonObject?>> {
                // A malformed priority (object, text) just sorts last; it must not abort the import.
                (it.third?.get("priority") as? JsonPrimitive)?.intOrNull ?: Int.MAX_VALUE
            }.thenBy { it.first }
        )

        for ((_, name, config) in ordered) {
            val system = keySystem(name) ?: continue
            if (config == null) continue
            if (config.keys.any { it in FORBIDDEN_CONFIG_KEYS }) continue
            val license = config["license"] as? JsonObject ?: JsonObject(emptyMap())
            if (license.keys.any { it !in ALLOWED_LICENSE_KEYS }) continue

            val url = (license["server_url"] as? JsonPrimitive)?.contentOrNull?.trim()
            val headers = (license["req_headers"] as? JsonPrimitive)?.contentOrNull
                ?.let { HeaderSuffixParser.parseHeaderList(it).toMap() }
                .orEmpty()
            val keyIds = license["keyids"] as? JsonObject

            if (keyIds != null && keyIds.isNotEmpty()) {
                if (system != DrmSystem.CLEARKEY) continue
                val keys = keyIds.entries.map { (kid, key) ->
                    val kidHex = normalizeKey(kid)
                    val keyHex = normalizeKey((key as? JsonPrimitive)?.contentOrNull.orEmpty())
                    if (kidHex == null || keyHex == null) {
                        return DrmSpec.unsupported("unreadable ClearKey key", system)
                    }
                    ClearKey(kidHex, keyHex)
                }
                return DrmSpec(system = system, clearKeys = keys, licenseHeaders = headers)
            }
            if (url.isNullOrEmpty() && system == DrmSystem.CLEARKEY) continue
            return DrmSpec(system = system, licenseUrl = url?.ifEmpty { null }, licenseHeaders = headers)
        }
        return DrmSpec.unsupported("no drm entry TS IPTV can use")
    }

    // --- ClearKey material --------------------------------------------------------------------

    /**
     * Keys given inline, in the forms seen in lists: `kid:key[,kid:key]`, a JWKS object, or a
     * `data:application/json;base64,` JWKS. A URL gives a licence server instead.
     * Returns null when [value] is none of these.
     */
    private fun clearKeyMaterial(value: String): DrmSpec? {
        val system = DrmSystem.CLEARKEY
        return when {
            value.startsWith("data:", ignoreCase = true) -> {
                val meta = value.substringBefore(',')
                val payload = value.substringAfter(',', "")
                // `data:application/json;base64,…` or plain (percent-encoded) `data:application/json,{…}`.
                val decoded = if (meta.contains(";base64", ignoreCase = true)) {
                    decodeBase64(payload)?.decodeToString()
                } else {
                    HeaderSuffixParser.percentDecode(payload)
                } ?: return null
                jwksKeys(decoded)?.let { DrmSpec(system = system, clearKeys = it) }
            }

            value.startsWith("{") -> jwksKeys(value)?.let { DrmSpec(system = system, clearKeys = it) }
                ?: DrmSpec.unsupported("unreadable ClearKey JWKS", system)

            isUrl(value) -> null

            KEY_PAIR_LIST.matches(value) -> {
                val keys = value.split(',').map { pair ->
                    val kid = normalizeKey(pair.substringBefore(':'))
                    val key = normalizeKey(pair.substringAfter(':'))
                    if (kid == null || key == null) {
                        return DrmSpec.unsupported("unreadable ClearKey key", system)
                    }
                    ClearKey(kid, key)
                }
                DrmSpec(system = system, clearKeys = keys)
            }

            else -> null
        }
    }

    private fun jwksKeys(text: String): List<ClearKey>? = runCatching {
        val keys = json.parseToJsonElement(text).jsonObject["keys"] ?: return null
        (keys as kotlinx.serialization.json.JsonArray).map { element ->
            val obj = element.jsonObject
            val kid = normalizeKey(obj["kid"]?.jsonPrimitive?.contentOrNull.orEmpty())
            val key = normalizeKey(obj["k"]?.jsonPrimitive?.contentOrNull.orEmpty())
            ClearKey(kid ?: return null, key ?: return null)
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /**
     * A KID or key as 32 lower-case hex characters. UUID dashes are stripped; anything that is
     * not hex is read as base64 or base64url and must come to exactly 16 bytes.
     */
    fun normalizeKey(value: String): String? {
        val trimmed = value.trim()
        val undashed = trimmed.replace("-", "")
        if (HEX_32.matches(undashed)) return undashed.lowercase()
        val bytes = decodeBase64(trimmed) ?: return null
        return if (bytes.size == 16) bytesToHex(bytes) else null
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBase64(value: String): ByteArray? {
        val standard = value.trim().replace('-', '+').replace('_', '/').trimEnd('=')
        if (standard.isEmpty()) return null
        val padded = standard + "=".repeat((4 - standard.length % 4) % 4)
        return runCatching { Base64.Default.decode(padded) }.getOrNull()
    }

    private fun isUrl(value: String) =
        value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)

    private val HEX_32 = Regex("^[0-9a-fA-F]{32}$")

    /**
     * Pairs of non-URL tokens separated by `:`, spaces allowed around `:` and `,`
     * (`kid : key`, `a:b, c:d`); each token is checked by [normalizeKey].
     */
    private val KEY_PAIR_LIST =
        Regex("^\\s*[^:,\\s]+\\s*:\\s*[^:,\\s]+\\s*(,\\s*[^:,\\s]+\\s*:\\s*[^:,\\s]+\\s*)*$")
}
