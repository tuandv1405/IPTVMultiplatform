package tss.t.tsiptv.core.database.entity

import kotlinx.serialization.Serializable

/**
 * Data class to hold the result of the query for channels with their valid program counts.
 */
@Serializable
data class ChannelWithProgramCount(
    val channelId: String,
    val programCount: Int,
    val name: String?,
    val categoryId: String?,
    val logoUrl: String?,
    val isFavorite: String?,
    /** The playlist whose guide this row counts (the per-channel programme list filters by it). */
    val playlistId: String? = null,
)
