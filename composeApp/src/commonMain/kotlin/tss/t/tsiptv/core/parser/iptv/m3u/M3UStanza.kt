package tss.t.tsiptv.core.parser.iptv.m3u

import tss.t.tsiptv.core.parser.model.IPTVChannel
import tss.t.tsiptv.core.parser.model.SkipReason
import tss.t.tsiptv.core.parser.model.playback.CatchupMode
import tss.t.tsiptv.core.parser.model.playback.CatchupSpec
import tss.t.tsiptv.core.parser.model.playback.StreamMimeTypes

/**
 * The PVR IPTV Simple Client stanza model shared by the M3U, plain URL list and `.strm`
 * parsers: everything that applies to the next URL line, and how it becomes a channel.
 */
internal class M3UStanza(val lineNumber: Int) {
    var extInf: ExtInf? = null
    var extGrp: String? = null
    val kodiProps = LinkedHashMap<String, String>()
    val vlcOpts = LinkedHashMap<String, String>()
    var vodMarker = false
    var webProp = false

    /** `#KODIPROP:key=value`: key lower-cased, value to the end of the line (JSON allowed). */
    fun addKodiProp(line: String) {
        val body = line.substringAfter(':')
        val eq = body.indexOf('=')
        if (eq <= 0) return
        var key = body.substring(0, eq).trim().lowercase()
        // Kodi rewrites these two to "inputstream"; they only pick Kodi's demuxer.
        if (key == "inputstreamaddon" || key == "inputstreamclass") key = "inputstream"
        kodiProps[key] = body.substring(eq + 1).trim()
    }

    /** `#EXTVLCOPT:key=value` and the `#EXTVLCOPT--key=value` spelling. */
    fun addVlcOpt(line: String) {
        val body = line.removePrefix("#EXTVLCOPT").removePrefix(":").removePrefix("--")
        val eq = body.indexOf('=')
        if (eq <= 0) return
        vlcOpts[body.substring(0, eq).trim().lowercase()] = body.substring(eq + 1).trim()
    }
}

internal data class ExtInf(
    /** Lower-cased keys, as written values. */
    val attributes: Map<String, String>,
    val name: String,
)

/** `#EXTM3U` attributes that apply to channels without their own value. */
internal data class M3UHeader(
    val attributes: Map<String, String> = emptyMap(),
) {
    val epgUrls: List<String> =
        splitUrls(attributes["x-tvg-url"]).ifEmpty { splitUrls(attributes["url-tvg"]) }
    val tvgShift: Double? = attributes["tvg-shift"]?.trim()?.toDoubleOrNull()

    private fun splitUrls(value: String?): List<String> =
        value?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct().orEmpty()
}

internal sealed interface StanzaResult {
    data class Built(val channel: IPTVChannel) : StanzaResult
    data class Skipped(val reason: SkipReason) : StanzaResult
}

internal object M3UChannelBuilder {

    private val ATTRIBUTE = Regex("([A-Za-z0-9_-]+)\\s*=\\s*\"([^\"]*)\"")
    private val DURATION = Regex("^\\s*(-?\\d+(?:\\.\\d+)?)")

    private val ATTRIBUTE_ALIASES = mapOf(
        "ch-number" to "tvg-chno",
        "catchup-type" to "catchup",
        "tvg-rec" to "catchup-days",
    )

    fun parseAttributes(text: String): Map<String, String> {
        val attributes = LinkedHashMap<String, String>()
        for (match in ATTRIBUTE.findAll(text)) {
            attributes[match.groupValues[1].lowercase()] = match.groupValues[2]
        }
        return attributes
    }

    /**
     * The name is the text after the first comma that is outside a quoted value, so both
     * `tvg-name="A, B"` and `,Channel, with comma` survive.
     */
    fun parseExtInf(line: String): ExtInf {
        val body = line.substringAfter(':')
        var inQuote = false
        var comma = -1
        for (i in body.indices) {
            when (body[i]) {
                '"' -> inQuote = !inQuote
                ',' -> if (!inQuote) {
                    comma = i
                    break
                }
            }
        }
        val head = if (comma >= 0) body.substring(0, comma) else body
        val attributes = LinkedHashMap<String, String>()
        DURATION.find(head)?.let { attributes["duration"] = it.groupValues[1] }
        attributes.putAll(parseAttributes(head))
        return ExtInf(
            attributes = attributes,
            name = if (comma >= 0) body.substring(comma + 1).trim() else "",
        )
    }

    /** Case-insensitive de-duplication: "News" and "news" share one category (one slug). */
    fun splitGroups(value: String?): List<String> =
        value?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.distinctBy { it.lowercase() }.orEmpty()

    fun slug(value: String): String = value.replace(" ", "_").lowercase()

    /**
     * @param urlLine the raw URL line, possibly with a `|` header suffix
     * @param stickyGroups groups set by an `#EXTGRP` before this stanza
     * @param fallbackName name when neither the `#EXTINF` nor `tvg-name` has one
     * @param id chooses the final channel id from the base id; see [IdAllocator]
     */
    fun build(
        stanza: M3UStanza,
        urlLine: String,
        header: M3UHeader,
        stickyGroups: List<String>,
        fallbackName: String,
        id: (String) -> String,
    ): StanzaResult {
        val raw = urlLine.trim()
        if (raw.startsWith("plugin://", ignoreCase = true)) return StanzaResult.Skipped(SkipReason.KODI_ADDON)
        if (raw.startsWith("@") || stanza.webProp) return StanzaResult.Skipped(SkipReason.WEB_SCRAPING)
        val split = HeaderSuffixParser.split(raw)
        if (split.url.isEmpty()) return StanzaResult.Skipped(SkipReason.NO_URL)

        val attrs = stanza.extInf?.attributes.orEmpty()
        fun attr(key: String): String? = attrs[key]
            ?: ATTRIBUTE_ALIASES.entries.firstOrNull { it.value == key }?.let { attrs[it.key] }

        // tvg-name is for guide matching only (Kodi semantics); it names the channel only
        // when the #EXTINF has no name of its own.
        val name = stanza.extInf?.name?.takeIf { it.isNotEmpty() }
            ?: attr("tvg-name")?.trim()?.takeIf { it.isNotEmpty() }
            ?: fallbackName

        val groups = splitGroups(attr("group-title")).ifEmpty {
            splitGroups(stanza.extGrp).ifEmpty { stickyGroups }
        }
        val primaryGroup = groups.firstOrNull()
        val epgId = attr("tvg-id")?.takeIf { it.isNotBlank() }

        val attributes = LinkedHashMap<String, String>(attrs)
        stanza.kodiProps.forEach { (k, v) -> attributes["kodiprop:$k"] = v }
        stanza.vlcOpts.forEach { (k, v) -> attributes["vlcopt:$k"] = v }

        // Ids are what history and favourites point at, so they follow the pre-F1 rule:
        // tvg-id, else the slug of tvg-name, else of the name. Before F1 the name was cut at
        // the last comma; that id is kept as legacyId so existing rows can be found again.
        val tvgName = attr("tvg-name")?.trim()?.takeIf { it.isNotEmpty() }
        val baseId = epgId ?: slug(tvgName ?: name)
        val legacyId = if (epgId == null && tvgName == null && ',' in name) {
            slug(name.substringAfterLast(',').trim())
        } else {
            null
        }

        return StanzaResult.Built(
            IPTVChannel(
                id = id(baseId),
                legacyId = legacyId,
                name = name,
                url = split.url,
                logoUrl = attr("tvg-logo")?.takeIf { it.isNotBlank() },
                groupTitle = primaryGroup,
                groupId = primaryGroup?.let(::slug),
                epgId = epgId,
                attributes = attributes,
                groups = groups,
                number = attr("tvg-chno")?.trim()?.toIntOrNull(),
                isRadio = attr("radio")?.trim().equals("true", ignoreCase = true),
                isVod = attr("media")?.trim().equals("true", ignoreCase = true) || stanza.vodMarker,
                headers = headers(attrs, stanza, split.headers),
                mimeType = StreamMimeTypes.fromMimeType(stanza.kodiProps["mimetype"])
                    ?: StreamMimeTypes.fromManifestType(stanza.kodiProps["inputstream.adaptive.manifest_type"]),
                drm = KodiDrmParser.parse(stanza.kodiProps),
                catchup = catchup(::attr, header),
                epgShiftHours = attr("tvg-shift")?.trim()?.toDoubleOrNull() ?: header.tvgShift,
            )
        )
    }

    /**
     * Priority, highest first: `|` suffix, `#EXTVLCOPT`, `inputstream.adaptive.*_headers`,
     * then the non-standard attributes some list generators write.
     */
    private fun headers(
        attrs: Map<String, String>,
        stanza: M3UStanza,
        suffix: List<Pair<String, String>>,
    ): Map<String, String> {
        val merged = HeaderMerger()
        (attrs["http-user-agent"] ?: attrs["user-agent"])?.let { merged.put("User-Agent", it) }
        (attrs["http-referrer"] ?: attrs["referrer"])?.let { merged.put("Referer", it) }

        for (key in listOf("common_headers", "manifest_headers", "stream_headers")) {
            stanza.kodiProps["inputstream.adaptive.$key"]?.let {
                merged.putAll(HeaderSuffixParser.parseHeaderList(it))
            }
        }

        stanza.vlcOpts["http-user-agent"]?.let { merged.put("User-Agent", it) }
        (stanza.vlcOpts["http-referrer"] ?: stanza.vlcOpts["http-referer"])?.let { merged.put("Referer", it) }

        merged.putAll(suffix)
        return merged.toMap()
    }

    private fun catchup(attr: (String) -> String?, header: M3UHeader): CatchupSpec? {
        val h = header.attributes
        val timeshiftDays = attr("timeshift")?.trim()?.toDoubleOrNull()?.toInt()
        val days = attr("catchup-days")?.trim()?.toDoubleOrNull()?.toInt()
            ?: (h["catchup-days"] ?: h["tvg-rec"])?.trim()?.toDoubleOrNull()?.toInt()

        // An explicit channel value the app does not know (`disabled`, `none`, empty) turns
        // catch-up off for that channel instead of falling back to the header default.
        val own = attr("catchup")
        if (own != null && CatchupMode.fromKodi(own) == null) return null
        val mode = CatchupMode.fromKodi(own)
            ?: timeshiftDays?.let { CatchupMode.SHIFT }
            ?: CatchupMode.fromKodi(h["catchup"] ?: h["catchup-type"])
            // A bare archive length is SIPTV's shift mode.
            ?: attr("catchup-days")?.let { CatchupMode.SHIFT }
            ?: return null

        return CatchupSpec(
            mode = mode,
            source = (attr("catchup-source") ?: h["catchup-source"])?.takeIf { it.isNotEmpty() },
            days = if (mode == CatchupMode.SHIFT && attr("catchup") == null && timeshiftDays != null) {
                timeshiftDays
            } else {
                days ?: timeshiftDays
            },
            correctionHours = (attr("catchup-correction") ?: h["catchup-correction"])
                ?.trim()?.toDoubleOrNull() ?: 0.0,
        )
    }
}

/**
 * Channel ids are global keys in the database, and playlists repeat `tvg-id`s (an SD and an
 * HD feed of the same channel). The first occurrence keeps the plain id so existing history
 * and favourites still point at it; later ones become `id~2`, `id~3`, … in file order.
 */
internal class IdAllocator {
    private val used = HashSet<String>()

    fun allocate(base: String): String {
        if (used.add(base)) return base
        var n = 2
        while (!used.add("$base~$n")) n++
        return "$base~$n"
    }
}
