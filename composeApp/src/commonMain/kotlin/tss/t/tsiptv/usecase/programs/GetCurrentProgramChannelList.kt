package tss.t.tsiptv.usecase.programs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.parser.model.IPTVProgram

class GetCurrentProgramChannelList(
    private val iptvDatabase: IPTVDatabase,
) {
    // Goes through IPTVDatabase (not the DAO) so programmes that two guides both list
    // for one channel are shown once.
    suspend operator fun invoke(channelId: String): List<IPTVProgram> {
        return withContext(Dispatchers.IO) {
            iptvDatabase.getProgramsForChannel(channelId)
        }
    }
}
