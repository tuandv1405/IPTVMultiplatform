package tss.t.tsiptv.core.parser.iptv.m3u

import tss.t.tsiptv.core.parser.IPTVParser
import tss.t.tsiptv.core.parser.model.IPTVChannel
import tss.t.tsiptv.core.parser.model.IPTVFormat
import tss.t.tsiptv.core.parser.model.IPTVGroup
import tss.t.tsiptv.core.parser.model.IPTVPlaylist
import tss.t.tsiptv.core.parser.model.SkipReason
import tss.t.tsiptv.core.parser.model.SkippedEntry
import tss.t.tsiptv.core.parser.model.exception.IPTVParserException

/**
 * Implementation of IPTVParser for M3U, in the dialect of Kodi's PVR IPTV Simple Client
 * (docs/research/kodi.md §1): header guide URLs and defaults, quote-aware names, `;` groups,
 * sticky `#EXTGRP`, `#KODIPROP`, `#EXTVLCOPT`, `|` header suffixes and per-channel DRM.
 */
class M3UParser : IPTVParser {
    override fun parse(content: String): IPTVPlaylist {
        val lines = content.removePrefix("﻿").lines()

        // Accept playlists that omit #EXTM3U, or open with #KODIPROP lines or comments, while
        // still rejecting text that merely happens to mention #EXTINF after some prose.
        val meaningful = lines.map { it.trim().dropWhile { c -> c.code == 0xFEFF } }.filter { it.isNotEmpty() }
        val firstEntry = meaningful.indexOfFirst { it.startsWith("#EXTM3U") || it.startsWith("#EXTINF") }
        if (firstEntry < 0 || meaningful.take(firstEntry).any { !it.startsWith("#") }) {
            throw IPTVParserException("Invalid M3U format: missing #EXTM3U header")
        }

        val channels = mutableListOf<IPTVChannel>()
        val skipped = mutableListOf<SkippedEntry>()
        val ids = IdAllocator()
        var playlistName = "IPTV Playlist"
        var header = M3UHeader()
        var stickyGroups = emptyList<String>()

        // Directives seen since the last URL line. Some lists put #KODIPROP before #EXTINF,
        // so the stanza starts at whichever comes first.
        var stanza: M3UStanza? = null

        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> Unit

                trimmed.startsWith("#EXTM3U") -> {
                    header = M3UHeader(header.attributes + M3UChannelBuilder.parseAttributes(trimmed))
                }

                trimmed.startsWith("#EXTINF:") -> {
                    val current = stanza
                    if (current?.extInf != null) {
                        skipped += SkippedEntry(SkipReason.NO_URL, current.lineNumber)
                        stanza = null
                    }
                    val open = stanza ?: M3UStanza(lineNumber).also { stanza = it }
                    open.extInf = M3UChannelBuilder.parseExtInf(trimmed)
                }

                trimmed.startsWith("#EXTGRP:") -> {
                    val value = trimmed.substringAfter(':').trim()
                    val open = stanza
                    if (open?.extInf != null) {
                        open.extGrp = value
                    } else {
                        // Before an #EXTINF it is a begin directive for every following channel.
                        stickyGroups = M3UChannelBuilder.splitGroups(value)
                    }
                }

                trimmed.startsWith("#KODIPROP:") ->
                    (stanza ?: M3UStanza(lineNumber).also { stanza = it }).addKodiProp(trimmed)

                trimmed.startsWith("#EXTVLCOPT") ->
                    (stanza ?: M3UStanza(lineNumber).also { stanza = it }).addVlcOpt(trimmed)

                trimmed.startsWith("#WEBPROP:") ->
                    (stanza ?: M3UStanza(lineNumber).also { stanza = it }).webProp = true

                trimmed.startsWith("#EXT-X-PLAYLIST-TYPE:") -> {
                    if (trimmed.substringAfter(':').trim().equals("VOD", ignoreCase = true)) {
                        (stanza ?: M3UStanza(lineNumber).also { stanza = it }).vodMarker = true
                    }
                }

                trimmed.startsWith("#PLAYLIST:") -> playlistName = trimmed.substringAfter(':').trim()

                trimmed.startsWith("#") -> Unit

                else -> {
                    val current = stanza
                    // A URL with no #EXTINF is not a channel entry in an M3U; ignore it, as before.
                    if (current?.extInf != null) {
                        when (val result = M3UChannelBuilder.build(
                            stanza = current,
                            urlLine = trimmed,
                            header = header,
                            stickyGroups = stickyGroups,
                            fallbackName = UNKNOWN_CHANNEL,
                            id = ids::allocate,
                        )) {
                            is StanzaResult.Built -> channels += result.channel
                            is StanzaResult.Skipped -> skipped += SkippedEntry(result.reason, current.lineNumber)
                        }
                    }
                    stanza = null
                }
            }
        }
        stanza?.takeIf { it.extInf != null }?.let {
            skipped += SkippedEntry(SkipReason.NO_URL, it.lineNumber)
        }

        return IPTVPlaylist(
            name = playlistName,
            channels = channels,
            groups = groupsOf(channels),
            epgUrl = header.epgUrls.firstOrNull(),
            epgUrls = header.epgUrls,
            skipped = skipped,
        )
    }

    override fun getSupportedFormat(): IPTVFormat {
        return IPTVFormat.M3U
    }

    companion object {
        const val UNKNOWN_CHANNEL = "Unknown channel"

        internal fun groupsOf(channels: List<IPTVChannel>): List<IPTVGroup> =
            channels.asSequence()
                .flatMap { it.groups }
                .distinct()
                .map { IPTVGroup(id = M3UChannelBuilder.slug(it), title = it) }
                .distinctBy { it.id }
                .toList()
    }
}

/**
 * Two or more stream URLs, one per line, with no `#EXTINF`. Each URL is a channel named
 * after the last segment of its path.
 */
class PlainUrlListParser : IPTVParser {
    override fun parse(content: String): IPTVPlaylist {
        val channels = mutableListOf<IPTVChannel>()
        val skipped = mutableListOf<SkippedEntry>()
        val ids = IdAllocator()
        var stanza: M3UStanza? = null
        var streamNumber = 0

        content.removePrefix("﻿").lines().forEachIndexed { index, line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> Unit
                trimmed.startsWith("#KODIPROP:") ->
                    (stanza ?: M3UStanza(index + 1).also { stanza = it }).addKodiProp(trimmed)

                trimmed.startsWith("#EXTVLCOPT") ->
                    (stanza ?: M3UStanza(index + 1).also { stanza = it }).addVlcOpt(trimmed)

                trimmed.startsWith("#") -> Unit
                else -> {
                    streamNumber++
                    val current = stanza ?: M3UStanza(index + 1)
                    val url = HeaderSuffixParser.split(trimmed).url
                    when (val result = M3UChannelBuilder.build(
                        stanza = current,
                        urlLine = trimmed,
                        header = M3UHeader(),
                        stickyGroups = emptyList(),
                        fallbackName = nameFromUrl(url) ?: "Stream $streamNumber",
                        id = ids::allocate,
                    )) {
                        is StanzaResult.Built -> channels += result.channel
                        is StanzaResult.Skipped -> skipped += SkippedEntry(result.reason, current.lineNumber)
                    }
                    stanza = null
                }
            }
        }
        return IPTVPlaylist(
            name = "IPTV Playlist",
            channels = channels,
            groups = emptyList(),
            skipped = skipped,
        )
    }

    override fun getSupportedFormat(): IPTVFormat = IPTVFormat.M3U_PLAIN

    companion object {
        /** Last path segment without its extension: `…/live/news-hd.m3u8?x=1` → `news-hd`. */
        fun nameFromUrl(url: String): String? {
            val path = url.substringAfter("://", url)
                .substringBefore('?')
                .substringBefore('#')
                .substringAfter('/', "")
            val segment = path.trimEnd('/').substringAfterLast('/')
            val decoded = HeaderSuffixParser.percentDecode(segment)
            val base = if ('.' in decoded) decoded.substringBeforeLast('.') else decoded
            return base.trim().takeIf { it.isNotEmpty() }
        }
    }
}
