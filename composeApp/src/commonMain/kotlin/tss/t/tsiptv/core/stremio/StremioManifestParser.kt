package tss.t.tsiptv.core.stremio

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

sealed interface ManifestParseResult {
    data class Valid(val manifest: StremioManifest) : ManifestParseResult

    /**
     * Maps to `addon_error_invalid_manifest`. [field] is the missing/invalid field name for the
     * details line (`null` when the body is not a JSON object at all).
     */
    data class Invalid(val field: String?) : ManifestParseResult
}

/**
 * Validates and parses a manifest (PRD §1): `id`, `name`, `version` (a string, not parsed as
 * semver; a bare number is accepted), `types` (array) and `resources` (array) must be present;
 * `catalogs` defaults to `[]`. Everything else is parsed tolerantly.
 */
object StremioManifestParser {
    const val FIELD_ID = "id"
    const val FIELD_NAME = "name"
    const val FIELD_VERSION = "version"
    const val FIELD_TYPES = "types"
    const val FIELD_RESOURCES = "resources"

    fun parse(body: String): ManifestParseResult {
        val element = try {
            StremioJson.parseToJsonElement(body.withoutBom())
        } catch (_: Exception) {
            return ManifestParseResult.Invalid(null)
        }
        return parse(element)
    }

    fun parse(element: JsonElement): ManifestParseResult {
        val obj = element as? JsonObject ?: return ManifestParseResult.Invalid(null)
        if (obj.nonBlankString(FIELD_ID) == null) return ManifestParseResult.Invalid(FIELD_ID)
        if (obj.nonBlankString(FIELD_NAME) == null) return ManifestParseResult.Invalid(FIELD_NAME)
        if (obj.nonBlankString(FIELD_VERSION) == null) return ManifestParseResult.Invalid(FIELD_VERSION)
        if (obj[FIELD_TYPES] !is JsonArray) return ManifestParseResult.Invalid(FIELD_TYPES)
        if (obj[FIELD_RESOURCES] !is JsonArray) return ManifestParseResult.Invalid(FIELD_RESOURCES)
        val manifest = try {
            StremioJson.decodeFromJsonElement(StremioManifest.serializer(), obj)
        } catch (_: Exception) {
            return ManifestParseResult.Invalid(null)
        }
        return ManifestParseResult.Valid(
            manifest.copy(
                id = manifest.id.trim(),
                catalogs = manifest.catalogs.orEmpty(),
                addonCatalogs = manifest.addonCatalogs.orEmpty(),
            )
        )
    }

    /** Convenience for data already validated once (e.g. the manifest persisted in Room). */
    fun parseOrNull(body: String): StremioManifest? = (parse(body) as? ManifestParseResult.Valid)?.manifest

    private fun JsonObject.nonBlankString(key: String): String? {
        val p = this[key] as? JsonPrimitive ?: return null
        return p.primitiveText()?.takeIf { it.isNotBlank() }
    }
}
