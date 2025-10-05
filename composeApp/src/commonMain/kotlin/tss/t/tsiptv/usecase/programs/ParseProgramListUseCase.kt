package tss.t.tsiptv.usecase.programs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.parser.EPGParserFactory
import tss.t.tsiptv.core.storage.KeyValueStorage
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ParseProgramListUseCase(
    private val networkClient: NetworkClient,
    private val iptvDatabase: IPTVDatabase,
    private val keyValueStorage: KeyValueStorage,
) {
    @OptIn(ExperimentalTime::class)
    suspend operator fun invoke(
        playListId: String,
        epgUrl: String,
    ) = withContext(Dispatchers.IO) {
        val content = networkClient.getManualGzipIfNeed(
            epgUrl,
            mapOf("Content-Encoding" to "gzip")
        )
        val epgParser = EPGParserFactory.createParserForContent(content)
        val epg = epgParser.parse(content)
        iptvDatabase.deleteProgramsForPlaylist(playListId)
        iptvDatabase.insertPrograms(epg, playListId)
        keyValueStorage.putLong(playListId, Clock.System.now().toEpochMilliseconds())
        return@withContext epg
    }
}
