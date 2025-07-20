package tss.t.tsiptv.core.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import tss.t.tsiptv.core.database.IPTVDatabase
import tss.t.tsiptv.core.model.ChannelHistory
import tss.t.tsiptv.core.database.entity.ChannelWithHistory

/**
 * Implementation of IHistoryRepository that uses IPTVDatabase.
 * This is a singleton repository for managing channel history operations.
 */
class HistoryRepositoryImpl(
    private val database: IPTVDatabase
) : IHistoryRepository {

    override fun getAllPlayedChannelsInPlaylist(playlistId: String): Flow<List<ChannelHistory>> {
        return database.getAllPlayedChannelsInPlaylist(playlistId)
    }

    override suspend fun getLastPlayedChannelInPlaylist(playlistId: String): ChannelWithHistory? {
        return database.getLastPlayedChannelInPlaylist(playlistId)
    }

    override fun getTop3MostPlayedChannelsInPlaylist(playlistId: String): Flow<List<ChannelWithHistory>> {
        return database.getTop3MostPlayedChannelsInPlaylist(playlistId)
    }

    override suspend fun getMostPlayedChannelInPlaylist(playlistId: String): ChannelWithHistory? {
        return database.getMostPlayedChannelInPlaylist(playlistId)
    }

    override suspend fun getLastWatchedChannelWithDetails(playlistId: String?): ChannelWithHistory? {
        return database.getLastWatchedChannelWithDetails(playlistId)
    }

    override fun getAllWatchedChannelsWithDetails(playlistId: String): Flow<List<ChannelWithHistory>> {
        return database.getAllWatchedChannelsWithDetails(playlistId)
    }

    override fun getLastTop3WatchedChannelsWithDetails(playlistId: String?): Flow<List<ChannelWithHistory>> {
        return database.getLastTop3WatchedChannelsWithDetails(playlistId)
    }
}
