package tss.t.tsiptv.core.model

import kotlinx.serialization.Serializable

/**
 * Data class representing a channel.
 *
 * @property id The unique ID of the channel
 * @property name The name of the channel
 * @property url The URL of the channel
 * @property logoUrl The URL of the channel's logo
 * @property categoryId The ID of the category the channel belongs to
 * @property playlistId The ID of the playlist the channel belongs to
 * @property isFavorite Whether the channel is a favorite
 * @property lastWatched The timestamp when the channel was last watched, or null if never watched
 */
@Serializable
data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val categoryId: String? = null,
    val playlistId: String,
    val isFavorite: Boolean = false,
    val lastWatched: Long? = null
)

