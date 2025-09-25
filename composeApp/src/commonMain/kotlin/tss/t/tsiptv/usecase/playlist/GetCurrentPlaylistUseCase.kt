package tss.t.tsiptv.usecase.playlist

import tss.t.tsiptv.core.storage.InMemoryKeyValueStorage
import tss.t.tsiptv.core.storage.MultiplatformSettingsKeyValueStorage
import tss.t.tsiptv.core.storage.ext.getCurrentPlayListId

class GetCurrentPlaylistUseCase(
    private val keyValueStorage: MultiplatformSettingsKeyValueStorage,
    private val inMemoryKeyValueStorage: InMemoryKeyValueStorage,
) {
    suspend operator fun invoke(): String? {
        return inMemoryKeyValueStorage.getCurrentPlayListId().takeIf {
            !it.isNullOrEmpty()
        } ?: keyValueStorage.getCurrentPlayListId()
    }
}
