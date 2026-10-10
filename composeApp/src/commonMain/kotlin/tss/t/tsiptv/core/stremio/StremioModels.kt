package tss.t.tsiptv.core.stremio

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.listSerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * DTOs for the Stremio addon protocol (research §2, §3). Types, posterShape and link categories
 * are raw strings on purpose: addons use free-form values (research §4).
 */

// ---------------------------------------------------------------------------------------------
// Manifest
// ---------------------------------------------------------------------------------------------

/** Well-known resource names. Any other name is kept as is. */
object StremioResource {
    const val CATALOG = "catalog"
    const val META = "meta"
    const val STREAM = "stream"
    const val SUBTITLES = "subtitles"
    const val ADDON_CATALOG = "addon_catalog"
}

/** Well-known extra names (research §2.1). */
object StremioExtra {
    const val SEARCH = "search"
    const val GENRE = "genre"
    const val SKIP = "skip"
    const val DATE = "date"
}

/** Well-known content types. Types are free-form; these are UI hints only. */
object StremioType {
    const val MOVIE = "movie"
    const val SERIES = "series"
    const val TV = "tv"
    const val CHANNEL = "channel"
}

/**
 * The parsed manifest. Build it with [StremioManifestParser], which validates the required fields;
 * decoding it directly skips that validation.
 */
@Serializable
data class StremioManifest(
    val id: String,
    @Serializable(with = LenientStringSerializer::class) val version: String? = null,
    val name: String,
    @Serializable(with = LenientStringSerializer::class) val description: String? = null,
    @Serializable(with = LenientStringListSerializer::class) val types: List<String>? = null,
    @Serializable(with = ManifestResourceListSerializer::class) val resources: List<ManifestResource>? = null,
    @Serializable(with = LenientStringListSerializer::class) val idPrefixes: List<String>? = null,
    @Serializable(with = CatalogListSerializer::class) val catalogs: List<ManifestCatalog>? = null,
    @Serializable(with = CatalogListSerializer::class) val addonCatalogs: List<ManifestCatalog>? = null,
    @Serializable(with = ManifestHintsSerializer::class) val behaviorHints: ManifestBehaviorHints? = null,
    @Serializable(with = LenientStringSerializer::class) val logo: String? = null,
    @Serializable(with = LenientStringSerializer::class) val background: String? = null,
    @Serializable(with = LenientStringSerializer::class) val contactEmail: String? = null,
) {
    val versionText: String get() = version.orEmpty()
    val typeList: List<String> get() = types.orEmpty()
    val resourceList: List<ManifestResource> get() = resources.orEmpty()
    val catalogList: List<ManifestCatalog> get() = catalogs.orEmpty()
    val addonCatalogList: List<ManifestCatalog> get() = addonCatalogs.orEmpty()
    val hints: ManifestBehaviorHints get() = behaviorHints ?: ManifestBehaviorHints()

    /** `logo` if it looks like a URL (core ignores empty or invalid values). */
    val logoUrl: String? get() = logo?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
}

@Serializable
data class ManifestBehaviorHints(
    @Serializable(with = LenientBooleanSerializer::class) val adult: Boolean? = null,
    @Serializable(with = LenientBooleanSerializer::class) val p2p: Boolean? = null,
    @Serializable(with = LenientBooleanSerializer::class) val configurable: Boolean? = null,
    @Serializable(with = LenientBooleanSerializer::class) val configurationRequired: Boolean? = null,
    @Serializable(with = LenientBooleanSerializer::class) val epgProvider: Boolean? = null,
) {
    val isAdult: Boolean get() = adult == true
    val isP2p: Boolean get() = p2p == true
    val isConfigurable: Boolean get() = configurable == true
    val isConfigurationRequired: Boolean get() = configurationRequired == true
    val isEpgProvider: Boolean get() = epgProvider == true
}

/**
 * One `resources` entry. [isShortForm] is true for the string form (`"stream"`), which uses the
 * manifest's `types` and `idPrefixes`; the object form uses its own ([types] null = no type,
 * [idPrefixes] null/empty = any id) — research §2.2.
 */
@Serializable(with = ManifestResourceSerializer::class)
data class ManifestResource(
    val name: String,
    val types: List<String>? = null,
    val idPrefixes: List<String>? = null,
    val isShortForm: Boolean = false,
)

/**
 * A catalog (or addon catalog) with its extras already resolved: `extra` wins, else the legacy
 * `extraSupported`/`extraRequired` (+ `genres` as `genre` options). `skip` is normalised.
 */
@Serializable(with = ManifestCatalogSerializer::class)
data class ManifestCatalog(
    val type: String,
    val id: String,
    val name: String? = null,
    val extras: List<CatalogExtra> = emptyList(),
) {
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: id
    fun extra(name: String): CatalogExtra? = extras.firstOrNull { it.name == name }
    val requiredExtras: List<CatalogExtra> get() = extras.filter { it.isRequired }
    val supportsSkip: Boolean get() = extra(StremioExtra.SKIP) != null
    val supportsSearch: Boolean get() = extra(StremioExtra.SEARCH) != null
}

data class CatalogExtra(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String> = emptyList(),
    val optionsLimit: Int = 1,
)

// ---------------------------------------------------------------------------------------------
// Meta
// ---------------------------------------------------------------------------------------------

/** A catalog item (MetaPreview) or a full meta. Unknown fields are ignored. */
@Serializable
data class StremioMeta(
    @Serializable(with = LenientStringSerializer::class) val id: String? = null,
    @Serializable(with = LenientStringSerializer::class) val type: String? = null,
    @Serializable(with = LenientStringSerializer::class) val name: String? = null,
    @Serializable(with = LenientStringSerializer::class) val poster: String? = null,
    @Serializable(with = LenientStringSerializer::class) val posterShape: String? = null,
    @Serializable(with = LenientStringSerializer::class) val background: String? = null,
    @Serializable(with = LenientStringSerializer::class) val logo: String? = null,
    @Serializable(with = LenientStringSerializer::class) val description: String? = null,
    @Serializable(with = LenientStringSerializer::class) val releaseInfo: String? = null,
    @Serializable(with = LenientStringSerializer::class) val released: String? = null,
    @Serializable(with = LenientStringSerializer::class) val year: String? = null,
    @Serializable(with = LenientStringSerializer::class) val runtime: String? = null,
    @Serializable(with = LenientStringSerializer::class) val language: String? = null,
    @Serializable(with = LenientStringSerializer::class) val country: String? = null,
    @Serializable(with = LenientStringSerializer::class) val awards: String? = null,
    @Serializable(with = LenientStringSerializer::class) val website: String? = null,
    @Serializable(with = LenientStringSerializer::class) val imdbRating: String? = null,
    @Serializable(with = LenientStringListSerializer::class) val genres: List<String>? = null,
    @Serializable(with = LenientStringListSerializer::class) val director: List<String>? = null,
    @Serializable(with = LenientStringListSerializer::class) val cast: List<String>? = null,
    @Serializable(with = MetaLinkListSerializer::class) val links: List<MetaLink>? = null,
    @Serializable(with = VideoListSerializer::class) val videos: List<StremioVideo>? = null,
    @Serializable(with = MetaHintsSerializer::class) val behaviorHints: MetaBehaviorHints? = null,
) {
    /** A meta is usable only with an id (`{"meta":{}}` means "not found here", research §3.2). */
    val isValid: Boolean get() = !id.isNullOrBlank()

    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: id.orEmpty()

    /** `poster`, `square`, `landscape`; anything else (e.g. the static example's `regular`) → `poster`. */
    val resolvedPosterShape: String
        get() = when (posterShape?.lowercase()) {
            "square" -> "square"
            "landscape" -> "landscape"
            else -> "poster"
        }

    /** `type: "tv"` implies live (research §3.2). */
    val isLive: Boolean get() = behaviorHints?.isLive == true || type == StremioType.TV

    /**
     * The videos to pick streams for. If `videos` is absent (or empty), the item has exactly one video
     * whose id equals the meta id (movies, `tv` without EPG).
     */
    val effectiveVideos: List<StremioVideo>
        get() = videos?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(id?.let { StremioVideo(id = it, title = name, released = released, thumbnail = poster) })

    /** Fills this meta's missing fields from [other] (first non-empty answer wins, PRD §6). */
    fun fillMissingFrom(other: StremioMeta?): StremioMeta {
        if (other == null) return this
        return copy(
            name = name ?: other.name,
            poster = poster ?: other.poster,
            posterShape = posterShape ?: other.posterShape,
            background = background ?: other.background,
            logo = logo ?: other.logo,
            description = description ?: other.description,
            releaseInfo = releaseInfo ?: other.releaseInfo,
            released = released ?: other.released,
            year = year ?: other.year,
            runtime = runtime ?: other.runtime,
            language = language ?: other.language,
            country = country ?: other.country,
            awards = awards ?: other.awards,
            website = website ?: other.website,
            imdbRating = imdbRating ?: other.imdbRating,
            genres = genres?.takeIf { it.isNotEmpty() } ?: other.genres,
            director = director?.takeIf { it.isNotEmpty() } ?: other.director,
            cast = cast?.takeIf { it.isNotEmpty() } ?: other.cast,
            links = links?.takeIf { it.isNotEmpty() } ?: other.links,
            videos = videos?.takeIf { it.isNotEmpty() } ?: other.videos,
            behaviorHints = behaviorHints ?: other.behaviorHints,
        )
    }
}

@Serializable
data class MetaLink(
    @Serializable(with = LenientStringSerializer::class) val name: String? = null,
    @Serializable(with = LenientStringSerializer::class) val category: String? = null,
    @Serializable(with = LenientStringSerializer::class) val url: String? = null,
) {
    /** Only `stremio:///…` links are handled by the app (PRD §6); everything else is not shown. */
    val isInternal: Boolean get() = url?.startsWith(StremioDeepLink.PREFIX) == true
}

@Serializable
data class MetaBehaviorHints(
    @Serializable(with = LenientStringSerializer::class) val defaultVideoId: String? = null,
    @Serializable(with = LenientBooleanSerializer::class) val isLive: Boolean? = null,
    @Serializable(with = LenientBooleanSerializer::class) val hasScheduledVideos: Boolean? = null,
)

@Serializable
data class StremioVideo(
    @Serializable(with = LenientStringSerializer::class) val id: String? = null,
    @Serializable(with = LenientStringSerializer::class) val title: String? = null,
    @Serializable(with = LenientStringSerializer::class) val name: String? = null,
    @Serializable(with = LenientStringSerializer::class) val released: String? = null,
    @Serializable(with = LenientIntSerializer::class) val season: Int? = null,
    @Serializable(with = LenientIntSerializer::class) val episode: Int? = null,
    @Serializable(with = LenientIntSerializer::class) val number: Int? = null,
    @Serializable(with = LenientStringSerializer::class) val thumbnail: String? = null,
    @Serializable(with = LenientStringSerializer::class) val overview: String? = null,
    @Serializable(with = LenientStringSerializer::class) val description: String? = null,
    /** Inline streams are **exclusive**: no `/stream/` request is made for this video (research §3.2). */
    @Serializable(with = StreamListSerializer::class) val streams: List<StremioStream>? = null,
    @Serializable(with = LenientBooleanSerializer::class) val available: Boolean? = null,
    @Serializable(with = LenientStringSerializer::class) val startTime: String? = null,
    @Serializable(with = LenientStringSerializer::class) val endTime: String? = null,
) {
    /** Cinemeta sends `name` instead of `title` for episodes. */
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: name.orEmpty()

    /** `episode`, else Cinemeta's legacy `number`. */
    val episodeNumber: Int? get() = episode ?: number

    val synopsis: String? get() = overview ?: description

    /** Non-empty inline streams only; `streams: []` still asks the stream addons. */
    val hasInlineStreams: Boolean get() = !streams.isNullOrEmpty()
}

// ---------------------------------------------------------------------------------------------
// Stream
// ---------------------------------------------------------------------------------------------

/**
 * One stream. Exactly one source field is expected; [StreamClassifier] decides the kind by field
 * presence. Unsupported source fields are kept only as presence markers (never shown in the UI).
 */
@Serializable
data class StremioStream(
    @Serializable(with = LenientStringSerializer::class) val url: String? = null,
    @Serializable(with = LenientStringSerializer::class) val externalUrl: String? = null,
    @Serializable(with = LenientStringSerializer::class) val androidTvUrl: String? = null,
    @Serializable(with = LenientStringSerializer::class) val ytId: String? = null,
    @Serializable(with = LenientStringSerializer::class) val infoHash: String? = null,
    @Serializable(with = LenientIntSerializer::class) val fileIdx: Int? = null,
    @Serializable(with = LenientStringSerializer::class) val nzbUrl: String? = null,
    val nzbUrls: JsonElement? = null,
    val rarUrls: JsonElement? = null,
    val zipUrls: JsonElement? = null,
    @kotlinx.serialization.SerialName("7zipUrls") val sevenZipUrls: JsonElement? = null,
    val tgzUrls: JsonElement? = null,
    val tarUrls: JsonElement? = null,
    @Serializable(with = LenientStringSerializer::class) val playerFrameUrl: String? = null,
    @Serializable(with = LenientStringSerializer::class) val name: String? = null,
    @Serializable(with = LenientStringSerializer::class) val description: String? = null,
    @Serializable(with = LenientStringSerializer::class) val title: String? = null,
    @Serializable(with = LenientStringSerializer::class) val thumbnail: String? = null,
    @Serializable(with = SubtitleListSerializer::class) val subtitles: List<StremioSubtitle>? = null,
    val behaviorHints: StreamBehaviorHints? = null,
) {
    /** `description`, else the legacy `title` (research §3.3). */
    val displayDescription: String? get() = description?.takeIf { it.isNotBlank() } ?: title?.takeIf { it.isNotBlank() }
    val hints: StreamBehaviorHints get() = behaviorHints ?: StreamBehaviorHints()

    /** Redacted: stream URLs carry tokens and proxy headers carry cookies. Host and header names only. */
    override fun toString(): String =
        "StremioStream(name=$name, urlHost=${url?.let { UrlParts.split(it.trim())?.host }}, " +
            "externalHost=${externalUrl?.let { UrlParts.split(it.trim())?.host }}, headers=${hints.requestHeaders.keys})"
}

/** Stream behaviour hints. Unknown hints are preserved in [other] (core does the same). */
@Serializable(with = StreamBehaviorHintsSerializer::class)
data class StreamBehaviorHints(
    val notWebReady: Boolean = false,
    val bingeGroup: String? = null,
    val countryWhitelist: List<String> = emptyList(),
    /** `proxyHeaders.request`: headers for every request of the media (playlist, variants, segments). */
    val requestHeaders: Map<String, String> = emptyMap(),
    /** `proxyHeaders.response`: no native equivalent; kept only as a MIME hint source. */
    val responseHeaders: Map<String, String> = emptyMap(),
    val filename: String? = null,
    val videoHash: String? = null,
    val videoSize: Long? = null,
    val other: Map<String, JsonElement> = emptyMap(),
    /**
     * App-only, never read from or written to the wire: the stream has DRM (TS IPTV Source streams),
     * so iOS refuses it whatever its format.
     */
    val hasDrm: Boolean = false,
)

// ---------------------------------------------------------------------------------------------
// Subtitles (phase F2b; DTO only)
// ---------------------------------------------------------------------------------------------

@Serializable
data class StremioSubtitle(
    @Serializable(with = LenientStringSerializer::class) val id: String? = null,
    @Serializable(with = LenientStringSerializer::class) val url: String? = null,
    @Serializable(with = LenientStringSerializer::class) val lang: String? = null,
    @Serializable(with = LenientStringSerializer::class) val label: String? = null,
) {
    val displayLabel: String get() = label?.takeIf { it.isNotBlank() } ?: lang.orEmpty()

    /** Redacted: subtitle URLs may be signed. */
    override fun toString(): String = "StremioSubtitle(id=$id, lang=$lang, host=${url?.let { UrlParts.split(it.trim())?.host }})"
}

// ---------------------------------------------------------------------------------------------
// Addon catalog (format only; never used to browse Stremio's own collections)
// ---------------------------------------------------------------------------------------------

@Serializable
data class AddonDescriptor(
    @Serializable(with = LenientStringSerializer::class) val transportName: String? = null,
    @Serializable(with = LenientStringSerializer::class) val transportUrl: String? = null,
    val manifest: JsonObject? = null,
)

// ---------------------------------------------------------------------------------------------
// Serializers
// ---------------------------------------------------------------------------------------------

private fun Decoder.json(): JsonDecoder = this as? JsonDecoder ?: error("Stremio DTOs can only be decoded from JSON")
private fun Encoder.json(): JsonEncoder = this as? JsonEncoder ?: error("Stremio DTOs can only be encoded to JSON")

object ManifestResourceSerializer : KSerializer<ManifestResource> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("tss.stremio.ManifestResource")

    override fun deserialize(decoder: Decoder): ManifestResource =
        parse(decoder.json().decodeJsonElement()) ?: throw kotlinx.serialization.SerializationException("Invalid resource")

    internal fun parse(element: JsonElement): ManifestResource? = when (element) {
        is JsonPrimitive -> element.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { ManifestResource(name = it, isShortForm = true) }
        is JsonObject -> element["name"].primitiveText()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            ManifestResource(
                name = it,
                types = element["types"].lenientStringList(),
                idPrefixes = element["idPrefixes"].lenientStringList(),
                isShortForm = false,
            )
        }
        else -> null
    }

    override fun serialize(encoder: Encoder, value: ManifestResource) {
        val element = if (value.isShortForm) JsonPrimitive(value.name) else buildJsonObject {
            put("name", value.name)
            value.types?.let { t -> put("types", buildJsonArray { t.forEach { add(JsonPrimitive(it)) } }) }
            value.idPrefixes?.let { p -> put("idPrefixes", buildJsonArray { p.forEach { add(JsonPrimitive(it)) } }) }
        }
        encoder.json().encodeJsonElement(element)
    }
}

object ManifestResourceListSerializer : KSerializer<List<ManifestResource>?> {
    override val descriptor: SerialDescriptor = listSerialDescriptor(ManifestResourceSerializer.descriptor).nullable
    override fun deserialize(decoder: Decoder): List<ManifestResource>? {
        val element = decoder.json().decodeJsonElement()
        if (element !is JsonArray) return null
        return element.mapNotNull { ManifestResourceSerializer.parse(it) }
    }
    override fun serialize(encoder: Encoder, value: List<ManifestResource>?) {
        if (value == null) encoder.encodeNull() else encoder.encodeSerializableValue(ListSerializer(ManifestResourceSerializer), value)
    }
}

object ManifestCatalogSerializer : KSerializer<ManifestCatalog> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("tss.stremio.ManifestCatalog")

    override fun deserialize(decoder: Decoder): ManifestCatalog =
        parse(decoder.json().decodeJsonElement()) ?: throw kotlinx.serialization.SerializationException("Invalid catalog")

    /** Returns null for catalogs without `type` or `id`. */
    internal fun parse(element: JsonElement): ManifestCatalog? {
        val obj = element as? JsonObject ?: return null
        val type = obj["type"].primitiveText()?.takeIf { it.isNotBlank() } ?: return null
        val id = obj["id"].primitiveText()?.takeIf { it.isNotBlank() } ?: return null
        val name = obj["name"].primitiveText()
        val extras = when (val extra = obj["extra"]) {
            is JsonArray -> extra.mapNotNull { parseExtra(it) }
            else -> legacyExtras(obj)
        }
        return ManifestCatalog(type = type, id = id, name = name, extras = normalise(extras))
    }

    private fun parseExtra(element: JsonElement): CatalogExtra? {
        val obj = element as? JsonObject ?: return null
        val name = obj["name"].primitiveText()?.takeIf { it.isNotBlank() } ?: return null
        return CatalogExtra(
            name = name,
            isRequired = obj["isRequired"].lenientBoolean() ?: false,
            options = obj["options"].lenientStringList().orEmpty(),
            optionsLimit = (obj["optionsLimit"].lenientInt() ?: 1).coerceAtLeast(1),
        )
    }

    /** Legacy `extraSupported`/`extraRequired`; `genres` become the `genre` options [inference, research §2.1]. */
    private fun legacyExtras(obj: JsonObject): List<CatalogExtra> {
        val required = obj["extraRequired"].lenientStringList().orEmpty()
        val supported = (obj["extraSupported"].lenientStringList().orEmpty() + required).distinct()
        val genres = obj["genres"].lenientStringList().orEmpty()
        return supported.filter { it.isNotBlank() }.map { name ->
            CatalogExtra(
                name = name,
                isRequired = name in required,
                options = if (name == StremioExtra.GENRE) genres else emptyList(),
            )
        }
    }

    /** First declaration of a name wins; `skip` is never required and has no options (core `ExtraPropValid`). */
    private fun normalise(extras: List<CatalogExtra>): List<CatalogExtra> =
        extras.distinctBy { it.name }.map {
            if (it.name == StremioExtra.SKIP) CatalogExtra(name = StremioExtra.SKIP) else it
        }

    override fun serialize(encoder: Encoder, value: ManifestCatalog) {
        encoder.json().encodeJsonElement(buildJsonObject {
            put("type", value.type)
            put("id", value.id)
            value.name?.let { put("name", it) }
            put("extra", buildJsonArray {
                value.extras.forEach { e ->
                    add(buildJsonObject {
                        put("name", e.name)
                        if (e.isRequired) put("isRequired", true)
                        if (e.options.isNotEmpty()) put("options", buildJsonArray { e.options.forEach { add(JsonPrimitive(it)) } })
                        if (e.optionsLimit != 1) put("optionsLimit", e.optionsLimit)
                    })
                }
            })
        })
    }
}

/** Catalog lists: malformed entries are dropped; duplicate `(type, id)` pairs keep the first (core). */
object CatalogListSerializer : KSerializer<List<ManifestCatalog>?> {
    override val descriptor: SerialDescriptor = listSerialDescriptor(ManifestCatalogSerializer.descriptor).nullable
    override fun deserialize(decoder: Decoder): List<ManifestCatalog>? {
        val element = decoder.json().decodeJsonElement()
        if (element !is JsonArray) return null
        return element.mapNotNull { ManifestCatalogSerializer.parse(it) }.distinctBy { it.type to it.id }
    }
    override fun serialize(encoder: Encoder, value: List<ManifestCatalog>?) {
        if (value == null) encoder.encodeNull() else encoder.encodeSerializableValue(ListSerializer(ManifestCatalogSerializer), value)
    }
}

/** A list whose malformed elements are dropped instead of failing the whole object. */
abstract class TolerantListSerializer<T>(private val element: KSerializer<T>) : KSerializer<List<T>?> {
    override val descriptor: SerialDescriptor = listSerialDescriptor(element.descriptor).nullable
    override fun deserialize(decoder: Decoder): List<T>? {
        val json = decoder.json()
        val array = json.decodeJsonElement() as? JsonArray ?: return null
        return array.decodeEach { json.json.decodeFromJsonElement(element, it) }
    }
    override fun serialize(encoder: Encoder, value: List<T>?) {
        if (value == null) encoder.encodeNull() else encoder.encodeSerializableValue(ListSerializer(element), value)
    }
}

/** Videos without an id cannot be played or requested: dropped. */
object VideoListSerializer : TolerantListSerializer<StremioVideo>(StremioVideo.serializer()) {
    override fun deserialize(decoder: Decoder): List<StremioVideo>? =
        super.deserialize(decoder)?.filter { !it.id.isNullOrBlank() }
}
object StreamListSerializer : TolerantListSerializer<StremioStream>(StremioStream.serializer())
object SubtitleListSerializer : TolerantListSerializer<StremioSubtitle>(StremioSubtitle.serializer())
object MetaLinkListSerializer : TolerantListSerializer<MetaLink>(MetaLink.serializer())

/** An object field that is dropped (null) when it is not an object or fails to decode. */
abstract class TolerantObjectSerializer<T : Any>(private val element: KSerializer<T>) : KSerializer<T?> {
    override val descriptor: SerialDescriptor = element.descriptor.nullable
    override fun deserialize(decoder: Decoder): T? {
        val json = decoder.json()
        val obj = json.decodeJsonElement() as? JsonObject ?: return null
        return try {
            json.json.decodeFromJsonElement(element, obj)
        } catch (_: kotlinx.serialization.SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
    override fun serialize(encoder: Encoder, value: T?) {
        if (value == null) encoder.encodeNull() else encoder.encodeSerializableValue(element, value)
    }
}

object ManifestHintsSerializer : TolerantObjectSerializer<ManifestBehaviorHints>(ManifestBehaviorHints.serializer())
object MetaHintsSerializer : TolerantObjectSerializer<MetaBehaviorHints>(MetaBehaviorHints.serializer())

object StreamBehaviorHintsSerializer : KSerializer<StreamBehaviorHints> {
    private val KNOWN = setOf(
        "notWebReady", "bingeGroup", "countryWhitelist", "proxyHeaders", "filename", "videoHash", "videoSize",
    )

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("tss.stremio.StreamBehaviorHints")

    override fun deserialize(decoder: Decoder): StreamBehaviorHints {
        val obj = decoder.json().decodeJsonElement() as? JsonObject ?: return StreamBehaviorHints()
        val proxy = obj["proxyHeaders"] as? JsonObject
        return StreamBehaviorHints(
            notWebReady = obj["notWebReady"].lenientBoolean() ?: false,
            bingeGroup = obj["bingeGroup"].primitiveText()?.takeIf { it.isNotBlank() },
            countryWhitelist = obj["countryWhitelist"].lenientStringList().orEmpty().map { it.lowercase() },
            requestHeaders = headerMap(proxy?.get("request")),
            responseHeaders = headerMap(proxy?.get("response"), forRequest = false),
            filename = obj["filename"].primitiveText()?.takeIf { it.isNotBlank() },
            videoHash = obj["videoHash"].primitiveText()?.takeIf { it.isNotBlank() },
            videoSize = obj["videoSize"].lenientLong(),
            other = obj.filterKeys { it !in KNOWN },
        )
    }

    /**
     * Header maps: primitive values only; names must be valid tokens and values may contain only tab
     * and visible ASCII/space (0x20–0x7E), so an addon cannot inject header lines. For request
     * headers, the ones that control the connection or framing are dropped as well.
     */
    internal fun headerMap(element: JsonElement?, forRequest: Boolean = true): Map<String, String> {
        val obj = element as? JsonObject ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        for ((key, value) in obj) {
            val name = key.trim()
            val text = value.primitiveText() ?: continue
            if (name.isEmpty() || !name.all { it.isHeaderTokenChar() }) continue
            if (!text.all { it == '\t' || it.code in 0x20..0x7E }) continue
            if (forRequest && name.lowercase() in FORBIDDEN_REQUEST_HEADERS) continue
            out[name] = text
        }
        return out
    }

    private val FORBIDDEN_REQUEST_HEADERS = setOf("host", "content-length", "transfer-encoding", "connection", "range")

    private fun Char.isHeaderTokenChar(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this in "!#$%&'*+-.^_`|~"

    override fun serialize(encoder: Encoder, value: StreamBehaviorHints) {
        val stringMap = MapSerializer(String.serializer(), String.serializer())
        val json = encoder.json()
        json.encodeJsonElement(buildJsonObject {
            value.other.forEach { (k, v) -> put(k, v) }
            if (value.notWebReady) put("notWebReady", true)
            value.bingeGroup?.let { put("bingeGroup", it) }
            if (value.countryWhitelist.isNotEmpty()) {
                put("countryWhitelist", buildJsonArray { value.countryWhitelist.forEach { add(JsonPrimitive(it)) } })
            }
            if (value.requestHeaders.isNotEmpty() || value.responseHeaders.isNotEmpty()) {
                put("proxyHeaders", buildJsonObject {
                    if (value.requestHeaders.isNotEmpty()) put("request", json.json.encodeToJsonElement(stringMap, value.requestHeaders))
                    if (value.responseHeaders.isNotEmpty()) put("response", json.json.encodeToJsonElement(stringMap, value.responseHeaders))
                })
            }
            value.filename?.let { put("filename", it) }
            value.videoHash?.let { put("videoHash", it) }
            value.videoSize?.let { put("videoSize", it) }
        })
    }
}

internal fun JsonElement?.isPresent(): Boolean = this != null && this !is JsonNull &&
    !(this is JsonPrimitive && this.isString && content.isBlank()) &&
    !(this is JsonArray && isEmpty())
