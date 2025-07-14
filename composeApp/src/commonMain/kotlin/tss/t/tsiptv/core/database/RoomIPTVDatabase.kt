package tss.t.tsiptv.core.database

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import tss.t.tsiptv.core.network.NetworkClient
import tss.t.tsiptv.core.parser.IPTVFormat
import tss.t.tsiptv.core.parser.IPTVParserFactory
import kotlin.time.Duration.Companion.days

/**
 * Room implementation of IPTVDatabase.
 * This implementation uses Room to persist data between app restarts.
 *
 * @property database The Room database instance
 * @property networkClient The network client to use for fetching playlists
 */
class RoomIPTVDatabase(
    private val database: AppDatabase,
    private val networkClient: NetworkClient,
) : IPTVDatabase {
    private val playlistDao = database.playlistDao()
    private val channelDao = database.channelDao()
    private val categoryDao = database.categoryDao()
    private val channelAttributeDao = database.channelAttributeDao()

    override fun getAllChannels(): Flow<List<Channel>> {
        return channelDao.getAllChannels().map { channelEntities ->
            channelEntities.map { it.toChannel() }
        }
    }

    override fun getAllChannelsByPlayListId(playListId: String): Flow<List<Channel>> {
        return channelDao.getChannelsInPlaylist(playListId).map { channelEntities ->
            channelEntities.map { it.toChannel() }
        }
    }

    override suspend fun getCategoriesByPlaylist(playlistId: String): List<Category> {
        return categoryDao.getCategoriesByPlaylist(playlistId).map {
            it.toCategory()
        }
    }

    override suspend fun getChannelById(id: String): Channel? {
        return channelDao.getChannelById(id)?.toChannel()
    }

    override fun getChannelsByCategory(categoryId: String): Flow<List<Channel>> {
        return channelDao.getChannelsByCategory(categoryId).map { channelEntities ->
            channelEntities.map { it.toChannel() }
        }
    }

    override fun searchChannels(query: String): Flow<List<Channel>> {
        return channelDao.searchChannels(query).map { channelEntities ->
            channelEntities.map { it.toChannel() }
        }
    }

    override suspend fun insertChannel(channel: Channel) {
        channelDao.insertChannel(channel.toChannelEntity())
    }

    override suspend fun insertChannels(channels: List<Channel>) {
        channelDao.insertChannels(channels.map { it.toChannelEntity() })
    }

    override suspend fun deleteChannel(channel: Channel) {
        channelDao.deleteChannel(channel.toChannelEntity())
    }

    override suspend fun deleteChannelById(id: String) {
        channelDao.deleteChannelById(id)
    }

    override fun getAllCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategories().map { categoryEntities ->
            categoryEntities.map { it.toCategory() }
        }
    }

    override suspend fun getAllCategoriesByPlayListId(playListId: String): List<Category> {
        return categoryDao.getCategoriesByPlaylist(playListId).map {
            it.toCategory()
        }
    }

    override suspend fun getCategoryById(id: String): Category? {
        return categoryDao.getCategoryById(id)?.toCategory()
    }

    override suspend fun insertCategory(category: Category) {
        categoryDao.insertCategory(category.toCategoryEntity())
    }

    override suspend fun insertCategories(categories: List<Category>) {
        categoryDao.insertCategories(categories.map { it.toCategoryEntity() })
    }

    override suspend fun deleteCategory(category: Category) {
        categoryDao.deleteCategory(category.toCategoryEntity())
    }

    override suspend fun deleteCategoryById(id: String) {
        categoryDao.deleteCategoryById(id)
    }

    override fun getAllPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists().map { playlistEntities ->
            playlistEntities.map { it.toPlaylist() }
        }
    }

    override suspend fun getPlaylistById(id: String): Playlist? {
        val playlist = playlistDao.getPlaylistById(id)?.toPlaylist() ?: return null

        // Check if playlist needs to be updated (older than 1 day)
        val oneDayInMillis = 1.days.inWholeMilliseconds
        val currentTime = Clock.System.now().toEpochMilliseconds()

        if (currentTime - playlist.lastUpdated > oneDayInMillis) {
            // Playlist is too old, update it from the network
            try {
                val content = networkClient.get(playlist.url)
                val format = IPTVParserFactory.detectFormat(content)
                val parser = IPTVParserFactory.createParser(format)
                val parsedPlaylist = parser.parse(content)

                // Delete old channels and categories
                channelDao.deleteChannelsByPlaylist(playlist.id)
                categoryDao.deleteCategoriesByPlaylist(playlist.id)

                // Insert new playlist with updated timestamp
                val updatedPlaylist = playlist.copy(lastUpdated = currentTime)
                insertPlaylist(updatedPlaylist)

                // Insert new categories
                val categories = parsedPlaylist.groups.map { group ->
                    Category(
                        id = group.id,
                        name = group.title,
                        playlistId = playlist.id
                    )
                }
                insertCategories(categories)

                // Insert new channels
                val channels = parsedPlaylist.channels.map { channel ->
                    Channel(
                        id = channel.id,
                        name = channel.name,
                        url = channel.url,
                        logoUrl = channel.logoUrl,
                        categoryId = channel.groupTitle,
                        playlistId = playlist.id,
                        isFavorite = false,
                        lastWatched = null
                    )
                }
                insertChannels(channels)

                // Insert channel attributes
                for (channel in parsedPlaylist.channels) {
                    val attributes = channel.attributes.map { (key, value) ->
                        ChannelAttributeEntity(
                            channelId = channel.id,
                            attrKey = key,
                            attrValue = value
                        )
                    }
                    channelAttributeDao.insertAttributes(attributes)
                }

                return updatedPlaylist
            } catch (e: Exception) {
                println(e.message)
                e.printStackTrace()
                // If update fails, return the existing playlist
                return playlist
            }
        }

        return playlist
    }

    override suspend fun insertPlaylist(playlist: Playlist) {
        playlistDao.insertPlaylist(playlist.toPlaylistEntity())
    }

    override suspend fun insertPlaylists(playlists: List<Playlist>) {
        playlistDao.insertPlaylists(playlists.map { it.toPlaylistEntity() })
    }

    override suspend fun deletePlaylist(playlist: Playlist) {
        playlistDao.deletePlaylist(playlist.toPlaylistEntity())
    }

    override suspend fun deletePlaylistById(id: String) {
        playlistDao.deletePlaylistById(id)
    }

    override suspend fun deleteChannelsInPlaylist(playlistId: String) {
        channelDao.deleteChannelsByPlaylist(playlistId)
    }

    override suspend fun clearAllData() {

    }

    // Extension functions to convert between domain models and entities
    private fun PlaylistEntity.toPlaylist(): Playlist {
        return Playlist(
            id = id,
            name = name,
            url = url,
            lastUpdated = lastUpdated
        )
    }

    private fun Playlist.toPlaylistEntity(): PlaylistEntity {
        return PlaylistEntity(
            id = id,
            name = name,
            url = url,
            lastUpdated = lastUpdated,
            format = IPTVFormat.UNKNOWN.name // This will be updated when the playlist is parsed
        )
    }

    private fun ChannelEntity.toChannel(): Channel {
        return Channel(
            id = id,
            name = name,
            url = url,
            logoUrl = logoUrl,
            categoryId = categoryId,
            playlistId = playlistId,
            isFavorite = isFavorite,
            lastWatched = lastWatched
        )
    }

    private fun Channel.toChannelEntity(): ChannelEntity {
        return ChannelEntity(
            id = id,
            name = name,
            url = url,
            logoUrl = logoUrl,
            categoryId = categoryId,
            playlistId = playlistId,
            isFavorite = isFavorite,
            lastWatched = lastWatched
        )
    }

    private fun CategoryEntity.toCategory(): Category {
        return Category(
            id = id,
            name = name,
            playlistId = playlistId
        )
    }

    private fun Category.toCategoryEntity(): CategoryEntity {
        return CategoryEntity(
            id = id,
            name = name,
            playlistId = playlistId
        )
    }
}
