package tss.t.tsiptv.usecase.programs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.database.entity.toIPTVProgram
import tss.t.tsiptv.core.parser.model.IPTVProgram

class GetCurrentProgramChannelList(
    private val iptvDatabase: IPTVDatabase,
) {
    suspend operator fun invoke(channelId: String): List<IPTVProgram> {
        return withContext(Dispatchers.IO) {
            iptvDatabase.programDao
                .getProgramsForChannel(channelId)
                .map {
                    it.toIPTVProgram()
                }
        }
    }
}
