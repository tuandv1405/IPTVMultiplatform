package tss.t.tsiptv.core.parser.strm

import tss.t.tsiptv.core.parser.IPTVParser
import tss.t.tsiptv.core.parser.iptv.m3u.IdAllocator
import tss.t.tsiptv.core.parser.iptv.m3u.M3UChannelBuilder
import tss.t.tsiptv.core.parser.iptv.m3u.M3UHeader
import tss.t.tsiptv.core.parser.iptv.m3u.M3UStanza
import tss.t.tsiptv.core.parser.iptv.m3u.StanzaResult
import tss.t.tsiptv.core.parser.model.IPTVFormat
import tss.t.tsiptv.core.parser.model.IPTVPlaylist
import tss.t.tsiptv.core.parser.model.SkipReason
import tss.t.tsiptv.core.parser.model.exception.IPTVParserException

/**
 * Thrown for a `.strm` that points at a Kodi add-on. Unlike an M3U entry, there is nothing
 * else in the file to import, so this is an error rather than a skipped entry.
 */
class StrmKodiAddonException : IPTVParserException("The .strm file points to a Kodi add-on")

/**
 * Kodi `.strm`: `#KODIPROP` / `#EXTVLCOPT` lines and one URL, optionally with a `|` header
 * suffix. It becomes a one-channel playlist named after the file.
 *
 * @param name File name without extension; names both the channel and the playlist
 */
class StrmParser(private val name: String) : IPTVParser {

    override fun parse(content: String): IPTVPlaylist {
        var stanza: M3UStanza? = null
        var urlLine: String? = null
        content.removePrefix("﻿").lines().forEachIndexed { index, line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> Unit
                trimmed.startsWith("#KODIPROP:") ->
                    (stanza ?: M3UStanza(index + 1).also { stanza = it }).addKodiProp(trimmed)

                trimmed.startsWith("#EXTVLCOPT") ->
                    (stanza ?: M3UStanza(index + 1).also { stanza = it }).addVlcOpt(trimmed)

                trimmed.startsWith("#") -> Unit
                urlLine == null -> urlLine = trimmed
                else -> throw IPTVParserException("A .strm file holds exactly one URL")
            }
        }
        val url = urlLine ?: throw IPTVParserException("The .strm file has no URL")

        val result = M3UChannelBuilder.build(
            stanza = stanza ?: M3UStanza(1),
            urlLine = url,
            header = M3UHeader(),
            stickyGroups = emptyList(),
            fallbackName = name,
            id = IdAllocator()::allocate,
        )
        val channel = when (result) {
            is StanzaResult.Built -> result.channel
            is StanzaResult.Skipped -> when (result.reason) {
                SkipReason.KODI_ADDON -> throw StrmKodiAddonException()
                else -> throw IPTVParserException("The .strm file has no playable URL")
            }
        }
        return IPTVPlaylist(
            name = name,
            channels = listOf(channel),
            groups = emptyList(),
        )
    }

    override fun getSupportedFormat(): IPTVFormat = IPTVFormat.STRM
}
