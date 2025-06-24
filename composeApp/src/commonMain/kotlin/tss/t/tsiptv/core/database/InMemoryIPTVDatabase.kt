package tss.t.tsiptv.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * A simple in-memory implementation of IPTVDatabase.
 * This implementation stores data in memory and doesn't persist it between app restarts.
 * It's useful for testing and as a placeholder until platform-specific implementations are created.
 */
class InMemoryIPTVDatabase : IPTVDatabase {
    private val channels = MutableStateFlow<Map<String, Channel>>(emptyMap())
    private val categories = MutableStateFlow<Map<String, Category>>(emptyMap())
    private val playlists = MutableStateFlow<Map<String, Playlist>>(emptyMap())

    override fun getAllChannels(): Flow<List<Channel>> {
        return channels.map { it.values.toList() }
    }

    override suspend fun getChannelById(id: String): Channel? {
        return channels.value[id]
    }

    override fun getChannelsByCategory(categoryId: String): Flow<List<Channel>> {
        return channels.map { channelMap ->
            channelMap.values.filter { it.categoryId == categoryId }
        }
    }

    override fun searchChannels(query: String): Flow<List<Channel>> {
        return channels.map { channelMap ->
            channelMap.values.filter {
                it.name.contains(query, ignoreCase = true)
            }
        }
    }

    override suspend fun insertChannel(channel: Channel) {
        channels.value = channels.value + (channel.id to channel)
    }

    override suspend fun insertChannels(channels: List<Channel>) {
        this.channels.value = this.channels.value + channels.associateBy { it.id }
    }

    override suspend fun deleteChannel(channel: Channel) {
        deleteChannelById(channel.id)
    }

    override suspend fun deleteChannelById(id: String) {
        channels.value = channels.value - id
    }

    override fun getAllCategories(): Flow<List<Category>> {
        return categories.map { it.values.toList() }
    }

    override suspend fun getCategoryById(id: String): Category? {
        return categories.value[id]
    }

    override suspend fun insertCategory(category: Category) {
        categories.value = categories.value + (category.id to category)
    }

    override suspend fun insertCategories(categories: List<Category>) {
        this.categories.value = this.categories.value + categories.associateBy { it.id }
    }

    override suspend fun deleteCategory(category: Category) {
        deleteCategoryById(category.id)
    }

    override suspend fun deleteCategoryById(id: String) {
        categories.value = categories.value - id
    }

    override fun getAllPlaylists(): Flow<List<Playlist>> {
        return playlists.map { it.values.toList() }
    }

    override suspend fun getPlaylistById(id: String): Playlist? {
        return playlists.value[id]
    }

    override suspend fun insertPlaylist(playlist: Playlist) {
        playlists.value = playlists.value + (playlist.id to playlist)
    }

    override suspend fun insertPlaylists(playlists: List<Playlist>) {
        this.playlists.value = this.playlists.value + playlists.associateBy { it.id }
    }

    override suspend fun deletePlaylist(playlist: Playlist) {
        deletePlaylistById(playlist.id)
    }

    override suspend fun deletePlaylistById(id: String) {
        playlists.value = playlists.value - id
    }

    override suspend fun clearAllData() {
        channels.value = emptyMap()
        categories.value = emptyMap()
        playlists.value = emptyMap()
    }
}
