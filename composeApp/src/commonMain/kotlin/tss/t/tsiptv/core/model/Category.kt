package tss.t.tsiptv.core.model

/**
 * Data class representing a category.
 *
 * @property id The unique ID of the category
 * @property name The name of the category
 * @property playlistId The ID of the playlist the category belongs to
 */
data class Category(
    val id: String,
    val name: String,
    val playlistId: String
)