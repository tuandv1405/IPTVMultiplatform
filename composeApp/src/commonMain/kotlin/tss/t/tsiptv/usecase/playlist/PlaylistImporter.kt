package tss.t.tsiptv.usecase.playlist

import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.model.Category
import tss.t.tsiptv.core.model.Channel
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.model.PlaylistSourceType
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.parser.EPGParserFactory
import tss.t.tsiptv.core.parser.IPTVParserFactory
import tss.t.tsiptv.core.parser.iptv.m3u.M3UChannelBuilder
import tss.t.tsiptv.core.parser.model.IPTVChannel
import tss.t.tsiptv.core.parser.model.IPTVFormat
import tss.t.tsiptv.core.parser.model.IPTVPlaylist
import tss.t.tsiptv.core.parser.model.IPTVProgram
import tss.t.tsiptv.core.parser.model.SkipReason
import tss.t.tsiptv.core.parser.strm.StrmKodiAddonException
import tss.t.tsiptv.core.parser.strm.StrmParser
import tss.t.tsiptv.core.tsiptv.TsiptvRefreshResult
import tss.t.tsiptv.core.tsiptv.TsiptvSourcePreview
import tss.t.tsiptv.core.tsiptv.TsiptvSourceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

/** Import failures the UI explains with its own message. */
enum class ImportError {
    HTML_PAGE,
    UNKNOWN_FORMAT,
    NEEDS_UPDATE,
    FILE_TOO_LARGE,

    /** The picker returned a file that could not be read (permission, provider error). */
    FILE_READ,
    STRM_KODI_ADDON,

    /** Refresh was asked for a playlist that was read from a file. Not a failure as such. */
    FILE_REFRESH,
}

class PlaylistImportException(val error: ImportError) : Exception(error.name)

/** F3: a source refresh could not fetch or read its root; the stored copy stays. [code] never holds a URL. */
class TsiptvRefreshFailedException(val code: String) : Exception("Source refresh failed: $code")

data class ImportResult(
    val playlist: Playlist,
    val channelCount: Int,
    val format: IPTVFormat,
    val skipped: Map<SkipReason, Int>,
)

sealed interface ImportOutcome {
    data class Imported(val result: ImportResult) : ImportOutcome

    /**
     * The link is an HLS manifest: one stream, not a list. The user decides whether to add it
     * as a one-channel playlist ([PlaylistImporter.importSingleStream]).
     */
    data class SingleStream(val name: String, val url: String) : ImportOutcome

    /**
     * F3: the link or file is a TS IPTV Source. Nothing is stored yet: the UI shows the preview,
     * then [tss.t.tsiptv.core.tsiptv.TsiptvSourceService.resolve] and `store` on Import.
     */
    class SourcePreview(val preview: TsiptvSourcePreview) : ImportOutcome
}

/**
 * The one way playlists get into the database: first import, manual refresh and the daily
 * auto-refresh all go download (or read file) → detect → parse → map → store, so they store
 * exactly the same rows for the same playlist.
 *
 * Network failures and parser errors propagate; format problems the user can act on are
 * [PlaylistImportException]s.
 */
@OptIn(ExperimentalTime::class)
class PlaylistImporter(
    private val database: IPTVDatabase,
    private val networkClient: NetworkClient,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** F3: TS IPTV Source support. Without it a source is reported as [ImportError.NEEDS_UPDATE]. */
    private val sources: TsiptvSourceService? = null,
) {

    // Callers are view models on the main thread; parsing a large playlist there would freeze
    // the UI. Every entry point hops to a background dispatcher.

    suspend fun importFromUrl(name: String, url: String): ImportOutcome =
        withContext(Dispatchers.Default) { importFromUrlImpl(name, url) }

    /** See [importFromFileImpl]. */
    suspend fun importFromFile(name: String, displayName: String, bytes: ByteArray): ImportResult =
        withContext(Dispatchers.Default) { importFromFileImpl(name, displayName, bytes) }

    /**
     * A picked file: a TS IPTV Source becomes a preview ([ImportOutcome.SourcePreview]); any other
     * format is imported as by [importFromFile].
     */
    suspend fun importFileOutcome(name: String, displayName: String, bytes: ByteArray): ImportOutcome =
        withContext(Dispatchers.Default) {
            val sources = sources
            if (sources != null && TsiptvSourceService.looksLikeSource(bytes.copyOf(minOf(bytes.size, SNIFF_BYTES)))) {
                ImportOutcome.SourcePreview(sources.preview(bytes, rootUrl = null, fileName = displayName))
            } else {
                ImportOutcome.Imported(importFromFileImpl(name, displayName, bytes))
            }
        }

    suspend fun importSingleStream(name: String, url: String): ImportResult =
        withContext(Dispatchers.Default) { importSingleStreamImpl(name, url) }

    /** See [refreshImpl]. */
    suspend fun refresh(playlistId: String): ImportResult =
        withContext(Dispatchers.Default) { refreshImpl(playlistId) }

    /** See [refreshIfStaleImpl]. */
    suspend fun refreshIfStale(playlistId: String): Playlist? =
        withContext(Dispatchers.Default) { refreshIfStaleImpl(playlistId) }

    /** See [fetchEpgImpl]. */
    suspend fun fetchEpg(playlistId: String, epgUrls: List<String>): Int? =
        withContext(Dispatchers.Default) { fetchEpgImpl(playlistId, epgUrls) }

    private suspend fun importFromUrlImpl(name: String, url: String): ImportOutcome {
        val content = when (val sources = sources) {
            null -> networkClient.getManualGzipIfNeed(url)
            // One download: capped at 5 MiB once it sniffs as a TS IPTV Source (spec §2).
            else -> when (val fetched = sources.fetchLink(url)) {
                is TsiptvSourceService.LinkFetch.Source -> return ImportOutcome.SourcePreview(fetched.preview)
                is TsiptvSourceService.LinkFetch.Other -> try {
                    IPTVParserFactory.decode(fetched.bytes)
                } catch (_: okio.IOException) {
                    throw tss.t.tsiptv.core.network.HttpStatusException(200, "Truncated or corrupt gzip body")
                }
                // Larger than the sniffed head and not a source: downloaded as F1 always did.
                TsiptvSourceService.LinkFetch.NotSource -> networkClient.getManualGzipIfNeed(url)
            }
        }
        val playlistId = urlPlaylistId(url)
        return when (val parsed = parse(content, fallbackName = name)) {
            is Parsed.SingleStream -> ImportOutcome.SingleStream(name, url)
            is Parsed.Channels -> ImportOutcome.Imported(
                store(
                    Playlist(
                        id = playlistId,
                        name = name,
                        url = url,
                        lastUpdated = now(),
                        sourceType = PlaylistSourceType.URL,
                    ),
                    parsed,
                )
            )
        }
    }

    /**
     * A file is read once and not kept, so the playlist is keyed by its display name:
     * importing a file with the same name replaces the earlier import.
     *
     * @param name Name the user typed; the file name (without extension) when blank
     */
    private suspend fun importFromFileImpl(name: String, displayName: String, bytes: ByteArray): ImportResult {
        if (bytes.size > MAX_FILE_BYTES) throw PlaylistImportException(ImportError.FILE_TOO_LARGE)
        val baseName = displayName.substringBeforeLast('.').ifBlank { displayName }
        // A truncated .gz throws from okio; report it as an unreadable file, not a crash.
        val content = try {
            IPTVParserFactory.decode(bytes)
        } catch (_: okio.IOException) {
            throw PlaylistImportException(ImportError.FILE_READ)
        }
        val isStrm = displayName.endsWith(".strm", ignoreCase = true)
        // A .strm names its playlist after the file, as Kodi names the video.
        val playlistName = if (isStrm) baseName else name.ifBlank { baseName }
        val parsed = parse(content, fallbackName = if (isStrm) baseName else playlistName)
        if (parsed !is Parsed.Channels) {
            // An HLS manifest in a file has no URL to add as a channel.
            throw PlaylistImportException(ImportError.UNKNOWN_FORMAT)
        }
        return store(
            Playlist(
                id = filePlaylistId(displayName),
                name = playlistName,
                url = "file:$displayName",
                lastUpdated = now(),
                sourceType = PlaylistSourceType.FILE,
            ),
            parsed,
        )
    }

    /** "Add as channel" for an HLS manifest link: a one-channel playlist named by the user. */
    private suspend fun importSingleStreamImpl(name: String, url: String): ImportResult =
        store(
            Playlist(
                id = urlPlaylistId(url),
                name = name,
                url = url,
                lastUpdated = now(),
                sourceType = PlaylistSourceType.URL,
            ),
            singleStream(name, url),
        )

    /**
     * Downloads and re-parses a URL playlist.
     *
     * @throws PlaylistImportException with [ImportError.FILE_REFRESH] for a file playlist,
     * without any network call
     */
    private suspend fun refreshImpl(playlistId: String): ImportResult {
        val playlist = database.getPlaylistById(playlistId)
            ?: throw IllegalArgumentException("No playlist $playlistId")
        val sources = sources
        if (sources != null && playlist.format == IPTVFormat.TSIPTV_SOURCE.name) {
            // A source refreshes its root (links only) and every include, ignoring refreshHours.
            return when (val result = sources.refresh(playlistId, force = true)) {
                is TsiptvRefreshResult.Stored -> {
                    // The includes were refreshed from the stored root, but the root itself failed.
                    result.rootError?.let { throw TsiptvRefreshFailedException(it) }
                    ImportResult(
                        playlist = result.result.playlist,
                        channelCount = result.result.channelCount,
                        format = IPTVFormat.TSIPTV_SOURCE,
                        skipped = emptyMap(),
                    )
                }
                TsiptvRefreshResult.Unchanged, TsiptvRefreshResult.AdultPending -> ImportResult(
                    playlist = database.getPlaylistById(playlistId) ?: playlist,
                    channelCount = database.getAllChannelsByPlayListId(playlistId).first().size,
                    format = IPTVFormat.TSIPTV_SOURCE,
                    skipped = emptyMap(),
                )
                is TsiptvRefreshResult.Failed -> throw TsiptvRefreshFailedException(result.code)
            }
        }
        if (playlist.sourceType == PlaylistSourceType.FILE) {
            throw PlaylistImportException(ImportError.FILE_REFRESH)
        }
        val content = networkClient.getManualGzipIfNeed(playlist.url)
        val parsed = when (val result = parse(content, fallbackName = playlist.name)) {
            is Parsed.Channels -> result
            // Added earlier with "Add as channel": keep it a one-channel playlist.
            is Parsed.SingleStream -> singleStream(playlist.name, playlist.url)
        }
        // A server that answers `[]` or an empty list for a while (maintenance, an expired
        // account) must not wipe what the user has; keep the stored copy instead.
        if (parsed.playlist.channels.isEmpty() &&
            database.getAllChannelsByPlayListId(playlistId).first().isNotEmpty()
        ) {
            throw PlaylistImportException(ImportError.UNKNOWN_FORMAT)
        }
        return store(playlist.copy(lastUpdated = now()), parsed)
    }

    /**
     * The daily auto-refresh, run when a playlist is opened. File playlists are never
     * refreshed; failures keep the stored copy, as a stale list beats an empty one.
     *
     * @return the playlist as stored after the attempt, or null when there is none
     */
    private suspend fun refreshIfStaleImpl(playlistId: String): Playlist? {
        val playlist = database.getPlaylistById(playlistId) ?: return null
        val sources = sources
        if (sources != null && playlist.format == IPTVFormat.TSIPTV_SOURCE.name) {
            // Root older than 24 h, or includes older than their refreshHours (PRD §5 "When").
            try {
                sources.refreshIfDue(playlistId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A stale source beats an empty one.
            }
            return database.getPlaylistById(playlistId) ?: playlist
        }
        if (playlist.sourceType != PlaylistSourceType.URL) return playlist
        if (now() - playlist.lastUpdated <= STALE_AFTER_MS) return playlist
        return try {
            refreshImpl(playlistId).playlist
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            playlist
        }
    }

    /**
     * Fetches every guide the playlist declares (at most [MAX_EPG_URLS], one after another,
     * each failing on its own), merges them and replaces the playlist's programmes.
     *
     * @return the number of programmes stored, or null when no guide could be read
     */
    private suspend fun fetchEpgImpl(playlistId: String, epgUrls: List<String>): Int? {
        val programs = mutableListOf<IPTVProgram>()
        var anyRead = false
        for (url in epgUrls.filter { it.isNotBlank() }.distinct().take(MAX_EPG_URLS)) {
            try {
                val content = networkClient.getManualGzipIfNeed(url, mapOf("Content-Encoding" to "gzip"))
                programs += EPGParserFactory.createParserForContent(content).parse(content)
                anyRead = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // One broken guide must not cost the others.
            }
        }
        if (!anyRead) return null
        val merged = programs.distinctBy { it.channelId to it.startTime }
        database.deleteProgramsForPlaylist(playlistId)
        database.insertPrograms(merged, playlistId)
        return merged.size
    }

    private sealed interface Parsed {
        data class Channels(val playlist: IPTVPlaylist, val format: IPTVFormat) : Parsed
        data object SingleStream : Parsed
    }

    private fun parse(rawContent: String, fallbackName: String): Parsed {
        // Downloads keep a BOM that the JSON parsers reject; drop it once for every format.
        val content = rawContent.removePrefix("﻿")
        return when (val format = IPTVParserFactory.detectFormat(content)) {
            IPTVFormat.HTML -> throw PlaylistImportException(ImportError.HTML_PAGE)
            IPTVFormat.TSIPTV_SOURCE -> throw PlaylistImportException(ImportError.NEEDS_UPDATE)
            IPTVFormat.UNKNOWN -> throw PlaylistImportException(ImportError.UNKNOWN_FORMAT)
            IPTVFormat.HLS_MANIFEST -> Parsed.SingleStream
            IPTVFormat.STRM -> try {
                Parsed.Channels(StrmParser(fallbackName).parse(content), format)
            } catch (_: StrmKodiAddonException) {
                throw PlaylistImportException(ImportError.STRM_KODI_ADDON)
            }

            else -> Parsed.Channels(IPTVParserFactory.createParser(format).parse(content), format)
        }
    }

    private fun singleStream(name: String, url: String) = Parsed.Channels(
        IPTVPlaylist(
            name = name,
            channels = listOf(
                IPTVChannel(
                    id = M3UChannelBuilder.slug(name).ifBlank { "stream" },
                    name = name,
                    url = url,
                )
            ),
            groups = emptyList(),
        ),
        IPTVFormat.HLS_MANIFEST,
    )

    private suspend fun store(base: Playlist, parsed: Parsed.Channels): ImportResult {
        val source = parsed.playlist
        val playlist = base.copy(
            epgUrl = source.epgUrls.firstOrNull(),
            epgUrls = source.epgUrls,
            format = parsed.format.name,
        )
        val channels = source.channels.mapIndexed { index, channel ->
            Channel(
                id = channel.id,
                name = channel.name,
                url = channel.url,
                logoUrl = channel.logoUrl,
                categoryId = channel.groupTitle,
                playlistId = playlist.id,
                number = channel.number,
                groups = channel.groups,
                isRadio = channel.isRadio,
                isVod = channel.isVod,
                headers = channel.headers,
                mimeType = channel.mimeType,
                drm = channel.drm,
                catchup = channel.catchup,
                epgShiftHours = channel.epgShiftHours,
                sortIndex = index,
            )
        }
        val categories = source.groups.map {
            Category(id = it.id, name = it.title, playlistId = playlist.id)
        }
        database.replacePlaylistContent(
            playlist = playlist,
            categories = categories,
            channels = channels,
            attributes = source.channels.associate { it.id to it.attributes },
            legacyIds = source.channels.mapNotNull { c -> c.legacyId?.let { c.id to it } }.toMap(),
        )
        return ImportResult(
            playlist = playlist,
            channelCount = channels.size,
            format = parsed.format,
            skipped = source.skipped.groupingBy { it.reason }.eachCount(),
        )
    }

    companion object {
        const val MAX_FILE_BYTES = 20 * 1024 * 1024
        const val MAX_EPG_URLS = 5
        private const val SNIFF_BYTES = 64 * 1024
        private val STALE_AFTER_MS = 1.days.inWholeMilliseconds

        /** Same id as before F1, so re-importing a link keeps its history and favourites. */
        fun urlPlaylistId(url: String): String = url.hashCode().toString()

        fun filePlaylistId(displayName: String): String = "file:" + displayName.hashCode()
    }
}
