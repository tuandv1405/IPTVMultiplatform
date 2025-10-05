package tss.t.tsiptv.core.storage.ext

import tss.t.tsiptv.core.storage.KeyValueStorage

internal const val KEY_CURRENT_PLAYLIST_ID = "key:current_playlist_id"

suspend fun KeyValueStorage.getCurrentPlayListId(): String? {
    return getString(KEY_CURRENT_PLAYLIST_ID)
}

suspend fun KeyValueStorage.setCurrentPlaylistId(id: String) {
    putString(KEY_CURRENT_PLAYLIST_ID, id)
}

suspend fun KeyValueStorage.clearCurrentPlaylist() {
    remove(KEY_CURRENT_PLAYLIST_ID)
}
