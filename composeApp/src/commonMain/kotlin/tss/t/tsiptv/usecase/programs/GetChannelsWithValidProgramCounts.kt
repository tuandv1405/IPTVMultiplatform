package tss.t.tsiptv.usecase.programs

import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.database.entity.ChannelWithProgramCount
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class GetChannelsWithValidProgramCounts(
    private val iptvDatabase: IPTVDatabase,
) {
    @OptIn(ExperimentalTime::class)
    suspend operator fun invoke(playlistId: String): List<ChannelWithProgramCount> {
        return iptvDatabase.programDao
            .getChannelsWithValidProgramCounts(
                playlistId = playlistId,
                timeStamp = Clock.System.now().toEpochMilliseconds()
            )
    }
}
