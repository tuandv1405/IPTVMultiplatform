package tss.t.tsiptv.usecase.playlist

import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.model.Playlist
import tss.t.tsiptv.core.storage.InMemoryKeyValueStorage
import tss.t.tsiptv.core.storage.KeyValueStorage
import tss.t.tsiptv.core.storage.ext.getCurrentPlayListId

class GetCurrentPlaylistUseCase(
    private val keyValueStorage: KeyValueStorage,
    private val inMemoryKeyValueStorage: InMemoryKeyValueStorage,
    private val iptvDatabase: IPTVDatabase,
) {
    suspend operator fun invoke(): String? {
        return inMemoryKeyValueStorage.getCurrentPlayListId().takeIf {
            !it.isNullOrEmpty()
        } ?: keyValueStorage.getCurrentPlayListId()
    }

    suspend fun playlist(): Playlist? {
        val id = invoke() ?: return null
        return iptvDatabase.getPlaylistById(id)
    }
}
