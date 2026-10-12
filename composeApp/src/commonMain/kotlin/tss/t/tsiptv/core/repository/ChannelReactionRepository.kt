package tss.t.tsiptv.core.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tss.t.tsiptv.core.storage.KeyValueStorage
import kotlin.time.ExperimentalTime

class ChannelReactionRepository(
    private val storage: KeyValueStorage
) {
    companion object {
        private const val KEY_LIKED_CHANNELS = "liked_channels"
        private const val KEY_DISLIKED_CHANNELS = "disliked_channels"
    }

    private val json = Json { ignoreUnknownKeys = true }

    // Maps channelId to timestamp
    fun observeLikedChannels(): Flow<Map<String, Long>> {
        return storage.observeString(KEY_LIKED_CHANNELS, "{}").map { parseMap(it) }
    }

    fun observeDislikedChannels(): Flow<Map<String, Long>> {
        return storage.observeString(KEY_DISLIKED_CHANNELS, "{}").map { parseMap(it) }
    }

    suspend fun getLikedChannels(): Map<String, Long> {
        val str = storage.getString(KEY_LIKED_CHANNELS, "{}")
        return parseMap(str)
    }

    suspend fun getDislikedChannels(): Map<String, Long> {
        val str = storage.getString(KEY_DISLIKED_CHANNELS, "{}")
        return parseMap(str)
    }

    @OptIn(ExperimentalTime::class)
    suspend fun likeChannel(channelId: String) {
        val liked = getLikedChannels().toMutableMap()
        val disliked = getDislikedChannels().toMutableMap()
        
        var changed = false

        if (disliked.containsKey(channelId)) {
            disliked.remove(channelId)
            storage.putString(KEY_DISLIKED_CHANNELS, json.encodeToString(disliked))
            changed = true
        }

        if (!liked.containsKey(channelId)) {
            liked[channelId] = Clock.System.now().toEpochMilliseconds()
            storage.putString(KEY_LIKED_CHANNELS, json.encodeToString(liked))
        }
    }

    suspend fun dislikeChannel(channelId: String) {
        val liked = getLikedChannels().toMutableMap()
        val disliked = getDislikedChannels().toMutableMap()
        
        var changed = false

        if (liked.containsKey(channelId)) {
            liked.remove(channelId)
            storage.putString(KEY_LIKED_CHANNELS, json.encodeToString(liked))
            changed = true
        }

        if (!disliked.containsKey(channelId)) {
            @OptIn(ExperimentalTime::class)
            disliked[channelId] = Clock.System.now().toEpochMilliseconds()
            storage.putString(KEY_DISLIKED_CHANNELS, json.encodeToString(disliked))
        }
    }

    suspend fun removeLike(channelId: String) {
        val liked = getLikedChannels().toMutableMap()
        if (liked.containsKey(channelId)) {
            liked.remove(channelId)
            storage.putString(KEY_LIKED_CHANNELS, json.encodeToString(liked))
        }
    }

    suspend fun removeDislike(channelId: String) {
        val disliked = getDislikedChannels().toMutableMap()
        if (disliked.containsKey(channelId)) {
            disliked.remove(channelId)
            storage.putString(KEY_DISLIKED_CHANNELS, json.encodeToString(disliked))
        }
    }

    private fun parseMap(jsonStr: String): Map<String, Long> {
        return try {
            if (jsonStr.isEmpty()) emptyMap() else json.decodeFromString(jsonStr)
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
