package tss.t.tsiptv.usecase.programs

import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.storage.InMemoryKeyValueStorage
import tss.t.tsiptv.core.storage.MultiplatformSettingsKeyValueStorage
import tss.t.tsiptv.core.storage.ext.getCurrentPlayListId

class GetCurrentProgramChannelList(
    private val keyValueStorage: MultiplatformSettingsKeyValueStorage,
    private val inMemoryKeyValueStorage: InMemoryKeyValueStorage,
    private val iptvDatabase: IPTVDatabase
) {
    suspend operator fun invoke(): String? {
        iptvDatabase.programDao
            .getProgramsForChannel()
        return inMemoryKeyValueStorage.getCurrentPlayListId().takeIf {
            !it.isNullOrEmpty()
        } ?: keyValueStorage.getCurrentPlayListId()
    }
}
