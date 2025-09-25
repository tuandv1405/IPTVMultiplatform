package tss.t.tsiptv.usecase.playlist

import tss.t.tsiptv.core.storage.InMemoryKeyValueStorage
import tss.t.tsiptv.core.storage.MultiplatformSettingsKeyValueStorage
import tss.t.tsiptv.core.storage.ext.clearCurrentPlaylist
import tss.t.tsiptv.core.storage.ext.setCurrentPlaylistId

class SetCurrentPlaylistUseCase(
    private val keyValueStorage: MultiplatformSettingsKeyValueStorage,
    private val inMemoryKeyValueStorage: InMemoryKeyValueStorage,
) {
    suspend operator fun invoke(id: String) {
        inMemoryKeyValueStorage.setCurrentPlaylistId(id)
        keyValueStorage.setCurrentPlaylistId(id)
    }
    suspend fun clear() {
        inMemoryKeyValueStorage.clearCurrentPlaylist()
        keyValueStorage.clearCurrentPlaylist()
    }
}
