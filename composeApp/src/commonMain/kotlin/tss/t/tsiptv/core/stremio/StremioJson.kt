package tss.t.tsiptv.core.stremio

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The JSON configuration for everything an addon sends (research §8.3). Addons are hand-written
 * as often as they are generated, so parsing is as tolerant as kotlinx.serialization allows.
 */
val StremioJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = false
}

/** Strips a leading UTF-8 BOM (hand-edited static manifests on Windows often have one). */
internal fun String.withoutBom(): String = removePrefix("\uFEFF")

/** A JSON primitive as text: strings as is, numbers and booleans as their literal. Arrays, objects, null → null. */
internal fun JsonElement?.primitiveText(): String? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.content
}

internal fun JsonElement?.lenientInt(): Int? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    p.longOrNull?.let { return it.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt() }
    p.doubleOrNull?.let { if (it.isFinite()) return it.toLong().toInt() }
    return p.content.trim().toIntOrNull()
}

internal fun JsonElement?.lenientLong(): Long? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    p.longOrNull?.let { return it }
    p.doubleOrNull?.let { if (it.isFinite()) return it.toLong() }
    return p.content.trim().toLongOrNull()
}

internal fun JsonElement?.lenientBoolean(): Boolean? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    p.booleanOrNull?.let { return it }
    return when (p.content.trim().lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }
}

/** An array of primitives as strings (non-primitives dropped); a single primitive becomes a one-element list. */
internal fun JsonElement?.lenientStringList(): List<String>? = when (this) {
    null, is JsonNull -> null
    is JsonArray -> mapNotNull { it.primitiveText() }
    is JsonPrimitive -> listOf(content)
    is JsonObject -> null
}

private fun Decoder.element(): JsonElement =
    (this as? JsonDecoder)?.decodeJsonElement()
        ?: error("Stremio DTOs can only be decoded from JSON")

/** Number-or-string fields such as `year`, `imdbRating`, `releaseInfo`: always a string. */
object LenientStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("tss.stremio.LenientString", PrimitiveKind.STRING).nullable

    override fun deserialize(decoder: Decoder): String? = decoder.element().primitiveText()

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

/** Number-or-string integer fields such as `season`, `episode`, `fileIdx`. Garbage → null. */
object LenientIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("tss.stremio.LenientInt", PrimitiveKind.INT).nullable

    override fun deserialize(decoder: Decoder): Int? = decoder.element().lenientInt()

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }
}

/** Booleans that some addons send as `"true"` or `1`. Garbage → null (the field's default applies in the model). */
object LenientBooleanSerializer : KSerializer<Boolean?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("tss.stremio.LenientBoolean", PrimitiveKind.BOOLEAN).nullable

    override fun deserialize(decoder: Decoder): Boolean? = decoder.element().lenientBoolean()

    override fun serialize(encoder: Encoder, value: Boolean?) {
        if (value == null) encoder.encodeNull() else encoder.encodeBoolean(value)
    }
}

/** `string[]` fields that are sometimes a single string, or contain numbers. */
object LenientStringListSerializer : KSerializer<List<String>?> {
    override val descriptor: SerialDescriptor = listSerialDescriptor(String.serializer().descriptor).nullable

    override fun deserialize(decoder: Decoder): List<String>? = decoder.element().lenientStringList()

    override fun serialize(encoder: Encoder, value: List<String>?) {
        if (value == null) encoder.encodeNull()
        else encoder.encodeSerializableValue(kotlinx.serialization.builtins.ListSerializer(String.serializer()), value)
    }
}

/**
 * Decodes each element of a JSON array on its own and drops the ones that fail, so one malformed
 * catalog item or stream never empties a whole response. Anything that is not an array → empty.
 */
internal inline fun <T> JsonElement?.decodeEach(decode: (JsonElement) -> T?): List<T> {
    val array = this as? JsonArray ?: return emptyList()
    val out = ArrayList<T>(array.size)
    for (element in array) {
        val value = try {
            decode(element)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (value != null) out += value
    }
    return out
}
