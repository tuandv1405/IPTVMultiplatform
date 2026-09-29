package tss.t.tsiptv.core.parser

import tss.t.tsiptv.TestAssets
import tss.t.tsiptv.core.parser.iptv.m3u.M3UParser
import tss.t.tsiptv.core.parser.iptv.m3u.PlainUrlListParser
import tss.t.tsiptv.core.parser.model.IPTVChannel
import tss.t.tsiptv.core.parser.model.IPTVPlaylist
import tss.t.tsiptv.core.parser.model.SkipReason
import tss.t.tsiptv.core.parser.model.playback.CatchupMode
import tss.t.tsiptv.core.parser.model.playback.CatchupSpec
import tss.t.tsiptv.core.parser.model.playback.StreamMimeTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PVR IPTV Simple Client dialect: docs/prd-kodi-m3u-compat.md, AC-K1 … AC-K10. */
class KodiM3UParserTest {

    private fun parse(file: String): IPTVPlaylist = M3UParser().parse(TestAssets.read("kodi/$file"))

    private fun IPTVPlaylist.channel(id: String): IPTVChannel =
        channels.firstOrNull { it.id == id } ?: error("No channel $id in ${channels.map { it.id }}")

    private fun IPTVPlaylist.named(name: String): IPTVChannel =
        channels.firstOrNull { it.name == name } ?: error("No channel named $name")

    // AC-K1
    @Test
    fun referencePlaylistParsesToNineChannels() {
        val playlist = parse("reference.m3u")
        assertEquals(9, playlist.channels.size)

        val x = playlist.channel("channel-x")
        assertEquals("Channel X", x.name)
        assertTrue(x.isRadio)
        assertEquals(10, x.number)
        assertEquals(-3.5, x.epgShiftHours)
        assertEquals("channel-x", x.epgId)

        val xHd = playlist.channel("channel-x~2")
        assertEquals("Channel X HD", xHd.name)
        assertEquals(listOf("Entertainment", "HD Channels"), xHd.groups)
        assertEquals("Entertainment", xHd.groupTitle)
        assertEquals("channel-x", xHd.epgId)
        // Header tvg-shift for a channel without its own.
        assertEquals(-4.5, xHd.epgShiftHours)

        // #EXTGRP inside the stanza gives the group.
        assertEquals(listOf("Entertainment"), playlist.channel("channel-y").groups)
        assertTrue(playlist.named("Channel M").isVod)
        assertEquals(listOf("http://guide.example.com/guide.xml"), playlist.epgUrls)
        assertTrue(playlist.skipped.isEmpty())
    }

    @Test
    fun referencePlaylistKeepsRawKodiAndVlcProperties() {
        val x = parse("reference.m3u").channel("channel-x")
        assertEquals("val", x.attributes["kodiprop:key"])
        assertEquals("745", x.attributes["vlcopt:program"])
        assertEquals("Channel_X", x.attributes["tvg-name"])
        assertTrue(x.headers.isEmpty(), "program= is not a header")
    }

    @Test
    fun referencePlaylistCatchupUsesHeaderCorrection() {
        val playlist = parse("reference.m3u")
        assertEquals(
            CatchupSpec(CatchupMode.DEFAULT, "http://stream.example.com/live/catchup-b.ts&cutv={Y}-{m}-{d}T{H}:{M}:{S}", 3, -2.5),
            playlist.named("Channel B").catchup
        )
        assertEquals(-4.0, playlist.named("Channel C").catchup?.correctionHours)
        assertEquals(CatchupMode.FLUSSONIC_TS, playlist.named("Channel G").catchup?.mode)
        assertEquals(CatchupMode.XC, playlist.named("Channel I").catchup?.mode)
        assertNull(playlist.named("Channel M").catchup)
    }

    // AC-K2
    @Test
    fun xTvgUrlListIsSplitAndWinsOverUrlTvg() {
        val playlist = parse("headers.m3u")
        assertEquals(listOf("https://a.example/1.xml", "https://a.example/2.xml.gz"), playlist.epgUrls)
        assertEquals("https://a.example/1.xml", playlist.epgUrl)

        val urlTvgOnly = M3UParser().parse(
            "#EXTM3U url-tvg=\"https://a.example/u.xml\"\n#EXTINF:-1,A\nhttps://cdn.example.com/a.m3u8"
        )
        assertEquals(listOf("https://a.example/u.xml"), urlTvgOnly.epgUrls)
    }

    // AC-K3
    @Test
    fun nameIsTakenAfterTheFirstUnquotedComma() {
        val channel = parse("groups.m3u").channels.first()
        assertEquals("Channel, with comma", channel.name)
        assertEquals(listOf("News"), channel.groups)
        assertEquals("A, B", channel.attributes["tvg-name"])
    }

    @Test
    fun tvgNameNamesTheChannelOnlyWhenTheExtinfHasNoName() {
        val playlist = parse("groups.m3u")
        assertEquals("Guide name", playlist.channels.last().name)
        val unknown = M3UParser().parse("#EXTM3U\n#EXTINF:-1,\nhttps://cdn.example.com/a.m3u8")
        assertEquals("Unknown channel", unknown.channels.single().name)
    }

    // AC-K4
    @Test
    fun attributeKeysAreLowerCasedAndAliased() {
        val channel = parse("groups.m3u").channel("X")
        assertEquals("X", channel.attributes["tvg-id"])
        assertEquals("X", channel.epgId)
        assertEquals(5, channel.number)
        assertNull(parse("groups.m3u").named("Bad number").number)
    }

    // AC-K5
    @Test
    fun extgrpBeforeAStanzaIsStickyUntilReset() {
        val playlist = parse("groups.m3u")
        assertEquals(listOf("Movies"), playlist.named("Sticky one").groups)
        assertEquals(listOf("Movies"), playlist.named("Sticky two").groups)
        assertEquals(listOf("Sports"), playlist.named("Own group").groups)
        assertEquals(emptyList(), playlist.named("After reset").groups)
        assertNull(playlist.named("After reset").groupTitle)
    }

    @Test
    fun groupTitleIsSplitTrimmedAndDeduplicated() {
        val playlist = parse("groups.m3u")
        val channel = playlist.named("Split groups")
        assertEquals(listOf("A", "B"), channel.groups)
        assertTrue(channel.isVod, "#EXT-X-PLAYLIST-TYPE:VOD in the stanza")
        assertTrue(playlist.groups.any { it.title == "B" })
    }

    // AC-K6
    @Test
    fun pipeSuffixIsStrippedAndDecoded() {
        val playlist = parse("headers.m3u")
        val encoded = playlist.channel("encoded")
        assertEquals("https://cdn.example.com/a.m3u8", encoded.url)
        assertEquals(mapOf("User-Agent" to "Mozilla/5.0", "Referer" to "https://example.com/"), encoded.headers)

        val plain = playlist.channel("plain")
        assertEquals("https://cdn.example.com/b.m3u8", plain.url)
        assertEquals(mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0)"), plain.headers)
    }

    // AC-K7
    @Test
    fun suffixBeatsExtvlcopt() {
        val channel = parse("headers.m3u").channel("priority")
        assertEquals("UA2", channel.headers["User-Agent"])
        assertEquals("R", channel.headers["Referer"])
        assertEquals("UA1", channel.attributes["vlcopt:http-user-agent"])
    }

    // AC-K8
    @Test
    fun kodipropHeadersAndMimeType() {
        val channel = parse("headers.m3u").channel("kodiprop")
        assertEquals(mapOf("X-Token" to "abc="), channel.headers)
        assertEquals(StreamMimeTypes.DASH, channel.mimeType)
    }

    @Test
    fun headerLayersMergeInPriorityOrder() {
        val channel = parse("headers.m3u").channel("layers")
        assertEquals(
            mapOf(
                "User-Agent" to "KodiUA",
                "Referer" to "https://vlc.example.com/",
                "X-Common" to "1",
                "X-Custom" to "yes",
                "Cookie" to "a=1",
            ),
            channel.headers
        )
    }

    // AC-K9
    @Test
    fun addonAndScrapingEntriesAreSkippedWithReasons() {
        val playlist = parse("skips.m3u")
        assertEquals(listOf("ok-1", "ok-2"), playlist.channels.map { it.id })
        val reasons = playlist.skipped.groupingBy { it.reason }.eachCount()
        assertEquals(1, reasons[SkipReason.KODI_ADDON])
        assertEquals(1, reasons[SkipReason.WEB_SCRAPING])
        assertEquals(2, reasons[SkipReason.NO_URL])
        assertEquals(4, playlist.skipped.first { it.reason == SkipReason.KODI_ADDON }.lineNumber)
    }

    // AC-K10
    @Test
    fun catchupAttributesAndDefaults() {
        val playlist = parse("groups.m3u")
        assertEquals(
            CatchupSpec(CatchupMode.APPEND, "&cutv={Y}-{m}-{d}T{H}:{M}:{S}", 3, 0.0),
            playlist.named("Append").catchup
        )
        // Header catchup="shift" for a channel with none of its own.
        assertEquals(CatchupMode.SHIFT, playlist.named("Sticky one").catchup?.mode)
        val timeshift = playlist.named("Legacy timeshift").catchup
        assertEquals(CatchupMode.SHIFT, timeshift?.mode)
        assertEquals(2, timeshift?.days)
    }

    @Test
    fun noCatchupWithoutAttributesOrHeaderDefault() {
        val playlist = M3UParser().parse("#EXTM3U\n#EXTINF:-1,A\nhttps://cdn.example.com/a.m3u8")
        assertNull(playlist.channels.single().catchup)
        assertFalse(playlist.channels.single().isRadio)
    }

    @Test
    fun kodipropBeforeExtinfBelongsToTheStanza() {
        val playlist = M3UParser().parse(
            """
            #EXTM3U
            #KODIPROP:mimetype=application/dash+xml
            #EXTINF:-1,Early props
            https://cdn.example.com/a/manifest
            """.trimIndent()
        )
        assertEquals(StreamMimeTypes.DASH, playlist.channels.single().mimeType)
    }

    @Test
    fun byteOrderMarkBeforeHeaderIsAccepted() {
        val playlist = M3UParser().parse("﻿#EXTM3U\n#EXTINF:-1,A\nhttps://cdn.example.com/a.m3u8")
        assertEquals(1, playlist.channels.size)
    }

    @Test
    fun plainUrlListNamesChannelsAfterTheirPath() {
        val playlist = PlainUrlListParser().parse(TestAssets.read("kodi/plain-list.txt"))
        assertEquals(listOf("news-hd", "Stream 2"), playlist.channels.map { it.name })
        assertEquals("https://cdn.example.com/live/news-hd.m3u8?token=abc", playlist.channels.first().url)
    }
}
